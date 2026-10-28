package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.data.WaynhaRepository
import com.example.core.domain.WaynhaMatchingEngine
import com.example.core.domain.WaynhaSecurityValidator
import com.example.core.model.AdminAuditLog
import com.example.core.model.AppNotification
import com.example.core.model.ChatMessage
import com.example.core.model.Conversation
import com.example.core.model.DateFilterOption
import com.example.core.model.ItemReport
import com.example.core.model.RecoveryConfirmation
import com.example.core.model.RecoveryRequest
import com.example.core.model.ReportCategory
import com.example.core.model.ReportFlag
import com.example.core.model.ReportImage
import com.example.core.model.ReportMatch
import com.example.core.model.ReportType
import com.example.core.model.SearchFilterState
import com.example.core.model.SystemLocationSettings
import com.example.core.model.UserProfile
import com.example.core.model.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class WaynhaScreen {
    SPLASH,
    ONBOARDING,
    AUTH,
    HOME,
    SEARCH,
    ADD_REPORT_SHEET,
    ADD_LOST_REPORT,
    ADD_FOUND_REPORT,
    EDIT_REPORT,
    REPORT_DETAILS,
    SAVED_REPORTS,
    MY_REPORTS,
    CONVERSATIONS,
    CHAT_ROOM,
    NOTIFICATIONS,
    PROFILE_SETTINGS,
    ADMIN_DASHBOARD
}

data class WaynhaUiState(
    val currentScreen: WaynhaScreen = WaynhaScreen.HOME,
    val screenHistory: List<WaynhaScreen> = emptyList(),
    val isDarkMode: Boolean = false,
    val isLiveSupabaseConnected: Boolean = false,
    val isOfflineSimulated: Boolean = false,
    val isImageUploadFailureSimulated: Boolean = false,
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false, // لمنع النقر المتكرر أثناء إرسال الطلبات
    val bannerMessage: String? = null,
    val isBannerError: Boolean = false,
    val currentUser: UserProfile? = null,
    val activeCityName: String = SystemLocationSettings.DEFAULT_CITY_AR,
    val homsNeighborhoods: List<String> = SystemLocationSettings.homsNeighborhoods,
    val categories: List<ReportCategory> = emptyList(),
    val allReports: List<ItemReport> = emptyList(),
    val filteredReports: List<ItemReport> = emptyList(),
    val userReports: List<ItemReport> = emptyList(),
    val savedReports: List<ItemReport> = emptyList(),
    val savedReportIds: Set<String> = emptySet(),
    val userMatches: List<ReportMatch> = emptyList(),
    val selectedReportId: String? = null,
    val selectedReportMatches: List<ReportMatch> = emptyList(),
    val selectedReportRecoveries: List<RecoveryConfirmation> = emptyList(),
    val verificationQuestions: List<Pair<String, String>> = emptyList(),
    val recoveryRequests: List<RecoveryRequest> = emptyList(),
    val editingReport: ItemReport? = null,
    val draftUploadedImages: List<ReportImage> = emptyList(),
    val searchFilter: SearchFilterState = SearchFilterState(),
    val conversations: List<Conversation> = emptyList(),
    val activeConversationId: String? = null,
    val activeConversationMessages: List<ChatMessage> = emptyList(),
    val notifications: List<AppNotification> = emptyList(),
    val unreadNotificationsCount: Int = 0,
    val adminProfiles: List<UserProfile> = emptyList(),
    val adminFlags: List<ReportFlag> = emptyList(),
    val adminAuditLogs: List<AdminAuditLog> = emptyList()
)

