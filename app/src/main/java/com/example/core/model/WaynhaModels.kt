package com.example.core.model

/**
 * إعدادات النطاق الجغرافي للنظام:
 * في الإصدار الأول (v1): مدينة حمص هي المدينة الوحيدة المفعّلة والثابتة.
 * صُممت هذه البنية بحيث يمكن تفعيل مدن جديدة مستقبلاً من جدول system_cities في الخادم
 * دون الحاجة إلى إعادة بناء التطبيق أو تعديل منطق البلاغات والمطابقة.
 */
data class CityConfig(
    val code: String,
    val nameAr: String,
    val countryAr: String = "سوريا",
    val isEnabled: Boolean,
    val isDefault: Boolean,
    val neighborhoods: List<String>
)

object SystemLocationSettings {
    const val DEFAULT_CITY_AR = "حمص"
    const val DEFAULT_CITY_CODE = "HOMS"
    const val DEFAULT_COUNTRY_AR = "سوريا"

    val homsNeighborhoods: List<String> = listOf(
        "الإنشاءات",
        "الحمراء",
        "الغوطة",
        "الوعر",
        "عكرمة",
        "الزهراء",
        "باب السباع",
        "باب هود",
        "بستان الديوان",
        "الحميدية",
        "الخالدية",
        "البياضة",
        "دير بعلبة",
        "كرم الشامي",
        "كرم اللوز",
        "وادي الذهب",
        "القصور",
        "المحطة",
        "طريق الشام",
        "جامعة البعث",
        "السوق المسقوف",
        "مركز المدينة"
    )

    private val configuredCities = listOf(
        CityConfig(
            code = DEFAULT_CITY_CODE,
            nameAr = DEFAULT_CITY_AR,
            countryAr = DEFAULT_COUNTRY_AR,
            isEnabled = true,
            isDefault = true,
            neighborhoods = homsNeighborhoods
        )
    )

    fun getActiveCity(): CityConfig = configuredCities.first { it.isEnabled && it.isDefault }

    fun getEnabledCities(): List<CityConfig> = configuredCities.filter { it.isEnabled }

    fun isCityAllowed(cityName: String): Boolean {
        return getEnabledCities().any { it.nameAr == cityName.trim() }
    }
}

enum class ReportType(val dbValue: String, val labelAr: String, val badgeAr: String) {
    LOST("lost", "مفقود", "أضعت غرضاً"),
    FOUND("found", "موجود", "وجدت غرضاً");

    companion object {
        fun fromDb(value: String): ReportType =
            entries.firstOrNull { it.dbValue.equals(value, ignoreCase = true) } ?: LOST
    }
}

enum class ReportStatus(val dbValue: String, val labelAr: String) {
    OPEN("open", "مفتوح"),
    MATCHED("matched", "تطابق محتمل"),
    RECOVERED("recovered", "تمت الاستعادة"),
    CLOSED("closed", "مغلق"),
    HIDDEN("hidden", "مخفي لمخالفة السياسات");

    val isEligibleForMatching: Boolean
        get() = this == OPEN || this == MATCHED

    companion object {
        fun fromDb(value: String): ReportStatus =
            entries.firstOrNull { it.dbValue.equals(value, ignoreCase = true) } ?: OPEN
    }
}

enum class UserRole(val dbValue: String, val labelAr: String) {
    USER("user", "مستخدم"),
    MODERATOR("moderator", "مشرف"),
    ADMIN("admin", "مدير النظام");

    val canAccessAdminPanel: Boolean
        get() = this == ADMIN || this == MODERATOR

    companion object {
        fun fromDb(value: String?): UserRole =
            entries.firstOrNull { it.dbValue.equals(value, ignoreCase = true) } ?: USER
    }
}

data class ReportCategory(
    val id: String,
    val nameAr: String,
    val isSensitiveDocument: Boolean = false,
    val isActive: Boolean = true
)

object InitialCategories {
    val all: List<ReportCategory> = listOf(
        ReportCategory("electronics", "هواتف وأجهزة إلكترونية", isSensitiveDocument = false),
        ReportCategory("wallets", "محافظ ومبالغ نقدية", isSensitiveDocument = false),
        ReportCategory("keys", "مفاتيح", isSensitiveDocument = false),
        ReportCategory("bags", "حقائب", isSensitiveDocument = false),
        ReportCategory("documents", "وثائق وأوراق", isSensitiveDocument = true),
        ReportCategory("jewelry", "مجوهرات وإكسسوارات", isSensitiveDocument = false),
        ReportCategory("pets", "حيوانات أليفة", isSensitiveDocument = false),
        ReportCategory("other", "أغراض أخرى", isSensitiveDocument = false)
    )

    fun byId(id: String): ReportCategory =
        all.firstOrNull { it.id == id } ?: all.last()
}

