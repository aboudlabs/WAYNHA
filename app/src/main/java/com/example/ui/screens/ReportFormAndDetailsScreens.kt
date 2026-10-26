package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.domain.WaynhaSecurityValidator
import com.example.core.model.ItemReport
import com.example.core.model.ReportStatus
import com.example.core.model.ReportType
import com.example.core.model.SystemLocationSettings
import com.example.ui.components.ProbabilisticMatchCard
import com.example.ui.theme.FoundReportEmerald
import com.example.ui.theme.FoundReportEmeraldContainer
import com.example.ui.theme.LostReportAmber
import com.example.ui.theme.LostReportAmberContainer
import com.example.ui.viewmodel.WaynhaUiState

/**
 * 5 & 6. شاشة نشر بلاغ مفقود / شاشة نشر بلاغ موجود / تعديل بلاغ المستخدم
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddOrEditReportScreen(
    uiState: WaynhaUiState,
    reportType: ReportType,
    editingReport: ItemReport? = null,
    onPickImageUri: (Uri?, Boolean) -> Unit,
    onAttachSampleImage: (String, Boolean) -> Unit,
    onRemoveDraftImage: (String) -> Unit,
    onSubmitNew: (
        type: ReportType,
        title: String,
        categoryId: String,
        description: String,
        neighborhood: String,
        incidentDateText: String,
        distinguishingMarks: String,
        isSensitiveMasked: Boolean
    ) -> Unit,
    onSubmitUpdate: (
        reportId: String,
        title: String,
        categoryId: String,
        description: String,
        neighborhood: String,
        distinguishingMarks: String
    ) -> Unit,
    onBack: () -> Unit
) {
    val isEditing = editingReport != null
    val effectiveType = editingReport?.reportType ?: reportType

    var title by remember(editingReport?.id) { mutableStateOf(editingReport?.title ?: "") }
    var selectedCategoryId by remember(editingReport?.id) {
        mutableStateOf(editingReport?.categoryId ?: "wallets")
    }
    var description by remember(editingReport?.id) {
        mutableStateOf(editingReport?.description ?: "")
    }
    var selectedNeighborhood by remember(editingReport?.id) {
        mutableStateOf(editingReport?.neighborhood ?: "الإنشاءات")
    }
    var incidentDateText by remember(editingReport?.id) {
        mutableStateOf(editingReport?.incidentDateText ?: "2026-10-26")
    }
    var distinguishingMarks by remember(editingReport?.id) {
        mutableStateOf(editingReport?.distinguishingMarks ?: "")
    }
    var isSensitiveMasked by remember(editingReport?.id) {
        mutableStateOf(editingReport?.isSensitiveMasked ?: true)
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onPickImageUri(uri, isSensitiveMasked)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("report_form_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
            }
            Column {
                Text(
                    text = when {
                        isEditing -> "تعديل بلاغك في حمص"
                        effectiveType == ReportType.LOST -> "نشر بلاغ مفقود في حمص"
                        else -> "نشر بلاغ موجود في حمص"
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "المدينة الافتراضية والثابتة: حمص، سوريا",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // تنبيه الخصوصية والأمان
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            )
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = "حماية الخصوصية",
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "لأمانك: لا تكتب رقم هاتفك أو عنوانك الدقيق أو رقم وثيقة حساسة داخل الوصف. سيتم التواصل عبر المحادثة الآمنة في التطبيق.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        // 1. المدينة الثابتة (حمص)
        OutlinedTextField(
            value = "حمص، سوريا (نطاق الإصدار الأول حصراً)",
            onValueChange = {},
            enabled = false,
            label = { Text("المدينة") },
            leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
            modifier = Modifier.fillMaxWidth()
        )

        // 2. عنوان الغرض
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("عنوان الغرض (مثال: محفظة جلدية بنية / مفتاح سيارة)") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("form_input_title")
        )

        // 3. التصنيف
        Text("التصنيف:", style = MaterialTheme.typography.titleMedium)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            uiState.categories.forEach { cat ->
                FilterChip(
                    selected = selectedCategoryId == cat.id,
                    onClick = { selectedCategoryId = cat.id },
                    label = { Text(cat.nameAr) },
                    modifier = Modifier.testTag("form_category_${cat.id}")
                )
            }
        }

        if (selectedCategoryId == "documents") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = LostReportAmberContainer)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isSensitiveMasked,
                        onCheckedChange = { isSensitiveMasked = it },
                        modifier = Modifier.testTag("checkbox_sensitive_masked")
                    )
                    Text(
                        text = "أؤكد عدم نشر صورة مكشوفة لرقم وطني أو وثيقة حساسة علناً (الاكتفاء بالاسم الأول ونوع الوثيقة).",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF78350F)
                    )
                }
            }
        }

        // 4. المنطقة أو الحي داخل حمص
        Text("المنطقة أو الحي داخل حمص (اختياري بدون موقع دقيق):", style = MaterialTheme.typography.titleMedium)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            uiState.homsNeighborhoods.take(12).forEach { hood ->
                FilterChip(
                    selected = selectedNeighborhood == hood,
                    onClick = { selectedNeighborhood = hood },
                    label = { Text(hood) },
                    modifier = Modifier.testTag("form_hood_$hood")
                )
            }
        }

        OutlinedTextField(
            value = selectedNeighborhood,
            onValueChange = { selectedNeighborhood = it },
            label = { Text("الحي أو المنطقة داخل حمص") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("form_input_neighborhood")
        )

        // 5. تاريخ ووقت الفقد أو العثور
        OutlinedTextField(
            value = incidentDateText,
            onValueChange = { incidentDateText = it },
            label = { Text("تاريخ ووقت الفقد أو العثور (YYYY-MM-DD)") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("form_input_date")
        )

        // 6. الوصف
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("الوصف التفصيلي (دون كتابة رقم هاتف أو إحداثيات)") },
            minLines = 3,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("form_input_description")
        )

        // 7. العلامات المميزة
        OutlinedTextField(
            value = distinguishingMarks,
            onValueChange = { distinguishingMarks = it },
            label = { Text("العلامات المميزة (تُستخدم في خوارزمية المطابقة والتحقق)") },
            singleLine = false,
            minLines = 2,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("form_input_marks")
        )

        // 8. الصور المرفقة
        if (!isEditing) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "صور الغرض",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            enabled = !uiState.isSubmitting,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_pick_gallery_photo")
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("اختيار صورة من الجهاز")
                        }

                        OutlinedButton(
                            onClick = {
                                onAttachSampleImage("homs_item_${System.currentTimeMillis()}.jpg", isSensitiveMasked)
                            },
                            enabled = !uiState.isSubmitting,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_upload_verified_photo")
                        ) {
                            Text("رفع صورة توضيحية")
                        }
                    }

                    if (uiState.draftUploadedImages.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        uiState.draftUploadedImages.forEach { img ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "تمت إضافة الصورة",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = FoundReportEmerald
                                    )
                                    IconButton(
                                        onClick = { onRemoveDraftImage(img.id) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "حذف الصورة")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // زر النشر أو حفظ التعديل مع منع النقر المتكرر أثناء الإرسال
        Button(
            onClick = {
                if (isEditing && editingReport != null) {
                    onSubmitUpdate(
                        editingReport.id,
                        title,
                        selectedCategoryId,
                        description,
                        selectedNeighborhood,
                        distinguishingMarks
                    )
                } else {
                    onSubmitNew(
                        effectiveType,
                        title,
                        selectedCategoryId,
                        description,
                        selectedNeighborhood,
                        incidentDateText,
                        distinguishingMarks,
                        isSensitiveMasked
                    )
                }
            },
            enabled = !uiState.isSubmitting,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("btn_submit_report"),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (effectiveType == ReportType.LOST) LostReportAmber else FoundReportEmerald
            )
        ) {
            Text(
                text = when {
                    uiState.isSubmitting -> "جاري الحفظ والمطابقة..."
                    isEditing -> "حفظ تعديلات البلاغ"
                    effectiveType == ReportType.LOST -> "نشر بلاغ المفقود في حمص"
                    else -> "نشر بلاغ الموجود في حمص"
                },
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
        }
    }
}

/**
 * 8. شاشة تفاصيل البلاغ + اقتراحات المطابقة + التواصل الآمن + إدارة حالة البلاغ
 */