class WaynhaViewModel(
    application: Application,
    val repository: WaynhaRepository = WaynhaRepository(application.applicationContext)
) : AndroidViewModel(application) {

    constructor(application: Application) : this(
        application,
        WaynhaRepository(application.applicationContext)
    )

    private val introPrefs = application.getSharedPreferences("waynha_intro", android.content.Context.MODE_PRIVATE)
    private val _uiState = MutableStateFlow(
        WaynhaUiState(
            currentScreen = if (introPrefs.getBoolean("intro_completed", false)) {
                WaynhaScreen.HOME
            } else {
                WaynhaScreen.SPLASH
            },
            isDarkMode = introPrefs.getBoolean("dark_mode", false)
        )
    )
    val uiState: StateFlow<WaynhaUiState> = _uiState.asStateFlow()

    init {
        refreshAllState()
        if (repository.isLiveSupabaseConnected) {
            viewModelScope.launch {
                repository.restorePersistedSession()
                    .onSuccess { refreshAllState() }
            }
        }
    }

    fun refreshHomeFeed() {
        if (!repository.isLiveSupabaseConnected) return
        viewModelScope.launch {
            repository.refreshLiveReports().onSuccess { refreshAllState() }
        }
    }

    fun completeIntroAndNavigate(target: WaynhaScreen) {
        introPrefs.edit().putBoolean("intro_completed", true).apply()
        navigateTo(target, clearHistory = true)
    }

    fun refreshAllState() {
        val user = repository.getCurrentUser()
        val allReps = repository.getAllReports()
        val currentFilter = _uiState.value.searchFilter
        val filtered = WaynhaMatchingEngine.filterAndSortReports(allReps, currentFilter)
        val savedIds = if (user != null) repository.getSavedReportIds(user.id) else emptySet()
        val savedList = if (user != null) repository.getSavedReports(user.id) else emptyList()
        val myReps = if (user != null) repository.getUserReports(user.id) else emptyList()
        val myMatches = if (user != null) repository.getMatchesForUser(user.id) else emptyList()
        val convs = repository.getConversationsForUser(user)
        val notifs = if (user != null) repository.getNotificationsForUser(user.id) else emptyList()
        val selectedId = _uiState.value.selectedReportId
        val selectedMatches = if (selectedId != null) repository.getMatchesForReport(selectedId) else emptyList()
        val selectedRecs = if (selectedId != null) repository.getRecoveryConfirmationsForReport(selectedId) else emptyList()

        val activeConvId = _uiState.value.activeConversationId
        val activeMsgs = if (activeConvId != null && user != null) {
            repository.getMessagesForConversation(user, activeConvId).getOrDefault(emptyList())
        } else {
            emptyList()
        }

        val isAdmin = WaynhaSecurityValidator.canAccessAdminDashboard(user)
        val adminUsers = if (isAdmin) repository.getAllProfilesForAdmin(user).getOrDefault(emptyList()) else emptyList()
        val adminFlags = if (isAdmin) repository.getReportFlagsForAdmin(user).getOrDefault(emptyList()) else emptyList()
        val adminLogs = if (isAdmin) repository.getAuditLogsForAdmin(user).getOrDefault(emptyList()) else emptyList()

        _uiState.update { state ->
            state.copy(
                isLiveSupabaseConnected = repository.isLiveSupabaseConnected,
                isOfflineSimulated = repository.simulateOfflineMode,
                isImageUploadFailureSimulated = repository.simulateImageUploadFailure,
                currentUser = user,
                categories = repository.getCategories(),
                allReports = allReps,
                filteredReports = filtered,
                userReports = myReps,
                savedReports = savedList,
                savedReportIds = savedIds,
                userMatches = myMatches,
                selectedReportMatches = selectedMatches,
                selectedReportRecoveries = selectedRecs,
                conversations = convs,
                activeConversationMessages = activeMsgs,
                notifications = notifs,
                unreadNotificationsCount = notifs.count { !it.isRead },
                adminProfiles = adminUsers,
                adminFlags = adminFlags,
                adminAuditLogs = adminLogs
            )
        }
        if (isAdmin && repository.isLiveSupabaseConnected) {
            viewModelScope.launch {
                repository.refreshLiveReportFlagsForAdmin(user).onSuccess { liveFlags ->
                    _uiState.update { it.copy(adminFlags = liveFlags) }
                }
            }
        }
        if (user != null && repository.isLiveSupabaseConnected) {
            viewModelScope.launch {
                repository.refreshLiveConversations(user).onSuccess { liveConversations ->
                    _uiState.update { it.copy(conversations = liveConversations) }
                    val activeId = _uiState.value.activeConversationId
                    if (activeId != null) {
                        repository.refreshLiveMessages(activeId).onSuccess { liveMessages ->
                            _uiState.update { it.copy(activeConversationMessages = liveMessages) }
                        }
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // التنقل بين الشاشات الـ 14
    // -------------------------------------------------------------------------
    fun navigateTo(screen: WaynhaScreen, clearHistory: Boolean = false) {
        _uiState.update { state ->
            val updatedHistory = if (clearHistory) {
                emptyList()
            } else if (state.currentScreen != screen) {
                state.screenHistory + state.currentScreen
            } else {
                state.screenHistory
            }
            state.copy(
                currentScreen = screen,
                screenHistory = updatedHistory,
                bannerMessage = null
            )
        }
        refreshAllState()
    }

    fun navigateBack(): Boolean {
        val history = _uiState.value.screenHistory
        if (history.isEmpty()) {
            if (_uiState.value.currentScreen != WaynhaScreen.HOME) {
                _uiState.update { it.copy(currentScreen = WaynhaScreen.HOME) }
                return true
            }
            return false
        }
        val previous = history.last()
        _uiState.update {
            it.copy(
                currentScreen = previous,
                screenHistory = history.dropLast(1),
                bannerMessage = null
            )
        }
        refreshAllState()
        return true
    }

    fun openReportDetails(reportId: String) {
        _uiState.update { state ->
            state.copy(
                selectedReportId = reportId,
                currentScreen = WaynhaScreen.REPORT_DETAILS,
                screenHistory = state.screenHistory + state.currentScreen,
                bannerMessage = null
            )
        }
        refreshAllState()
        viewModelScope.launch {
            val questions = repository.getVerificationQuestions(reportId).getOrDefault(emptyList())
            val requests = repository.getRecoveryRequestsForReport(reportId).getOrDefault(emptyList())
            _uiState.update { it.copy(verificationQuestions = questions, recoveryRequests = requests) }
        }
    }

    fun openAddReportScreen(type: ReportType) {
        if (_uiState.value.currentUser == null) {
            showBanner("يرجى تسجيل الدخول أولاً لنشر بلاغ في حمص.", isError = true)
            navigateTo(WaynhaScreen.AUTH)
            return
        }
        _uiState.update { state ->
            state.copy(
                draftUploadedImages = emptyList(),
                editingReport = null,
                currentScreen = if (type == ReportType.LOST) WaynhaScreen.ADD_LOST_REPORT else WaynhaScreen.ADD_FOUND_REPORT,
                screenHistory = state.screenHistory + state.currentScreen,
                bannerMessage = null
            )
        }
    }

    fun openEditReportScreen(report: ItemReport) {
        val user = _uiState.value.currentUser
        if (!WaynhaSecurityValidator.canModifyReport(user, report)) {
            showBanner("غير مصرح لك بتعديل بلاغ مستخدم آخر.", isError = true)
            return
        }
        _uiState.update { state ->
            state.copy(
                editingReport = report,
                draftUploadedImages = report.images,
                currentScreen = WaynhaScreen.EDIT_REPORT,
                screenHistory = state.screenHistory + state.currentScreen,
                bannerMessage = null
            )
        }
    }

    fun openConversationRoom(conversationId: String) {
        val user = _uiState.value.currentUser
        val res = repository.getMessagesForConversation(user, conversationId)
        if (res.isFailure) {
            showBanner(res.exceptionOrNull()?.message ?: "غير مصرح لك بفتح هذه المحادثة.", isError = true)
            return
        }
        repository.markConversationRead(conversationId)
        _uiState.update { state ->
            state.copy(
                activeConversationId = conversationId,
                currentScreen = WaynhaScreen.CHAT_ROOM,
                screenHistory = state.screenHistory + state.currentScreen,
                bannerMessage = null
            )
        }
        refreshAllState()
        if (repository.isLiveSupabaseConnected) {
            viewModelScope.launch {
                repository.refreshLiveMessages(conversationId).onSuccess { messages ->
                    _uiState.update { state ->
                        if (state.activeConversationId == conversationId) state.copy(activeConversationMessages = messages) else state
                    }
                }.onFailure { showBanner(it.message ?: "تعذر تحميل الرسائل.", true) }
            }
        }
    }

    fun toggleDarkMode() {
        val enabled = !_uiState.value.isDarkMode
        introPrefs.edit().putBoolean("dark_mode", enabled).apply()
        _uiState.update { it.copy(isDarkMode = enabled) }
    }

    fun toggleOfflineSimulation(enabled: Boolean) {
        repository.simulateOfflineMode = enabled
        refreshAllState()
        val msg = if (enabled) {
            "تم تفعيل محاكاة انقطاع الاتصال لاختبار معالجة أخطاء الشبكة."
        } else {
            "تمت استعادة الاتصال الطبيعي."
        }
        showBanner(msg, isError = enabled)
    }

    fun toggleImageUploadFailureSimulation(enabled: Boolean) {
        repository.simulateImageUploadFailure = enabled
        refreshAllState()
        val msg = if (enabled) {
            "تم تفعيل محاكاة فشل رفع الصور لاختبار رسائل الخطأ."
        } else {
            "تم إيقاف محاكاة فشل رفع الصور."
        }
        showBanner(msg, isError = enabled)
    }

    fun clearBanner() {
        _uiState.update { it.copy(bannerMessage = null) }
    }

    fun showBanner(message: String, isError: Boolean = false) {
        _uiState.update { it.copy(bannerMessage = message, isBannerError = isError) }
    }

    // -------------------------------------------------------------------------
    // المصادقة والحسابات
    // -------------------------------------------------------------------------
    fun login(email: String, password: String) {
        if (_uiState.value.isSubmitting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, isLoading = true, bannerMessage = null) }
            val result = repository.login(email, password)
            _uiState.update { it.copy(isSubmitting = false, isLoading = false) }
            result.onSuccess { user ->
                refreshAllState()
                showBanner("أهلاً بك يا ${user.fullName} في وينها - حمص!", isError = false)
                navigateTo(WaynhaScreen.HOME, clearHistory = true)
            }.onFailure { err ->
                showBanner(err.message ?: "تعذر تسجيل الدخول.", isError = true)
            }
        }
    }

    fun register(fullName: String, email: String, password: String, neighborhood: String?) {
        if (_uiState.value.isSubmitting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, isLoading = true, bannerMessage = null) }
            val result = repository.registerAccount(fullName, email, password, neighborhood)
            _uiState.update { it.copy(isSubmitting = false, isLoading = false) }
            result.onSuccess { user ->
                refreshAllState()
                showBanner("تم إنشاء حسابك بنجاح في مدينة حمص يا ${user.fullName}!", isError = false)
                navigateTo(WaynhaScreen.HOME, clearHistory = true)
            }.onFailure { err ->
                val message = err.message ?: "تعذر إنشاء الحساب."
                if (message.contains("تم إنشاء الحساب بنجاح")) {
                    showBanner(message, isError = false)
                } else {
                    showBanner(message, isError = true)
                }
            }
        }
    }

    fun switchDemoAccount(email: String, password: String) {
        login(email, password)
    }

    fun logout() {
        repository.logout()
        refreshAllState()
        showBanner("تم تسجيل الخروج بنجاح.", isError = false)
        navigateTo(WaynhaScreen.AUTH, clearHistory = true)
    }

    fun updateProfile(
        fullName: String,
        neighborhood: String?,
        privacyHideLastSeen: Boolean,
        allowMatchNotifications: Boolean
    ) {
        if (_uiState.value.isSubmitting) return
        _uiState.update { it.copy(isSubmitting = true) }
        viewModelScope.launch {
            val res = repository.updateProfile(
                fullName = fullName,
                neighborhood = neighborhood,
                avatarUrl = _uiState.value.currentUser?.avatarUrl,
                privacyHideLastSeen = privacyHideLastSeen,
                allowMatchNotifications = allowMatchNotifications
            )
            _uiState.update { it.copy(isSubmitting = false) }
            res.onSuccess {
                refreshAllState()
                showBanner("تم حفظ تعديلات الملف الشخصي بنجاح.", isError = false)
            }.onFailure { err -> showBanner(err.message ?: "تعذر تحديث الملف الشخصي.", isError = true) }
        }
    }


    fun updateAccountCredentials(newEmail: String, newPassword: String) {
        if (_uiState.value.isSubmitting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, isLoading = true) }
            val result = repository.updateAccountCredentials(newEmail, newPassword)
            _uiState.update { it.copy(isSubmitting = false, isLoading = false) }
            result.onSuccess {
                refreshAllState()
                showBanner(
                    if (newEmail.isNotBlank() && newPassword.isNotBlank()) "تم تحديث بيانات الدخول. إذا طُلب تأكيد البريد، راجع الرسالة التي وصلت إلى بريدك الجديد."
                    else if (newPassword.isNotBlank()) "تم تغيير كلمة المرور بنجاح."
                    else "تم إرسال طلب تغيير البريد. راجع بريدك الجديد لتأكيد التغيير إذا طُلب منك ذلك.",
                    isError = false
                )
            }.onFailure { err -> showBanner(err.message ?: "تعذر تحديث بيانات الدخول.", isError = true) }
        }
    }

    fun saveProfileAvatar(uri: Uri, offsetX: Float, offsetY: Float) {
        if (_uiState.value.isSubmitting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, isLoading = true) }
            val bitmap = try {
                getApplication<Application>().contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it) }
            } catch (_: Exception) { null }
            val bytes = try {
                if (bitmap == null) null else {
                    val side = (minOf(bitmap.width, bitmap.height) * 0.8f).toInt().coerceAtLeast(1)
                    val extraX = (bitmap.width - side).coerceAtLeast(0)
                    val extraY = (bitmap.height - side).coerceAtLeast(0)
                    val left = ((bitmap.width - side) / 2f - offsetX.coerceIn(-1f, 1f) * extraX / 2f).toInt().coerceIn(0, extraX)
                    val top = ((bitmap.height - side) / 2f - offsetY.coerceIn(-1f, 1f) * extraY / 2f).toInt().coerceIn(0, extraY)
                    val crop = android.graphics.Bitmap.createBitmap(bitmap, left, top, side, side)
                    val output = java.io.ByteArrayOutputStream()
                    crop.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, output)
                    if (crop !== bitmap) crop.recycle()
                    bitmap.recycle()
                    output.toByteArray()
                }
            } catch (_: Exception) { null }
            val result = if (bytes == null) Result.failure(IllegalArgumentException("تعذر تجهيز الصورة. اختر صورة أخرى وحاول مجدداً.")) else repository.saveProfileAvatar(bytes)
            _uiState.update { it.copy(isSubmitting = false, isLoading = false) }
            result.onSuccess {
                refreshAllState()
                showBanner("تم تحديث الصورة الشخصية بنجاح.", isError = false)
            }.onFailure { err -> showBanner(err.message ?: "تعذر تحديث الصورة الشخصية.", isError = true) }
        }
    }

    fun requestAccountDeletion() {
        val res = repository.requestAccountDeletion()
        res.onSuccess {
            refreshAllState()
            showBanner("تم تسجيل طلب حذف الحساب والبيانات بأمان، وسيتم تنفيذه وفق سياسة الخصوصية.", isError = false)
        }.onFailure { err ->
            showBanner(err.message ?: "تعذر تقديم طلب الحذف.", isError = true)
        }
    }

    // -------------------------------------------------------------------------
    // رفع الصور وإدارة البلاغات
    // -------------------------------------------------------------------------
    fun uploadDraftImageFromUri(uri: Uri?, isBlurredForPrivacy: Boolean = false) {
        if (uri == null || _uiState.value.isSubmitting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, isLoading = true) }
            val bytes = try {
                getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes() }
            } catch (_: Exception) {
                null
            }
            val res = repository.uploadReportImage(
                imageUriOrPath = uri.toString(),
                imageBytes = bytes,
                isBlurredForPrivacy = isBlurredForPrivacy
            )
            _uiState.update { it.copy(isSubmitting = false, isLoading = false) }
            res.onSuccess { uploaded ->
                _uiState.update { state ->
                    state.copy(draftUploadedImages = state.draftUploadedImages + uploaded)
                }
                showBanner("تم رفع الصورة وإرفاقها بالبلاغ بنجاح.", isError = false)
            }.onFailure { err ->
                showBanner(err.message ?: "فشل رفع الصورة.", isError = true)
            }
        }
    }

    fun attachSampleVerifiedPhoto(label: String, isBlurredForPrivacy: Boolean = false) {
        if (_uiState.value.isSubmitting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, isLoading = true) }
            val res = repository.uploadReportImage(
                imageUriOrPath = "waynha://storage/report-images/$label",
                imageBytes = label.encodeToByteArray(),
                isBlurredForPrivacy = isBlurredForPrivacy
            )
            _uiState.update { it.copy(isSubmitting = false, isLoading = false) }
            res.onSuccess { uploaded ->
                _uiState.update { state ->
                    state.copy(draftUploadedImages = state.draftUploadedImages + uploaded)
                }
                showBanner("تم رفع الصورة إلى وحدة التخزين بنجاح.", isError = false)
            }.onFailure { err ->
                showBanner(err.message ?: "فشل رفع الصورة.", isError = true)
            }
        }
    }

    fun removeDraftImage(imageId: String) {
        _uiState.update { state ->
            state.copy(draftUploadedImages = state.draftUploadedImages.filterNot { it.id == imageId })
        }
    }

    fun submitNewReport(
        reportType: ReportType,
        title: String,
        categoryId: String,
        description: String,
        neighborhood: String,
        incidentDateText: String,
        distinguishingMarks: String,
        isSensitiveMasked: Boolean,
        verificationQuestions: List<Pair<String, String>> = emptyList(),
        cityOverrideForTesting: String = SystemLocationSettings.DEFAULT_CITY_AR
    ) {
        if (_uiState.value.isSubmitting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, isLoading = true, bannerMessage = null) }
            val res = repository.createReport(
                reportType = reportType,
                title = title,
                categoryId = categoryId,
                description = description,
                city = cityOverrideForTesting,
                neighborhood = neighborhood,
                incidentDateText = incidentDateText,
                distinguishingMarks = distinguishingMarks,
                uploadedImages = _uiState.value.draftUploadedImages,
                isSensitiveMasked = isSensitiveMasked,
                verificationQuestions = verificationQuestions
            )
            _uiState.update { it.copy(isSubmitting = false, isLoading = false) }
            res.onSuccess { created ->
                refreshAllState()
                val matchesCount = repository.getMatchesForReport(created.id).size
                val matchMsg = if (matchesCount > 0) {
                    " وتم العثور على $matchesCount بلاغ مشابه في حمص!"
                } else {
                    ""
                }
                showBanner("تم نشر بلاغك في مدينة حمص بنجاح!$matchMsg", isError = false)
                openReportDetails(created.id)
            }.onFailure { err ->
                showBanner(err.message ?: "تعذر نشر البلاغ.", isError = true)
            }
        }
    }

    fun submitReportUpdate(
        reportId: String,
        title: String,
        categoryId: String,
        description: String,
        neighborhood: String,
        distinguishingMarks: String
    ) {
        if (_uiState.value.isSubmitting) return
        _uiState.update { it.copy(isSubmitting = true) }
        val res = repository.updateReport(
            reportId = reportId,
            title = title,
            categoryId = categoryId,
            description = description,
            neighborhood = neighborhood,
            distinguishingMarks = distinguishingMarks
        )
        _uiState.update { it.copy(isSubmitting = false) }
        res.onSuccess { updated ->
            refreshAllState()
            showBanner("تم حفظ تعديلات البلاغ بنجاح.", isError = false)
            openReportDetails(updated.id)
        }.onFailure { err ->
            showBanner(err.message ?: "تعذر تعديل البلاغ.", isError = true)
        }
    }

    fun closeOrRecoverReport(reportId: String, markRecovered: Boolean, notes: String = "") {
        if (_uiState.value.isSubmitting) return
        _uiState.update { it.copy(isSubmitting = true) }
        val res = repository.closeOrRecoverReport(reportId, markRecovered, notes)
        _uiState.update { it.copy(isSubmitting = false) }
        res.onSuccess {
            refreshAllState()
            val msg = if (markRecovered) {
                "تم تأكيد استعادة الغرض وإغلاق البلاغ وإيقاف المطابقات الجديدة."
            } else {
                "تم إغلاق البلاغ وإيقاف التنبيهات الجديدة له."
            }
            showBanner(msg, isError = false)
        }.onFailure { err ->
            showBanner(err.message ?: "تعذر تحديث حالة البلاغ.", isError = true)
        }
    }

    fun deleteReport(reportId: String) {
        val res = repository.deleteReport(reportId)
        res.onSuccess {
            refreshAllState()
            showBanner("تم حذف البلاغ نهائياً.", isError = false)
            navigateTo(WaynhaScreen.MY_REPORTS, clearHistory = true)
        }.onFailure { err ->
            showBanner(err.message ?: "لا تملك صلاحية حذف هذا البلاغ.", isError = true)
        }
    }

    fun toggleSaveReport(reportId: String) {
        val res = repository.toggleSavedReport(reportId)
        res.onSuccess { saved ->
            refreshAllState()
            showBanner(
                if (saved) "تمت إضافة البلاغ إلى المحفوظات." else "تمت إزالة البلاغ من المحفوظات.",
                isError = false
            )
        }.onFailure { err ->
            showBanner(err.message ?: "يرجى تسجيل الدخول أولاً.", isError = true)
        }
    }

    // -------------------------------------------------------------------------
    // البحث والتصفية
    // -------------------------------------------------------------------------
    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchFilter = it.searchFilter.copy(query = query)) }
        refreshAllState()
    }

    fun updateFilterReportType(type: ReportType?) {
        _uiState.update { it.copy(searchFilter = it.searchFilter.copy(reportType = type)) }
        refreshAllState()
    }

    fun updateFilterCategory(categoryId: String?) {
        _uiState.update { it.copy(searchFilter = it.searchFilter.copy(categoryId = categoryId)) }
        refreshAllState()
    }

    fun updateFilterNeighborhood(neighborhood: String?) {
        _uiState.update { it.copy(searchFilter = it.searchFilter.copy(neighborhood = neighborhood)) }
        refreshAllState()
    }

    fun updateFilterDate(dateOption: DateFilterOption) {
        _uiState.update { it.copy(searchFilter = it.searchFilter.copy(dateFilter = dateOption)) }
        refreshAllState()
    }

    fun clearAllSearchFilters() {
        _uiState.update { it.copy(searchFilter = SearchFilterState()) }
        refreshAllState()
    }

    // -------------------------------------------------------------------------
    // المحادثات الآمنة والإشعارات والحظر
    // -------------------------------------------------------------------------
    fun initiateSafeChatFromReport(reportId: String, matchedReportId: String? = null) {
        if (_uiState.value.isSubmitting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            val res = repository.startSafeConversationForReport(
                targetReportId = reportId,
                matchedReportId = matchedReportId
            )
            _uiState.update { it.copy(isSubmitting = false) }
            res.onSuccess { conv ->
                refreshAllState()
                openConversationRoom(conv.id)
            }.onFailure { err ->
                showBanner(err.message ?: "تعذر بدء المحادثة الآمنة.", isError = true)
            }
        }
    }

    fun refreshActiveConversation() {
        val conversationId = _uiState.value.activeConversationId ?: return
        if (!repository.isLiveSupabaseConnected) {
            refreshAllState()
            return
        }
        viewModelScope.launch {
            repository.refreshLiveMessages(conversationId).onSuccess { messages ->
                _uiState.update { state ->
                    if (state.activeConversationId == conversationId) state.copy(activeConversationMessages = messages) else state
                }
            }.onFailure { showBanner(it.message ?: "تعذر تحديث الرسائل.", true) }
        }
    }

    fun sendChatMessage(body: String) {
        val convId = _uiState.value.activeConversationId ?: return
        if (_uiState.value.isSubmitting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            val res = repository.sendMessage(convId, body)
            _uiState.update { it.copy(isSubmitting = false) }
            res.onSuccess {
                refreshAllState()
            }.onFailure { err ->
                showBanner(err.message ?: "فشل إرسال الرسالة.", isError = true)
            }
        }
    }

    fun blockUserFromConversation(targetUserId: String) {
        val res = repository.blockUser(targetUserId, "حظر من داخل المحادثة")
        res.onSuccess {
            refreshAllState()
            showBanner("تم حظر المستخدم وإغلاق المحادثة لحمايتك.", isError = false)
            navigateTo(WaynhaScreen.CONVERSATIONS, clearHistory = true)
        }.onFailure { err ->
            showBanner(err.message ?: "تعذر حظر المستخدم.", isError = true)
        }
    }

    fun submitAbuseFlag(reportId: String?, conversationId: String?, reason: String, details: String) {
        viewModelScope.launch {
            val res = repository.flagReportOrConversation(reportId, conversationId, reason, details)
            res.onSuccess {
                refreshAllState()
                showBanner("تم إرسال البلاغ إلى فريق الإدارة لمراجعته.", isError = false)
            }.onFailure { err ->
                showBanner(err.message ?: "تعذر إرسال بلاغ المخالفة.", isError = true)
            }
        }
    }

    fun submitRecoveryRequest(reportId: String, answers: List<Pair<String, String>>, message: String) {
        if (_uiState.value.isSubmitting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            val result = repository.submitRecoveryRequest(reportId, answers, message)
            _uiState.update { it.copy(isSubmitting = false) }
            result.onSuccess {
                showBanner("تم إرسال طلب الاستعادة بعد التحقق من الإجابات.", false)
                openReportDetails(reportId)
            }.onFailure { showBanner(it.message ?: "تعذر إرسال طلب الاستعادة.", true) }
        }
    }

    fun reviewRecoveryRequest(reportId: String, requestId: String, accept: Boolean) {
        viewModelScope.launch {
            val result = repository.reviewRecoveryRequest(requestId, accept)
            result.onSuccess {
                showBanner(if (accept) "تم قبول طلب الاستعادة. نسّقوا التسليم داخل المحادثة." else "تم رفض طلب الاستعادة.", false)
                openReportDetails(reportId)
            }.onFailure { showBanner(it.message ?: "تعذر تحديث طلب الاستعادة.", true) }
        }
    }

    fun confirmRecoveryHandover(reportId: String, requestId: String, notes: String) {
        viewModelScope.launch {
            val result = repository.confirmRecoveryHandover(requestId, notes)
            result.onSuccess {
                showBanner("تم تسجيل تأكيدك. لن تكتمل الاستعادة حتى يؤكد الطرف الآخر أيضاً.", false)
                openReportDetails(reportId)
            }.onFailure { showBanner(it.message ?: "تعذر تأكيد التسليم.", true) }
        }
    }

    fun markNotificationsRead() {
        repository.markAllNotificationsRead()
        refreshAllState()
    }

    // -------------------------------------------------------------------------
    // لوحة الإدارة (Admin Panel)
    // -------------------------------------------------------------------------
    fun openAdminDashboard() {
        val user = _uiState.value.currentUser
        if (!WaynhaSecurityValidator.canAccessAdminDashboard(user)) {
            showBanner("رفض الوصول (Server Role Guard): لوحة الإدارة متاحة لمديري النظام فقط.", isError = true)
            return
        }
        navigateTo(WaynhaScreen.ADMIN_DASHBOARD)
    }

    fun adminHideReport(reportId: String, flagId: String?, notes: String) {
        viewModelScope.launch {
            val user = _uiState.value.currentUser
            val res = repository.adminHideFlaggedReport(user, reportId, flagId, notes)
            res.onSuccess {
                refreshAllState()
                showBanner("تم إخفاء المحتوى المخالف وتسجيل العملية في سجل التدقيق الإداري.", isError = false)
            }.onFailure { err ->
                showBanner(err.message ?: "عملية غير مصرح بها.", isError = true)
            }
        }
    }

    fun adminDismissFlag(flagId: String, notes: String = "بعد المراجعة لم تثبت المخالفة") {
        viewModelScope.launch {
            val result = repository.adminDismissReportFlag(_uiState.value.currentUser, flagId, notes)
            result.onSuccess {
                refreshAllState()
                showBanner("تم إغلاق بلاغ المخالفة بعد المراجعة وتوثيق القرار.", false)
            }.onFailure { showBanner(it.message ?: "تعذر حفظ قرار المراجعة.", true) }
        }
    }

    fun adminToggleSuspendUser(targetUserId: String, suspend: Boolean) {
        val user = _uiState.value.currentUser
        val res = repository.adminToggleUserSuspension(
            adminUser = user,
            targetUserId = targetUserId,
            suspend = suspend,
            notes = if (suspend) "إيقاف حساب مخالف من لوحة الإدارة" else "إعادة تفعيل الحساب"
        )
        res.onSuccess {
            refreshAllState()
            showBanner(if (suspend) "تم إيقاف الحساب المخالف." else "تمت إعادة تفعيل الحساب.", isError = false)
        }.onFailure { err ->
            showBanner(err.message ?: "عملية غير مصرح بها.", isError = true)
        }
    }

    fun adminAddNewCategory(id: String, nameAr: String, isSensitive: Boolean) {
        val user = _uiState.value.currentUser
        val res = repository.adminAddCategory(user, id, nameAr, isSensitive)
        res.onSuccess {
            refreshAllState()
            showBanner("تمت إضافة التصنيف «$nameAr» وتوثيقه في سجل التدقيق.", isError = false)
        }.onFailure { err ->
            showBanner(err.message ?: "تعذر إضافة التصنيف.", isError = true)
        }
    }
}
