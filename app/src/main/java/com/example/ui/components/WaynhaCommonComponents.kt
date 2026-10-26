package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.model.ItemReport
import com.example.core.model.ReportMatch
import com.example.core.model.ReportStatus
import com.example.core.model.ReportType
import com.example.ui.theme.FoundReportEmerald
import com.example.ui.theme.FoundReportEmeraldContainer
import com.example.ui.theme.LostReportAmber
import com.example.ui.theme.LostReportAmberContainer
import com.example.ui.viewmodel.WaynhaScreen

@Composable
fun HomsScopeBanner(
    isLiveSupabase: Boolean,
    isOfflineSimulated: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = "نطاق مدينة حمص",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "النطاق الجغرافي الثابت: حمص، سوريا فقط",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            // Connection/provider status is intentionally hidden from end users.
        }
    }
}

@Composable
fun StatusFeedbackBanner(
    message: String?,
    isError: Boolean,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(visible = !message.isNullOrBlank()) {
        if (message != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("status_feedback_banner"),
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                shape = RoundedCornerShape(14.dp),
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isError) Icons.Default.ErrorOutline else Icons.Default.CheckCircle,
                            contentDescription = "حالة العملية",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "إغلاق الرسالة",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ReportItemCard(
    report: ItemReport,
    isSaved: Boolean,
    onCardClick: () -> Unit,
    onToggleSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLost = report.reportType == ReportType.LOST
    val badgeBg = if (isLost) LostReportAmberContainer else FoundReportEmeraldContainer
    val badgeColor = if (isLost) LostReportAmber else FoundReportEmerald

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onCardClick)
            .testTag("report_card_${report.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = badgeBg,
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = report.reportType.labelAr,
                            color = badgeColor,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = report.category.nameAr,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    if (report.status != ReportStatus.OPEN) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = report.status.labelAr,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onToggleSave,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("save_btn_${report.id}")
                ) {
                    Icon(
                        imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = if (isSaved) "إزالة من المحفوظات" else "حفظ البلاغ",
                        tint = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = report.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = report.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "الحي داخل حمص",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${report.city} • حي ${report.neighborhood}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = report.createdAtText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ProbabilisticMatchCard(
    match: ReportMatch,
    counterpartReport: ItemReport,
    onOpenReport: () -> Unit,
    onMightBeMineClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.5.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                shape = RoundedCornerShape(18.dp)
            )
            .testTag("match_card_${match.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = "اقتراح مطابقة",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "قد يكون هذا الغرض هو ما تبحث عنه",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = "تشابه ${match.similarityScore}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = counterpartReport.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "حمص - حي ${counterpartReport.neighborhood} • ${counterpartReport.category.nameAr}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "أسباب الاقتراح: ${match.matchReasons.joinToString(" • ")}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "تنبيه: المطابقة اقتراح احتمالي بناءً على القواعد وليست تأكيداً للملكية.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onMightBeMineClick,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_might_be_mine_${match.id}"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("قد يكون هذا غرضي")
                }

                OutlinedButton(
                    onClick = onOpenReport,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("عرض التفاصيل")
                }
            }
        }
    }
}

@Composable
fun EmptyOrErrorStateBox(
    title: String,
    subtitle: String,
    isError: Boolean = false,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .testTag("empty_or_error_state_box"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        if (isError) MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isError) Icons.Default.WifiOff else Icons.Default.Search,
                    contentDescription = title,
                    tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (actionLabel != null && onActionClick != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = onActionClick,
                    modifier = Modifier.testTag("empty_state_action_button")
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
fun WaynhaBottomBar(
    currentScreen: WaynhaScreen,
    onSelectTab: (WaynhaScreen) -> Unit
) {
    NavigationBar(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .testTag("waynha_bottom_nav"),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    ) {
        NavigationBarItem(
            selected = currentScreen == WaynhaScreen.HOME,
            onClick = { onSelectTab(WaynhaScreen.HOME) },
            icon = {
                Icon(
                    imageVector = if (currentScreen == WaynhaScreen.HOME) Icons.Filled.Home else Icons.Outlined.Home,
                    contentDescription = "الرئيسية"
                )
            },
            label = { Text("الرئيسية") },
            modifier = Modifier.testTag("nav_tab_home")
        )

        NavigationBarItem(
            selected = currentScreen == WaynhaScreen.SEARCH,
            onClick = { onSelectTab(WaynhaScreen.SEARCH) },
            icon = {
                Icon(
                    imageVector = if (currentScreen == WaynhaScreen.SEARCH) Icons.Filled.Search else Icons.Outlined.Search,
                    contentDescription = "البحث"
                )
            },
            label = { Text("البحث") },
            modifier = Modifier.testTag("nav_tab_search")
        )

        NavigationBarItem(
            selected = currentScreen == WaynhaScreen.ADD_REPORT_SHEET ||
                currentScreen == WaynhaScreen.ADD_LOST_REPORT ||
                currentScreen == WaynhaScreen.ADD_FOUND_REPORT,
            onClick = { onSelectTab(WaynhaScreen.ADD_REPORT_SHEET) },
            icon = {
                Icon(
                    imageVector = Icons.Filled.AddCircle,
                    contentDescription = "إضافة بلاغ",
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            label = { Text("إضافة بلاغ") },
            modifier = Modifier.testTag("nav_tab_add")
        )

        NavigationBarItem(
            selected = currentScreen == WaynhaScreen.SAVED_REPORTS,
            onClick = { onSelectTab(WaynhaScreen.SAVED_REPORTS) },
            icon = {
                Icon(
                    imageVector = if (currentScreen == WaynhaScreen.SAVED_REPORTS) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = "المحفوظات"
                )
            },
            label = { Text("المحفوظات") },
            modifier = Modifier.testTag("nav_tab_saved")
        )

        NavigationBarItem(
            selected = currentScreen == WaynhaScreen.PROFILE_SETTINGS ||
                currentScreen == WaynhaScreen.MY_REPORTS ||
                currentScreen == WaynhaScreen.ADMIN_DASHBOARD,
            onClick = { onSelectTab(WaynhaScreen.PROFILE_SETTINGS) },
            icon = {
                Icon(
                    imageVector = if (currentScreen == WaynhaScreen.PROFILE_SETTINGS) Icons.Filled.Person else Icons.Outlined.Person,
                    contentDescription = "حسابي"
                )
            },
            label = { Text("حسابي") },
            modifier = Modifier.testTag("nav_tab_profile")
        )
    }
}
