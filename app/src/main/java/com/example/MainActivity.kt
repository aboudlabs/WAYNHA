package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.model.ReportType
import com.example.ui.components.StatusFeedbackBanner
import com.example.ui.components.WaynhaBottomBar
import com.example.ui.screens.AddOrEditReportScreen
import com.example.ui.screens.AddReportChooserScreen
import com.example.ui.screens.AdminDashboardScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ChatRoomScreen
import com.example.ui.screens.ConversationsListScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MyReportsManagementScreen
import com.example.ui.screens.NotificationsScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.ProfileAndSettingsScreen
import com.example.ui.screens.ReportDetailsScreen
import com.example.ui.screens.SavedReportsScreen
import com.example.ui.screens.SearchAndFilterScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.theme.WaynhaTheme
import com.example.ui.viewmodel.WaynhaScreen
import com.example.ui.viewmodel.WaynhaViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: WaynhaViewModel = viewModel()
            WaynhaRootApp(viewModel = viewModel)
        }
    }
}

@Composable
fun WaynhaRootApp(viewModel: WaynhaViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler(enabled = uiState.currentScreen != WaynhaScreen.HOME) {
        viewModel.navigateBack()
    }

    val hideBottomBar = uiState.currentScreen in setOf(
        WaynhaScreen.SPLASH,
        WaynhaScreen.ONBOARDING,
        WaynhaScreen.CHAT_ROOM
    )

    WaynhaTheme(darkTheme = uiState.isDarkMode) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (!hideBottomBar) {
                    WaynhaBottomBar(
                        currentScreen = uiState.currentScreen,
                        onSelectTab = { target ->
                            viewModel.navigateTo(target, clearHistory = target == WaynhaScreen.HOME)
                        }
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 720.dp)
                ) {
                    if (uiState.isLoading || uiState.isSubmitting) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    StatusFeedbackBanner(
                        message = uiState.bannerMessage,
                        isError = uiState.isBannerError,
                        onDismiss = { viewModel.clearBanner() }
                    )

                    when (uiState.currentScreen) {
                        WaynhaScreen.SPLASH -> {
                            SplashScreen(
                                onContinueToOnboarding = { viewModel.navigateTo(WaynhaScreen.ONBOARDING) },
                                onSkipToHome = { viewModel.navigateTo(WaynhaScreen.HOME, clearHistory = true) }
                            )
                        }

                        WaynhaScreen.ONBOARDING -> {
                            OnboardingScreen(
                                onFinishOnboarding = { viewModel.navigateTo(WaynhaScreen.HOME, clearHistory = true) },
                                onGoToAuth = { viewModel.navigateTo(WaynhaScreen.AUTH) }
                            )
                        }

                        WaynhaScreen.AUTH -> {
                            AuthScreen(
                                uiState = uiState,
                                onLogin = { email, pass -> viewModel.login(email, pass) },
                                onRegister = { name, email, pass, hood ->
                                    viewModel.register(name, email, pass, hood)
                                },
                                onQuickSwitchDemoAccount = { email, pass ->
                                    viewModel.switchDemoAccount(email, pass)
                                },
                                onBackToHome = { viewModel.navigateTo(WaynhaScreen.HOME, clearHistory = true) }
                            )
                        }

                        WaynhaScreen.HOME -> {
                            HomeScreen(
                                uiState = uiState,
                                onOpenSearchWithQuery = { q ->
                                    viewModel.updateSearchQuery(q)
                                    if (q.isNotBlank()) {
                                        viewModel.navigateTo(WaynhaScreen.SEARCH)
                                    }
                                },
                                onSelectCategoryFilter = { catId ->
                                    viewModel.updateFilterCategory(catId.ifBlank { null })
                                    viewModel.navigateTo(WaynhaScreen.SEARCH)
                                },
                                onLostItemClick = { viewModel.openAddReportScreen(ReportType.LOST) },
                                onFoundItemClick = { viewModel.openAddReportScreen(ReportType.FOUND) },
                                onOpenReportDetails = { id -> viewModel.openReportDetails(id) },
                                onToggleSaveReport = { id -> viewModel.toggleSaveReport(id) },
                                onMightBeMineClick = { targetId, matchedId ->
                                    viewModel.initiateSafeChatFromReport(targetId, matchedId)
                                },
                                onOpenNotifications = { viewModel.navigateTo(WaynhaScreen.NOTIFICATIONS) },
                                onOpenConversations = { viewModel.navigateTo(WaynhaScreen.CONVERSATIONS) }
                            )
                        }

                        WaynhaScreen.SEARCH -> {
                            SearchAndFilterScreen(
                                uiState = uiState,
                                onQueryChange = { viewModel.updateSearchQuery(it) },
                                onTypeChange = { viewModel.updateFilterReportType(it) },
                                onCategoryChange = { viewModel.updateFilterCategory(it) },
                                onNeighborhoodChange = { viewModel.updateFilterNeighborhood(it) },
                                onDateFilterChange = { viewModel.updateFilterDate(it) },
                                onClearAllFilters = { viewModel.clearAllSearchFilters() },
                                onOpenReportDetails = { viewModel.openReportDetails(it) },
                                onToggleSaveReport = { viewModel.toggleSaveReport(it) }
                            )
                        }

                        WaynhaScreen.ADD_REPORT_SHEET -> {
                            AddReportChooserScreen(
                                onChooseLost = { viewModel.openAddReportScreen(ReportType.LOST) },
                                onChooseFound = { viewModel.openAddReportScreen(ReportType.FOUND) }
                            )
                        }

                        WaynhaScreen.ADD_LOST_REPORT -> {
                            AddOrEditReportScreen(
                                uiState = uiState,
                                reportType = ReportType.LOST,
                                editingReport = null,
                                onPickImageUri = { uri, blurred -> viewModel.uploadDraftImageFromUri(uri, blurred) },
                                onAttachSampleImage = { label, blurred -> viewModel.attachSampleVerifiedPhoto(label, blurred) },
                                onRemoveDraftImage = { viewModel.removeDraftImage(it) },
                                onSubmitNew = { type, title, cat, desc, hood, date, marks, masked, verificationQuestions ->
                                    viewModel.submitNewReport(type, title, cat, desc, hood, date, marks, masked, verificationQuestions)
                                },
                                onSubmitUpdate = { _, _, _, _, _, _ -> },
                                onBack = { viewModel.navigateBack() }
                            )
                        }

                        WaynhaScreen.ADD_FOUND_REPORT -> {
                            AddOrEditReportScreen(
                                uiState = uiState,
                                reportType = ReportType.FOUND,
                                editingReport = null,
                                onPickImageUri = { uri, blurred -> viewModel.uploadDraftImageFromUri(uri, blurred) },
                                onAttachSampleImage = { label, blurred -> viewModel.attachSampleVerifiedPhoto(label, blurred) },
                                onRemoveDraftImage = { viewModel.removeDraftImage(it) },
                                onSubmitNew = { type, title, cat, desc, hood, date, marks, masked, verificationQuestions ->
                                    viewModel.submitNewReport(type, title, cat, desc, hood, date, marks, masked, verificationQuestions)
                                },
                                onSubmitUpdate = { _, _, _, _, _, _ -> },
                                onBack = { viewModel.navigateBack() }
                            )
                        }

                        WaynhaScreen.EDIT_REPORT -> {
                            AddOrEditReportScreen(
                                uiState = uiState,
                                reportType = uiState.editingReport?.reportType ?: ReportType.LOST,
                                editingReport = uiState.editingReport,
                                onPickImageUri = { uri, blurred -> viewModel.uploadDraftImageFromUri(uri, blurred) },
                                onAttachSampleImage = { label, blurred -> viewModel.attachSampleVerifiedPhoto(label, blurred) },
                                onRemoveDraftImage = { viewModel.removeDraftImage(it) },
                                onSubmitNew = { _, _, _, _, _, _, _, _, _ -> },
                                onSubmitUpdate = { id, title, cat, desc, hood, marks ->
                                    viewModel.submitReportUpdate(id, title, cat, desc, hood, marks)
                                },
                                onBack = { viewModel.navigateBack() }
                            )
                        }

                        WaynhaScreen.REPORT_DETAILS -> {
                            val selectedReport = uiState.allReports.firstOrNull { it.id == uiState.selectedReportId }
                            ReportDetailsScreen(
                                uiState = uiState,
                                report = selectedReport,
                                onBack = { viewModel.navigateBack() },
                                onToggleSave = { viewModel.toggleSaveReport(it) },
                                onEditReport = { viewModel.openEditReportScreen(it) },
                                onConfirmRecovery = { id, notes -> viewModel.closeOrRecoverReport(id, true, notes) },
                                onCloseReport = { id -> viewModel.closeOrRecoverReport(id, false) },
                                onDeleteReport = { id -> viewModel.deleteReport(id) },
                                onStartSafeConversation = { targetId, matchedId ->
                                    viewModel.initiateSafeChatFromReport(targetId, matchedId)
                                },
                                onOpenCounterpartReport = { id -> viewModel.openReportDetails(id) },
                                onSubmitAbuseFlag = { repId, reason, details ->
                                    viewModel.submitAbuseFlag(repId, null, reason, details)
                                },
                                onSubmitRecoveryRequest = { repId, answers, message ->
                                    viewModel.submitRecoveryRequest(repId, answers, message)
                                },
                                onReviewRecoveryRequest = { repId, requestId, accept ->
                                    viewModel.reviewRecoveryRequest(repId, requestId, accept)
                                },
                                onConfirmHandover = { repId, requestId, notes ->
                                    viewModel.confirmRecoveryHandover(repId, requestId, notes)
                                }
                            )
                        }

                        WaynhaScreen.SAVED_REPORTS -> {
                            SavedReportsScreen(
                                uiState = uiState,
                                onOpenReportDetails = { viewModel.openReportDetails(it) },
                                onToggleSaveReport = { viewModel.toggleSaveReport(it) },
                                onBrowseAllClick = { viewModel.navigateTo(WaynhaScreen.SEARCH) }
                            )
                        }

                        WaynhaScreen.MY_REPORTS -> {
                            MyReportsManagementScreen(
                                uiState = uiState,
                                onBack = { viewModel.navigateBack() },
                                onOpenReportDetails = { viewModel.openReportDetails(it) },
                                onEditReport = { viewModel.openEditReportScreen(it) },
                                onConfirmRecovery = { id -> viewModel.closeOrRecoverReport(id, true) },
                                onCloseReport = { id -> viewModel.closeOrRecoverReport(id, false) },
                                onAddLostClick = { viewModel.openAddReportScreen(ReportType.LOST) }
                            )
                        }

                        WaynhaScreen.CONVERSATIONS -> {
                            ConversationsListScreen(
                                uiState = uiState,
                                onBack = { viewModel.navigateBack() },
                                onOpenConversation = { viewModel.openConversationRoom(it) }
                            )
                        }

                        WaynhaScreen.CHAT_ROOM -> {
                            ChatRoomScreen(
                                uiState = uiState,
                                onBack = { viewModel.navigateBack() },
                                onSendMessage = { viewModel.sendChatMessage(it) },
                                onBlockOtherUser = { viewModel.blockUserFromConversation(it) },
                                onFlagConversation = { convId, reason ->
                                    viewModel.submitAbuseFlag(null, convId, reason, "")
                                },
                                onRefreshConversation = { viewModel.refreshActiveConversation() }
                            )
                        }

                        WaynhaScreen.NOTIFICATIONS -> {
                            NotificationsScreen(
                                uiState = uiState,
                                onBack = { viewModel.navigateBack() },
                                onMarkAllRead = { viewModel.markNotificationsRead() },
                                onOpenRelatedReport = { viewModel.openReportDetails(it) },
                                onOpenRelatedConversation = { viewModel.openConversationRoom(it) }
                            )
                        }

                        WaynhaScreen.PROFILE_SETTINGS -> {
                            ProfileAndSettingsScreen(
                                uiState = uiState,
                                onSaveProfile = { name, hood, hideSeen, allowMatch ->
                                    viewModel.updateProfile(name, hood, hideSeen, allowMatch)
                                },
                                onOpenMyReports = { viewModel.navigateTo(WaynhaScreen.MY_REPORTS) },
                                onOpenSavedReports = { viewModel.navigateTo(WaynhaScreen.SAVED_REPORTS) },
                                onOpenConversations = { viewModel.navigateTo(WaynhaScreen.CONVERSATIONS) },
                                onOpenNotifications = { viewModel.navigateTo(WaynhaScreen.NOTIFICATIONS) },
                                onOpenAdminDashboard = { viewModel.openAdminDashboard() },
                                onOpenSplashScreen = { viewModel.navigateTo(WaynhaScreen.SPLASH) },
                                onOpenOnboardingScreen = { viewModel.navigateTo(WaynhaScreen.ONBOARDING) },
                                onToggleDarkMode = { viewModel.toggleDarkMode() },
                                onToggleOfflineSim = { viewModel.toggleOfflineSimulation(it) },
                                onToggleUploadFailureSim = { viewModel.toggleImageUploadFailureSimulation(it) },
                                onRequestDeletion = { viewModel.requestAccountDeletion() },
                                onLogout = { viewModel.logout() },
                                onGoToLogin = { viewModel.navigateTo(WaynhaScreen.AUTH) }
                            )
                        }

                        WaynhaScreen.ADMIN_DASHBOARD -> {
                            AdminDashboardScreen(
                                uiState = uiState,
                                onBack = { viewModel.navigateBack() },
                                onHideFlaggedReport = { repId, flagId, notes ->
                                    viewModel.adminHideReport(repId, flagId, notes)
                                },
                                onToggleSuspendUser = { uId, suspend ->
                                    viewModel.adminToggleSuspendUser(uId, suspend)
                                },
                                onAddCategory = { id, nameAr, isSens ->
                                    viewModel.adminAddNewCategory(id, nameAr, isSens)
                                },
                                onOpenReportDetails = { viewModel.openReportDetails(it) },
                                onDismissFlag = { viewModel.adminDismissFlag(it) }
                            )
                        }
                    }
                }
            }
        }
    }
}
