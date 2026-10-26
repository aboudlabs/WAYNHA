package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.model.SystemLocationSettings
import com.example.ui.viewmodel.WaynhaUiState

/**
 * 1. شاشة البداية بشعار WAYNHA والاسم العربي «وينها؟»
 */
@Composable
fun SplashScreen(
    onContinueToOnboarding: () -> Unit,
    onSkipToHome: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0A4D46),
                        Color(0xFF0F766E),
                        Color(0xFF083934)
                    )
                )
            )
            .padding(24.dp)
            .testTag("splash_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                modifier = Modifier.size(104.dp),
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.14f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "شعار WAYNHA",
                        tint = Color.White,
                        modifier = Modifier.size(56.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "WAYNHA",
                style = MaterialTheme.typography.displayLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "وينها؟",
                style = MaterialTheme.typography.displayMedium,
                color = Color(0xFF5EEAD4),
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "«ضاع منك شي؟ خلّينا نلاقيه.»",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                color = Color.White.copy(alpha = 0.16f),
                shape = RoundedCornerShape(50)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "حمص",
                        tint = Color(0xFFFBBF24),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "الإصدار الأول • مخصص لمدينة حمص، سوريا",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            Button(
                onClick = onContinueToOnboarding,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("splash_btn_onboarding"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF0A4D46)
                )
            ) {
                Text("تعرّف على تطبيق وينها", style = MaterialTheme.typography.titleMedium)
            }

            Spacer(modifier = Modifier.height(10.dp))

            TextButton(
                onClick = onSkipToHome,
                modifier = Modifier.testTag("splash_btn_skip")
            ) {
                Text("الدخول المباشر إلى الصفحة الرئيسية", color = Color.White)
            }
        }
    }
}

/**
 * 2. شاشة تعريف قصيرة تشرح فائدة التطبيق
 */
@Composable
fun OnboardingScreen(
    onFinishOnboarding: () -> Unit,
    onGoToAuth: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .testTag("onboarding_screen"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_homs_hero),
            contentDescription = "رسم توضيحي لمجتمع مدينة حمص",
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(22.dp)),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "مجتمع حمص يتعاون لاستعادة المفقودات",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "يساعد تطبيق WAYNHA (وينها؟) أهالي مدينة حمص على الإبلاغ عن الأغراض المفقودة والموجودة ومطابقتها تلقائياً بأمان.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        OnboardingFeatureItem(
            icon = Icons.Default.Search,
            title = "مطابقة ذكية داخل أحياء حمص",
            description = "نقارن التصنيف والحي والتاريخ والعلامات المميزة لنقترح عليك البلاغات المتشابهة في الإنشاءات، الحمراء، الوعر، الغوطة وبقية أحياء حمص."
        )

        Spacer(modifier = Modifier.height(12.dp))

        OnboardingFeatureItem(
            icon = Icons.Default.Shield,
            title = "خصوصية كاملة بدون نشر رقم هاتفك",
            description = "لا نعرض رقم هاتفك أو عنوانك الدقيق علناً، ولا نسمح بنشر وثائق مكشوفة البيانات. يتم التواصل عبر محادثة آمنة مرتبطة بالبلاغ."
        )

        Spacer(modifier = Modifier.height(12.dp))

        OnboardingFeatureItem(
            icon = Icons.Default.CheckCircle,
            title = "تأكيد الاستعادة وإغلاق البلاغ",
            description = "عند استعادة الغرض، يتم توثيق الاستلام وإغلاق البلاغ وإيقاف التنبيهات الجديدة فوراً."
        )

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = onFinishOnboarding,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("onboarding_btn_start")
        ) {
            Text("ابدأ تصفح بلاغات حمص")
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedButton(
            onClick = onGoToAuth,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("onboarding_btn_auth")
        ) {
            Text("تسجيل الدخول أو إنشاء حساب")
        }
    }
}

@Composable
private fun OnboardingFeatureItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = CircleShape,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 3. شاشة تسجيل الدخول وإنشاء الحساب
 */
@Composable
fun AuthScreen(
    uiState: WaynhaUiState,
    onLogin: (String, String) -> Unit,
    onRegister: (String, String, String, String?) -> Unit,
    onQuickSwitchDemoAccount: (String, String) -> Unit,
    onBackToHome: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = تسجيل الدخول، 1 = إنشاء حساب
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var selectedNeighborhood by remember { mutableStateOf("الإنشاءات") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .testTag("auth_screen")
    ) {
        Text(
            text = "WAYNHA • وينها؟",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "حسابك المجتمعي للمفقودات والموجودات في مدينة حمص",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("تسجيل الدخول") },
                modifier = Modifier.testTag("tab_login")
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("إنشاء حساب جديد") },
                modifier = Modifier.testTag("tab_register")
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        if (selectedTab == 1) {
            OutlinedTextField(
                value = fullName,
                onValueChange = { fullName = it },
                label = { Text("الاسم الكامل") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("auth_input_fullname"),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("البريد الإلكتروني") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("auth_input_email"),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("كلمة المرور") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("auth_input_password"),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        // حقل المدينة ثابت وغير قابل للتغيير (حمص، سوريا)
        OutlinedTextField(
            value = "حمص، سوريا (المدينة الافتراضية والثابتة للإصدار الأول)",
            onValueChange = {},
            enabled = false,
            label = { Text("المدينة") },
            leadingIcon = {
                Icon(Icons.Default.LocationOn, contentDescription = "حمص")
            },
            modifier = Modifier.fillMaxWidth()
        )

        if (selectedTab == 1) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "اختر الحي الاختياري داخل حمص:",
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SystemLocationSettings.homsNeighborhoods.take(4).forEach { hood ->
                    FilterChip(
                        selected = selectedNeighborhood == hood,
                        onClick = { selectedNeighborhood = hood },
                        label = { Text(hood) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                if (selectedTab == 0) {
                    onLogin(email, password)
                } else {
                    onRegister(fullName, email, password, selectedNeighborhood)
                }
            },
            enabled = !uiState.isSubmitting,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("auth_submit_button")
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                if (uiState.isSubmitting) "جاري المعالجة..."
                else if (selectedTab == 0) "تسجيل الدخول"
                else "إنشاء الحساب في حمص"
            )
        }

        if (!uiState.isLiveSupabaseConnected) {
            Spacer(modifier = Modifier.height(24.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "حسابات تجريبية جاهزة لاختبار الصلاحيات وRLS:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "يمكنك التبديل الفوري بين مستخدم عادي، مستخدم ثانٍ، وحساب مدير النظام لاختبار سياسات الأمان:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = { onQuickSwitchDemoAccount("ahmad@homs.sy", "123456") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("demo_login_ahmad")
                    ) {
                        Text("دخول كـ أحمد الرفاعي (مستخدم عادي - الإنشاءات)")
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = { onQuickSwitchDemoAccount("sara@homs.sy", "123456") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("demo_login_sara")
                    ) {
                        Text("دخول كـ سارة الجندي (مستخدم ثانٍ - الحمراء)")
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = { onQuickSwitchDemoAccount("admin@waynha.sy", "admin123") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("demo_login_admin")
                    ) {
                        Text("دخول كـ مدير النظام (صلاحية لوحة الإدارة)")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        TextButton(
            onClick = onBackToHome,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("العودة إلى الصفحة الرئيسية")
        }
    }
}
