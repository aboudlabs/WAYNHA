package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.core.data.WaynhaRepository
import com.example.core.model.ReportType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `1_verify app name and Arabic strings for Homs`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("WAYNHA", context.getString(R.string.app_name))
        assertEquals("وينها؟", context.getString(R.string.app_name_ar))
        assertEquals("ضاع منك شي؟ خلّينا نلاقيه.", context.getString(R.string.app_slogan))
        assertEquals("حمص", context.getString(R.string.city_homs))
    }

    @Test
    fun `2_saved reports and published reports persist across repository restarts`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo1 = WaynhaRepository(context)
        val created = repo1.createReport(
            reportType = ReportType.LOST,
            title = "دفتر جامعي هندسة معلوماتية",
            categoryId = "documents",
            description = "فقدت دفتر محاضرات في جامعة البعث بمدينة حمص",
            city = "حمص",
            neighborhood = "جامعة البعث",
            incidentDateText = "2026-10-26",
            distinguishingMarks = "غلاف أزرق"
        ).getOrThrow()
        repo1.toggleSavedReport(created.id)

        // محاكاة إعادة تشغيل التطبيق عبر إنشاء مستودع جديد يقرأ من التخزين الدائم
        val repo2 = WaynhaRepository(context)
        val persistedReport = repo2.getReportById(created.id)
        assertTrue(persistedReport != null)
        assertEquals("دفتر جامعي هندسة معلوماتية", persistedReport?.title)
        assertTrue(repo2.getSavedReportIds(repo2.getCurrentUser()!!.id).contains(created.id))
    }

    @Test
    fun `3_home screen renders RTL Arabic elements and bottom navigation tabs work`() {
        composeTestRule.waitForIdle()

        // التحقق من عناصر الصفحة الرئيسية: حقل البحث «شو عم تدور عليه؟» وزرَي «أضعت غرضاً» و«وجدت غرضاً»
        composeTestRule.onNodeWithTag("home_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_home_lost_item").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_home_found_item").assertIsDisplayed()
        composeTestRule.onNodeWithText("أضعت غرضاً").assertIsDisplayed()
        composeTestRule.onNodeWithText("وجدت غرضاً").assertIsDisplayed()

        // التنقل إلى شاشة البحث والتصفية
        composeTestRule.onNodeWithTag("nav_tab_search").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("search_screen").assertIsDisplayed()

        // التنقل إلى شاشة المحفوظات
        composeTestRule.onNodeWithTag("nav_tab_saved").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("saved_reports_screen").assertIsDisplayed()

        // التنقل إلى شاشة حسابي والملف الشخصي
        composeTestRule.onNodeWithTag("nav_tab_profile").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("profile_settings_screen").assertIsDisplayed()
    }
}