data class UserProfile(
    val id: String,
    val email: String,
    val fullName: String,
    val avatarUrl: String? = null,
    val city: String = SystemLocationSettings.DEFAULT_CITY_AR,
    val neighborhood: String? = null,
    val role: UserRole = UserRole.USER,
    val isSuspended: Boolean = false,
    val privacyHideLastSeen: Boolean = false,
    val allowMatchNotifications: Boolean = true,
    val deletionRequestedAt: String? = null,
    val createdAt: String = ""
)

data class ReportImage(
    val id: String,
    val reportId: String,
    val storagePath: String,
    val publicUrl: String,
    val isBlurredForPrivacy: Boolean = false
)

data class ItemReport(
    val id: String,
    val userId: String,
    val userName: String,
    val reportType: ReportType,
    val title: String,
    val categoryId: String,
    val description: String,
    val city: String = SystemLocationSettings.DEFAULT_CITY_AR,
    val neighborhood: String, // الحي أو المنطقة داخل حمص دون كشف العنوان الدقيق
    val incidentDateText: String,
    val incidentEpochMillis: Long,
    val distinguishingMarks: String,
    val status: ReportStatus = ReportStatus.OPEN,
    val images: List<ReportImage> = emptyList(),
    val isSensitiveMasked: Boolean = true,
    val createdAtText: String,
    val createdAtEpochMillis: Long
) {
    val category: ReportCategory
        get() = InitialCategories.byId(categoryId)
}

enum class MatchReviewStatus(val dbValue: String, val labelAr: String) {
    PENDING("pending", "قيد المراجعة"),
    CONTACTED("contacted", "تم بدء التواصل"),
    CONFIRMED("confirmed", "تم تأكيد التطابق"),
    DISMISSED("dismissed", "غير مطابق"),
    ARCHIVED("archived", "مؤرشف (البلاغ مغلق)")
}

data class ReportMatch(
    val id: String,
    val lostReportId: String,
    val foundReportId: String,
    val similarityScore: Int, // 0..100
    val matchReasons: List<String>,
    val reviewStatus: MatchReviewStatus = MatchReviewStatus.PENDING,
    val createdAtText: String,
    val createdAtEpochMillis: Long
)

data class Conversation(
    val id: String,
    val reportId: String,
    val reportTitle: String,
    val reportType: ReportType,
    val matchedReportId: String? = null,
    val initiatorId: String,
    val initiatorName: String,
    val recipientId: String,
    val recipientName: String,
    val status: String = "active",
    val lastMessagePreview: String = "",
    val updatedAtText: String = "",
    val unreadCount: Int = 0
)

data class ChatMessage(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val senderName: String,
    val body: String,
    val isRead: Boolean = false,
    val createdAtText: String,
    val createdAtEpochMillis: Long
)

enum class NotificationType(val dbValue: String, val labelAr: String) {
    NEW_MESSAGE("new_message", "رسالة جديدة"),
    SIMILAR_REPORT("similar_report", "ظهور بلاغ مشابه"),
    REPORT_STATUS("report_status", "تحديث حالة البلاغ"),
    RECOVERY_UPDATE("recovery_update", "تحديث استعادة الغرض")
}

data class AppNotification(
    val id: String,
    val userId: String,
    val type: NotificationType,
    val title: String,
    val body: String,
    val relatedReportId: String? = null,
    val relatedConversationId: String? = null,
    val isRead: Boolean = false,
    val createdAtText: String
)

data class ReportFlag(
    val id: String,
    val reportId: String?,
    val reportTitle: String,
    val conversationId: String?,
    val reporterId: String,
    val reporterName: String,
    val reason: String,
    val details: String,
    val status: String = "pending",
    val createdAtText: String
)

data class RecoveryConfirmation(
    val id: String,
    val reportId: String,
    val matchedReportId: String?,
    val ownerUserId: String,
    val finderUserId: String?,
    val ownerConfirmed: Boolean,
    val finderConfirmed: Boolean,
    val handoverNotes: String,
    val status: String,
    val confirmedAtText: String?
)

data class AdminAuditLog(
    val id: String,
    val adminId: String,
    val adminName: String,
    val actionType: String,
    val targetTable: String,
    val targetId: String,
    val notes: String,
    val createdAtText: String
)

enum class DateFilterOption(val labelAr: String, val maxDaysAgo: Int?) {
    ALL("كل الأوقات", null),
    TODAY("اليوم", 1),
    LAST_3_DAYS("آخر 3 أيام", 3),
    LAST_WEEK("آخر 7 أيام", 7),
    LAST_MONTH("آخر 30 يوماً", 30)
}

data class SearchFilterState(
    val query: String = "",
    val reportType: ReportType? = null,
    val categoryId: String? = null,
    val neighborhood: String? = null,
    val dateFilter: DateFilterOption = DateFilterOption.ALL,
    val sortByNewest: Boolean = true
) {
    val hasActiveFilters: Boolean
        get() = query.isNotBlank() || reportType != null || categoryId != null ||
            neighborhood != null || dateFilter != DateFilterOption.ALL
}
