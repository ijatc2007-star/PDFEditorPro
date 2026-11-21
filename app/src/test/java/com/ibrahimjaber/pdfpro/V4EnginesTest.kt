package com.ibrahimjaber.pdfpro

import com.ibrahimjaber.pdfpro.engine.OfflineAiPdfEngine
import com.ibrahimjaber.pdfpro.engine.OfflineTranslatorEngine
import com.ibrahimjaber.pdfpro.engine.SummaryDepth
import com.ibrahimjaber.pdfpro.engine.TranslationDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class V4EnginesTest {

    @Test
    fun offlineTranslatorTranslatesEnglishToArabicAndBack() {
        val toAr = OfflineTranslatorEngine.translate(
            "Executive Summary: This contract agreement must be approved before the deadline.",
            TranslationDirection.EN_TO_AR
        )
        assertTrue(toAr.contains("الملخص التنفيذي"))
        assertTrue(toAr.contains("عقد") || toAr.contains("اتفاقية"))
        assertTrue(toAr.contains("يجب"))

        val toEn = OfflineTranslatorEngine.translate(
            "الملخص التنفيذي: يجب اعتماد التقرير النهائي قبل الموعد النهائي",
            TranslationDirection.AR_TO_EN
        )
        assertTrue(toEn.lowercase().contains("executive summary"))
        assertTrue(toEn.lowercase().contains("final report") || toEn.lowercase().contains("report"))
    }

    @Test
    fun studyFlashcardsAndFormalReplyDraftGenerateProperly() {
        val pages = listOf(
            "1. Security Policy\nAll passwords must be encrypted and verified before 2026-12-31 with a budget of $12,000 USD."
        )
        val cards = OfflineTranslatorEngine.generateStudyFlashcards(pages)
        assertTrue(cards.isNotEmpty())

        val rep = OfflineAiPdfEngine.analyzeDocument(pages, SummaryDepth.SHORT)
        val replyEn = OfflineTranslatorEngine.draftFormalReply(rep, "Policy.pdf", inArabic = false)
        val replyAr = OfflineTranslatorEngine.draftFormalReply(rep, "Policy.pdf", inArabic = true)
        assertTrue(replyEn.contains("Ibrahim Jaber"))
        assertTrue(replyAr.contains("إبراهيم جابر"))
    }

    @Test
    fun ggufModelHeaderValidatorDetectsValidAndInvalidFiles() {
        val validFile = File.createTempFile("test_model", ".gguf")
        val invalidFile = File.createTempFile("not_model", ".bin")
        try {
            // Write GGUF magic "GGUF" + version 3 little-endian
            validFile.writeBytes(byteArrayOf(0x47, 0x47, 0x55, 0x46, 0x03, 0x00, 0x00, 0x00, 0x00))
            invalidFile.writeBytes("hello world".toByteArray())

            val validInfo = OfflineTranslatorEngine.inspectLocalGgufModel(validFile)
            assertTrue(validInfo.isValidGgufHeader)
            assertEquals(3, validInfo.ggufVersion)

            val invalidInfo = OfflineTranslatorEngine.inspectLocalGgufModel(invalidFile)
            assertFalse(invalidInfo.isValidGgufHeader)
        } finally {
            validFile.delete()
            invalidFile.delete()
        }
    }
}
