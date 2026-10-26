package com.example.core.data

import android.content.Context
import android.content.SharedPreferences
import com.example.core.domain.ValidationResult
import com.example.core.domain.WaynhaMatchingEngine
import com.example.core.domain.WaynhaSecurityValidator
import com.example.core.model.AdminAuditLog
import com.example.core.model.AppNotification
import com.example.core.model.ChatMessage
import com.example.core.model.Conversation
import com.example.core.model.InitialCategories
import com.example.core.model.ItemReport
import com.example.core.model.MatchReviewStatus
import com.example.core.model.NotificationType
import com.example.core.model.RecoveryConfirmation
import com.example.core.model.RecoveryRequest
import com.example.core.model.ReportCategory
import com.example.core.model.ReportFlag
import com.example.core.model.ReportImage
import com.example.core.model.ReportMatch
import com.example.core.model.ReportStatus
import com.example.core.model.ReportType
import com.example.core.model.SystemLocationSettings
import com.example.core.model.UserProfile
import com.example.core.model.UserRole
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * مستودع بيانات WAYNHA الموحد:
 * 1. عند إعداد مفاتيح Supabase في Secrets Panel: يتصل مباشرة بـ Supabase الفعلي ولا يخلط أي بيانات تجريبية مع البيانات الحقيقية.
 * 2. قبل إعداد مفاتيح Supabase: يعمل في وضع المعاينة الواضح ببيانات تجريبية مخصصة لمدينة حمص مع حفظ التعديلات والبلاغات والمحفوظات محلياً (SharedPreferences)
 *    بحيث تبقى البلاغات المحفوظة والمنشورة موجودة بعد إعادة فتح التطبيق.
 * 3. يطبق جميع سياسات الأمان والتحقق من مدينة حمص وصلاحيات RLS والحد من الطلبات المتكررة.
 */
