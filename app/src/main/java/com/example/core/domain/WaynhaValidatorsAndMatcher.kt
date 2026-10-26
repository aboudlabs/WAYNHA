package com.example.core.domain

import com.example.core.model.Conversation
import com.example.core.model.ItemReport
import com.example.core.model.MatchReviewStatus
import com.example.core.model.ReportMatch
import com.example.core.model.ReportStatus
import com.example.core.model.ReportType
import com.example.core.model.SearchFilterState
import com.example.core.model.SystemLocationSettings
import com.example.core.model.UserProfile
import com.example.core.model.UserRole
import java.util.UUID
import kotlin.math.abs
import kotlin.math.min

sealed class ValidationResult {
    data object Valid : ValidationResult()
    data class Invalid(val messageAr: String) : ValidationResult()
}

/**
 * مدقق الأمان والخصوصية والنطاق الجغرافي لمدينة حمص:
 * - يمنع نشر أي بلاغ خارج مدينة حمص.
 * - يمنع تضمين أرقام الهواتف أو الأرقام الوطنية الحساسة في الحقول العامة.
 * - يمنع كشف الإحداثيات أو العناوين التفصيلية الدقيقة.
 * - يتحقق من صلاحيات المستخدم (RLS Guard) قبل التعديل أو الحذف أو قراءة المحادثات.
 */
object WaynhaSecurityValidator {

    private val syrianPhoneOrSensitiveNumberRegex =
        Regex("""(\+?963[\s-]?\d{8,9}|09\d[\s-]?\d{3}[\s-]?\d{4}|09\d{8}|\b\d{9,12}\b)""")

    private val gpsCoordinatesRegex =
        Regex("""\b\d{1,2}\.\d{4,}\s*,\s*\d{1,2}\.\d{4,}\b""")

    fun validateReportInput(
        title: String,
        description: String,
        categoryId: String,
        city: String,
        neighborhood: String?,
        distinguishingMarks: String,
        hasUnmaskedSensitiveDocumentImage: Boolean = false
    ): ValidationResult {
        val cleanCity = city.trim()
        if (!SystemLocationSettings.isCityAllowed(cleanCity) || cleanCity != SystemLocationSettings.DEFAULT_CITY_AR) {
            return ValidationResult.Invalid(
                "التطبيق مخصص حالياً لمدينة حمص فقط. لا يُسمح بنشر بلاغات خارج مدينة حمص."
            )
        }

        if (title.trim().length < 3) {
            return ValidationResult.Invalid("يرجى إدخال عنوان واضح للغرض (3 أحرف على الأقل).")
        }

        if (description.trim().length < 10) {
            return ValidationResult.Invalid("يرجى كتابة وصف كافٍ للغرض (10 أحرف على الأقل).")
        }

        if (categoryId.isBlank()) {
            return ValidationResult.Invalid("يرجى اختيار تصنيف الغرض.")
        }

        if (neighborhood != null && neighborhood.isNotBlank() &&
            !SystemLocationSettings.homsNeighborhoods.contains(neighborhood.trim())
        ) {
            // نسمح بالأحياء المعرّفة في حمص أو إدخال حي داخل حمص بشرط عدم كتابة إحداثيات
            if (gpsCoordinatesRegex.containsMatchIn(neighborhood)) {
                return ValidationResult.Invalid("لا تعرض الإحداثيات أو الموقع الدقيق علناً؛ اكتفِ باسم الحي داخل حمص.")
            }
        }

        val combinedPublicText = "$title $description $distinguishingMarks"
        if (syrianPhoneOrSensitiveNumberRegex.containsMatchIn(combinedPublicText)) {
            return ValidationResult.Invalid(
                "حمايةً لخصوصيتك، يُمنع نشر رقم الهاتف أو رقم الوثيقة الشخصي علناً في البلاغ. سيتم التواصل بأمان عبر محادثات التطبيق."
            )
        }

        if (gpsCoordinatesRegex.containsMatchIn(combinedPublicText)) {
            return ValidationResult.Invalid(
                "يرجى عدم كتابة إحداثيات الموقع الدقيق علناً في الوصف."
            )
        }

        if (categoryId == "documents" && hasUnmaskedSensitiveDocumentImage) {
            return ValidationResult.Invalid(
                "لا يُسمح بنشر صورة وثيقة حساسة مكشوفة البيانات علناً. يرجى تفعيل خيار إخفاء الأرقام الحساسة أو الاكتفاء بذكر نوع الوثيقة."
            )
        }

        return ValidationResult.Valid
    }

