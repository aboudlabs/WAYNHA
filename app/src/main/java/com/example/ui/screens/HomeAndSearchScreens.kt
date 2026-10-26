package com.example.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterAltOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.model.DateFilterOption
import com.example.core.model.ReportType
import com.example.ui.components.EmptyOrErrorStateBox
import com.example.ui.components.HomsScopeBanner
import com.example.ui.components.ProbabilisticMatchCard
import com.example.ui.components.ReportItemCard
import com.example.ui.theme.FoundReportEmerald
import com.example.ui.theme.LostReportAmber
import com.example.ui.viewmodel.WaynhaUiState

/**
 * 4. الصفحة الرئيسية لتطبيق WAYNHA (وينها؟)
 */
@Composable
fun HomeScreen(
    uiState: WaynhaUiState,
    onOpenSearchWithQuery: (String) -> Unit,
    onSelectCategoryFilter: (String) -> Unit,
    onLostItemClick: () -> Unit,
    onFoundItemClick: () -> Unit,
    onOpenReportDetails: (String) -> Unit,
    onToggleSaveReport: (String) -> Unit,
    onMightBeMineClick: (targetReportId: String, matchedReportId: String) -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenConversations: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("home_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. شريط الترحيب والإشعارات والمحادثات
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (uiState.currentUser != null) {
                            "أهلاً بك، ${uiState.currentUser.fullName}"
                        } else {
                            "أهلاً بك في وينها؟ (WAYNHA)"
                        },
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "ضاع منك شي؟ خلّينا نلاقيه • مجتمع مدينة حمص",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onOpenConversations,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("home_btn_conversations")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Chat,
                            contentDescription = "المحادثات الآمنة",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(
                        onClick = onOpenNotifications,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("home_btn_notifications")
                    ) {
                        BadgedBox(
                            badge = {
                                if (uiState.unreadNotificationsCount > 0) {
                                    Badge {
                                        Text(uiState.unreadNotificationsCount.toString())
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "الإشعارات",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        // 2. شارة النطاق الجغرافي الثابت: حمص، سوريا
        item {
            HomsScopeBanner(
                isLiveSupabase = uiState.isLiveSupabaseConnected,
                isOfflineSimulated = uiState.isOfflineSimulated
            )
        }

        // 3. حقل البحث بعنوان «شو عم تدور عليه؟»
        item {
            OutlinedTextField(
                value = uiState.searchFilter.query,
                onValueChange = { onOpenSearchWithQuery(it) },
                placeholder = { Text("شو عم تدور عليه؟") },
                label = { Text("شو عم تدور عليه؟") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "بحث"
                    )
                },
                trailingIcon = {
                    if (uiState.searchFilter.query.isNotEmpty()) {
                        IconButton(onClick = { onOpenSearchWithQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "مسح البحث")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_search_field")
            )
        }

        // 4. الزران البارزان: «أضعت غرضاً» و«وجدت غرضاً»
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onLostItemClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .testTag("btn_home_lost_item"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LostReportAmber,
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.AddCircleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "أضعت غرضاً",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = onFoundItemClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .testTag("btn_home_found_item"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FoundReportEmerald,
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "وجدت غرضاً",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 5. بطاقة الهوية البصرية لمدينة حمص
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .clip(RoundedCornerShape(22.dp))
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_homs_hero),
                        contentDescription = "بنر مدينة حمص",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0x22051010),
                                        Color(0xB8051716),
                                        Color(0xF0082421)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Surface(
                            color = Color(0xCC062E2A),
                            shape = RoundedCornerShape(50)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = Color(0xFFFBBF24),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "المفقودات والموجودات • حمص",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(9.dp))
                        Text(
                            text = "ضاع منك شي؟",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "خلّينا نلاقيه",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color(0xFF6EE7D2),
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "مع بعض منساعدك تلاقيه بأمان",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFF1F5F9)
                        )
                    }
                }
            }
        }

        // 6. تصنيفات المفقودات
        item {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "تصنيفات المفقودات",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    TextButton(onClick = { onSelectCategoryFilter("") }) {
                        Text("عرض الكل")
                    }
                }
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.categories, key = { it.id }) { cat ->
                        FilterChip(
                            selected = uiState.searchFilter.categoryId == cat.id,
                            onClick = { onSelectCategoryFilter(cat.id) },
                            label = { Text(cat.nameAr) },
                            modifier = Modifier.testTag("home_category_chip_${cat.id}")
                        )
                    }
                }
            }
        }

        // 7. اقتراحات البلاغات المتشابهة (إن وجدت)
        if (uiState.userMatches.isNotEmpty()) {
            item {
                Text(
                    text = "اقتراحات البلاغات المتشابهة في حمص",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            items(uiState.userMatches.take(2), key = { it.id }) { match ->
                val myReportIds = uiState.userReports.map { it.id }.toSet()
                val counterpartId = if (myReportIds.contains(match.lostReportId)) {
                    match.foundReportId
                } else {
                    match.lostReportId
                }
                val myId = if (myReportIds.contains(match.lostReportId)) {
                    match.lostReportId
                } else {
                    match.foundReportId
                }
                val counterpart = uiState.allReports.firstOrNull { it.id == counterpartId }
                if (counterpart != null) {
                    ProbabilisticMatchCard(
                        match = match,
                        counterpartReport = counterpart,
                        onOpenReport = { onOpenReportDetails(counterpart.id) },
                        onMightBeMineClick = { onMightBeMineClick(counterpart.id, myId) }
                    )
                }
            }
        }

        // 8. بلاغات حديثة من حمص
        item {
            Text(
                text = "بلاغات حديثة من حمص (${uiState.allReports.size})",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        if (uiState.allReports.isEmpty()) {
            item {
                EmptyOrErrorStateBox(
                    title = "لا توجد بلاغات منشورة حالياً في حمص",
                    subtitle = "كن أول من ينشر بلاغ مفقود أو موجود لمساعدة أهالي حمص.",
                    actionLabel = "نشر بلاغ جديد",
                    onActionClick = onLostItemClick
                )
            }
        } else {
            items(uiState.allReports, key = { it.id }) { report ->
                ReportItemCard(
                    report = report,
                    isSaved = uiState.savedReportIds.contains(report.id),
                    onCardClick = { onOpenReportDetails(report.id) },
                    onToggleSave = { onToggleSaveReport(report.id) }
                )
            }
        }
    }
}

/**
 * 7. شاشة البحث والتصفية المتقدمة داخل مدينة حمص
 */
@Composable
fun SearchAndFilterScreen(
    uiState: WaynhaUiState,
    onQueryChange: (String) -> Unit,
    onTypeChange: (ReportType?) -> Unit,
    onCategoryChange: (String?) -> Unit,
    onNeighborhoodChange: (String?) -> Unit,
    onDateFilterChange: (DateFilterOption) -> Unit,
    onClearAllFilters: () -> Unit,
    onOpenReportDetails: (String) -> Unit,
    onToggleSaveReport: (String) -> Unit
) {
    val filter = uiState.searchFilter

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("search_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "البحث والتصفية في مدينة حمص",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "ابحث بالاسم أو الوصف أو العلامات المميزة وصفِّ النتائج حسب الحي والتصنيف والتاريخ",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            OutlinedTextField(
                value = filter.query,
                onValueChange = onQueryChange,
                label = { Text("شو عم تدور عليه؟ (الاسم أو الوصف)") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "بحث") },
                trailingIcon = {
                    if (filter.query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "مسح النص")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_screen_query_input")
            )
        }

        // التصفية حسب النوع: الكل / مفقود / موجود
        item {
            Column {
                Text("نوع البلاغ:", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = filter.reportType == null,
                        onClick = { onTypeChange(null) },
                        label = { Text("الكل") },
                        modifier = Modifier.testTag("filter_type_all")
                    )
                    FilterChip(
                        selected = filter.reportType == ReportType.LOST,
                        onClick = { onTypeChange(ReportType.LOST) },
                        label = { Text("مفقود") },
                        modifier = Modifier.testTag("filter_type_lost")
                    )
                    FilterChip(
                        selected = filter.reportType == ReportType.FOUND,
                        onClick = { onTypeChange(ReportType.FOUND) },
                        label = { Text("موجود") },
                        modifier = Modifier.testTag("filter_type_found")
                    )
                }
            }
        }

        // التصفية حسب التصنيف
        item {
            Column {
                Text("التصنيف:", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = filter.categoryId == null,
                            onClick = { onCategoryChange(null) },
                            label = { Text("كل التصنيفات") }
                        )
                    }
                    items(uiState.categories, key = { it.id }) { cat ->
                        FilterChip(
                            selected = filter.categoryId == cat.id,
                            onClick = {
                                onCategoryChange(if (filter.categoryId == cat.id) null else cat.id)
                            },
                            label = { Text(cat.nameAr) },
                            modifier = Modifier.testTag("search_filter_cat_${cat.id}")
                        )
                    }
                }
            }
        }

        // التصفية حسب المنطقة أو الحي داخل حمص
        item {
            Column {
                Text("المنطقة أو الحي داخل حمص:", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = filter.neighborhood == null,
                            onClick = { onNeighborhoodChange(null) },
                            label = { Text("كل أحياء حمص") }
                        )
                    }
                    items(uiState.homsNeighborhoods, key = { it }) { hood ->
                        FilterChip(
                            selected = filter.neighborhood == hood,
                            onClick = {
                                onNeighborhoodChange(if (filter.neighborhood == hood) null else hood)
                            },
                            label = { Text(hood) },
                            modifier = Modifier.testTag("search_filter_hood_$hood")
                        )
                    }
                }
            }
        }

        // التصفية حسب التاريخ
        item {
            Column {
                Text("تاريخ الفقد أو العثور:", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(DateFilterOption.entries, key = { it.name }) { opt ->
                        FilterChip(
                            selected = filter.dateFilter == opt,
                            onClick = { onDateFilterChange(opt) },
                            label = { Text(opt.labelAr) }
                        )
                    }
                }
            }
        }

        // شريط عدد النتائج وزر مسح الفلاتر
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "النتائج المطابقة في حمص (${uiState.filteredReports.size}) • مرتبة حسب الأحدث",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (filter.hasActiveFilters) {
                    OutlinedButton(
                        onClick = onClearAllFilters,
                        modifier = Modifier.testTag("btn_clear_search_filters")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterAltOff,
                            contentDescription = "مسح الفلاتر",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("مسح الفلاتر")
                    }
                }
            }
        }

        if (uiState.filteredReports.isEmpty()) {
            item {
                EmptyOrErrorStateBox(
                    title = "لا توجد بلاغات مطابقة لبحثك في حمص",
                    subtitle = "جرّب تعديل الكلمات المفتاحية أو مسح الفلاتر لعرض جميع بلاغات مدينة حمص.",
                    actionLabel = "مسح الفلاتر وإعادة البحث",
                    onActionClick = onClearAllFilters
                )
            }
        } else {
            items(uiState.filteredReports, key = { it.id }) { report ->
                ReportItemCard(
                    report = report,
                    isSaved = uiState.savedReportIds.contains(report.id),
                    onCardClick = { onOpenReportDetails(report.id) },
                    onToggleSave = { onToggleSaveReport(report.id) }
                )
            }
        }
    }
}

