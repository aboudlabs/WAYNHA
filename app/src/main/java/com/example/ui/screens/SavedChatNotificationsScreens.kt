package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.core.model.ItemReport
import com.example.ui.components.EmptyOrErrorStateBox
import com.example.ui.components.ReportItemCard
import com.example.ui.theme.FoundReportEmerald
import com.example.ui.viewmodel.WaynhaUiState

/**
 * 9. شاشة البلاغات المحفوظة
 */
@Composable
fun SavedReportsScreen(
    uiState: WaynhaUiState,
    onOpenReportDetails: (String) -> Unit,
    onToggleSaveReport: (String) -> Unit,
    onBrowseAllClick: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("saved_reports_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "البلاغات المحفوظة (${uiState.savedReports.size})",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "تُحفظ البلاغات في حسابك وتبقى متاحة بعد إعادة فتح التطبيق لمتابعتها بسهولة.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (uiState.savedReports.isEmpty()) {
            item {
                EmptyOrErrorStateBox(
                    title = "قائمة المحفوظات فارغة حالياً",
                    subtitle = "احفظ أي بلاغ مفقود أو موجود في حمص للرجوع إليه بسرعة من هنا.",
                    actionLabel = "تصفح بلاغات حمص",
                    onActionClick = onBrowseAllClick
                )
            }
        } else {
            items(uiState.savedReports, key = { it.id }) { report ->
                ReportItemCard(
                    report = report,
                    isSaved = true,
                    onCardClick = { onOpenReportDetails(report.id) },
                    onToggleSave = { onToggleSaveReport(report.id) }
                )
            }
        }
    }
}

/**
 * 14. شاشة إدارة البلاغات الخاصة بالمستخدم
 */