    fun validateMessageInput(body: String): ValidationResult {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) {
            return ValidationResult.Invalid("لا يمكن إرسال رسالة فارغة.")
        }
        if (trimmed.length > 1500) {
            return ValidationResult.Invalid("الرسالة طويلة جداً (الحد الأقصى 1500 حرف).")
        }
        return ValidationResult.Valid
    }

    fun canModifyReport(user: UserProfile?, report: ItemReport): Boolean {
        if (user == null || user.isSuspended) return false
        return user.id == report.userId || user.role.canAccessAdminPanel
    }

    fun canAccessConversation(
        user: UserProfile?,
        conversation: Conversation,
        blockedUserIds: Set<String> = emptySet()
    ): Boolean {
        if (user == null || user.isSuspended) return false
        if (conversation.status == "blocked") return false
        val isParticipant = user.id == conversation.initiatorId || user.id == conversation.recipientId
        if (!isParticipant && !user.role.canAccessAdminPanel) return false
        val otherPartyId = if (user.id == conversation.initiatorId) conversation.recipientId else conversation.initiatorId
        if (blockedUserIds.contains(otherPartyId)) return false
        return true
    }

    fun canSendMessageInConversation(
        user: UserProfile?,
        conversation: Conversation,
        blockedUserIds: Set<String> = emptySet()
    ): Boolean {
        if (user == null || user.isSuspended) return false
        if (conversation.status != "active") return false
        val isParticipant = user.id == conversation.initiatorId || user.id == conversation.recipientId
        if (!isParticipant) return false
        val otherPartyId = if (user.id == conversation.initiatorId) conversation.recipientId else conversation.initiatorId
        return !blockedUserIds.contains(otherPartyId)
    }

    fun canAccessAdminDashboard(user: UserProfile?): Boolean {
        return user != null && !user.isSuspended &&
            (user.role == UserRole.ADMIN || user.role == UserRole.MODERATOR)
    }
}

/**
 * محرك البحث والمطابقة المعتمد على القواعد الواضحة (Rule-Based Matching Engine):
 * - يطابق بلاغات المفقودات (LOST) مع بلاغات الموجودات (FOUND) داخل مدينة حمص فقط.
 * - يستبعد البلاغات المغلقة (CLOSED) والمستردة (RECOVERED) والمخفية (HIDDEN).
 * - يحسب درجة تشابه دقيقة من 100 بناءً على التصنيف، الحي داخل حمص، التاريخ، الوصف، والعلامات المميزة.
 * - يمنع تكرار نفس المطابقة.
 */
object WaynhaMatchingEngine {

    private val arabicStopWords = setOf(
        "في", "من", "على", "إلى", "عن", "مع", "هذا", "هذه", "تم", "أو", "و",
        "حمص", "حي", "منطقة", "قرب", "عند", "ضاع", "فقدت", "وجدت", "لقيت", "غرض", "شي"
    )

    fun filterAndSortReports(
        reports: List<ItemReport>,
        filter: SearchFilterState,
        nowEpochMillis: Long = System.currentTimeMillis()
    ): List<ItemReport> {
        return reports
            .asSequence()
            .filter { it.city == SystemLocationSettings.DEFAULT_CITY_AR }
            .filter { it.status != ReportStatus.HIDDEN }
            .filter { report ->
                if (filter.reportType != null) report.reportType == filter.reportType else true
            }
            .filter { report ->
                if (!filter.categoryId.isNullOrBlank()) report.categoryId == filter.categoryId else true
            }
            .filter { report ->
                if (!filter.neighborhood.isNullOrBlank()) {
                    report.neighborhood.trim() == filter.neighborhood.trim()
                } else {
                    true
                }
            }
            .filter { report ->
                val maxDays = filter.dateFilter.maxDaysAgo
                if (maxDays != null) {
                    val cutoff = nowEpochMillis - maxDays * 24L * 60L * 60L * 1000L
                    report.incidentEpochMillis >= cutoff || report.createdAtEpochMillis >= cutoff
                } else {
                    true
                }
            }
            .filter { report ->
                if (filter.query.isBlank()) {
                    true
                } else {
                    val q = normalizeArabic(filter.query)
                    val searchable = normalizeArabic(
                        "${report.title} ${report.description} ${report.neighborhood} ${report.distinguishingMarks} ${report.category.nameAr}"
                    )
                    q.split(" ").filter { it.isNotBlank() }.all { token -> searchable.contains(token) }
                }
            }
            .let { seq ->
                if (filter.sortByNewest) {
                    seq.sortedByDescending { it.createdAtEpochMillis }
                } else {
                    seq.sortedBy { it.createdAtEpochMillis }
                }
            }
            .toList()
    }

