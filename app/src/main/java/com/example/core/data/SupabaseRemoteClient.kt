package com.example.core.data

import com.example.BuildConfig
import com.example.core.model.ChatMessage
import com.example.core.model.Conversation
import com.example.core.model.ItemReport
import com.example.core.model.ReportImage
import com.example.core.model.RecoveryRequest
import com.example.core.model.ReportFlag
import com.example.core.model.ReportStatus
import com.example.core.model.ReportType
import com.example.core.model.SystemLocationSettings
import com.example.core.model.UserProfile
import com.example.core.model.UserRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class SupabaseAuthSession(
    val accessToken: String,
    val userId: String,
    val email: String,
    val fullName: String
)

/**
 * عميل الاتصال الفعلي بخدمة Supabase (Auth + PostgREST + Storage + RPC).
 * يقرأ المفاتيح بأمان من BuildConfig (المحقونة عبر Secrets Panel / .env) دون أي مفاتيح سرية داخل الكود.
 * لا يستخدم أبداً مفتاح service_role، بل يعتمد حصراً على anon key + JWT للمستخدم المصادق عليه لاحترام سياسات RLS.
 */
class SupabaseRemoteClient(
    private val rawBaseUrl: String = BuildConfig.SUPABASE_URL,
    private val rawAnonKey: String = BuildConfig.SUPABASE_ANON_KEY
) {
    val baseUrl: String = rawBaseUrl.trim().trimEnd('/')
    val anonKey: String = rawAnonKey.trim()

    val isConfigured: Boolean
        get() = baseUrl.startsWith("https://") &&
            !baseUrl.contains("your-project-ref") &&
            anonKey.isNotBlank() &&
            anonKey != "your-supabase-anon-public-key"

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun signUpWithEmail(
        email: String,
        password: String,
        fullName: String
    ): Result<SupabaseAuthSession> = withContext(Dispatchers.IO) {
        if (!isConfigured) {
            return@withContext Result.failure(IllegalStateException("لم يتم إعداد مفاتيح Supabase بعد."))
        }
        try {
            val payload = JSONObject().apply {
                put("email", email.trim())
                put("password", password)
                put("data", JSONObject().apply {
                    put("full_name", fullName.trim())
                    put("city", SystemLocationSettings.DEFAULT_CITY_AR)
                })
            }
            val request = Request.Builder()
                .url("$baseUrl/auth/v1/signup")
                .addHeader("apikey", anonKey)
                .addHeader("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            httpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException(parseSupabaseError(bodyStr, "فشل إنشاء الحساب في Supabase")))
                }
                val json = JSONObject(bodyStr)
                val accessToken = json.optString("access_token", "")
                val userObj = json.optJSONObject("user") ?: json
                val userId = userObj.optString("id", "")
                if (userId.isBlank()) {
                    return@withContext Result.failure(IOException("تم إرسال طلب التسجيل. يرجى تأكيد البريد الإلكتروني إذا كان التفعيل مفعلاً في مشروع Supabase."))
                }
                Result.success(
                    SupabaseAuthSession(
                        accessToken = accessToken,
                        userId = userId,
                        email = email.trim(),
                        fullName = fullName.trim()
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signInWithEmail(
        email: String,
        password: String
    ): Result<SupabaseAuthSession> = withContext(Dispatchers.IO) {
        if (!isConfigured) {
            return@withContext Result.failure(IllegalStateException("لم يتم إعداد مفاتيح Supabase بعد."))
        }
        try {
            val payload = JSONObject().apply {
                put("email", email.trim())
                put("password", password)
            }
            val request = Request.Builder()
                .url("$baseUrl/auth/v1/token?grant_type=password")
                .addHeader("apikey", anonKey)
                .addHeader("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            httpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException(parseSupabaseError(bodyStr, "بيانات تسجيل الدخول غير صحيحة")))
                }
                val json = JSONObject(bodyStr)
                val accessToken = json.getString("access_token")
                val userObj = json.getJSONObject("user")
                val userId = userObj.getString("id")
                val meta = userObj.optJSONObject("user_metadata")
                val fullName = meta?.optString("full_name")?.takeIf { it.isNotBlank() }
                    ?: email.substringBefore("@")
                Result.success(
                    SupabaseAuthSession(
                        accessToken = accessToken,
                        userId = userId,
                        email = email.trim(),
                        fullName = fullName
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchUserProfile(userId: String, accessToken: String): Result<UserProfile> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/rest/v1/profiles?id=eq.$userId&select=*")
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException(parseSupabaseError(bodyStr, "تعذر جلب الملف الشخصي")))
                }
                val arr = JSONArray(bodyStr)
                if (arr.length() == 0) {
                    return@withContext Result.failure(IOException("لم يتم العثور على الملف الشخصي"))
                }
                val obj = arr.getJSONObject(0)
                Result.success(
                    UserProfile(
                        id = obj.getString("id"),
                        email = "",
                        fullName = obj.optString("full_name", "مستخدم وينها"),
                        avatarUrl = obj.optString("avatar_url").takeIf { it.isNotBlank() && it != "null" },
                        city = SystemLocationSettings.DEFAULT_CITY_AR,
                        neighborhood = obj.optString("neighborhood").takeIf { it.isNotBlank() && it != "null" },
                        role = UserRole.fromDb(obj.optString("role", "user")),
                        isSuspended = obj.optBoolean("is_suspended", false),
                        privacyHideLastSeen = obj.optBoolean("privacy_hide_last_seen", false),
                        allowMatchNotifications = obj.optBoolean("allow_match_notifications", true),
                        deletionRequestedAt = obj.optString("deletion_requested_at").takeIf { it.isNotBlank() && it != "null" }
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchReportsFromSupabase(accessToken: String?): Result<List<ItemReport>> = withContext(Dispatchers.IO) {
        try {
            val token = accessToken?.takeIf { it.isNotBlank() } ?: anonKey
            val request = Request.Builder()
                .url("$baseUrl/rest/v1/reports?city=eq.%D8%AD%D9%85%D8%B5&select=*,profiles(full_name),report_images(*)&order=created_at.desc")
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException(parseSupabaseError(bodyStr, "فشل جلب البلاغات من Supabase")))
                }
                val arr = JSONArray(bodyStr)
                val list = mutableListOf<ItemReport>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val profileObj = obj.optJSONObject("profiles")
                    val imagesArr = obj.optJSONArray("report_images") ?: JSONArray()
                    val images = mutableListOf<ReportImage>()
                    val reportId = obj.getString("id")
                    for (j in 0 until imagesArr.length()) {
                        val img = imagesArr.getJSONObject(j)
                        images.add(
                            ReportImage(
                                id = img.optString("id", "$j"),
                                reportId = reportId,
                                storagePath = img.optString("storage_path", ""),
                                publicUrl = img.optString("public_url", ""),
                                isBlurredForPrivacy = img.optBoolean("is_blurred_for_privacy", false)
                            )
                        )
                    }
                    list.add(
                        ItemReport(
                            id = reportId,
                            userId = obj.getString("user_id"),
                            userName = profileObj?.optString("full_name") ?: "مستخدم من حمص",
                            reportType = ReportType.fromDb(obj.optString("report_type", "lost")),
                            title = obj.getString("title"),
                            categoryId = obj.getString("category_id"),
                            description = obj.getString("description"),
                            city = SystemLocationSettings.DEFAULT_CITY_AR,
                            neighborhood = obj.optString("neighborhood", "مركز المدينة"),
                            incidentDateText = obj.optString("incident_date", "").take(10),
                            incidentEpochMillis = System.currentTimeMillis(),
                            distinguishingMarks = obj.optString("distinguishing_marks", ""),
                            status = ReportStatus.fromDb(obj.optString("status", "open")),
                            images = images,
                            isSensitiveMasked = obj.optBoolean("is_sensitive_masked", true),
                            createdAtText = obj.optString("created_at", "").take(10),
                            createdAtEpochMillis = System.currentTimeMillis() - (i * 60_000L)
                        )
                    )
                }
                Result.success(list)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun insertReportToSupabase(
        report: ItemReport,
        accessToken: String
    ): Result<ItemReport> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("user_id", report.userId)
                put("report_type", report.reportType.dbValue)
                put("title", report.title)
                put("category_id", report.categoryId)
                put("description", report.description)
                put("city", SystemLocationSettings.DEFAULT_CITY_AR)
                put("neighborhood", report.neighborhood)
                put("distinguishing_marks", report.distinguishingMarks)
                put("status", report.status.dbValue)
                put("is_sensitive_masked", report.isSensitiveMasked)
            }

            val request = Request.Builder()
                .url("$baseUrl/rest/v1/reports")
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Prefer", "return=representation")
                .addHeader("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            httpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException(parseSupabaseError(bodyStr, "رفض الخادم نشر البلاغ (تحقق من سياسات RLS وحقول حمص)")))
                }
                val arr = JSONArray(bodyStr)
                val createdId = if (arr.length() > 0) arr.getJSONObject(0).optString("id", report.id) else report.id

                // تشغيل دالة المطابقة التلقائية في قاعدة البيانات RPC
                triggerServerMatchComputation(createdId, accessToken)

                Result.success(report.copy(id = createdId))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadImageToSupabaseStorage(
        userId: String,
        fileName: String,
        bytes: ByteArray,
        accessToken: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val storagePath = "$userId/$fileName"
            val request = Request.Builder()
                .url("$baseUrl/storage/v1/object/report-images/$storagePath")
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("x-upsert", "true")
                .post(bytes.toRequestBody("image/jpeg".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException(parseSupabaseError(bodyStr, "فشل رفع الصورة إلى Supabase Storage")))
                }
                val publicUrl = "$baseUrl/storage/v1/object/public/report-images/$storagePath"
                Result.success(publicUrl)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun triggerServerMatchComputation(reportId: String, accessToken: String) {
        try {
            val payload = JSONObject().apply { put("target_report_id", reportId) }
            val request = Request.Builder()
                .url("$baseUrl/rest/v1/rpc/compute_report_matches")
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()
            httpClient.newCall(request).execute().close()
        } catch (_: Exception) {
            // يتم احتساب المطابقات أيضاً محلياً لضمان الاستمرارية
        }
    }


    suspend fun setVerificationQuestions(
        reportId: String,
        questions: List<Pair<String, String>>,
        accessToken: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val items = JSONArray()
            questions.take(3).forEach { (question, answer) ->
                items.put(JSONObject().put("question", question.trim()).put("answer", answer.trim()))
            }
            val payload = JSONObject().put("p_report_id", reportId).put("p_questions", items)
            val request = Request.Builder().url("$baseUrl/rest/v1/rpc/set_report_verification_questions")
                .addHeader("apikey", anonKey).addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(jsonMediaType)).build()
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) Result.failure(IOException(parseSupabaseError(body, "تعذر حفظ أسئلة التحقق.")))
                else Result.success(Unit)
            }
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun fetchVerificationQuestions(reportId: String, accessToken: String): Result<List<Pair<String, String>>> =
        withContext(Dispatchers.IO) {
            try {
                val encodedId = java.net.URLEncoder.encode(reportId, "UTF-8")
                val request = Request.Builder().url("$baseUrl/rest/v1/report_verification_questions?report_id=eq.$encodedId&select=id,question_text,position&order=position.asc")
                    .addHeader("apikey", anonKey).addHeader("Authorization", "Bearer $accessToken").get().build()
                httpClient.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) return@withContext Result.failure(IOException(parseSupabaseError(body, "تعذر تحميل أسئلة التحقق.")))
                    val arr = JSONArray(body)
                    Result.success((0 until arr.length()).map { i ->
                        val item = arr.getJSONObject(i)
                        item.getString("id") to item.getString("question_text")
                    })
                }
            } catch (e: Exception) { Result.failure(e) }
        }

    suspend fun submitRecoveryRequest(
        reportId: String,
        answers: List<Pair<String, String>>,
        message: String,
        accessToken: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val answerArray = JSONArray()
            answers.forEach { (questionId, answer) ->
                answerArray.put(JSONObject().put("question_id", questionId).put("answer", answer.trim()))
            }
            val payload = JSONObject().put("p_report_id", reportId).put("p_answers", answerArray).put("p_message", message.trim())
            val request = Request.Builder().url("$baseUrl/rest/v1/rpc/submit_recovery_request")
                .addHeader("apikey", anonKey).addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(jsonMediaType)).build()
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) Result.failure(IOException(parseSupabaseError(body, "تعذر إرسال طلب الاستعادة.")))
                else {
                    val result = JSONObject(body)
                    when {
                        result.optBoolean("rate_limited") -> Result.failure(SecurityException("وصلت للحد الأقصى لمحاولات التحقق. حاول بعد ساعة."))
                        !result.optBoolean("verified", false) -> Result.failure(SecurityException("إجابات التحقق غير صحيحة. راجع التفاصيل وحاول مجدداً."))
                        result.optString("request_id").isBlank() -> Result.failure(IOException("لم يتم إنشاء طلب الاستعادة."))
                        else -> Result.success(result.optString("request_id"))
                    }
                }
            }
        } catch (e: Exception) { Result.failure(e) }
    }



    suspend fun submitReportFlag(
        reporterId: String,
        reportId: String?,
        conversationId: String?,
        reason: String,
        details: String,
        accessToken: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val safeDetails = if (conversationId != null && !conversationId.matches(
                    Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
                )) {
                (details.trim() + " (مرجع المحادثة داخل التطبيق: " + conversationId.take(80) + ")").take(1000)
            } else details.trim()
            val payload = JSONObject().put("reporter_id", reporterId)
                .put("reason", reason.trim()).put("details", safeDetails)
            if (reportId != null && reportId.matches(Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"))) payload.put("report_id", reportId)
            if (conversationId != null && conversationId.matches(
                    Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
                )) payload.put("conversation_id", conversationId)
            val request = Request.Builder().url("$baseUrl/rest/v1/reports_flags")
                .addHeader("apikey", anonKey).addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Content-Type", "application/json").addHeader("Prefer", "return=minimal")
                .post(payload.toString().toRequestBody(jsonMediaType)).build()
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) Result.failure(IOException(parseSupabaseError(body, "تعذر إرسال البلاغ للمراجعة.")))
                else Result.success(Unit)
            }
        } catch (e: Exception) { Result.failure(e) }
    }


    suspend fun reviewReportFlag(flagId: String, action: String, notes: String, accessToken: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val payload = JSONObject().put("p_flag_id", flagId).put("p_action", action).put("p_notes", notes)
                val request = Request.Builder().url("$baseUrl/rest/v1/rpc/review_report_flag")
                    .addHeader("apikey", anonKey).addHeader("Authorization", "Bearer $accessToken")
                    .addHeader("Content-Type", "application/json")
                    .post(payload.toString().toRequestBody(jsonMediaType)).build()
                httpClient.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) Result.failure(IOException(parseSupabaseError(body, "تعذر حفظ قرار المراجعة.")))
                    else Result.success(Unit)
                }
            } catch (e: Exception) { Result.failure(e) }
        }

    suspend fun fetchReportFlags(accessToken: String): Result<List<ReportFlag>> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url("$baseUrl/rest/v1/reports_flags?select=*&order=created_at.desc")
                    .addHeader("apikey", anonKey).addHeader("Authorization", "Bearer $accessToken").get().build()
                httpClient.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) return@withContext Result.failure(IOException(parseSupabaseError(body, "تعذر تحميل البلاغات للمراجعة.")))
                    val arr = JSONArray(body)
                    Result.success((0 until arr.length()).map { i ->
                        val o = arr.getJSONObject(i)
                        val reportId = o.optString("report_id").takeIf { it.isNotBlank() && it != "null" }
                        val convId = o.optString("conversation_id").takeIf { it.isNotBlank() && it != "null" }
                        ReportFlag(
                            id = o.optString("id"),
                            reportId = reportId,
                            reportTitle = reportId ?: "محادثة مستخدم",
                            conversationId = convId,
                            reporterId = o.optString("reporter_id"),
                            reporterName = "مستخدم",
                            reason = o.optString("reason"),
                            details = o.optString("details"),
                            status = o.optString("status", "pending"),
                            createdAtText = o.optString("created_at")
                        )
                    })
                }
            } catch (e: Exception) { Result.failure(e) }
        }

    suspend fun fetchRecoveryRequests(reportId: String, accessToken: String): Result<List<RecoveryRequest>> =
        withContext(Dispatchers.IO) {
            try {
                val encodedId = java.net.URLEncoder.encode(reportId, "UTF-8")
                val request = Request.Builder().url("$baseUrl/rest/v1/recovery_requests?report_id=eq.$encodedId&select=*&order=created_at.desc")
                    .addHeader("apikey", anonKey).addHeader("Authorization", "Bearer $accessToken").get().build()
                httpClient.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) return@withContext Result.failure(IOException(parseSupabaseError(body, "تعذر تحميل طلبات الاستعادة.")))
                    val arr = JSONArray(body)
                    Result.success((0 until arr.length()).map { i ->
                        val o = arr.getJSONObject(i)
                        RecoveryRequest(
                            id = o.optString("id"),
                            reportId = o.optString("report_id"),
                            claimantId = o.optString("claimant_id"),
                            finderId = o.optString("finder_id"),
                            message = o.optString("message"),
                            status = o.optString("status", "pending"),
                            ownerConfirmed = o.optBoolean("owner_confirmed"),
                            finderConfirmed = o.optBoolean("finder_confirmed"),
                            handoverNotes = o.optString("handover_notes"),
                            createdAtText = o.optString("created_at")
                        )
                    })
                }
            } catch (e: Exception) { Result.failure(e) }
        }

    suspend fun reviewRecoveryRequest(requestId: String, accept: Boolean, accessToken: String): Result<Unit> =
        callRecoveryRpc("review_recovery_request", JSONObject().put("p_request_id", requestId).put("p_accept", accept), accessToken)

    suspend fun confirmRecoveryHandover(requestId: String, notes: String, accessToken: String): Result<Unit> =
        callRecoveryRpc("confirm_recovery_handover", JSONObject().put("p_request_id", requestId).put("p_notes", notes), accessToken)

    private suspend fun callRecoveryRpc(name: String, payload: JSONObject, accessToken: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url("$baseUrl/rest/v1/rpc/$name")
                    .addHeader("apikey", anonKey).addHeader("Authorization", "Bearer $accessToken")
                    .addHeader("Content-Type", "application/json")
                    .post(payload.toString().toRequestBody(jsonMediaType)).build()
                httpClient.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) Result.failure(IOException(parseSupabaseError(body, "تعذر تحديث طلب الاستعادة.")))
                    else Result.success(Unit)
                }
            } catch (e: Exception) { Result.failure(e) }
        }

    private fun parseSupabaseError(rawBody: String, fallback: String): String {
        return try {
            val json = JSONObject(rawBody)
            json.optString("msg")
                .ifBlank { json.optString("message") }
                .ifBlank { json.optString("error_description") }
                .ifBlank { fallback }
        } catch (_: Exception) {
            fallback
        }
    }
}
