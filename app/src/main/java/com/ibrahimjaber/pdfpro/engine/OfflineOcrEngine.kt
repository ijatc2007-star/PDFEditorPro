package com.ibrahimjaber.pdfpro.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.googlecode.tesseract.android.TessBaseAPI
import com.ibrahimjaber.pdfpro.data.AppBranding
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

enum class OcrLanguageMode(val tessCode: String, val labelEn: String, val labelAr: String) {
    ARABIC_AND_ENGLISH("ara+eng", "Arabic + English (Bilingual)", "عربي + إنجليزي (ثنائي)"),
    ARABIC_ONLY("ara", "Arabic Only (العربية)", "العربية فقط"),
    ENGLISH_ONLY("eng", "English Only", "الإنجليزية فقط")
}

data class OcrResult(
    val pageTexts: List<String>,
    val fullText: String,
    val searchablePdfPath: String?
)

/**
 * 100% Offline Scanned-PDF OCR Engine powered by Tesseract 5 LSTM (tesseract4android).
 * Bundles ara.traineddata & eng.traineddata inside assets/tessdata with ZERO internet permission.
 * Programmed by Ibrahim Jaber 2026 | برمجة إبراهيم جابر 2026
 */
class OfflineOcrEngine(private val context: Context) {

    private val ocrRootDir: File get() = File(context.filesDir, "ocr").apply { mkdirs() }
    private val tessdataDir: File get() = File(ocrRootDir, "tessdata").apply { mkdirs() }

    /**
     * Copies bundled ara.traineddata & eng.traineddata from APK assets to local storage once.
     */
    fun ensureTessDataReady(): List<String> {
        val assetFiles = runCatching { context.assets.list("tessdata")?.toList() ?: emptyList() }
            .getOrDefault(emptyList())
        val available = mutableListOf<String>()
        for (name in assetFiles) {
            if (!name.endsWith(".traineddata")) continue
            val target = File(tessdataDir, name)
            if (!target.exists() || target.length() < 1024L) {
                context.assets.open("tessdata/$name").use { input ->
                    FileOutputStream(target).use { output -> input.copyTo(output) }
                }
            }
            available.add(name.removeSuffix(".traineddata"))
        }
        return available
    }

