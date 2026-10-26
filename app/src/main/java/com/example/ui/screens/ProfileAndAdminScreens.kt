package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.model.ReportStatus
import com.example.ui.viewmodel.WaynhaUiState

/**
 * 12. شاشة الملف الشخصي والإعدادات
 */
@Composable
fun ProfileAndSettingsScreen(
    uiState: WaynhaUiState,
    onSaveProfile: (fullName: String, neighborhood: String?, hideLastSeen: Boolean, allowMatchNotifs: Boolean) -> Unit,
    onOpenMyReports: () -> Unit,
    onOpenSavedReports: () -> Unit,
    onOpenConversations: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenAdminDashboard: () -> Unit,
    onOpenSplashScreen: () -> Unit,
    onOpenOnboardingScreen: () -> Unit,
    onToggleDarkMode: () -> Unit,
    onToggleOfflineSim: (Boolean) -> Unit,
    onToggleUploadFailureSim: (Boolean) -> Unit,
    onRequestDeletion: () -> Unit,
    onLogout: () -> Unit,
    onGoToLogin: () -> Unit
) {
    val user = uiState.currentUser

    if (user == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .testTag("profile_unauthenticated_screen"),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("أنت تتصفح حالياً كزائر", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "سجّل الدخول لإدارة بلاغاتك ومحفوظاتك ومحادثاتك الآمنة في مدينة حمص.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onGoToLogin,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("profile_btn_go_login")
            ) {
                Text("تسجيل الدخول أو إنشاء حساب")
            }
        }
        return
    }

    var fullName by remember(user.id, user.fullName) { mutableStateOf(user.fullName) }
    var neighborhood by remember(user.id, user.neighborhood) { mutableStateOf(user.neighborhood ?: "الإنشاءات") }
    var hideLastSeen by remember(user.id, user.privacyHideLastSeen) { mutableStateOf(user.privacyHideLastSeen) }
    var allowMatchNotifs by remember(user.id, user.allowMatchNotifications) { mutableStateOf(user.allowMatchNotifications) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("profile_settings_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. بطاقة معلومات المستخدم والمدينة الثابتة (حمص)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "صورة الحساب",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = user.fullName,
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            text = "${user.email} • الدور: ${user.role.labelAr}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "المدينة الثابتة: حمص، سوريا",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text("الاسم الكامل") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_input_name")
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = neighborhood,
                    onValueChange = { neighborhood = it },
                    label = { Text("الحي داخل حمص (اختياري)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_input_neighborhood")
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { onSaveProfile(fullName, neighborhood, hideLastSeen, allowMatchNotifs) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_btn_save")
                ) {
                    Text("حفظ بيانات الملف الشخصي")
                }
            }
        }

        // 2. روابط الوصول السريع (بلاغاتي، المحفوظات، المحادثات، الإشعارات، لوحة الإدارة)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "بلاغاتي ونشاطي في حمص",
                    style = MaterialTheme.typography.titleLarge
                )

                OutlinedButton(
                    onClick = onOpenMyReports,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_btn_my_reports")
                ) {
                    Icon(Icons.Default.ListAlt, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("بلاغاتي وإدارتها (${uiState.userReports.size})")
                }

                OutlinedButton(
                    onClick = onOpenSavedReports,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_btn_saved_reports")
                ) {
                    Icon(Icons.Default.Bookmark, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("بلاغاتي المحفوظة (${uiState.savedReports.size})")
                }

                OutlinedButton(
                    onClick = onOpenConversations,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_btn_conversations")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("المحادثات الآمنة (${uiState.conversations.size})")
                }

                OutlinedButton(
                    onClick = onOpenNotifications,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_btn_notifications")
                ) {
                    Icon(Icons.Default.Notifications, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("الإشعارات (${uiState.notifications.size})")
                }

                // زر لوحة الإدارة (يتحقق من الصلاحية على الخادم/المستودع)
                Button(
                    onClick = onOpenAdminDashboard,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_btn_admin_dashboard"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Icon(Icons.Default.AdminPanelSettings, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("لوحة الإدارة والمراجعة (للمديرين فقط)")
                }
            }
        }

        // 3. إعدادات الخصوصية والمظهر (الوضع الفاتح والداكن)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "إعدادات الخصوصية والمظهر",
                    style = MaterialTheme.typography.titleLarge
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DarkMode, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("الوضع الداكن (Dark Mode)", style = MaterialTheme.typography.bodyLarge)
                    }
                    Switch(
                        checked = uiState.isDarkMode,
                        onCheckedChange = { onToggleDarkMode() },
                        modifier = Modifier.testTag("switch_dark_mode")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("تنبيهات البلاغات المتشابهة في حمص", style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = allowMatchNotifs,
                        onCheckedChange = {
                            allowMatchNotifs = it
                            onSaveProfile(fullName, neighborhood, hideLastSeen, it)
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("إخفاء حالة آخر ظهور في المحادثات", style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = hideLastSeen,
                        onCheckedChange = {
                            hideLastSeen = it
                            onSaveProfile(fullName, neighborhood, it, allowMatchNotifs)
                        }
                    )
                }
            }
        }

        // 4. معاينة شاشات البداية والتعريف واختبار حالات انقطاع الشبكة
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "أدوات فحص الشاشات وحالات الشبكة",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onOpenSplashScreen,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_preview_splash")
                    ) {
                        Text("شاشة البداية")
                    }
                    OutlinedButton(
                        onClick = onOpenOnboardingScreen,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_preview_onboarding")
                    ) {
                        Text("شاشة التعريف")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("اختبار حالة عدم الاتصال (Offline)", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = uiState.isOfflineSimulated,
                        onCheckedChange = onToggleOfflineSim,
                        modifier = Modifier.testTag("switch_offline_sim")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("اختبار حالة فشل رفع الصور", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = uiState.isImageUploadFailureSimulated,
                        onCheckedChange = onToggleUploadFailureSim,
                        modifier = Modifier.testTag("switch_upload_fail_sim")
                    )
                }
            }
        }

        // 5. تسجيل الخروج وطلب حذف الحساب والبيانات
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (user.deletionRequestedAt != null) {
                    Text(
                        text = "تم تسجيل طلب حذف حسابك بتاريخ: ${user.deletionRequestedAt}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    OutlinedButton(
                        onClick = onRequestDeletion,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_btn_request_deletion"),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("طلب حذف الحساب والبيانات نهائياً")
                    }
                }

                Button(
                    onClick = onLogout,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_btn_logout"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("تسجيل الخروج")
                }
            }
        }
    }
}