    fun computeMatches(
        allReports: List<ItemReport>,
        existingMatches: List<ReportMatch> = emptyList(),
        minimumScoreThreshold: Int = 45
    ): List<ReportMatch> {
        val eligibleReports = allReports.filter {
            it.city == SystemLocationSettings.DEFAULT_CITY_AR && it.status.isEligibleForMatching
        }

        val lostReports = eligibleReports.filter { it.reportType == ReportType.LOST }
        val foundReports = eligibleReports.filter { it.reportType == ReportType.FOUND }

        val existingByPair = existingMatches.associateBy { "${it.lostReportId}_${it.foundReportId}" }
        val results = mutableListOf<ReportMatch>()

        // أرشفة المطابقات التي أُغلقت بلاغاتها
        val openReportIds = eligibleReports.map { it.id }.toSet()
        for (existing in existingMatches) {
            if (!openReportIds.contains(existing.lostReportId) || !openReportIds.contains(existing.foundReportId)) {
                results.add(existing.copy(reviewStatus = MatchReviewStatus.ARCHIVED))
            }
        }

        for (lost in lostReports) {
            for (found in foundReports) {
                // لا نطابق بلاغين لنفس الحساب إذا كانا تجريبيين لنفس الشخص إلا عند اختلاف الهوية أو الغرض،
                // وفي القواعد القياسية نتحقق من تطابق التصنيف أولاً
                if (lost.categoryId != found.categoryId) continue

                var score = 40 // تطابق التصنيف الأساسي (40 نقطة)
                val reasons = mutableListOf("تطابق التصنيف (${lost.category.nameAr})")

                // 1. تطابق المنطقة أو الحي داخل حمص (25 نقطة)
                if (lost.neighborhood.isNotBlank() &&
                    normalizeArabic(lost.neighborhood) == normalizeArabic(found.neighborhood)
                ) {
                    score += 25
                    reasons.add("نفس الحي داخل حمص (${lost.neighborhood})")
                }

                // 2. تقارب التاريخ (حتى 15 نقطة)
                val daysDiff = abs(lost.incidentEpochMillis - found.incidentEpochMillis) / (1000L * 60L * 60L * 24L)
                if (daysDiff <= 2) {
                    score += 15
                    reasons.add("تاريخ متقارب جداً (خلال يومين)")
                } else if (daysDiff <= 7) {
                    score += 10
                    reasons.add("تقارب تاريخ الفقد والعثور (خلال أسبوع)")
                } else if (daysDiff <= 14) {
                    score += 5
                }

                // 3. تشابه الكلمات المفتاحية في العنوان والوصف (حتى 12 نقطة)
                val lostTokens = extractKeywords("${lost.title} ${lost.description}")
                val foundTokens = extractKeywords("${found.title} ${found.description}")
                val sharedKeywords = lostTokens.intersect(foundTokens)
                if (sharedKeywords.isNotEmpty()) {
                    val keywordPoints = min(12, sharedKeywords.size * 4)
                    score += keywordPoints
                    reasons.add("كلمات مشتركة في الوصف (${sharedKeywords.take(3).joinToString("، ")})")
                }

                // 4. تشابه العلامات المميزة (حتى 8 نقاط)
                val lostMarks = extractKeywords(lost.distinguishingMarks)
                val foundMarks = extractKeywords("${found.distinguishingMarks} ${found.description}")
                val sharedMarks = lostMarks.intersect(foundMarks)
                if (sharedMarks.isNotEmpty()) {
                    score += min(8, sharedMarks.size * 4)
                    reasons.add("تشابه في العلامات المميزة")
                }

                val cappedScore = score.coerceIn(0, 98)
                if (cappedScore >= minimumScoreThreshold) {
                    val pairKey = "${lost.id}_${found.id}"
                    val previous = existingByPair[pairKey]
                    val match = ReportMatch(
                        id = previous?.id ?: "match_${lost.id}_${found.id}",
                        lostReportId = lost.id,
                        foundReportId = found.id,
                        similarityScore = cappedScore,
                        matchReasons = reasons,
                        reviewStatus = previous?.reviewStatus ?: MatchReviewStatus.PENDING,
                        createdAtText = previous?.createdAtText ?: "الآن",
                        createdAtEpochMillis = previous?.createdAtEpochMillis ?: System.currentTimeMillis()
                    )
                    results.add(match)
                }
            }
        }

        return results
            .distinctBy { "${it.lostReportId}_${it.foundReportId}" }
            .sortedByDescending { it.similarityScore }
    }

    fun normalizeArabic(input: String): String {
        return input
            .trim()
            .lowercase()
            .replace(Regex("[أإآ]"), "ا")
            .replace("ة", "ه")
            .replace("ى", "ي")
            .replace(Regex("[ًٌٍَُِّْ]"), "")
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun extractKeywords(text: String): Set<String> {
        return normalizeArabic(text)
            .split(" ")
            .map { it.trim() }
            .filter { it.length >= 3 && !arabicStopWords.contains(it) }
            .toSet()
    }
}