    /**
     * Runs offline Arabic/English OCR across all pages of a scanned PDF and optionally
     * produces a searchable PDF with an invisible embedded text layer.
     */
    suspend fun ocrScannedPdf(
        inputPdf: File,
        mode: OcrLanguageMode = OcrLanguageMode.ARABIC_AND_ENGLISH,
        createSearchablePdf: Boolean = true,
        outputPdfFile: File? = null,
        onPageProgress: ((current: Int, total: Int) -> Unit)? = null
    ): OcrResult = withContext(Dispatchers.IO) {
        val availableLangs = ensureTessDataReady()
        require(availableLangs.isNotEmpty()) {
            "Offline OCR traineddata not found in APK assets."
        }

        val requestedParts = mode.tessCode.split("+").filter { it in availableLangs }
        val effectiveLang = requestedParts.ifEmpty { listOf(availableLangs.first()) }.joinToString("+")

        val tess = TessBaseAPI()
        val initOk = tess.init(ocrRootDir.absolutePath, effectiveLang)
        require(initOk) { "Failed to initialize offline Tesseract OCR engine for '$effectiveLang'." }

        val extractedPages = mutableListOf<String>()
        var outPdfPath: String? = null

        try {
            ParcelFileDescriptor.open(inputPdf, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val total = renderer.pageCount
                    val searchableDoc = if (createSearchablePdf && outputPdfFile != null) PDDocument() else null

                    try {
                        for (i in 0 until total) {
                            onPageProgress?.invoke(i + 1, total)
                            renderer.openPage(i).use { rPage ->
                                val ptW = rPage.width.toFloat()
                                val ptH = rPage.height.toFloat()
                                val scale = 2.4f // ~173-220 DPI for crisp Arabic & English OCR
                                val bmpW = (ptW * scale).toInt().coerceIn(500, 2200)
                                val bmpH = (ptH * scale).toInt().coerceIn(500, 3000)

                                val bmp = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                                bmp.eraseColor(Color.WHITE)
                                rPage.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)

                                tess.setImage(bmp)
                                val rawText = tess.utF8Text ?: ""
                                val cleanText = ArabicTextProcessor.cleanExtractedText(rawText)
                                extractedPages.add(cleanText)

                                if (searchableDoc != null) {
                                    val newPage = PDPage(PDRectangle(ptW, ptH))
                                    searchableDoc.addPage(newPage)
                                    val pdImg = JPEGFactory.createFromImage(searchableDoc, bmp, 0.85f)
                                    PDPageContentStream(searchableDoc, newPage).use { cs ->
                                        cs.drawImage(pdImg, 0f, 0f, ptW, ptH)
                                        // Embed invisible ASCII/Latin searchable text layer behind image
                                        val asciiLines = cleanText.lines()
                                            .map { it.filter { ch -> ch.code in 32..126 }.trim() }
                                            .filter { it.isNotEmpty() }
                                        if (asciiLines.isNotEmpty()) {
                                            cs.beginText()
                                            cs.setRenderingMode(RenderingMode.NEITHER)
                                            cs.setFont(PDType1Font.HELVETICA, 10f)
                                            var y = ptH - 28f
                                            for (line in asciiLines.take(70)) {
                                                cs.newLineAtOffset(24f, y)
                                                cs.showText(line.take(110))
                                                cs.newLineAtOffset(-24f, -y)
                                                y -= 11f
                                                if (y < 16f) break
                                            }
                                            cs.endText()
                                        }
                                    }
                                }
                                bmp.recycle()
                            }
                        }

                        if (searchableDoc != null && outputPdfFile != null) {
                            outputPdfFile.parentFile?.mkdirs()
                            searchableDoc.documentInformation.apply {
                                title = "${inputPdf.nameWithoutExtension} (OCR)"
                                subject = "OCR ($effectiveLang): ${extractedPages.joinToString(" ").take(240)}"
                                creator = AppBranding.PDF_CREATOR_TAG
                            }
                            searchableDoc.save(outputPdfFile)
                            outPdfPath = outputPdfFile.absolutePath
                        }
                    } finally {
                        searchableDoc?.close()
                    }
                }
            }
        } finally {
            tess.recycle()
        }

        val combined = extractedPages.mapIndexed { idx, txt ->
            "--- Page ${idx + 1} ---\n$txt"
        }.joinToString("\n\n")

        OcrResult(
            pageTexts = extractedPages,
            fullText = combined,
            searchablePdfPath = outPdfPath
        )
    }

    /**
     * Fast single-bitmap offline OCR (Arabic + English) used by Live Camera Translate and CamScanner Pro.
     */
    suspend fun ocrBitmap(
        bitmap: Bitmap,
        mode: OcrLanguageMode = OcrLanguageMode.ARABIC_AND_ENGLISH
    ): String = withContext(Dispatchers.IO) {
        val availableLangs = ensureTessDataReady()
        if (availableLangs.isEmpty()) return@withContext ""

        val requestedParts = mode.tessCode.split("+").filter { it in availableLangs }
        val effectiveLang = requestedParts.ifEmpty { listOf(availableLangs.first()) }.joinToString("+")

        val argb = if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, false)
        val tess = TessBaseAPI()
        try {
            if (!tess.init(ocrRootDir.absolutePath, effectiveLang)) return@withContext ""
            tess.setImage(argb)
            ArabicTextProcessor.cleanExtractedText(tess.utF8Text ?: "")
        } finally {
            tess.recycle()
            if (argb !== bitmap && !argb.isRecycled) argb.recycle()
        }
    }
}