/**
 * لوحة الإدارة الآمنة (محمية بصلاحيات الدور على الخادم + سجلات تدقيق)
 */
@Composable
fun AdminDashboardScreen(
    uiState: WaynhaUiState,
    onBack: () -> Unit,
    onHideFlaggedReport: (reportId: String, flagId: String?, notes: String) -> Unit,
    onToggleSuspendUser: (userId: String, suspend: Boolean) -> Unit,
    onAddCategory: (id: String, nameAr: String, isSensitive: Boolean) -> Unit,
    onOpenReportDetails: (String) -> Unit,
    onDismissFlag: (flagId: String) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var adminSearchQuery by remember { mutableStateOf("") }
    var newCategoryId by remember { mutableStateOf("") }
    var newCategoryName by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("admin_dashboard_screen")
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                    Column {
                        Text(
                            text = "لوحة الإدارة والتدقيق • وينها (حمص)",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "تم التحقق من صلاحية المدير (${uiState.currentUser?.fullName})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("الإحصائيات والبلاغات") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("المخالفات (${uiState.adminFlags.size})") }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("الحسابات والتصنيفات") }
                    )
                    Tab(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        text = { Text("سجل التدقيق") }
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (selectedTab) {
                0 -> {
                    item {
                        val openCount = uiState.allReports.count { it.status == ReportStatus.OPEN }
                        val recoveredCount = uiState.allReports.count { it.status == ReportStatus.RECOVERED }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            AdminStatCard("إجمالي البلاغات", uiState.allReports.size.toString(), Modifier.weight(1f))
                            AdminStatCard("المفتوحة", openCount.toString(), Modifier.weight(1f))
                            AdminStatCard("المستردة", recoveredCount.toString(), Modifier.weight(1f))
                            AdminStatCard("المستخدمون", uiState.adminProfiles.size.toString(), Modifier.weight(1f))
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = adminSearchQuery,
                            onValueChange = { adminSearchQuery = it },
                            label = { Text("البحث عن بلاغ محدد بالاسم أو المعرّف") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_search_report_input")
                        )
                    }

                    val matchingReports = uiState.allReports.filter {
                        adminSearchQuery.isBlank() ||
                            it.title.contains(adminSearchQuery, ignoreCase = true) ||
                            it.id.contains(adminSearchQuery, ignoreCase = true)
                    }

                    items(matchingReports, key = { it.id }) { rep ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(rep.title, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "${rep.id} • ${rep.neighborhood} • الحالة: ${rep.status.labelAr}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedButton(onClick = { onOpenReportDetails(rep.id) }) {
                                        Text("عرض")
                                    }
                                    if (rep.status != ReportStatus.HIDDEN) {
                                        IconButton(
                                            onClick = {
                                                onHideFlaggedReport(rep.id, null, "إخفاء مباشر من لوحة الإدارة")
                                            },
                                            modifier = Modifier.testTag("admin_hide_report_${rep.id}")
                                        ) {
                                            Icon(
                                                Icons.Default.VisibilityOff,
                                                contentDescription = "إخفاء البلاغ المخالف",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    item {
                        Text(
                            text = "مراجعة البلاغات المُبلّغ عنها من المستخدمين",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    items(uiState.adminFlags, key = { it.id }) { flag ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "بلاغ على: ${flag.reportTitle}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "المبلِّغ: ${flag.reporterName} • الحالة: ${flag.status}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("السبب: ${flag.reason}", style = MaterialTheme.typography.bodyMedium)
                                if (flag.details.isNotBlank()) {
                                    Text("التفاصيل: ${flag.details}", style = MaterialTheme.typography.bodySmall)
                                }
                                if (flag.status == "pending") {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    if (flag.reportId != null) {
                                        Button(
                                            onClick = {
                                                onHideFlaggedReport(
                                                    flag.reportId,
                                                    flag.id,
                                                    "تمت المراجعة وإخفاء المحتوى المخالف"
                                                )
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                            modifier = Modifier.fillMaxWidth().testTag("admin_action_hide_flag_${flag.id}")
                                        ) { Text("إخفاء المحتوى المخالف وتوثيق الإجراء") }
                                    }
                                    OutlinedButton(
                                        onClick = { onDismissFlag(flag.id) },
                                        modifier = Modifier.fillMaxWidth().testTag("admin_action_dismiss_flag_${flag.id}")
                                    ) { Text("إغلاق البلاغ بعد المراجعة دون إجراء") }
                                }
                            }
                        }
                    }
                }

                2 -> {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "إدارة التصنيفات (${uiState.categories.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = newCategoryId,
                                    onValueChange = { newCategoryId = it },
                                    label = { Text("معرّف التصنيف بالإنجليزية (مثال: glasses)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = newCategoryName,
                                    onValueChange = { newCategoryName = it },
                                    label = { Text("اسم التصنيف بالعربية (مثال: نظارات وساعات)") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("admin_new_category_name")
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        onAddCategory(newCategoryId, newCategoryName, false)
                                        newCategoryId = ""
                                        newCategoryName = ""
                                    },
                                    modifier = Modifier.testTag("admin_btn_add_category")
                                ) {
                                    Text("إضافة تصنيف جديد")
                                }
                            }
                        }
                    }

                    item {
                        HorizontalDivider()
                        Text(
                            text = "مراجعة الحسابات في مدينة حمص",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }

                    items(uiState.adminProfiles, key = { it.id }) { prof ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(prof.fullName, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "${prof.email} • الدور: ${prof.role.labelAr} • ${if (prof.isSuspended) "موقوف" else "نشط"}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                if (!prof.role.canAccessAdminPanel) {
                                    OutlinedButton(
                                        onClick = { onToggleSuspendUser(prof.id, !prof.isSuspended) },
                                        modifier = Modifier.testTag("admin_toggle_suspend_${prof.id}")
                                    ) {
                                        Text(if (prof.isSuspended) "إلغاء الإيقاف" else "إيقاف الحساب")
                                    }
                                }
                            }
                        }
                    }
                }

                3 -> {
                    item {
                        Text(
                            text = "سجلات التدقيق للعمليات الإدارية الحساسة (${uiState.adminAuditLogs.size})",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    if (uiState.adminAuditLogs.isEmpty()) {
                        item {
                            Text(
                                text = "لم يتم تسجيل عمليات إدارية حساسة في هذه الجلسة بعد.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(uiState.adminAuditLogs, key = { it.id }) { log ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = log.actionType,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = log.createdAtText,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                    Text(
                                        text = "الجدول: ${log.targetTable} • الهدف: ${log.targetId}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        text = "بواسطة: ${log.adminName} — ${log.notes}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminStatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}
