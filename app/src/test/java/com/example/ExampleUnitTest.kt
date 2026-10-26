package com.example

import com.example.core.data.WaynhaRepository
import com.example.core.domain.ValidationResult
import com.example.core.domain.WaynhaMatchingEngine
import com.example.core.domain.WaynhaSecurityValidator
import com.example.core.model.ReportStatus
import com.example.core.model.ReportType
import com.example.core.model.SearchFilterState
import com.example.core.model.UserProfile
import com.example.core.model.UserRole
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * اختبارات الوحدة والمنطق والأمان لتطبيق WAYNHA (وينها؟) - مدينة حمص
 */
class ExampleUnitTest {

    @Test
    fun `1_account registration login and logout work properly`() = runTest {
        val repo = WaynhaRepository(context = null)
        repo.logout()
        assertEquals(null, repo.getCurrentUser())

        val regRes = repo.registerAccount(
            fullName = "مازن الحمصي",
            email = "mazen@homs.sy",
            password = "password123",
            neighborhood = "الغوطة"
        )
        assertTrue(regRes.isSuccess)
        assertEquals("حمص", regRes.getOrThrow().city)
        assertEquals("مازن الحمصي", repo.getCurrentUser()?.fullName)

        repo.logout()
        assertEquals(null, repo.getCurrentUser())

        val loginRes = repo.login("mazen@homs.sy", "password123")
        assertTrue(loginRes.isSuccess)
        assertEquals("مازن الحمصي", repo.getCurrentUser()?.fullName)
    }

    @Test
    fun `2_report outside Homs or containing phone number is strictly rejected`() = runTest {
        val repo = WaynhaRepository(context = null)

        // محاولة نشر بلاغ خارج حمص (دمشق مثلاً) -> يجب رفضه
        val outsideHomsRes = repo.createReport(
            reportType = ReportType.LOST,
            title = "حقيبة يد جلدية",
            categoryId = "bags",
            description = "فقدت حقيبة يد سوداء كبيرة بداخلها دفاتر",
            city = "دمشق",
            neighborhood = "المزة",
            incidentDateText = "2026-10-26",
            distinguishingMarks = "سحاب ذهبي"
        )
        assertTrue(outsideHomsRes.isFailure)
        assertTrue(outsideHomsRes.exceptionOrNull()?.message?.contains("حمص") == true)

        // محاولة نشر رقم هاتف سوري علناً داخل الوصف -> يجب رفضه لحماية الخصوصية
        val phoneInDescriptionValidation = WaynhaSecurityValidator.validateReportInput(
            title = "مفاتيح منزل",
            description = "ضاعت مفاتيح في حي الوعر تواصلوا معي على الرقم 0944123456",
            categoryId = "keys",
            city = "حمص",
            neighborhood = "الوعر",
            distinguishingMarks = ""
        )
        assertTrue(phoneInDescriptionValidation is ValidationResult.Invalid)
    }

    @Test
    fun `3_create edit close report and stop new matches on closed report`() = runTest {
        val repo = WaynhaRepository(context = null)

        // رفع صورة وإرفاقها ببلاغ جديد في حمص
        val imgRes = repo.uploadReportImage("waynha://storage/report-images/watch.jpg")
        assertTrue(imgRes.isSuccess)

        val createRes = repo.createReport(
            reportType = ReportType.LOST,
            title = "ساعة يد فضية كلاسيكية",
            categoryId = "jewelry",
            description = "ساعة يد فضية فقدت في حي الحمراء قرب الملعب البلدي بحمص",
            city = "حمص",
            neighborhood = "الحمراء",
            incidentDateText = "2026-10-26",
            distinguishingMarks = "حزام جلدي أسود وخدش بسيط"
        )
        assertTrue(createRes.isSuccess)
        val created = createRes.getOrThrow()

        // تعديل البلاغ من قبل صاحبه
        val updateRes = repo.updateReport(
            reportId = created.id,
            title = "ساعة يد فضية كلاسيكية محدثة",
            categoryId = "jewelry",
            description = "ساعة يد فضية فقدت في حي الحمراء قرب الملعب البلدي بحمص صباحاً",
            neighborhood = "الحمراء",
            distinguishingMarks = "حزام جلدي أسود"
        )
        assertTrue(updateRes.isSuccess)
        assertEquals("ساعة يد فضية كلاسيكية محدثة", updateRes.getOrThrow().title)

        // إغلاق البلاغ بعد استعادة الغرض والتحقق من عدم دخوله في مطابقات جديدة
        val closeRes = repo.closeOrRecoverReport(created.id, markRecovered = true)
        assertTrue(closeRes.isSuccess)
        assertEquals(ReportStatus.RECOVERED, closeRes.getOrThrow().status)
        assertTrue(repo.getMatchesForReport(created.id).isEmpty())
    }