@Composable
fun ReportDetailsScreen(
    uiState: WaynhaUiState,
    report: ItemReport?,
    onBack: () -> Unit,
    onToggleSave: (String) -> Unit,
    onEditReport: (ItemReport) -> Unit,
    onConfirmRecovery: (String, String) -> Unit,
    onCloseReport: (String) -> Unit,
    onDeleteReport: (String) -> Unit,
    onStartSafeConversation: (targetReportId: String, matchedReportId: String?) -> Unit,
    onOpenCounterpartReport: (String) -> Unit,
    onSubmitAbuseFlag: (reportId: String, reason: String, details: String) -> Unit
) {
    if (report == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("لم يتم العثور على البلاغ المطلوب.", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onBack) { Text("العودة") }
        }
        return
    }

    val currentUser = uiState.currentUser
    val canManage = WaynhaSecurityValidator.canModifyReport(currentUser, report)
    val isSaved = uiState.savedReportIds.contains(report.id)
    val isLost = report.reportType == ReportType.LOST

    var showFlagDialog by remember { mutableStateOf(false) }
    var flagReason by remember { mutableStateOf("معلومات غير دقيقة أو مخالفة") }
    var flagDetails by remember { mutableStateOf("") }

    var showRecoveryDialog by remember { mutableStateOf(false) }
    var recoveryNotes by remember { mutableStateOf("تم التحقق من العلامات المميزة واستلام الغرض في حمص.") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("report_details_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // شريط علوي
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
                    text = "تفاصيل البلاغ في حمص",
                    style = MaterialTheme.typography.titleLarge
                )
            }

            Row {
                IconButton(
                    onClick = { onToggleSave(report.id) },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("details_btn_save")
                ) {
                    Icon(
                        imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = "حفظ البلاغ",
                        tint = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { showFlagDialog = true },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("details_btn_flag")
                ) {
                    Icon(
                        imageVector = Icons.Default.Flag,
                        contentDescription = "الإبلاغ عن محتوى مخالف",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // البطاقة الرئيسية للبلاغ
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = if (isLost) LostReportAmberContainer else FoundReportEmeraldContainer,
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = "بلاغ ${report.reportType.labelAr}",
                            color = if (isLost) LostReportAmber else FoundReportEmerald,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = "الحالة: ${report.status.labelAr}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = report.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${report.city} • حي ${report.neighborhood} (الموقع الدقيق مخفي للخصوصية)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                DetailRowItem("التصنيف:", report.category.nameAr)
                DetailRowItem("تاريخ الفقد/العثور:", report.incidentDateText)
                DetailRowItem("صاحب البلاغ:", report.userName)
                DetailRowItem("وقت النشر:", report.createdAtText)

                Spacer(modifier = Modifier.height(12.dp))
                Text("الوصف:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = report.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (report.distinguishingMarks.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("العلامات المميزة:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = report.distinguishingMarks,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (report.images.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "الصور المرفقة (${report.images.size}):",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    report.images.forEach { img ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = "صورة مرفقة",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }
        }

        // زر التواصل الآمن («قد يكون هذا غرضي») للمستخدمين الآخرين عندما يكون البلاغ مفتوحاً
        if (currentUser?.id != report.userId && report.status.isEligibleForMatching) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "تواصل آمن دون إظهار أرقام الهواتف",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "إذا كان هذا الغرض يخصك أو عثرت عليه في حمص، اضغط الزر أدناه لبدء محادثة آمنة مرتبطة بهذا البلاغ.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { onStartSafeConversation(report.id, null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("details_btn_might_be_mine")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("قد يكون هذا غرضي • بدء تواصل آمن")
                    }
                }
            }
        }

        // اقتراحات المطابقة لهذا البلاغ في حمص
        if (uiState.selectedReportMatches.isNotEmpty() && report.status.isEligibleForMatching) {
            Text(
                text = "اقتراحات البلاغات المتشابهة في حمص (${uiState.selectedReportMatches.size})",
                style = MaterialTheme.typography.titleLarge
            )
            uiState.selectedReportMatches.forEach { match ->
                val counterpartId = if (match.lostReportId == report.id) match.foundReportId else match.lostReportId
                val counterpart = uiState.allReports.firstOrNull { it.id == counterpartId }
                if (counterpart != null) {
                    ProbabilisticMatchCard(
                        match = match,
                        counterpartReport = counterpart,
                        onOpenReport = { onOpenCounterpartReport(counterpart.id) },
                        onMightBeMineClick = { onStartSafeConversation(counterpart.id, report.id) }
                    )
                }
            }
        }

        // لوحة تحكم صاحب البلاغ (أو الإدارة): تعديل، تأكيد استعادة، إغلاق، حذف
        if (canManage) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "إدارة البلاغ الخاص بك",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onEditReport(report) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("details_btn_edit_report")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("تعديل البلاغ")
                        }

                        if (report.status.isEligibleForMatching) {
                            Button(
                                onClick = { showRecoveryDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("details_btn_confirm_recovery"),
                                colors = ButtonDefaults.buttonColors(containerColor = FoundReportEmerald)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("تأكيد الاستعادة")
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (report.status.isEligibleForMatching) {
                            OutlinedButton(
                                onClick = { onCloseReport(report.id) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("details_btn_close_report")
                            ) {
                                Text("إغلاق البلاغ وإيقاف التنبيهات")
                            }
                        }

                        OutlinedButton(
                            onClick = { onDeleteReport(report.id) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("details_btn_delete_report"),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("حذف البلاغ")
                        }
                    }
                }
            }
        }
    }

    if (showRecoveryDialog) {
        AlertDialog(
            onDismissRequest = { showRecoveryDialog = false },
            title = { Text("تأكيد استعادة الغرض وإغلاق البلاغ") },
            text = {
                Column {
                    Text("سيتم تحديث حالة البلاغ إلى «تمت الاستعادة» وإيقاف جميع المطابقات والتنبيهات الجديدة له.")
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = recoveryNotes,
                        onValueChange = { recoveryNotes = it },
                        label = { Text("ملاحظات الاستلام (اختياري)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRecoveryDialog = false
                        onConfirmRecovery(report.id, recoveryNotes)
                    },
                    modifier = Modifier.testTag("dialog_confirm_recovery_btn")
                ) {
                    Text("تأكيد الاستعادة الآن")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRecoveryDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    if (showFlagDialog) {
        AlertDialog(
            onDismissRequest = { showFlagDialog = false },
            title = { Text("الإبلاغ عن محتوى مخالف") },
            text = {
                Column {
                    OutlinedTextField(
                        value = flagReason,
                        onValueChange = { flagReason = it },
                        label = { Text("سبب البلاغ") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = flagDetails,
                        onValueChange = { flagDetails = it },
                        label = { Text("تفاصيل إضافية للإدارة") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showFlagDialog = false
                        onSubmitAbuseFlag(report.id, flagReason, flagDetails)
                    }
                ) {
                    Text("إرسال البلاغ للإدارة")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFlagDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
private fun DetailRowItem(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
