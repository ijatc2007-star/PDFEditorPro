package com.ibrahimjaber.pdfpro

import com.ibrahimjaber.pdfpro.data.AppBranding
import com.ibrahimjaber.pdfpro.data.CompressionResult
import com.ibrahimjaber.pdfpro.data.PdfMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfModelsTest {

    @Test
    fun brandingContainsIbrahimJaber2026() {
        assertEquals("Ibrahim Jaber", AppBranding.DEVELOPER_NAME)
        assertEquals("2026", AppBranding.COPYRIGHT_YEAR)
        assertTrue(AppBranding.CREDIT_LINE.contains("Ibrahim Jaber 2026"))
        assertTrue(AppBranding.PDF_CREATOR_TAG.contains("Ibrahim Jaber 2026"))
    }

    @Test
    fun defaultMetadataUsesBrandedCreator() {
        val meta = PdfMetadata()
        assertEquals(AppBranding.PDF_CREATOR_TAG, meta.creator)
    }

    @Test
    fun compressionResultComputesSavingsAccurately() {
        val res = CompressionResult(
            outputPath = "/tmp/out.pdf",
            originalBytes = 1_000_000L,
            compressedBytes = 350_000L
        )
        assertEquals(650_000L, res.savedBytes)
        assertEquals(65, res.savedPercent)
    }
}