@Composable
fun MyReportsManagementScreen(
    uiState: WaynhaUiState,
    onBack: () -> Unit,
    onOpenReportDetails: (String) -> Unit,
    onEditReport: (ItemReport) -> Unit,
    onConfirmRecovery: (String) -> Unit,
    onCloseReport: (String) -> Unit,
    onAddLostClick: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("my_reports_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                }
                Column {
                    Text(
                        text = "إدارة بلاغاتي في حمص (${uiState.userReports.size})",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = "عدّل بلاغاتك أو أغلقها بعد استعادة الغرض لإيقاف المطابقات الجديدة",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (uiState.userReports.isEmpty()) {
            item {
                EmptyOrErrorStateBox(
                    title = "لم تقم بنشر أي بلاغ بعد",
                    subtitle = "انشر بلاغاً عن غرض مفقود أو موجود في مدينة حمص ليبدأ نظام المطابقة بالعمل.",
                    actionLabel = "نشر بلاغ جديد",
                    onActionClick = onAddLostClick
                )
            }
        } else {
            items(uiState.userReports, key = { it.id }) { report ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("my_report_item_${report.id}"),
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
                            Text(
                                text = report.title,
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(50)
                            ) {
                                Text(
                                    text = report.status.labelAr,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${report.reportType.labelAr} • حي ${report.neighborhood} • ${report.category.nameAr}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onOpenReportDetails(report.id) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("التفاصيل")
                            }
                            OutlinedButton(
                                onClick = { onEditReport(report) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("my_reports_edit_${report.id}")
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("تعديل")
                            }
                            if (report.status.isEligibleForMatching) {
                                Button(
                                    onClick = { onConfirmRecovery(report.id) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("my_reports_recover_${report.id}"),
                                    colors = ButtonDefaults.buttonColors(containerColor = FoundReportEmerald)
                                ) {
                                    Text("تمت الاستعادة")
                                }
                            }
                        }
                        if (report.status.isEligibleForMatching) {
                            TextButton(
                                onClick = { onCloseReport(report.id) },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("إغلاق البلاغ وإيقاف التنبيهات")
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 10. شاشة المحادثات الآمنة المرتبطة ببلاغات محددة
 */
@Composable
fun ConversationsListScreen(
    uiState: WaynhaUiState,
    onBack: () -> Unit,
    onOpenConversation: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("conversations_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                }
                Column {
                    Text(
                        text = "المحادثات الآمنة (${uiState.conversations.size})",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = "مرتبطة ببلاغات محددة • لا تُعرض أرقام الهواتف تلقائياً",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (uiState.conversations.isEmpty()) {
            item {
                EmptyOrErrorStateBox(
                    title = "لا توجد محادثات نشطة حالياً",
                    subtitle = "عند الضغط على زر «قد يكون هذا غرضي» داخل أي بلاغ في حمص، ستظهر المحادثة الآمنة هنا."
                )
            }
        } else {
            items(uiState.conversations, key = { it.id }) { conv ->
                val currentUserId = uiState.currentUser?.id
                val otherPartyName = if (conv.initiatorId == currentUserId) conv.recipientName else conv.initiatorName
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenConversation(conv.id) }
                        .testTag("conversation_item_${conv.id}"),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = otherPartyName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = conv.updatedAtText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "حول بلاغ: ${conv.reportTitle}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = conv.lastMessagePreview,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * غرفة المحادثة الآمنة (Realtime / RLS Protected)
 */
@Composable
fun ChatRoomScreen(
    uiState: WaynhaUiState,
    onBack: () -> Unit,
    onSendMessage: (String) -> Unit,
    onBlockOtherUser: (String) -> Unit,
    onFlagConversation: (String, String) -> Unit,
    onRefreshConversation: () -> Unit
) {
    val conv = uiState.conversations.firstOrNull { it.id == uiState.activeConversationId }
    var messageInput by remember { mutableStateOf("") }

    if (conv == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("المحادثة غير متاحة أو تم حظرها.", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onBack) { Text("رجوع") }
        }
        return
    }

    val myId = uiState.currentUser?.id.orEmpty()
    val otherUserId = if (conv.initiatorId == myId) conv.recipientId else conv.initiatorId
    val otherUserName = if (conv.initiatorId == myId) conv.recipientName else conv.initiatorName

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("chat_room_screen")
    ) {
        // رأس المحادثة مع أزرار الحظر والإبلاغ
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                        }
                        Column {
                            Text(
                                text = otherUserName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "بلاغ: ${conv.reportTitle}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Row {
                        IconButton(
                            onClick = onRefreshConversation,
                            modifier = Modifier.size(48.dp).testTag("chat_btn_refresh")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "تحديث الرسائل")
                        }
                        IconButton(
                            onClick = {
                                onFlagConversation(conv.id, "إبلاغ عن إساءة في المحادثة")
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("chat_btn_flag")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Flag,
                                contentDescription = "الإبلاغ عن إساءة",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = { onBlockOtherUser(otherUserId) },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("chat_btn_block")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Block,
                                contentDescription = "حظر المستخدم",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "محادثة محمية بـ RLS: لا تشارك معلومات حساسة، وسلّم الغرض في مكان عام معروف بحمص.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        // قائمة الرسائل
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(uiState.activeConversationMessages, key = { it.id }) { msg ->
                val isMine = msg.senderId == myId
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isMine) Arrangement.Start else Arrangement.End
                ) {
                    Surface(
                        color = if (isMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(
                            topStart = 18.dp,
                            topEnd = 18.dp,
                            bottomStart = if (isMine) 4.dp else 18.dp,
                            bottomEnd = if (isMine) 18.dp else 4.dp
                        ),
                        modifier = Modifier.widthIn(max = 300.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = msg.body,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isMine) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = msg.createdAtText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isMine) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (isMine) {
                                    Icon(
                                        imageVector = Icons.Default.DoneAll,
                                        contentDescription = if (msg.isRead) "مقروءة" else "مرسلة",
                                        tint = if (msg.isRead) Color(0xFF5EEAD4) else Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // شريط كتابة وإرسال رسالة
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = messageInput,
                    onValueChange = { messageInput = it },
                    placeholder = { Text("اكتب رسالتك بأمان...") },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_message_input")
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (messageInput.isNotBlank()) {
                            val toSend = messageInput
                            messageInput = ""
                            onSendMessage(toSend)
                        }
                    },
                    enabled = !uiState.isSubmitting && messageInput.isNotBlank(),
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .testTag("chat_send_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "إرسال الرسالة",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

/**
 * 11. شاشة الإشعارات
 */
@Composable
fun NotificationsScreen(
    uiState: WaynhaUiState,
    onBack: () -> Unit,
    onMarkAllRead: () -> Unit,
    onOpenRelatedReport: (String) -> Unit,
    onOpenRelatedConversation: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("notifications_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                    Text(
                        text = "الإشعارات (${uiState.notifications.size})",
                        style = MaterialTheme.typography.headlineMedium
                    )
                }
                if (uiState.unreadNotificationsCount > 0) {
                    TextButton(
                        onClick = onMarkAllRead,
                        modifier = Modifier.testTag("btn_mark_all_notifications_read")
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("تحديد الكل كمقروء")
                    }
                }
            }
        }

        if (uiState.notifications.isEmpty()) {
            item {
                EmptyOrErrorStateBox(
                    title = "لا توجد إشعارات حالياً",
                    subtitle = "ستصلك هنا تنبيهات فورية عند ظهور بلاغ مشابه في حمص أو وصول رسالة جديدة أو تحديث حالة الاستعادة."
                )
            }
        } else {
            items(uiState.notifications, key = { it.id }) { notif ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            when {
                                notif.relatedConversationId != null -> onOpenRelatedConversation(notif.relatedConversationId)
                                notif.relatedReportId != null -> onOpenRelatedReport(notif.relatedReportId)
                            }
                        }
                        .testTag("notification_item_${notif.id}"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (!notif.isRead) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = notif.type.labelAr,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = notif.type.labelAr,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = notif.createdAtText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = notif.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = notif.body,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
