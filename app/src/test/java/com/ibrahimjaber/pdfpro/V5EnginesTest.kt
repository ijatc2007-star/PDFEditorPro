package com.ibrahimjaber.pdfpro

import com.ibrahimjaber.pdfpro.engine.OfflineAiPdfEngine
import com.ibrahimjaber.pdfpro.engine.SummaryDepth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for v5.0 Large-Document Map-Reduce AI Engine & Inverted-Index BM25 Q&A (100+ pages).
 * Programmed by Ibrahim Jaber 2026 | برمجة إبراهيم جابر 2026
 */
class V5EnginesTest {

    @Test
    fun largeBookMapReduceHandles120PagesCleanly() {
        val pages = (1..120).map { p ->
            when (p) {
                1 -> "Chapter 1 Introduction to Network Security.\nAll enterprise systems must enforce zero-trust firewall rules and encrypted backups before 2026-12-31."
                60 -> "Chapter 6 Financial Budget Review.\nThe approved annual budget total is $450,000 USD for offline PDF Editor Pro deployment."
                115 -> "الفصل العاشر الخاتمة والتوصيات.\nيجب اعتماد نظام التوقيع الرقمي والترجمة الفورية بالكاميرا لجميع الموظفين."
                else -> "Section $p Standard Operational Procedure.\nPage $p discusses security compliance, document archiving, and offline privacy controls for teams."
            }
        }

        val report = OfflineAiPdfEngine.analyzeLargeDocument(
            pages = pages,
            startPageNumber = 1,
            totalDocumentPages = 120,
            depth = SummaryDepth.BOOK
        )

        assertTrue("Expected Map-Reduce mode for 120-page book", report.usedMapReduce)
        assertEquals(120, report.analyzedPageCount)
        assertEquals(120, report.totalDocumentPages)
        assertTrue("Expected chapter chunk summaries", report.chapterSummaries.size >= 4)
        assertTrue("Expected executive summary bullets", report.summaryBullets.isNotEmpty())
        assertTrue("Expected budget amount extracted", report.amountsFound.any { it.contains("450,000") })
        assertTrue("Expected deadline date extracted", report.datesFound.any { it.contains("2026-12-31") })

        // Verify <50ms Inverted-Index Q&A hits exact page 60 (0-based index 59) & Arabic page 115 (index 114)
        val index = OfflineAiPdfEngine.buildLargeDocIndex(pages, startPageNumber = 1)
        val enQa = OfflineAiPdfEngine.askQuestionIndexed(index, "annual budget 450,000 USD")
        assertTrue("Expected Q&A to cite page 60", enQa.passages.any { it.pageIndex == 59 })

        val arQa = OfflineAiPdfEngine.askQuestionIndexed(index, "التوقيع الرقمي والترجمة الفورية")
        assertTrue("Expected Arabic Q&A to cite page 115", arQa.passages.any { it.pageIndex == 114 })
    }

    @Test
    fun arabEyesSqlDictionaryRowRegexParsesCleanly() {
        val sampleLines = listOf(
            "(1, '10th', 'العاشر'),",
            "(42, 'Agreement', 'اتفاقية::عقد'),",
            "(999, 'Security', 'أمن::حماية');"
        )
        val regex = Regex("\\(\\d+,\\s*'([^']{1,60})',\\s*'([^']{1,160})'\\)")
        val parsed = sampleLines.mapNotNull { line ->
            regex.find(line)?.let { it.groupValues[1].lowercase() to it.groupValues[2].split("::").first().trim() }
        }.toMap()
        assertEquals(3, parsed.size)
        assertEquals("العاشر", parsed["10th"])
        assertEquals("اتفاقية", parsed["agreement"])
        assertEquals("أمن", parsed["security"])
    }

    @Test
    fun camScannerCropRectClampsSafely() {
        val clamped = com.ibrahimjaber.pdfpro.engine.CamScannerEngine.clampCropRect(-0.2f, 0.8f, 0.1f, 1.5f)
        assertEquals(0f, clamped[0], 0.001f)
        assertEquals(0.45f, clamped[1], 0.001f)
        assertTrue(clamped[2] >= clamped[0] + 0.15f)
        assertTrue(clamped[3] >= clamped[1] + 0.15f && clamped[3] <= 1f)
    }
}