/**
 * شاشة اختيار نوع البلاغ عند الضغط على زر "إضافة بلاغ" في الشريط السفلي
 */
@Composable
fun AddReportChooserScreen(
    onChooseLost: () -> Unit,
    onChooseFound: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .testTag("add_report_chooser_screen"),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "إضافة بلاغ جديد في مدينة حمص",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "اختر نوع البلاغ ليتم مطابقته تلقائياً مع البلاغات المقابلة داخل أحياء حمص:",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onChooseLost)
                .testTag("chooser_btn_lost"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = LostReportAmber.copy(alpha = 0.12f)
            )
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = LostReportAmber,
                    shape = CircleShape,
                    modifier = Modifier.size(54.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.AddCircleOutline,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "أضعت غرضاً (بلاغ مفقود)",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "انشر مواصفات الغرض الذي فقدته في حمص ليصلك تنبيه عند العثور عليه.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onChooseFound)
                .testTag("chooser_btn_found"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = FoundReportEmerald.copy(alpha = 0.12f)
            )
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = FoundReportEmerald,
                    shape = CircleShape,
                    modifier = Modifier.size(54.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.CheckCircleOutline,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "وجدت غرضاً (بلاغ موجود)",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "بلّغ عن غرض عثرت عليه في حمص دون كشف رقمك أو تفاصيله الدقيقة للعامة.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