    @Test
    fun `4_search filter and rule based matching in Homs work accurately`() {
        val repo = WaynhaRepository(context = null)
        val allReports = repo.getAllReports()

        // فحص التصفية حسب حي الإنشاءات في حمص
        val inshaatFilter = SearchFilterState(neighborhood = "الإنشاءات")
        val inshaatResults = WaynhaMatchingEngine.filterAndSortReports(allReports, inshaatFilter)
        assertTrue(inshaatResults.isNotEmpty())
        assertTrue(inshaatResults.all { it.neighborhood == "الإنشاءات" && it.city == "حمص" })

        // فحص وجود مطابقة عالية التشابه بين المحفظة المفقودة والمحفظة الموجودة في حي الإنشاءات
        val matches = repo.getMatchesForReport("rep_homs_1")
        assertTrue(matches.isNotEmpty())
        assertEquals("rep_homs_2", matches.first().foundReportId)
        assertTrue(matches.first().similarityScore >= 75)
    }

    @Test
    fun `5_RLS security prevents non-owner report edits non-participant chat access and self-admin escalation`() = runTest {
        val repo = WaynhaRepository(context = null)

        // المستخدم الحالي هو أحمد (demo_user_ahmad)
        // محاولة تعديل أو حذف بلاغ يخص سارة (rep_homs_2) -> يجب رفضها
        val unauthorizedEdit = repo.updateReport(
            reportId = "rep_homs_2",
            title = "تعديل غير مصرح",
            categoryId = "wallets",
            description = "محاولة تعديل بلاغ مستخدم آخر في حمص",
            neighborhood = "الإنشاءات",
            distinguishingMarks = ""
        )
        assertTrue(unauthorizedEdit.isFailure)

        val unauthorizedDelete = repo.deleteReport("rep_homs_2")
        assertTrue(unauthorizedDelete.isFailure)

        // مستخدم ثالث غير مشارك (طارق demo_user_tarek) يحاول قراءة أو إرسال رسالة في محادثة أحمد وسارة (conv_homs_1) -> يجب رفضه
        val outsiderUser = UserProfile(
            id = "demo_user_tarek",
            email = "tarek@homs.sy",
            fullName = "طارق السباعي",
            role = UserRole.USER
        )
        val outsiderReadRes = repo.getMessagesForConversation(outsiderUser, "conv_homs_1")
        assertTrue(outsiderReadRes.isFailure)

        val outsiderSendRes = repo.sendMessage("conv_homs_1", "رسالة متطفلة", actingUser = outsiderUser)
        assertTrue(outsiderSendRes.isFailure)

        // محاولة مستخدم عادي تعيين نفسه مديراً أو الدخول للوحة الإدارة -> يجب رفضها
        val selfAdminAttempt = repo.attemptSelfRoleChange(UserRole.ADMIN)
        assertTrue(selfAdminAttempt.isFailure)
        assertFalse(WaynhaSecurityValidator.canAccessAdminDashboard(outsiderUser))
        assertTrue(repo.getAllProfilesForAdmin(outsiderUser).isFailure)
    }

    @Test
    fun `6_offline mode and image upload failure are handled gracefully`() = runTest {
        val repo = WaynhaRepository(context = null)

        repo.simulateImageUploadFailure = true
        val failedUpload = repo.uploadReportImage("waynha://storage/report-images/test.jpg")
        assertTrue(failedUpload.isFailure)
        repo.simulateImageUploadFailure = false

        repo.simulateOfflineMode = true
        val offlineReport = repo.createReport(
            reportType = ReportType.FOUND,
            title = "نظارة شمسية سوداء",
            categoryId = "other",
            description = "وجدت نظارة شمسية في حي عكرمة بحمص",
            neighborhood = "عكرمة",
            incidentDateText = "2026-10-26",
            distinguishingMarks = "إطار معدني"
        )
        assertTrue(offlineReport.isFailure)
        repo.simulateOfflineMode = false
    }
}