class WaynhaRepository(
    context: Context? = null,
    val remoteClient: SupabaseRemoteClient = SupabaseRemoteClient()
) {
    private val prefs: SharedPreferences? =
        context?.getSharedPreferences("waynha_homs_persistence_v1", Context.MODE_PRIVATE)

    val isLiveSupabaseConnected: Boolean
        get() = remoteClient.isConfigured

    var simulateOfflineMode: Boolean = false
    var simulateImageUploadFailure: Boolean = false

    private var accessToken: String? = null
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

    // الجداول المحفوظة في الذاكرة + التخزين الدائم
    private val profiles = mutableMapOf<String, UserProfile>()
    private val accountPasswords = mutableMapOf<String, String>()
    private val reports = mutableListOf<ItemReport>()
    private val savedReportIdsByUser = mutableMapOf<String, MutableSet<String>>()
    private val reportMatches = mutableListOf<ReportMatch>()
    private val conversations = mutableListOf<Conversation>()
    private val messagesByConversation = mutableMapOf<String, MutableList<ChatMessage>>()
    private val notifications = mutableListOf<AppNotification>()
    private val reportFlags = mutableListOf<ReportFlag>()
    private val blockedUsersByBlocker = mutableMapOf<String, MutableSet<String>>()
    private val recoveryConfirmations = mutableListOf<RecoveryConfirmation>()
    private val recoveryRequests = mutableListOf<RecoveryRequest>()
    private val verificationAnswersByReport = mutableMapOf<String, List<Pair<String, String>>>()
    private val adminAuditLogs = mutableListOf<AdminAuditLog>()
    private val categories = InitialCategories.all.toMutableList()

    private var currentUser: UserProfile? = null

    init {
        if (!isLiveSupabaseConnected) {
            seedExplicitDemoDataForHoms()
            loadPersistedState()
        } else {
            loadPersistedSessionOnly()
        }
    }

    fun getCurrentUser(): UserProfile? = currentUser

    fun getCategories(): List<ReportCategory> = categories.toList()

    fun getAllReports(): List<ItemReport> {
        val viewer = currentUser
        return reports.filter { report ->
            report.city == SystemLocationSettings.DEFAULT_CITY_AR &&
                (report.status != ReportStatus.HIDDEN ||
                    viewer?.id == report.userId ||
                    WaynhaSecurityValidator.canAccessAdminDashboard(viewer))
        }.sortedByDescending { it.createdAtEpochMillis }
    }

    fun getSavedReportIds(userId: String): Set<String> {
        return savedReportIdsByUser[userId]?.toSet() ?: emptySet()
    }

    fun getSavedReports(userId: String): List<ItemReport> {
        val ids = getSavedReportIds(userId)
        return getAllReports().filter { ids.contains(it.id) }
    }

    fun getUserReports(userId: String): List<ItemReport> {
        return reports.filter { it.userId == userId }.sortedByDescending { it.createdAtEpochMillis }
    }

    fun getMatchesForReport(reportId: String): List<ReportMatch> {
        return reportMatches.filter {
            (it.lostReportId == reportId || it.foundReportId == reportId) &&
                it.reviewStatus != MatchReviewStatus.DISMISSED &&
                it.reviewStatus != MatchReviewStatus.ARCHIVED
        }.sortedByDescending { it.similarityScore }
    }

    fun getMatchesForUser(userId: String): List<ReportMatch> {
        val myReportIds = reports.filter { it.userId == userId && it.status.isEligibleForMatching }
            .map { it.id }
            .toSet()
        return reportMatches.filter {
            (myReportIds.contains(it.lostReportId) || myReportIds.contains(it.foundReportId)) &&
                it.reviewStatus != MatchReviewStatus.DISMISSED &&
                it.reviewStatus != MatchReviewStatus.ARCHIVED
        }.sortedByDescending { it.similarityScore }
    }

    fun getReportById(reportId: String): ItemReport? = reports.firstOrNull { it.id == reportId }

    fun getConversationsForUser(user: UserProfile?): List<Conversation> {
        if (user == null) return emptyList()
        val blocked = blockedUsersByBlocker[user.id] ?: emptySet()
        return conversations.filter { conv ->
            WaynhaSecurityValidator.canAccessConversation(user, conv, blocked)
        }
    }

    suspend fun refreshLiveConversations(user: UserProfile?): Result<List<Conversation>> {
        if (!isLiveSupabaseConnected) return Result.success(getConversationsForUser(user))
        val actualUser = user ?: return Result.failure(SecurityException("يرجى تسجيل الدخول أولاً."))
        val token = accessToken?.takeIf { it.isNotBlank() }
            ?: return Result.failure(SecurityException("انتهت جلسة الدخول. سجّل الدخول مجدداً."))
        val result = remoteClient.fetchConversations(actualUser, token)
        result.onSuccess { live ->
            conversations.clear()
            conversations.addAll(live)
        }
        return result
    }

    suspend fun refreshLiveMessages(conversationId: String): Result<List<ChatMessage>> {
        if (!isLiveSupabaseConnected) return Result.success(messagesByConversation[conversationId]?.toList() ?: emptyList())
        val token = accessToken?.takeIf { it.isNotBlank() }
            ?: return Result.failure(SecurityException("انتهت جلسة الدخول. سجّل الدخول مجدداً."))
        val result = remoteClient.fetchMessages(conversationId, token)
        result.onSuccess { messagesByConversation[conversationId] = it.toMutableList() }
        return result
    }

    fun getMessagesForConversation(user: UserProfile?, conversationId: String): Result<List<ChatMessage>> {
        val conv = conversations.firstOrNull { it.id == conversationId }
            ?: return Result.failure(IllegalArgumentException("المحادثة غير موجودة."))
        val blocked = if (user != null) blockedUsersByBlocker[user.id] ?: emptySet() else emptySet()
        if (!WaynhaSecurityValidator.canAccessConversation(user, conv, blocked)) {
            return Result.failure(SecurityException("رفض الوصول (RLS): غير مصرح لك بقراءة محادثات مستخدمين آخرين."))
        }
        val list = messagesByConversation[conversationId]?.toList() ?: emptyList()
        return Result.success(list)
    }

    fun getNotificationsForUser(userId: String): List<AppNotification> {
        return notifications.filter { it.userId == userId }
    }

    fun getBlockedUsers(userId: String): Set<String> {
        return blockedUsersByBlocker[userId]?.toSet() ?: emptySet()
    }

    fun getRecoveryConfirmationsForReport(reportId: String): List<RecoveryConfirmation> {
        return recoveryConfirmations.filter { it.reportId == reportId }
    }

    suspend fun getVerificationQuestions(reportId: String): Result<List<Pair<String, String>>> {
        if (isLiveSupabaseConnected) {
            val token = accessToken?.takeIf { it.isNotBlank() }
                ?: return Result.failure(SecurityException("انتهت جلسة الدخول. سجّل الدخول مجدداً."))
            return remoteClient.fetchVerificationQuestions(reportId, token)
        }
        val saved = verificationAnswersByReport[reportId].orEmpty()
        return Result.success(saved.mapIndexed { index, pair -> "local-$index" to pair.first })
    }

    suspend fun getRecoveryRequestsForReport(reportId: String): Result<List<RecoveryRequest>> {
        if (isLiveSupabaseConnected) {
            val token = accessToken?.takeIf { it.isNotBlank() }
                ?: return Result.failure(SecurityException("انتهت جلسة الدخول. سجّل الدخول مجدداً."))
            return remoteClient.fetchRecoveryRequests(reportId, token)
        }
        return Result.success(recoveryRequests.filter { it.reportId == reportId })
    }

    suspend fun submitRecoveryRequest(
        reportId: String,
        answers: List<Pair<String, String>>,
        message: String
    ): Result<Unit> {
        val user = currentUser ?: return Result.failure(SecurityException("يرجى تسجيل الدخول أولاً."))
        val report = getReportById(reportId) ?: return Result.failure(IllegalArgumentException("البلاغ غير موجود."))
        if (report.reportType != ReportType.FOUND || report.userId == user.id) {
            return Result.failure(IllegalArgumentException("يمكنك طلب استعادة غرض من بلاغ موجود لشخص آخر فقط."))
        }
        if (isLiveSupabaseConnected) {
            val token = accessToken?.takeIf { it.isNotBlank() }
                ?: return Result.failure(SecurityException("انتهت جلسة الدخول. سجّل الدخول مجدداً."))
            return remoteClient.submitRecoveryRequest(reportId, answers, message, token).map { Unit }
        }
        val expected = verificationAnswersByReport[reportId].orEmpty()
        if (expected.isEmpty()) return Result.failure(IllegalStateException("لا توجد أسئلة تحقق لهذا البلاغ."))
        val correct = expected.indices.all { i ->
            val expectedAnswer = expected[i].second.trim().lowercase()
            val supplied = answers.firstOrNull { it.first == "local-$i" }?.second?.trim()?.lowercase().orEmpty()
            supplied == expectedAnswer
        }
        if (!correct) return Result.failure(SecurityException("إجابات التحقق غير صحيحة."))
        val request = RecoveryRequest(
            id = "recovery-${UUID.randomUUID()}",
            reportId = reportId,
            claimantId = user.id,
            finderId = report.userId,
            message = message.trim(),
            createdAtText = nowFormatted()
        )
        recoveryRequests.removeAll { it.reportId == reportId && it.claimantId == user.id }
        recoveryRequests.add(0, request)
        persistState()
        return Result.success(Unit)
    }

    suspend fun reviewRecoveryRequest(requestId: String, accept: Boolean): Result<Unit> {
        val token = accessToken?.takeIf { it.isNotBlank() }
        if (isLiveSupabaseConnected) {
            if (token == null) return Result.failure(SecurityException("انتهت جلسة الدخول. سجّل الدخول مجدداً."))
            return remoteClient.reviewRecoveryRequest(requestId, accept, token)
        }
        val user = currentUser ?: return Result.failure(SecurityException("يرجى تسجيل الدخول أولاً."))
        val idx = recoveryRequests.indexOfFirst { it.id == requestId && it.finderId == user.id && it.status == "pending" }
        if (idx < 0) return Result.failure(SecurityException("هذا الطلب غير متاح للمراجعة."))
        recoveryRequests[idx] = recoveryRequests[idx].copy(status = if (accept) "accepted" else "rejected")
        persistState()
        return Result.success(Unit)
    }

    suspend fun confirmRecoveryHandover(requestId: String, notes: String): Result<Unit> {
        val token = accessToken?.takeIf { it.isNotBlank() }
        if (isLiveSupabaseConnected) {
            if (token == null) return Result.failure(SecurityException("انتهت جلسة الدخول. سجّل الدخول مجدداً."))
            return remoteClient.confirmRecoveryHandover(requestId, notes, token)
        }
        val user = currentUser ?: return Result.failure(SecurityException("يرجى تسجيل الدخول أولاً."))
        val idx = recoveryRequests.indexOfFirst { it.id == requestId && it.status == "accepted" &&
            (it.claimantId == user.id || it.finderId == user.id) }
        if (idx < 0) return Result.failure(SecurityException("طلب الاستعادة غير متاح للتأكيد."))
        val old = recoveryRequests[idx]
        val updated = old.copy(
            ownerConfirmed = old.ownerConfirmed || old.claimantId == user.id,
            finderConfirmed = old.finderConfirmed || old.finderId == user.id,
            handoverNotes = notes.trim().ifBlank { old.handoverNotes }
        )
        val both = updated.ownerConfirmed && updated.finderConfirmed
        recoveryRequests[idx] = updated.copy(status = if (both) "confirmed" else "accepted")
        if (both) {
            reports.replaceAll { if (it.id == old.reportId) it.copy(status = ReportStatus.RECOVERED) else it }
        }
        persistState()
        return Result.success(Unit)
    }

    fun getAllProfilesForAdmin(requester: UserProfile?): Result<List<UserProfile>> {
        if (!WaynhaSecurityValidator.canAccessAdminDashboard(requester)) {
            return Result.failure(SecurityException("رفض الوصول: هذه الصلاحية مخصصة لمديري النظام فقط."))
        }
        return Result.success(profiles.values.toList())
    }

    fun getReportFlagsForAdmin(requester: UserProfile?): Result<List<ReportFlag>> {
        if (!WaynhaSecurityValidator.canAccessAdminDashboard(requester)) {
            return Result.failure(SecurityException("رفض الوصول: غير مصرح لغير المدير بمراجعة البلاغات المخالفة."))
        }
        return Result.success(reportFlags.toList())
    }

    suspend fun refreshLiveReportFlagsForAdmin(requester: UserProfile?): Result<List<ReportFlag>> {
        if (!WaynhaSecurityValidator.canAccessAdminDashboard(requester)) {
            return Result.failure(SecurityException("رفض الوصول: غير مصرح لغير المدير بمراجعة البلاغات المخالفة."))
        }
        if (!isLiveSupabaseConnected) return Result.success(reportFlags.toList())
        val token = accessToken?.takeIf { it.isNotBlank() }
            ?: return Result.failure(SecurityException("انتهت جلسة الدخول. سجّل الدخول مجدداً."))
        val result = remoteClient.fetchReportFlags(token)
        result.onSuccess { liveFlags ->
            reportFlags.clear()
            reportFlags.addAll(liveFlags)
        }
        return result
    }

    fun getAuditLogsForAdmin(requester: UserProfile?): Result<List<AdminAuditLog>> {
        if (!WaynhaSecurityValidator.canAccessAdminDashboard(requester)) {
            return Result.failure(SecurityException("رفض الوصول: سجلات التدقيق متاحة للإدارة فقط."))
        }
        return Result.success(adminAuditLogs.toList())
    }

    // -------------------------------------------------------------------------
    // 1. المصادقة وإدارة الحساب (Auth & Profile)
    // -------------------------------------------------------------------------
    suspend fun registerAccount(
        fullName: String,
        email: String,
        password: String,
        neighborhood: String?
    ): Result<UserProfile> {
        if (simulateOfflineMode) {
            return Result.failure(IOException("لا يوجد اتصال بالإنترنت حالياً. يرجى التحقق من الشبكة والمحاولة مجدداً."))
        }
        val cleanEmail = email.trim().lowercase()
        if (fullName.trim().length < 2) {
            return Result.failure(IllegalArgumentException("يرجى إدخال الاسم الكامل (حرفين على الأقل)."))
        }
        if (!cleanEmail.contains("@") || !cleanEmail.contains(".")) {
            return Result.failure(IllegalArgumentException("يرجى إدخال بريد إلكتروني صحيح."))
        }
        if (password.length < 6) {
            return Result.failure(IllegalArgumentException("كلمة المرور يجب أن تتكون من 6 أحرف أو أرقام على الأقل."))
        }

        if (isLiveSupabaseConnected) {
            val res = remoteClient.signUpWithEmail(cleanEmail, password, fullName.trim())
            return res.map { session ->
                accessToken = session.accessToken
                val profile = UserProfile(
                    id = session.userId,
                    email = session.email,
                    fullName = session.fullName,
                    city = SystemLocationSettings.DEFAULT_CITY_AR,
                    neighborhood = neighborhood,
                    role = UserRole.USER,
                    createdAt = nowFormatted()
                )
                profiles[profile.id] = profile
                currentUser = profile
                persistState()
                profile
            }
        }

        if (profiles.values.any { it.email.equals(cleanEmail, ignoreCase = true) }) {
            return Result.failure(IllegalArgumentException("هذا البريد الإلكتروني مسجل مسبقاً."))
        }

        val newUser = UserProfile(
            id = "user_${UUID.randomUUID().toString().take(8)}",
            email = cleanEmail,
            fullName = fullName.trim(),
            city = SystemLocationSettings.DEFAULT_CITY_AR,
            neighborhood = neighborhood?.takeIf { it.isNotBlank() },
            role = UserRole.USER,
            createdAt = nowFormatted()
        )
        profiles[newUser.id] = newUser
        accountPasswords[cleanEmail] = password
        currentUser = newUser
        persistState()
        return Result.success(newUser)
    }

    suspend fun login(email: String, password: String): Result<UserProfile> {
        if (simulateOfflineMode) {
            return Result.failure(IOException("تعذر الاتصال بالخادم. تحقق من اتصالك بالإنترنت."))
        }
        val cleanEmail = email.trim().lowercase()
        if (cleanEmail.isBlank() || password.isBlank()) {
            return Result.failure(IllegalArgumentException("يرجى إدخال البريد الإلكتروني وكلمة المرور."))
        }

        if (isLiveSupabaseConnected) {
            val sessionRes = remoteClient.signInWithEmail(cleanEmail, password)
            if (sessionRes.isFailure) {
                return Result.failure(sessionRes.exceptionOrNull() ?: IOException("فشل تسجيل الدخول"))
            }
            val session = sessionRes.getOrThrow()
            accessToken = session.accessToken
            val profileRes = remoteClient.fetchUserProfile(session.userId, session.accessToken)
            val profile = profileRes.getOrElse {
                UserProfile(
                    id = session.userId,
                    email = session.email,
                    fullName = session.fullName,
                    city = SystemLocationSettings.DEFAULT_CITY_AR,
                    role = UserRole.USER
                )
            }.copy(email = session.email)

            if (profile.isSuspended) {
                return Result.failure(SecurityException("تم إيقاف هذا الحساب لمخالفته سياسات الاستخدام."))
            }
            profiles[profile.id] = profile
            currentUser = profile
            refreshLiveReports()
            persistState()
            return Result.success(profile)
        }

        val existing = profiles.values.firstOrNull { it.email.equals(cleanEmail, ignoreCase = true) }
            ?: return Result.failure(IllegalArgumentException("لم يتم العثور على حساب بهذا البريد الإلكتروني."))
        val expectedPass = accountPasswords[cleanEmail]
        if (expectedPass != null && expectedPass != password) {
            return Result.failure(IllegalArgumentException("كلمة المرور غير صحيحة."))
        }
        if (existing.isSuspended) {
            return Result.failure(SecurityException("هذا الحساب موقوف حالياً من قبل الإدارة."))
        }
        currentUser = existing
        persistState()
        return Result.success(existing)
    }

    fun logout() {
        accessToken = null
        currentUser = null
        persistState()
    }

    suspend fun updateProfile(
        fullName: String,
        neighborhood: String?,
        avatarUrl: String?,
        privacyHideLastSeen: Boolean,
        allowMatchNotifications: Boolean
    ): Result<UserProfile> {
        val user = currentUser ?: return Result.failure(SecurityException("يجب تسجيل الدخول أولاً."))
        if (fullName.trim().length < 2) {
            return Result.failure(IllegalArgumentException("الاسم يجب أن يتكون من حرفين على الأقل."))
        }
        val updated = user.copy(
            fullName = fullName.trim(),
            city = SystemLocationSettings.DEFAULT_CITY_AR,
            neighborhood = neighborhood?.takeIf { it.isNotBlank() },
            avatarUrl = avatarUrl,
            privacyHideLastSeen = privacyHideLastSeen,
            allowMatchNotifications = allowMatchNotifications
        )
        if (simulateOfflineMode) return Result.failure(IOException("ما قدرنا نحفظ التعديلات لأن الاتصال بالإنترنت مقطوع. حاول مرة ثانية بعد عودة الاتصال."))
        if (isLiveSupabaseConnected) {
            val token = accessToken?.takeIf { it.isNotBlank() } ?: return Result.failure(SecurityException("انتهت جلسة الدخول. سجّل الدخول من جديد."))
            val saved = remoteClient.updateProfileInSupabase(updated, token)
            if (saved.isFailure) return Result.failure(saved.exceptionOrNull() ?: IOException("تعذر حفظ الملف الشخصي."))
        }
        profiles[user.id] = updated
        currentUser = updated
        persistState()
        return Result.success(updated)
    }


    suspend fun saveProfileAvatar(bytes: ByteArray): Result<UserProfile> {
        val user = currentUser ?: return Result.failure(SecurityException("يرجى تسجيل الدخول لتغيير الصورة الشخصية."))
        if (simulateOfflineMode) return Result.failure(IOException("ما قدرنا نرفع الصورة لأن الاتصال بالإنترنت مقطوع. حاول مرة ثانية بعد عودة الاتصال."))
        if (!isLiveSupabaseConnected) return Result.failure(IOException("رفع الصورة الشخصية غير متاح حالياً. حاول بعد الاتصال بالخدمة."))
        val token = accessToken?.takeIf { it.isNotBlank() } ?: return Result.failure(SecurityException("انتهت جلسة الدخول. سجّل الدخول من جديد."))
        val uploaded = remoteClient.uploadImageToSupabaseStorage(user.id, "avatar.jpg", bytes, token)
        if (uploaded.isFailure) return Result.failure(IOException("ما قدرنا نحفظ الصورة الشخصية. تحقق من اتصال الإنترنت وحاول مرة ثانية."))
        return updateProfile(user.fullName, user.neighborhood, uploaded.getOrThrow(), user.privacyHideLastSeen, user.allowMatchNotifications)
    }

    suspend fun updateAccountCredentials(newEmail: String, newPassword: String): Result<UserProfile> {
        val user = currentUser ?: return Result.failure(SecurityException("يرجى تسجيل الدخول أولاً."))
        val email = newEmail.trim().lowercase()
        if (email.isNotBlank() && (!email.contains("@") || !email.substringAfter("@").contains("."))) return Result.failure(IllegalArgumentException("أدخل بريداً إلكترونياً صحيحاً."))
        if (newPassword.isNotBlank() && newPassword.length < 6) return Result.failure(IllegalArgumentException("كلمة المرور الجديدة يجب أن تتكون من 6 أحرف على الأقل."))
        if (email.isBlank() && newPassword.isBlank()) return Result.failure(IllegalArgumentException("أدخل بريداً جديداً أو كلمة مرور جديدة."))
        if (simulateOfflineMode) return Result.failure(IOException("ما قدرنا نحدّث بيانات الدخول بسبب انقطاع الإنترنت."))
        val changedEmail = email.takeIf { it.isNotBlank() && !it.equals(user.email, true) }
        if (isLiveSupabaseConnected) {
            val token = accessToken?.takeIf { it.isNotBlank() } ?: return Result.failure(SecurityException("انتهت جلسة الدخول. سجّل الدخول من جديد."))
            val result = remoteClient.updateAuthCredentials(token, changedEmail, newPassword.takeIf { it.isNotBlank() })
            if (result.isFailure) return Result.failure(result.exceptionOrNull() ?: IOException("تعذر تحديث بيانات الدخول."))
        } else {
            if (changedEmail != null && profiles.values.any { it.id != user.id && it.email.equals(changedEmail, true) }) return Result.failure(IllegalArgumentException("هذا البريد الإلكتروني مستخدم في حساب آخر."))
            if (newPassword.isNotBlank()) accountPasswords[user.email.lowercase()] = newPassword
        }
        val updated = user.copy(email = changedEmail ?: user.email)
        profiles[user.id] = updated
        currentUser = updated
        persistState()
        return Result.success(updated)
    }

    /**
     * اختبار أمني صريح: أي محاولة من المستخدم العادي لترقية صلاحياته إلى مدير تُرفض فوراً.
     */
    fun attemptSelfRoleChange(targetRole: UserRole): Result<Unit> {
        val user = currentUser ?: return Result.failure(SecurityException("غير مسجل الدخول."))
        if (user.role != UserRole.ADMIN) {
            return Result.failure(
                SecurityException("رفض الخادم (RLS Trigger): لا يمكن لأي مستخدم تعيين نفسه مديراً أو تعديل حقل الصلاحيات.")
            )
        }
        return Result.success(Unit)
    }

    fun requestAccountDeletion(): Result<UserProfile> {
        val user = currentUser ?: return Result.failure(SecurityException("يجب تسجيل الدخول أولاً."))
        val timestamp = nowFormatted()
        val updated = user.copy(deletionRequestedAt = timestamp)
        profiles[user.id] = updated
        currentUser = updated
        persistState()
        return Result.success(updated)
    }

    // -------------------------------------------------------------------------
    // 2. إدارة البلاغات ورفع الصور (Reports & Storage)
    // -------------------------------------------------------------------------
    suspend fun uploadReportImage(
        imageUriOrPath: String,
        imageBytes: ByteArray? = null,
        isBlurredForPrivacy: Boolean = false
    ): Result<ReportImage> {
        val user = currentUser ?: return Result.failure(SecurityException("يجب تسجيل الدخول لرفع الصور."))
        if (simulateOfflineMode) {
            return Result.failure(IOException("فشل رفع الصورة بسبب انقطاع الاتصال بالإنترنت."))
        }
        if (simulateImageUploadFailure) {
            return Result.failure(IOException("ما قدرنا نرفع الصورة. تأكد من اتصال الإنترنت وحاول مرة ثانية."))
        }
        if (imageUriOrPath.isBlank()) {
            return Result.failure(IllegalArgumentException("مسار الصورة غير صالح."))
        }

        val imageId = "img_${UUID.randomUUID().toString().take(8)}"
        val storagePath = "${user.id}/$imageId.jpg"

        if (isLiveSupabaseConnected && accessToken != null && imageBytes != null) {
            val uploadRes = remoteClient.uploadImageToSupabaseStorage(
                userId = user.id,
                fileName = "$imageId.jpg",
                bytes = imageBytes,
                accessToken = accessToken!!
            )
            if (uploadRes.isFailure) {
                return Result.failure(IOException("ما قدرنا نرفع الصورة. تحقق من اتصال الإنترنت وحاول مرة ثانية."))
            }
            val publicUrl = uploadRes.getOrThrow()
            return Result.success(
                ReportImage(
                    id = imageId,
                    reportId = "",
                    storagePath = storagePath,
                    publicUrl = publicUrl,
                    isBlurredForPrivacy = isBlurredForPrivacy
                )
            )
        }

        delay(120)
        return Result.success(
            ReportImage(
                id = imageId,
                reportId = "",
                storagePath = storagePath,
                publicUrl = imageUriOrPath,
                isBlurredForPrivacy = isBlurredForPrivacy
            )
        )
    }

    suspend fun createReport(
        reportType: ReportType,
        title: String,
        categoryId: String,
        description: String,
        city: String = SystemLocationSettings.DEFAULT_CITY_AR,
        neighborhood: String,
        incidentDateText: String,
        incidentEpochMillis: Long = System.currentTimeMillis(),
        distinguishingMarks: String,
        uploadedImages: List<ReportImage> = emptyList(),
        isSensitiveMasked: Boolean = true,
        verificationQuestions: List<Pair<String, String>> = emptyList()
    ): Result<ItemReport> {
        val user = currentUser ?: return Result.failure(SecurityException("يجب تسجيل الدخول لنشر بلاغ."))
        if (user.isSuspended) {
            return Result.failure(SecurityException("حسابك موقوف ولا يمكنك نشر بلاغات جديدة."))
        }
        if (simulateOfflineMode) {
            return Result.failure(IOException("لا يوجد اتصال بالإنترنت. تعذر إرسال البلاغ إلى الخادم."))
        }

        val hasUnmaskedDocImage = categoryId == "documents" && uploadedImages.isNotEmpty() && !isSensitiveMasked
        when (
            val validation = WaynhaSecurityValidator.validateReportInput(
                title = title,
                description = description,
                categoryId = categoryId,
                city = city,
                neighborhood = neighborhood,
                distinguishingMarks = distinguishingMarks,
                hasUnmaskedSensitiveDocumentImage = hasUnmaskedDocImage
            )
        ) {
            is ValidationResult.Invalid -> return Result.failure(IllegalArgumentException(validation.messageAr))
            ValidationResult.Valid -> Unit
        }

        if (reportType == ReportType.FOUND) {
            val validQuestions = verificationQuestions.filter { it.first.isNotBlank() && it.second.isNotBlank() }
            if (validQuestions.isEmpty()) {
                return Result.failure(IllegalArgumentException("أضف سؤال تحقق واحداً مع إجابته السرّية على الأقل قبل نشر بلاغ الموجود."))
            }
            if (validQuestions.size > 3 || validQuestions.any { it.first.trim().length < 5 || it.second.trim().length < 2 }) {
                return Result.failure(IllegalArgumentException("أضف من سؤال إلى ثلاثة أسئلة، وتأكد أن كل سؤال وإجابة مكتملان."))
            }
        }
        val reportId = "rep_${UUID.randomUUID().toString().take(8)}"
        val linkedImages = uploadedImages.map { it.copy(reportId = reportId) }
        val newReport = ItemReport(
            id = reportId,
            userId = user.id,
            userName = user.fullName,
            reportType = reportType,
            title = title.trim(),
            categoryId = categoryId,
            description = description.trim(),
            city = SystemLocationSettings.DEFAULT_CITY_AR,
            neighborhood = neighborhood.trim().ifBlank { "مركز المدينة" },
            incidentDateText = incidentDateText.ifBlank { nowFormatted().take(10) },
            incidentEpochMillis = incidentEpochMillis,
            distinguishingMarks = distinguishingMarks.trim(),
            status = ReportStatus.OPEN,
            images = linkedImages,
            isSensitiveMasked = isSensitiveMasked,
            createdAtText = nowFormatted(),
            createdAtEpochMillis = System.currentTimeMillis()
        )

        if (isLiveSupabaseConnected) {
            val token = accessToken?.takeIf { it.isNotBlank() }
                ?: return Result.failure(SecurityException("انتهت جلسة الدخول أو لم تكتمل. سجّل الخروج ثم ادخل من جديد قبل نشر البلاغ."))
            val remoteRes = remoteClient.insertReportToSupabase(newReport, token)
            if (remoteRes.isFailure) {
                return Result.failure(remoteRes.exceptionOrNull() ?: IOException("تعذر حفظ البلاغ. تحقق من اتصال الإنترنت وحاول مرة أخرى."))
            }
            val savedRemote = remoteRes.getOrThrow()
            val privateDetailsRes = remoteClient.setReportPrivateDetails(savedRemote.id, distinguishingMarks, token)
            if (privateDetailsRes.isFailure) {
                return Result.failure(IOException("تم نشر البلاغ، لكن تعذر حفظ العلامات الخاصة. راجع البلاغ قبل الاعتماد عليه للتحقق من الملكية."))
            }
            if (reportType == ReportType.FOUND) {
                val questionsRes = remoteClient.setVerificationQuestions(savedRemote.id, verificationQuestions, token)
                if (questionsRes.isFailure) {
                    return Result.failure(IOException("تم نشر البلاغ، لكن لم تُحفظ أسئلة التحقق. لا تسلّم الغرض قبل ضبط أسئلة التحقق من جديد."))
                }
            }
            val savedWithPrivateMarks = savedRemote.copy(distinguishingMarks = distinguishingMarks.trim())
            reports.add(0, savedWithPrivateMarks)
            recalculateMatchesAndNotify(savedWithPrivateMarks)
            persistState()
            return Result.success(savedWithPrivateMarks)
        }

        reports.add(0, newReport)
        if (reportType == ReportType.FOUND) {
            verificationAnswersByReport[newReport.id] = verificationQuestions.filter { it.first.isNotBlank() && it.second.isNotBlank() }
        }
        recalculateMatchesAndNotify(newReport)
        persistState()
        return Result.success(newReport)
    }

    fun updateReport(
        reportId: String,
        title: String,
        categoryId: String,
        description: String,
        neighborhood: String,
        distinguishingMarks: String,
        city: String = SystemLocationSettings.DEFAULT_CITY_AR
    ): Result<ItemReport> {
        val user = currentUser ?: return Result.failure(SecurityException("يجب تسجيل الدخول أولاً."))
        val existingIndex = reports.indexOfFirst { it.id == reportId }
        if (existingIndex == -1) {
            return Result.failure(IllegalArgumentException("البلاغ غير موجود."))
        }
        val existing = reports[existingIndex]
        if (!WaynhaSecurityValidator.canModifyReport(user, existing)) {
            return Result.failure(SecurityException("رفض الخادم (RLS): لا تملك صلاحية تعديل بلاغ يخص مستخدماً آخر."))
        }

        when (
            val validation = WaynhaSecurityValidator.validateReportInput(
                title = title,
                description = description,
                categoryId = categoryId,
                city = city,
                neighborhood = neighborhood,
                distinguishingMarks = distinguishingMarks
            )
        ) {
            is ValidationResult.Invalid -> return Result.failure(IllegalArgumentException(validation.messageAr))
            ValidationResult.Valid -> Unit
        }

        val updated = existing.copy(
            title = title.trim(),
            categoryId = categoryId,
            description = description.trim(),
            city = SystemLocationSettings.DEFAULT_CITY_AR,
            neighborhood = neighborhood.trim().ifBlank { existing.neighborhood },
            distinguishingMarks = distinguishingMarks.trim()
        )
        reports[existingIndex] = updated
        recalculateMatchesAndNotify(updated)
        persistState()
        return Result.success(updated)
    }

    fun closeOrRecoverReport(
        reportId: String,
        markRecovered: Boolean,
        handoverNotes: String = ""
    ): Result<ItemReport> {
        val user = currentUser ?: return Result.failure(SecurityException("يجب تسجيل الدخول أولاً."))
        val index = reports.indexOfFirst { it.id == reportId }
        if (index == -1) return Result.failure(IllegalArgumentException("البلاغ غير موجود."))
        val existing = reports[index]
        if (!WaynhaSecurityValidator.canModifyReport(user, existing)) {
            return Result.failure(SecurityException("رفض الخادم (RLS): لا يمكنك إغلاق بلاغ مستخدم آخر."))
        }
        if (markRecovered && existing.reportType == ReportType.FOUND) {
            return Result.failure(IllegalStateException("لا يمكن تأكيد استعادة الغرض من طرف واحد. استخدم طلب الاستعادة وتأكيد التسليم من الطرفين."))
        }

        val newStatus = if (markRecovered) ReportStatus.RECOVERED else ReportStatus.CLOSED
        val updated = existing.copy(status = newStatus)
        reports[index] = updated

        // إيقاف المطابقات والتنبيهات الجديدة للبلاغ المغلق وأرشفة المطابقات المعلقة
        for (i in reportMatches.indices) {
            val m = reportMatches[i]
            if (m.lostReportId == reportId || m.foundReportId == reportId) {
                reportMatches[i] = m.copy(
                    reviewStatus = if (markRecovered) MatchReviewStatus.CONFIRMED else MatchReviewStatus.ARCHIVED
                )
            }
        }

        if (markRecovered) {
            recoveryConfirmations.add(
                0,
                RecoveryConfirmation(
                    id = "rec_${UUID.randomUUID().toString().take(8)}",
                    reportId = reportId,
                    matchedReportId = null,
                    ownerUserId = existing.userId,
                    finderUserId = user.id,
                    ownerConfirmed = true,
                    finderConfirmed = true,
                    handoverNotes = handoverNotes.ifBlank { "تمت استعادة الغرض بنجاح في مدينة حمص." },
                    status = "confirmed",
                    confirmedAtText = nowFormatted()
                )
            )
            notifications.add(
                0,
                AppNotification(
                    id = "notif_${UUID.randomUUID().toString().take(8)}",
                    userId = existing.userId,
                    type = NotificationType.RECOVERY_UPDATE,
                    title = "الحمد لله على السلامة! تم تأكيد الاستعادة",
                    body = "تم إغلاق البلاغ «${existing.title}» وتأكيد استعادة الغرض وإيقاف التنبيهات الجديدة.",
                    relatedReportId = existing.id,
                    createdAtText = nowFormatted()
                )
            )
        } else {
            notifications.add(
                0,
                AppNotification(
                    id = "notif_${UUID.randomUUID().toString().take(8)}",
                    userId = existing.userId,
                    type = NotificationType.REPORT_STATUS,
                    title = "تم إغلاق البلاغ",
                    body = "تم إغلاق بلاغ «${existing.title}» وإيقاف المطابقات الجديدة له.",
                    relatedReportId = existing.id,
                    createdAtText = nowFormatted()
                )
            )
        }

        persistState()
        return Result.success(updated)
    }

    fun deleteReport(reportId: String): Result<Unit> {
        val user = currentUser ?: return Result.failure(SecurityException("يجب تسجيل الدخول أولاً."))
        val existing = reports.firstOrNull { it.id == reportId }
            ?: return Result.failure(IllegalArgumentException("البلاغ غير موجود."))
        if (!WaynhaSecurityValidator.canModifyReport(user, existing)) {
            return Result.failure(SecurityException("رفض الخادم (RLS): لا يمكنك حذف بلاغات الآخرين."))
        }
        reports.removeAll { it.id == reportId }
        reportMatches.removeAll { it.lostReportId == reportId || it.foundReportId == reportId }
        savedReportIdsByUser.values.forEach { it.remove(reportId) }
        persistState()
        return Result.success(Unit)
    }

    fun toggleSavedReport(reportId: String): Result<Boolean> {
        val user = currentUser ?: return Result.failure(SecurityException("يرجى تسجيل الدخول لحفظ البلاغات."))
        val set = savedReportIdsByUser.getOrPut(user.id) { mutableSetOf() }
        val isNowSaved = if (set.contains(reportId)) {
            set.remove(reportId)
            false
        } else {
            set.add(reportId)
            true
        }
        persistState()
        return Result.success(isNowSaved)
    }

    // -------------------------------------------------------------------------
    // 3. نظام المطابقة والتواصل الآمن ("قد يكون هذا غرضي")
    // -------------------------------------------------------------------------
    fun recalculateMatchesAndNotify(triggerReport: ItemReport? = null) {
        val updatedMatches = WaynhaMatchingEngine.computeMatches(
            allReports = reports,
            existingMatches = reportMatches
        )
        val previousPairs = reportMatches.map { "${it.lostReportId}_${it.foundReportId}" }.toSet()
        reportMatches.clear()
        reportMatches.addAll(updatedMatches)

        if (triggerReport != null && triggerReport.status.isEligibleForMatching) {
            val newlyAdded = updatedMatches.filter {
                (it.lostReportId == triggerReport.id || it.foundReportId == triggerReport.id) &&
                    !previousPairs.contains("${it.lostReportId}_${it.foundReportId}") &&
                    it.reviewStatus == MatchReviewStatus.PENDING
            }
            for (match in newlyAdded) {
                val lostRep = getReportById(match.lostReportId) ?: continue
                val foundRep = getReportById(match.foundReportId) ?: continue
                notifications.add(
                    0,
                    AppNotification(
                        id = "notif_${UUID.randomUUID().toString().take(8)}",
                        userId = lostRep.userId,
                        type = NotificationType.SIMILAR_REPORT,
                        title = "قد يكون هذا الغرض هو ما تبحث عنه (${match.similarityScore}%)",
                        body = "تطابق احتمالي في حمص بين «${lostRep.title}» و«${foundRep.title}» (${match.matchReasons.firstOrNull() ?: "نفس التصنيف"}).",
                        relatedReportId = foundRep.id,
                        createdAtText = nowFormatted()
                    )
                )
            }
        }
    }

    suspend fun startSafeConversationForReport(
        targetReportId: String,
        matchedReportId: String? = null,
        initialMessage: String = "مرحباً، قد يكون هذا الغرض هو ما أبحث عنه. هل يمكننا التحقق من العلامات المميزة؟"
    ): Result<Conversation> {
        val user = currentUser ?: return Result.failure(SecurityException("يرجى تسجيل الدخول لبدء محادثة آمنة."))
        if (simulateOfflineMode) {
            return Result.failure(IOException("لا يوجد اتصال بالإنترنت لبدء المحادثة."))
        }
        val targetReport = getReportById(targetReportId)
            ?: return Result.failure(IllegalArgumentException("البلاغ غير موجود."))

        if (!targetReport.status.isEligibleForMatching) {
            return Result.failure(IllegalStateException("لا يمكن بدء محادثة جديدة لأن هذا البلاغ مغلق أو تمت استعادته."))
        }
        if (targetReport.userId == user.id) {
            return Result.failure(IllegalArgumentException("هذا البلاغ منشور من حسابك الشخصي."))
        }

        val blocked = blockedUsersByBlocker[user.id] ?: emptySet()
        val blockedByTarget = blockedUsersByBlocker[targetReport.userId] ?: emptySet()
        if (blocked.contains(targetReport.userId) || blockedByTarget.contains(user.id)) {
            return Result.failure(SecurityException("لا يمكن بدء محادثة لوجود حظر بين الحسابين."))
        }

        if (isLiveSupabaseConnected) {
            val token = accessToken?.takeIf { it.isNotBlank() }
                ?: return Result.failure(SecurityException("انتهت جلسة الدخول. سجّل الدخول مجدداً."))
            val remoteConversation = remoteClient.createConversation(targetReport, user, matchedReportId, initialMessage, token)
            if (remoteConversation.isFailure) {
                return Result.failure(remoteConversation.exceptionOrNull() ?: IOException("تعذر بدء المحادثة الآمنة."))
            }
            val remoteConv = remoteConversation.getOrThrow()
            val loadedMessages = remoteClient.fetchMessages(remoteConv.id, token).getOrDefault(emptyList())
            messagesByConversation[remoteConv.id] = loadedMessages.toMutableList()
            val conv = remoteConv.copy(
                reportTitle = targetReport.title,
                reportType = targetReport.reportType,
                initiatorName = if (remoteConv.initiatorId == user.id) user.fullName else remoteConv.initiatorName,
                recipientName = if (remoteConv.recipientId == user.id) user.fullName else remoteConv.recipientName,
                lastMessagePreview = loadedMessages.lastOrNull()?.body.orEmpty()
            )
            conversations.removeAll { it.id == conv.id }
            conversations.add(0, conv)
            persistState()
            return Result.success(conv)
        }

        // منع إنشاء محادثات مكررة لنفس البلاغ ونفس المبادر
        val existingConv = conversations.firstOrNull {
            it.reportId == targetReportId &&
                ((it.initiatorId == user.id && it.recipientId == targetReport.userId) ||
                    (it.initiatorId == targetReport.userId && it.recipientId == user.id))
        }
        if (existingConv != null) {
            return Result.success(existingConv)
        }

        val convId = "conv_${UUID.randomUUID().toString().take(8)}"
        val newConv = Conversation(
            id = convId,
            reportId = targetReport.id,
            reportTitle = targetReport.title,
            reportType = targetReport.reportType,
            matchedReportId = matchedReportId,
            initiatorId = user.id,
            initiatorName = user.fullName,
            recipientId = targetReport.userId,
            recipientName = targetReport.userName,
            status = "active",
            lastMessagePreview = initialMessage,
            updatedAtText = "الآن",
            unreadCount = 0
        )
        conversations.add(0, newConv)

        val msg = ChatMessage(
            id = "msg_${UUID.randomUUID().toString().take(8)}",
            conversationId = convId,
            senderId = user.id,
            senderName = user.fullName,
            body = initialMessage,
            isRead = false,
            createdAtText = nowFormatted().takeLast(5),
            createdAtEpochMillis = System.currentTimeMillis()
        )
        messagesByConversation.getOrPut(convId) { mutableListOf() }.add(msg)

        // تحديث حالة المطابقة إلى تم التواصل
        reportMatches.replaceAll { m ->
            if ((m.lostReportId == targetReportId || m.foundReportId == targetReportId) &&
                m.reviewStatus == MatchReviewStatus.PENDING
            ) {
                m.copy(reviewStatus = MatchReviewStatus.CONTACTED)
            } else {
                m
            }
        }

        notifications.add(
            0,
            AppNotification(
                id = "notif_${UUID.randomUUID().toString().take(8)}",
                userId = targetReport.userId,
                type = NotificationType.NEW_MESSAGE,
                title = "رسالة جديدة بخصوص بلاغك في حمص",
                body = "تواصل معك ${user.fullName} بخصوص «${targetReport.title}» دون كشف رقم الهاتف.",
                relatedReportId = targetReport.id,
                relatedConversationId = convId,
                createdAtText = nowFormatted()
            )
        )

        persistState()
        return Result.success(newConv)
    }

    suspend fun sendMessage(
        conversationId: String,
        body: String,
        actingUser: UserProfile? = currentUser
    ): Result<ChatMessage> {
        if (simulateOfflineMode) {
            return Result.failure(IOException("انقطع الاتصال بالإنترنت. لم يتم إرسال الرسالة."))
        }
        val convIndex = conversations.indexOfFirst { it.id == conversationId }
        if (convIndex == -1) return Result.failure(IllegalArgumentException("المحادثة غير موجودة."))
        val conv = conversations[convIndex]

        val blocked = if (actingUser != null) {
            (blockedUsersByBlocker[actingUser.id] ?: emptySet()) +
                (blockedUsersByBlocker[if (actingUser.id == conv.initiatorId) conv.recipientId else conv.initiatorId] ?: emptySet())
        } else {
            emptySet()
        }

        if (!WaynhaSecurityValidator.canSendMessageInConversation(actingUser, conv, blocked)) {
            return Result.failure(
                SecurityException("رفض الخادم (RLS): غير مصرح لك بإرسال رسائل في محادثة لست طرفاً فيها أو محظورة.")
            )
        }

        when (val validation = WaynhaSecurityValidator.validateMessageInput(body)) {
            is ValidationResult.Invalid -> return Result.failure(IllegalArgumentException(validation.messageAr))
            ValidationResult.Valid -> Unit
        }

        val sender = actingUser!!
        if (isLiveSupabaseConnected) {
            val token = accessToken?.takeIf { it.isNotBlank() }
                ?: return Result.failure(SecurityException("انتهت جلسة الدخول. سجّل الدخول مجدداً."))
            val remoteMessage = remoteClient.insertChatMessage(conversationId, sender.id, body.trim(), token)
            if (remoteMessage.isFailure) {
                return Result.failure(remoteMessage.exceptionOrNull() ?: IOException("تعذر إرسال الرسالة."))
            }
            val saved = remoteMessage.getOrThrow()
            messagesByConversation.getOrPut(conversationId) { mutableListOf() }.add(saved)
            conversations[convIndex] = conv.copy(lastMessagePreview = saved.body, updatedAtText = "الآن")
            persistState()
            return Result.success(saved)
        }
        val msg = ChatMessage(
            id = "msg_${UUID.randomUUID().toString().take(8)}",
            conversationId = conversationId,
            senderId = sender.id,
            senderName = sender.fullName,
            body = body.trim(),
            isRead = false,
            createdAtText = nowFormatted().takeLast(5),
            createdAtEpochMillis = System.currentTimeMillis()
        )
        messagesByConversation.getOrPut(conversationId) { mutableListOf() }.add(msg)
        conversations[convIndex] = conv.copy(
            lastMessagePreview = msg.body,
            updatedAtText = "الآن"
        )

        val recipientId = if (sender.id == conv.initiatorId) conv.recipientId else conv.initiatorId
        notifications.add(
            0,
            AppNotification(
                id = "notif_${UUID.randomUUID().toString().take(8)}",
                userId = recipientId,
                type = NotificationType.NEW_MESSAGE,
                title = "رسالة جديدة حول «${conv.reportTitle}»",
                body = "أرسل ${sender.fullName} رسالة جديدة في المحادثة الآمنة.",
                relatedReportId = conv.reportId,
                relatedConversationId = conv.id,
                createdAtText = nowFormatted()
            )
        )

        persistState()
        return Result.success(msg)
    }

    fun markConversationRead(conversationId: String) {
        val user = currentUser ?: return
        val list = messagesByConversation[conversationId] ?: return
        for (i in list.indices) {
            val m = list[i]
            if (m.senderId != user.id && !m.isRead) {
                list[i] = m.copy(isRead = true)
            }
        }
        val convIndex = conversations.indexOfFirst { it.id == conversationId }
        if (convIndex != -1) {
            conversations[convIndex] = conversations[convIndex].copy(unreadCount = 0)
        }
    }

    fun markAllNotificationsRead() {
        val user = currentUser ?: return
        for (i in notifications.indices) {
            if (notifications[i].userId == user.id) {
                notifications[i] = notifications[i].copy(isRead = true)
            }
        }
        persistState()
    }

    // -------------------------------------------------------------------------
    // 4. الإبلاغ عن المخالفات والحظر (Flags & Blocks)
    // -------------------------------------------------------------------------
    suspend fun flagReportOrConversation(
        reportId: String?,
        conversationId: String?,
        reason: String,
        details: String
    ): Result<ReportFlag> {
        val user = currentUser ?: return Result.failure(SecurityException("يرجى تسجيل الدخول للإبلاغ عن مخالفة."))
        if (reason.trim().length < 3) {
            return Result.failure(IllegalArgumentException("يرجى تحديد سبب الإبلاغ."))
        }
        if (isLiveSupabaseConnected) {
            val token = accessToken?.takeIf { it.isNotBlank() }
                ?: return Result.failure(SecurityException("انتهت جلسة الدخول. سجّل الدخول مجدداً لإرسال البلاغ."))
            val remote = remoteClient.submitReportFlag(user.id, reportId, conversationId, reason, details, token)
            if (remote.isFailure) return Result.failure(remote.exceptionOrNull() ?: IOException("تعذر إرسال البلاغ للمراجعة."))
        }
        val targetTitle = reportId?.let { getReportById(it)?.title } ?: "محادثة مستخدم"
        val flag = ReportFlag(
            id = "flag_${UUID.randomUUID().toString().take(8)}",
            reportId = reportId,
            reportTitle = targetTitle,
            conversationId = conversationId,
            reporterId = user.id,
            reporterName = user.fullName,
            reason = reason.trim(),
            details = details.trim(),
            status = "pending",
            createdAtText = nowFormatted()
        )
        reportFlags.add(0, flag)
        persistState()
        return Result.success(flag)
    }

    fun blockUser(targetUserId: String, reason: String = "إزعاج"): Result<Unit> {
        val user = currentUser ?: return Result.failure(SecurityException("يرجى تسجيل الدخول أولاً."))
        if (user.id == targetUserId) {
            return Result.failure(IllegalArgumentException("لا يمكنك حظر حسابك الشخصي."))
        }
        blockedUsersByBlocker.getOrPut(user.id) { mutableSetOf() }.add(targetUserId)
        conversations.replaceAll { conv ->
            if ((conv.initiatorId == user.id && conv.recipientId == targetUserId) ||
                (conv.initiatorId == targetUserId && conv.recipientId == user.id)
            ) {
                conv.copy(status = "blocked")
            } else {
                conv
            }
        }
        persistState()
        return Result.success(Unit)
    }

    // -------------------------------------------------------------------------
    // 5. عمليات لوحة الإدارة الآمنة (Admin Operations + Audit Logs)
    // -------------------------------------------------------------------------
    suspend fun adminHideFlaggedReport(
        adminUser: UserProfile?,
        reportId: String,
        flagId: String?,
        reasonNotes: String
    ): Result<Unit> {
        if (!WaynhaSecurityValidator.canAccessAdminDashboard(adminUser)) {
            return Result.failure(SecurityException("رفض الخادم (RLS): صلاحية الإدارة مطلوبة لإخفاء المحتوى المخالف."))
        }
        if (isLiveSupabaseConnected) {
            val token = accessToken?.takeIf { it.isNotBlank() }
                ?: return Result.failure(SecurityException("انتهت جلسة الدخول. سجّل الدخول مجدداً."))
            if (flagId != null) {
                val remoteReview = remoteClient.reviewReportFlag(flagId, "hide", reasonNotes, token)
                if (remoteReview.isFailure) return Result.failure(remoteReview.exceptionOrNull() ?: IOException("تعذر مراجعة البلاغ."))
            }
        }
        val idx = reports.indexOfFirst { it.id == reportId }
        if (idx == -1) return Result.failure(IllegalArgumentException("البلاغ غير موجود."))
        reports[idx] = reports[idx].copy(status = ReportStatus.HIDDEN)

        if (flagId != null) {
            reportFlags.replaceAll { f ->
                if (f.id == flagId) f.copy(status = "action_taken") else f
            }
        }

        adminAuditLogs.add(
            0,
            AdminAuditLog(
                id = "audit_${UUID.randomUUID().toString().take(8)}",
                adminId = adminUser!!.id,
                adminName = adminUser.fullName,
                actionType = "HIDE_REPORT",
                targetTable = "reports",
                targetId = reportId,
                notes = reasonNotes.ifBlank { "إخفاء بلاغ مخالف لسياسات النشر في حمص" },
                createdAtText = nowFormatted()
            )
        )
        persistState()
        return Result.success(Unit)
    }

    suspend fun adminDismissReportFlag(adminUser: UserProfile?, flagId: String, notes: String): Result<Unit> {
        if (!WaynhaSecurityValidator.canAccessAdminDashboard(adminUser)) {
            return Result.failure(SecurityException("صلاحية الإدارة مطلوبة لمراجعة البلاغات."))
        }
        if (isLiveSupabaseConnected) {
            val token = accessToken?.takeIf { it.isNotBlank() }
                ?: return Result.failure(SecurityException("انتهت جلسة الدخول. سجّل الدخول مجدداً."))
            val remote = remoteClient.reviewReportFlag(flagId, "dismiss", notes, token)
            if (remote.isFailure) return Result.failure(remote.exceptionOrNull() ?: IOException("تعذر حفظ قرار المراجعة."))
        }
        reportFlags.replaceAll { if (it.id == flagId) it.copy(status = "dismissed") else it }
        adminAuditLogs.add(0, AdminAuditLog(
            id = "audit_${UUID.randomUUID().toString().take(8)}",
            adminId = adminUser!!.id,
            adminName = adminUser.fullName,
            actionType = "dismiss_report_flag",
            targetTable = "reports_flags",
            targetId = flagId,
            notes = notes,
            createdAtText = nowFormatted()
        ))
        persistState()
        return Result.success(Unit)
    }

    fun adminToggleUserSuspension(
        adminUser: UserProfile?,
        targetUserId: String,
        suspend: Boolean,
        notes: String
    ): Result<UserProfile> {
        if (!WaynhaSecurityValidator.canAccessAdminDashboard(adminUser)) {
            return Result.failure(SecurityException("رفض الخادم (RLS): غير مصرح لك بإدارة الحسابات."))
        }
        val target = profiles[targetUserId]
            ?: return Result.failure(IllegalArgumentException("المستخدم غير موجود."))
        if (target.role == UserRole.ADMIN) {
            return Result.failure(IllegalStateException("لا يمكن إيقاف حساب مدير النظام الأساسي."))
        }
        val updated = target.copy(isSuspended = suspend)
        profiles[targetUserId] = updated
        adminAuditLogs.add(
            0,
            AdminAuditLog(
                id = "audit_${UUID.randomUUID().toString().take(8)}",
                adminId = adminUser!!.id,
                adminName = adminUser.fullName,
                actionType = if (suspend) "SUSPEND_USER" else "UNSUSPEND_USER",
                targetTable = "profiles",
                targetId = targetUserId,
                notes = notes.ifBlank { if (suspend) "إيقاف حساب مخالف" else "إعادة تفعيل الحساب" },
                createdAtText = nowFormatted()
            )
        )
        persistState()
        return Result.success(updated)
    }

    fun adminAddCategory(
        adminUser: UserProfile?,
        categoryId: String,
        nameAr: String,
        isSensitive: Boolean
    ): Result<ReportCategory> {
        if (!WaynhaSecurityValidator.canAccessAdminDashboard(adminUser)) {
            return Result.failure(SecurityException("رفض الخادم (RLS): صلاحية الإدارة مطلوبة لإدارة التصنيفات."))
        }
        if (nameAr.trim().length < 2) {
            return Result.failure(IllegalArgumentException("يرجى إدخال اسم التصنيف بالعربية."))
        }
        val cleanId = categoryId.trim().lowercase().ifBlank { "cat_${UUID.randomUUID().toString().take(5)}" }
        val newCat = ReportCategory(cleanId, nameAr.trim(), isSensitiveDocument = isSensitive)
        categories.removeAll { it.id == cleanId }
        categories.add(newCat)
        adminAuditLogs.add(
            0,
            AdminAuditLog(
                id = "audit_${UUID.randomUUID().toString().take(8)}",
                adminId = adminUser!!.id,
                adminName = adminUser.fullName,
                actionType = "UPSERT_CATEGORY",
                targetTable = "categories",
                targetId = cleanId,
                notes = "إضافة/تحديث تصنيف: ${nameAr.trim()}",
                createdAtText = nowFormatted()
            )
        )
        persistState()
        return Result.success(newCat)
    }

    suspend fun refreshLiveReports(): Result<List<ItemReport>> {
        if (!isLiveSupabaseConnected) return Result.success(getAllReports())
        val res = remoteClient.fetchReportsFromSupabase(accessToken)
        if (res.isSuccess) {
            reports.clear()
            reports.addAll(res.getOrThrow())
            recalculateMatchesAndNotify()
        }
        return res
    }

    // -------------------------------------------------------------------------
    // حفظ واسترجاع الحالة (Persistence across App Restarts)
    // -------------------------------------------------------------------------
    private fun persistState() {
        val sp = prefs ?: return
        val editor = sp.edit()
        editor.putString("current_user_id", currentUser?.id)

        val savedObj = JSONObject()
        savedReportIdsByUser.forEach { (uId, set) ->
            savedObj.put(uId, JSONArray(set.toList()))
        }
        editor.putString("saved_reports_json", savedObj.toString())

        val reportsArray = JSONArray()
        for (r in reports) {
            val obj = JSONObject().apply {
                put("id", r.id)
                put("userId", r.userId)
                put("userName", r.userName)
                put("reportType", r.reportType.dbValue)
                put("title", r.title)
                put("categoryId", r.categoryId)
                put("description", r.description)
                put("city", SystemLocationSettings.DEFAULT_CITY_AR)
                put("neighborhood", r.neighborhood)
                put("incidentDateText", r.incidentDateText)
                put("incidentEpochMillis", r.incidentEpochMillis)
                put("distinguishingMarks", r.distinguishingMarks)
                put("status", r.status.dbValue)
                put("isSensitiveMasked", r.isSensitiveMasked)
                put("createdAtText", r.createdAtText)
                put("createdAtEpochMillis", r.createdAtEpochMillis)
                val imgArr = JSONArray()
                r.images.forEach { img ->
                    imgArr.put(
                        JSONObject().apply {
                            put("id", img.id)
                            put("reportId", img.reportId)
                            put("storagePath", img.storagePath)
                            put("publicUrl", img.publicUrl)
                            put("isBlurredForPrivacy", img.isBlurredForPrivacy)
                        }
                    )
                }
                put("images", imgArr)
            }
            reportsArray.put(obj)
        }
        editor.putString("reports_json", reportsArray.toString())
        editor.apply()
    }

    private fun loadPersistedSessionOnly() {
        val sp = prefs ?: return
        val savedJson = sp.getString("saved_reports_json", null)
        if (!savedJson.isNullOrBlank()) {
            try {
                val obj = JSONObject(savedJson)
                obj.keys().forEach { uId ->
                    val arr = obj.getJSONArray(uId)
                    val set = mutableSetOf<String>()
                    for (i in 0 until arr.length()) set.add(arr.getString(i))
                    savedReportIdsByUser[uId] = set
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun loadPersistedState() {
        val sp = prefs ?: return
        loadPersistedSessionOnly()
        val reportsJson = sp.getString("reports_json", null)
        if (!reportsJson.isNullOrBlank()) {
            try {
                val arr = JSONArray(reportsJson)
                if (arr.length() > 0) {
                    reports.clear()
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val imgs = mutableListOf<ReportImage>()
                        val imgArr = obj.optJSONArray("images") ?: JSONArray()
                        for (j in 0 until imgArr.length()) {
                            val im = imgArr.getJSONObject(j)
                            imgs.add(
                                ReportImage(
                                    id = im.optString("id"),
                                    reportId = im.optString("reportId"),
                                    storagePath = im.optString("storagePath"),
                                    publicUrl = im.optString("publicUrl"),
                                    isBlurredForPrivacy = im.optBoolean("isBlurredForPrivacy", false)
                                )
                            )
                        }
                        reports.add(
                            ItemReport(
                                id = obj.getString("id"),
                                userId = obj.getString("userId"),
                                userName = obj.getString("userName"),
                                reportType = ReportType.fromDb(obj.getString("reportType")),
                                title = obj.getString("title"),
                                categoryId = obj.getString("categoryId"),
                                description = obj.getString("description"),
                                city = SystemLocationSettings.DEFAULT_CITY_AR,
                                neighborhood = obj.getString("neighborhood"),
                                incidentDateText = obj.getString("incidentDateText"),
                                incidentEpochMillis = obj.optLong("incidentEpochMillis", System.currentTimeMillis()),
                                distinguishingMarks = obj.optString("distinguishingMarks"),
                                status = ReportStatus.fromDb(obj.optString("status", "open")),
                                images = imgs,
                                isSensitiveMasked = obj.optBoolean("isSensitiveMasked", true),
                                createdAtText = obj.getString("createdAtText"),
                                createdAtEpochMillis = obj.optLong("createdAtEpochMillis", System.currentTimeMillis())
                            )
                        )
                    }
                    recalculateMatchesAndNotify()
                }
            } catch (_: Exception) {
            }
        }
        val savedUserId = sp.getString("current_user_id", null)
        if (savedUserId != null && profiles.containsKey(savedUserId)) {
            currentUser = profiles[savedUserId]
        }
    }

    /**
     * بيانات تجريبية واضحة ومفصولة تماماً لمدينة حمص (تُستخدم فقط عندما لا تكون مفاتيح Supabase مربوطة بعد).
     */
    private fun seedExplicitDemoDataForHoms() {
        val now = System.currentTimeMillis()
        val user1 = UserProfile(
            id = "demo_user_ahmad",
            email = "ahmad@homs.sy",
            fullName = "أحمد الرفاعي",
            city = "حمص",
            neighborhood = "الإنشاءات",
            role = UserRole.USER,
            createdAt = "2026-10-24"
        )
        val user2 = UserProfile(
            id = "demo_user_sara",
            email = "sara@homs.sy",
            fullName = "سارة الجندي",
            city = "حمص",
            neighborhood = "الحمراء",
            role = UserRole.USER,
            createdAt = "2026-10-24"
        )
        val user3 = UserProfile(
            id = "demo_user_tarek",
            email = "tarek@homs.sy",
            fullName = "طارق السباعي",
            city = "حمص",
            neighborhood = "الوعر",
            role = UserRole.USER,
            createdAt = "2026-10-25"
        )
        val adminUser = UserProfile(
            id = "demo_admin_homs",
            email = "admin@waynha.sy",
            fullName = "إدارة وينها - حمص",
            city = "حمص",
            neighborhood = "مركز المدينة",
            role = UserRole.ADMIN,
            createdAt = "2026-10-20"
        )

        profiles[user1.id] = user1
        profiles[user2.id] = user2
        profiles[user3.id] = user3
        profiles[adminUser.id] = adminUser

        accountPasswords[user1.email] = "123456"
        accountPasswords[user2.email] = "123456"
        accountPasswords[user3.email] = "123456"
        accountPasswords[adminUser.email] = "admin123"

        currentUser = user1

        val demoReports = listOf(
            ItemReport(
                id = "rep_homs_1",
                userId = user1.id,
                userName = user1.fullName,
                reportType = ReportType.LOST,
                title = "محفظة جلدية بنية تحتوي بطاقة جامعية",
                categoryId = "wallets",
                description = "فقدت محفظة جلدية لون بني غامق أثناء المرور قرب شارع البرازيل في حي الإنشاءات. تحتوي على بطاقة كلية الهندسة ومبلغ رمزي.",
                city = "حمص",
                neighborhood = "الإنشاءات",
                incidentDateText = "2026-10-25",
                incidentEpochMillis = now - 3600_000L * 18,
                distinguishingMarks = "خياطة يدوية بيضاء على الزاوية وبطاقة جامعية باسم أحمد",
                status = ReportStatus.OPEN,
                createdAtText = "منذ 3 ساعات",
                createdAtEpochMillis = now - 3600_000L * 3
            ),
            ItemReport(
                id = "rep_homs_2",
                userId = user2.id,
                userName = user2.fullName,
                reportType = ReportType.FOUND,
                title = "عُثر على محفظة جلدية بنية في الإنشاءات",
                categoryId = "wallets",
                description = "وجدت محفظة رجالية جلدية بنية اللون قرب مكتبة في حي الإنشاءات بحمص، بداخلها بطاقة جامعية وأوراق خاصة محفوظة بأمان.",
                city = "حمص",
                neighborhood = "الإنشاءات",
                incidentDateText = "2026-10-25",
                incidentEpochMillis = now - 3600_000L * 12,
                distinguishingMarks = "خياطة بيضاء على الطرف وبطاقة كلية الهندسة",
                status = ReportStatus.OPEN,
                createdAtText = "منذ ساعتين",
                createdAtEpochMillis = now - 3600_000L * 2
            ),
            ItemReport(
                id = "rep_homs_3",
                userId = user3.id,
                userName = user3.fullName,
                reportType = ReportType.LOST,
                title = "ميدالية مفاتيح سيارة مع تعليقة خشبية",
                categoryId = "keys",
                description = "ضاعت مني ميدالية تضم 3 مفاتيح منزل ومفتاح سيارة أسود في حي الوعر قرب الحديقة.",
                city = "حمص",
                neighborhood = "الوعر",
                incidentDateText = "2026-10-25",
                incidentEpochMillis = now - 3600_000L * 24,
                distinguishingMarks = "تعليقة خشبية محفور عليها حرف T",
                status = ReportStatus.OPEN,
                createdAtText = "منذ 5 ساعات",
                createdAtEpochMillis = now - 3600_000L * 5
            ),
            ItemReport(
                id = "rep_homs_4",
                userId = user2.id,
                userName = user2.fullName,
                reportType = ReportType.FOUND,
                title = "هاتف سامسونج أزرق بكفر شفاف",
                categoryId = "electronics",
                description = "تم العثور على هاتف محمول في حي الحمراء قرب الملعب البلدي. الهاتف مقفل وبحالة جيدة، يرجى ذكر خلفية الشاشة للتأكد.",
                city = "حمص",
                neighborhood = "الحمراء",
                incidentDateText = "2026-10-26",
                incidentEpochMillis = now - 3600_000L * 6,
                distinguishingMarks = "ملصق صغير خلف الغطاء الشفاف",
                status = ReportStatus.OPEN,
                createdAtText = "منذ ساعة",
                createdAtEpochMillis = now - 3600_000L * 1
            ),
            ItemReport(
                id = "rep_homs_5",
                userId = user3.id,
                userName = user3.fullName,
                reportType = ReportType.FOUND,
                title = "حقيبة ظهر طلابية سوداء تحتوي دفاتر محاضرات",
                categoryId = "bags",
                description = "وجدت حقيبة ظهر سوداء عند موقف جامعة البعث في حمص، بداخلها دفاتر محاضرات ومقلمة.",
                city = "حمص",
                neighborhood = "جامعة البعث",
                incidentDateText = "2026-10-24",
                incidentEpochMillis = now - 3600_000L * 36,
                distinguishingMarks = "سحاب فضي وميدالية زرقاء صغيرة",
                status = ReportStatus.RECOVERED,
                createdAtText = "أمس",
                createdAtEpochMillis = now - 3600_000L * 28
            )
        )

        reports.clear()
        reports.addAll(demoReports)
        savedReportIdsByUser[user1.id] = mutableSetOf("rep_homs_2", "rep_homs_4")

        recalculateMatchesAndNotify()

        val conv1 = Conversation(
            id = "conv_homs_1",
            reportId = "rep_homs_2",
            reportTitle = "عُثر على محفظة جلدية بنية في الإنشاءات",
            reportType = ReportType.FOUND,
            matchedReportId = "rep_homs_1",
            initiatorId = user1.id,
            initiatorName = user1.fullName,
            recipientId = user2.id,
            recipientName = user2.fullName,
            status = "active",
            lastMessagePreview = "نعم، البطاقة الجامعية لكلية الهندسة المدنية وعليها خياطة بيضاء.",
            updatedAtText = "منذ 20 دقيقة",
            unreadCount = 1
        )
        conversations.add(conv1)
        messagesByConversation[conv1.id] = mutableListOf(
            ChatMessage(
                id = "m_1",
                conversationId = conv1.id,
                senderId = user1.id,
                senderName = user1.fullName,
                body = "السلام عليكم، ظهر لي في تطبيق وينها أن المحفظة التي وجدتها في الإنشاءات تطابق محفظتي المفقودة.",
                isRead = true,
                createdAtText = "10:05",
                createdAtEpochMillis = now - 1800_000L
            ),
            ChatMessage(
                id = "m_2",
                conversationId = conv1.id,
                senderId = user2.id,
                senderName = user2.fullName,
                body = "وعليكم السلام أهلاً بك. للتأكد من الملكية بأمان، ما اسم الكلية المكتوب على البطاقة وما العلامة الموجودة على طرف المحفظة؟",
                isRead = true,
                createdAtText = "10:08",
                createdAtEpochMillis = now - 1500_000L
            ),
            ChatMessage(
                id = "m_3",
                conversationId = conv1.id,
                senderId = user1.id,
                senderName = user1.fullName,
                body = "نعم، البطاقة الجامعية لكلية الهندسة المدنية وعليها خياطة بيضاء.",
                isRead = false,
                createdAtText = "10:12",
                createdAtEpochMillis = now - 1200_000L
            )
        )

        notifications.addAll(
            listOf(
                AppNotification(
                    id = "notif_demo_1",
                    userId = user1.id,
                    type = NotificationType.SIMILAR_REPORT,
                    title = "قد يكون هذا الغرض هو ما تبحث عنه (95%)",
                    body = "تم رصد بلاغ موجود في حي الإنشاءات بحمص يطابق بلاغك «محفظة جلدية بنية تحتوي بطاقة جامعية».",
                    relatedReportId = "rep_homs_2",
                    isRead = false,
                    createdAtText = "منذ ساعتين"
                ),
                AppNotification(
                    id = "notif_demo_2",
                    userId = user1.id,
                    type = NotificationType.NEW_MESSAGE,
                    title = "رد جديد من سارة الجندي",
                    body = "ردت سارة على محادثتك الآمنة بخصوص المحفظة في حي الإنشاءات.",
                    relatedReportId = "rep_homs_2",
                    relatedConversationId = conv1.id,
                    isRead = false,
                    createdAtText = "منذ 20 دقيقة"
                )
            )
        )

        reportFlags.add(
            ReportFlag(
                id = "flag_demo_1",
                reportId = "rep_homs_3",
                reportTitle = "ميدالية مفاتيح سيارة مع تعليقة خشبية",
                conversationId = null,
                reporterId = user2.id,
                reporterName = user2.fullName,
                reason = "مراجعة دقة التصنيف",
                details = "بلاغ تجريبي لاختبار لوحة مراجعة البلاغات في الإدارة.",
                status = "pending",
                createdAtText = "اليوم"
            )
        )
    }

    private fun nowFormatted(): String = dateFormatter.format(Date())
}
