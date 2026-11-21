package com.ibrahimjaber.pdfpro

import com.ibrahimjaber.pdfpro.engine.ArabicTextProcessor
import com.ibrahimjaber.pdfpro.engine.DetectedLanguage
import com.ibrahimjaber.pdfpro.engine.OfflineAiPdfEngine
import com.ibrahimjaber.pdfpro.engine.SummaryDepth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineAiEngineTest {

    @Test
    fun arabicNormalizationUnifiesHamzaDiacriticsAndPresentationForms() {
        val norm = ArabicTextProcessor.normalizeForSearch("إبْرَاهِيمُ جَابِر — مَدرَسَةٌ")
        assertTrue(norm.contains("ابراهيم"))
        assertTrue(norm.contains("مدرسه"))
    }

    @Test
    fun languageDetectionIdentifiesArabicAndEnglish() {
        val ar = ArabicTextProcessor.analyzeLanguage("تطبيق محرر ملفات PDF الاحترافي برمجة إبراهيم جابر 2026 يعمل بدون إنترنت")
        assertEquals(DetectedLanguage.ARABIC, ar.primary)

        val en = ArabicTextProcessor.analyzeLanguage("PDF Editor Pro is a complete offline viewer and editor programmed by Ibrahim Jaber 2026.")
        assertEquals(DetectedLanguage.ENGLISH, en.primary)
    }

    @Test
    fun offlineAiSummarizesExtractsPiiAndAnswersArabicAndEnglishQuestions() {
        val pages = listOf(
            """
            1. Executive Contract Overview
            This agreement must be approved before 2026-12-31 with a total budget of ${'$'}45,000 USD.
            Contact legal@example.com or +1-555-234-5678 for verification.
            """.trimIndent(),
            """
            الفصل الثاني: شروط الدفع والتسليم
            يجب تسليم المشروع النهائي قبل تاريخ 2026-12-31 بقيمة إجمالية 50000 ريال سعودي.
            تم تطوير هذا النظام بواسطة إبراهيم جابر 2026 للعمل أوفلاين بالكامل.
            """.trimIndent()
        )

        val report = OfflineAiPdfEngine.analyzeDocument(pages, SummaryDepth.SHORT)
        assertTrue(report.summaryBullets.isNotEmpty())
        assertTrue(report.datesFound.any { it.contains("2026-12-31") })
        assertTrue(report.amountsFound.isNotEmpty())
        assertTrue(report.emailsFound.contains("legal@example.com"))
        assertTrue(report.piiFindings.any { it.category == "Email" })
        assertTrue(report.autoHeadings.isNotEmpty())

        val qaEn = OfflineAiPdfEngine.askQuestion(pages, "What is the total budget and deadline?")
        assertTrue(qaEn.passages.isNotEmpty())
        assertEquals(0, qaEn.passages.first().pageIndex)

        val qaAr = OfflineAiPdfEngine.askQuestion(pages, "ما هي شروط الدفع والتسليم والقيمة؟")
        assertTrue(qaAr.passages.isNotEmpty())
        assertEquals(1, qaAr.passages.first().pageIndex)
    }
}
