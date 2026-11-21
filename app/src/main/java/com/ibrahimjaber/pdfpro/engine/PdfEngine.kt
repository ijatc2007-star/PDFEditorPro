package com.ibrahimjaber.pdfpro.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.exifinterface.media.ExifInterface
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.ibrahimjaber.pdfpro.data.AppBranding
import com.ibrahimjaber.pdfpro.data.CompressionLevel
import com.ibrahimjaber.pdfpro.data.CompressionResult
import com.ibrahimjaber.pdfpro.data.HeaderFooterConfig
import com.ibrahimjaber.pdfpro.data.PageNumberFormat
import com.ibrahimjaber.pdfpro.data.PageNumberPosition
import com.ibrahimjaber.pdfpro.data.PageOperationSpec
import com.ibrahimjaber.pdfpro.data.PageRangeParser
import com.ibrahimjaber.pdfpro.data.PageTemplate
import com.ibrahimjaber.pdfpro.data.PdfAnnotation
import com.ibrahimjaber.pdfpro.data.PdfDiffSummary
import com.ibrahimjaber.pdfpro.data.PdfMetadata
import com.ibrahimjaber.pdfpro.data.SearchMatch
import com.ibrahimjaber.pdfpro.data.ShapeKind
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * 100% Offline Pro PDF Engine powered by Apache PDFBox-Android & Android PdfRenderer.
 * Programmed by Ibrahim Jaber 2026.
 */
class PdfEngine(private val context: Context) {

    data class DocumentInspection(
        val pageCount: Int,
        val sizeBytes: Long,
        val isEncrypted: Boolean
    )

    suspend fun inspectDocument(file: File, password: String? = null): DocumentInspection =
        withContext(Dispatchers.IO) {
            if (!file.exists()) {
                return@withContext DocumentInspection(0, 0L, false)
            }
            try {
                PDDocument.load(file, password ?: "").use { doc ->
                    DocumentInspection(
                        pageCount = doc.numberOfPages,
                        sizeBytes = file.length(),
                        isEncrypted = doc.isEncrypted
                    )
                }
            } catch (_: InvalidPasswordException) {
                DocumentInspection(pageCount = 0, sizeBytes = file.length(), isEncrypted = true)
            } catch (_: Exception) {
                // Fallback to PdfRenderer for non-standard PDFs
                runCatching {
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                        PdfRenderer(pfd).use { renderer ->
                            DocumentInspection(renderer.pageCount, file.length(), false)
                        }
                    }
                }.getOrDefault(DocumentInspection(0, file.length(), false))
            }
        }

    /**
     * Permanently embeds user annotations (freehand pen, highlighter, shapes, arrows,
     * text boxes with full Unicode/Arabic support, stamps, sticky notes, and handwritten
     * signatures) into a PDF document offline.
     */
    suspend fun saveAnnotations(
        inputFile: File,
        outputFile: File,
        annotations: List<PdfAnnotation>,
        password: String? = null
    ): File = withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, "annot_save_${System.currentTimeMillis()}.pdf")
        PDDocument.load(inputFile, password ?: "").use { doc ->
            doc.isAllSecurityToBeRemoved = true
            val byPage = annotations.groupBy { it.pageIndex }

            for ((pageIndex, pageAnnotations) in byPage) {
                if (pageIndex !in 0 until doc.numberOfPages) continue
                val page = doc.getPage(pageIndex)
                val box = page.cropBox ?: page.mediaBox
                val pageW = box.width
                val pageH = box.height
                val ox = box.lowerLeftX
                val oy = box.lowerLeftY

                PDPageContentStream(
                    doc,
                    page,
                    PDPageContentStream.AppendMode.APPEND,
                    true,
                    true
                ).use { cs ->
                    for (ann in pageAnnotations) {
                        when (ann) {
                            is PdfAnnotation.Freehand -> {
                                if (ann.points.size < 2) continue
                                cs.saveGraphicsState()
                                if (ann.isHighlighter) {
                                    val gs = PDExtendedGraphicsState().apply {
                                        strokingAlphaConstant = 0.36f
                                        nonStrokingAlphaConstant = 0.36f
                                    }
                                    cs.setGraphicsStateParameters(gs)
                                }
                                cs.setStrokingColor(
                                    Color.red(ann.colorArgb),
                                    Color.green(ann.colorArgb),
                                    Color.blue(ann.colorArgb)
                                )
                                cs.setLineWidth((ann.strokeWidthNorm * pageW).coerceAtLeast(1f))
                                cs.setLineCapStyle(1) // Round cap
                                cs.setLineJoinStyle(1) // Round join

                                val first = ann.points.first()
                                cs.moveTo(ox + first.x * pageW, oy + (1f - first.y) * pageH)
                                for (i in 1 until ann.points.size) {
                                    val pt = ann.points[i]
                                    cs.lineTo(ox + pt.x * pageW, oy + (1f - pt.y) * pageH)
                                }
                                cs.stroke()
                                cs.restoreGraphicsState()
                            }

                            is PdfAnnotation.Shape -> {
                                cs.saveGraphicsState()
                                cs.setStrokingColor(
                                    Color.red(ann.colorArgb),
                                    Color.green(ann.colorArgb),
                                    Color.blue(ann.colorArgb)
                                )
                                cs.setLineWidth((ann.strokeWidthNorm * pageW).coerceAtLeast(1.5f))
                                cs.setLineCapStyle(1)
                                cs.setLineJoinStyle(1)

                                val x1 = ox + ann.startX * pageW
                                val y1 = oy + (1f - ann.startY) * pageH
                                val x2 = ox + ann.endX * pageW
                                val y2 = oy + (1f - ann.endY) * pageH

                                when (ann.kind) {
                                    ShapeKind.RECTANGLE -> {
                                        val left = min(x1, x2)
                                        val bottom = min(y1, y2)
                                        val w = max(4f, kotlin.math.abs(x2 - x1))
                                        val h = max(4f, kotlin.math.abs(y2 - y1))
                                        cs.addRect(left, bottom, w, h)
                                        cs.stroke()
                                    }

                                    ShapeKind.OVAL -> {
                                        val left = min(x1, x2)
                                        val bottom = min(y1, y2)
                                        val w = max(4f, kotlin.math.abs(x2 - x1))
                                        val h = max(4f, kotlin.math.abs(y2 - y1))
                                        drawOvalPath(cs, left, bottom, w, h)
                                        cs.stroke()
                                    }

                                    ShapeKind.LINE -> {
                                        cs.moveTo(x1, y1)
                                        cs.lineTo(x2, y2)
                                        cs.stroke()
                                    }

                                    ShapeKind.ARROW -> {
                                        cs.moveTo(x1, y1)
                                        cs.lineTo(x2, y2)
                                        val angle = atan2((y2 - y1).toDouble(), (x2 - x1).toDouble())
                                        val headLen = (pageW * 0.025f).coerceIn(8f, 18f)
                                        val wing = Math.toRadians(28.0)
                                        val ax1 = x2 - (headLen * cos(angle - wing)).toFloat()
                                        val ay1 = y2 - (headLen * sin(angle - wing)).toFloat()
                                        val ax2 = x2 - (headLen * cos(angle + wing)).toFloat()
                                        val ay2 = y2 - (headLen * sin(angle + wing)).toFloat()
                                        cs.moveTo(x2, y2)
                                        cs.lineTo(ax1, ay1)
                                        cs.moveTo(x2, y2)
                                        cs.lineTo(ax2, ay2)
                                        cs.stroke()
                                    }
                                }
                                cs.restoreGraphicsState()
                            }

                            is PdfAnnotation.MaskRect -> {
                                cs.saveGraphicsState()
                                cs.setNonStrokingColor(
                                    Color.red(ann.fillColorArgb),
                                    Color.green(ann.fillColorArgb),
                                    Color.blue(ann.fillColorArgb)
                                )
                                val x1 = ox + ann.startX * pageW
                                val y1 = oy + (1f - ann.startY) * pageH
                                val x2 = ox + ann.endX * pageW
                                val y2 = oy + (1f - ann.endY) * pageH
                                val left = min(x1, x2)
                                val bottom = min(y1, y2)
                                val w = max(6f, kotlin.math.abs(x2 - x1))
                                val h = max(6f, kotlin.math.abs(y2 - y1))
                                cs.addRect(left, bottom, w, h)
                                cs.fill()
                                cs.restoreGraphicsState()
                            }

                            is PdfAnnotation.TextBox -> {
                                val bmp = renderTextBitmap(
                                    text = ann.text,
                                    textSizePx = ann.fontSizePt * 3f,
                                    colorArgb = ann.colorArgb,
                                    isBold = ann.isBold,
                                    bgColorArgb = Color.TRANSPARENT,
                                    borderColorArgb = null
                                )
                                val pdfImg = LosslessFactory.createFromImage(doc, bmp)
                                val drawW = bmp.width / 3f
                                val drawH = bmp.height / 3f
                                val px = (ox + ann.x * pageW).coerceIn(ox, ox + pageW - drawW.coerceAtMost(pageW))
                                val py = (oy + (1f - ann.y) * pageH - drawH).coerceIn(oy, oy + pageH - drawH.coerceAtMost(pageH))
                                cs.drawImage(pdfImg, px, py, drawW.coerceAtMost(pageW), drawH.coerceAtMost(pageH))
                                bmp.recycle()
                            }

                            is PdfAnnotation.Stamp -> {
                                val bmp = renderStampBitmap(ann.label, ann.colorArgb)
                                val pdfImg = LosslessFactory.createFromImage(doc, bmp)
                                val drawW = (pageW * 0.30f).coerceIn(110f, 200f)
                                val drawH = drawW * (bmp.height.toFloat() / bmp.width.toFloat())
                                val px = (ox + ann.x * pageW - drawW / 2f).coerceIn(ox, ox + pageW - drawW)
                                val py = (oy + (1f - ann.y) * pageH - drawH / 2f).coerceIn(oy, oy + pageH - drawH)
                                cs.drawImage(pdfImg, px, py, drawW, drawH)
                                bmp.recycle()
                            }

                            is PdfAnnotation.StickyNote -> {
                                val bmp = renderTextBitmap(
                                    text = ann.text,
                                    textSizePx = 36f,
                                    colorArgb = Color.rgb(30, 41, 59),
                                    isBold = false,
                                    bgColorArgb = ann.colorArgb,
                                    borderColorArgb = Color.rgb(217, 119, 6),
                                    maxWidthPx = 540
                                )
                                val pdfImg = LosslessFactory.createFromImage(doc, bmp)
                                val drawW = (bmp.width / 3f).coerceAtMost(pageW * 0.45f)
                                val drawH = drawW * (bmp.height.toFloat() / bmp.width.toFloat())
                                val px = (ox + ann.x * pageW).coerceIn(ox, ox + pageW - drawW)
                                val py = (oy + (1f - ann.y) * pageH - drawH).coerceIn(oy, oy + pageH - drawH)
                                cs.drawImage(pdfImg, px, py, drawW, drawH)
                                bmp.recycle()
                            }

                            is PdfAnnotation.Signature -> {
                                val sigFile = File(ann.imagePath)
                                if (sigFile.exists()) {
                                    val bmp = BitmapFactory.decodeFile(sigFile.absolutePath)
                                    if (bmp != null) {
                                        val pdfImg = LosslessFactory.createFromImage(doc, bmp)
                                        val drawW = ann.widthNorm * pageW
                                        val drawH = ann.heightNorm * pageH
                                        val px = (ox + ann.x * pageW - drawW / 2f).coerceIn(ox, ox + pageW - drawW)
                                        val py = (oy + (1f - ann.y) * pageH - drawH / 2f).coerceIn(oy, oy + pageH - drawH)
                                        cs.drawImage(pdfImg, px, py, drawW, drawH)
                                        bmp.recycle()
                                    }
                                }
                            }
                        }
                    }
                }
            }

            doc.documentInformation.creator = AppBranding.PDF_CREATOR_TAG
            doc.save(tempFile)
        }

        outputFile.parentFile?.mkdirs()
        tempFile.copyTo(outputFile, overwrite = true)
        tempFile.delete()
        outputFile
    }

    /**
     * Merges multiple PDF files in order into a single output PDF offline.
     */
    suspend fun mergePdfs(inputFiles: List<File>, outputFile: File): File =
        withContext(Dispatchers.IO) {
            require(inputFiles.size >= 2) { "Select at least 2 PDF files to merge." }
            outputFile.parentFile?.mkdirs()
            val merger = PDFMergerUtility()
            for (file in inputFiles) {
                merger.addSource(file)
            }
            merger.destinationFileName = outputFile.absolutePath
            merger.mergeDocuments(MemoryUsageSetting.setupTempFileOnly())
            outputFile
        }

    /**
     * Splits a PDF either by custom 1-based range expression (e.g., "1-3, 5")
     * or into individual single-page PDFs.
     */
    suspend fun splitPdf(
        inputFile: File,
        outputDir: File,
        baseName: String,
        rangeExpression: String,
        splitEveryPage: Boolean,
        password: String? = null
    ): List<File> = withContext(Dispatchers.IO) {
        outputDir.mkdirs()
        val outputs = mutableListOf<File>()
        PDDocument.load(inputFile, password ?: "").use { sourceDoc ->
            val total = sourceDoc.numberOfPages
            require(total > 0) { "Source PDF has no pages." }

            if (splitEveryPage) {
                for (i in 0 until total) {
                    PDDocument().use { singleDoc ->
                        singleDoc.importPage(sourceDoc.getPage(i))
                        singleDoc.documentInformation.creator = AppBranding.PDF_CREATOR_TAG
                        val outFile = File(outputDir, "${baseName}_page_${i + 1}.pdf")
                        singleDoc.save(outFile)
                        outputs.add(outFile)
                    }
                }
            } else {
                val indices = PageRangeParser.parseToZeroBasedIndices(rangeExpression, total)
                PDDocument().use { extractedDoc ->
                    for (idx in indices) {
                        extractedDoc.importPage(sourceDoc.getPage(idx))
                    }
                    extractedDoc.documentInformation.creator = AppBranding.PDF_CREATOR_TAG
                    val outFile = File(outputDir, "${baseName}_split.pdf")
                    extractedDoc.save(outFile)
                    outputs.add(outFile)
                }
            }
        }
        outputs
    }

    /**
     * Reorders, rotates, duplicates, deletes, and inserts blank A4 pages in a PDF.
     */
    suspend fun organizePages(
        inputFile: File,
        outputFile: File,
        specs: List<PageOperationSpec>,
        password: String? = null
    ): File = withContext(Dispatchers.IO) {
        require(specs.isNotEmpty()) { "A PDF must keep at least 1 page." }
        val tempFile = File(context.cacheDir, "organize_${System.currentTimeMillis()}.pdf")
        PDDocument.load(inputFile, password ?: "").use { sourceDoc ->
            PDDocument().use { outDoc ->
                for (spec in specs) {
                    if (spec.sourcePageIndex == null) {
                        outDoc.addPage(PDPage(PDRectangle.A4))
                    } else if (spec.sourcePageIndex in 0 until sourceDoc.numberOfPages) {
                        val imported = outDoc.importPage(sourceDoc.getPage(spec.sourcePageIndex))
                        val newRot = ((imported.rotation + spec.extraRotationDeg) % 360 + 360) % 360
                        imported.rotation = newRot
                    }
                }
                outDoc.documentInformation.creator = AppBranding.PDF_CREATOR_TAG
                outDoc.save(tempFile)
            }
        }
        outputFile.parentFile?.mkdirs()
        tempFile.copyTo(outputFile, overwrite = true)
        tempFile.delete()
        outputFile
    }

    /**
     * Converts multiple image files into a crisp, auto-oriented A4 PDF offline.
     */
    suspend fun imagesToPdf(
        imageFiles: List<File>,
        outputFile: File,
        marginPt: Float = 24f,
        jpegQuality: Float = 0.88f
    ): File = withContext(Dispatchers.IO) {
        require(imageFiles.isNotEmpty()) { "Select at least one image." }
        outputFile.parentFile?.mkdirs()
        PDDocument().use { doc ->
            for (imgFile in imageFiles) {
                val bitmap = decodeOrientedBitmap(imgFile, maxDim = 2200) ?: continue
                val isLandscape = bitmap.width > bitmap.height
                val pageRect = if (isLandscape) {
                    PDRectangle(PDRectangle.A4.height, PDRectangle.A4.width)
                } else {
                    PDRectangle.A4
                }
                val page = PDPage(pageRect)
                doc.addPage(page)

                val pdImage = JPEGFactory.createFromImage(doc, bitmap, jpegQuality)
                val availW = pageRect.width - (marginPt * 2f)
                val availH = pageRect.height - (marginPt * 2f)
                val scale = min(availW / bitmap.width.toFloat(), availH / bitmap.height.toFloat())
                val drawW = bitmap.width * scale
                val drawH = bitmap.height * scale
                val drawX = (pageRect.width - drawW) / 2f
                val drawY = (pageRect.height - drawH) / 2f

                PDPageContentStream(doc, page).use { cs ->
                    cs.drawImage(pdImage, drawX, drawY, drawW, drawH)
                }
                bitmap.recycle()
            }
            doc.documentInformation.creator = AppBranding.PDF_CREATOR_TAG
            doc.save(outputFile)
        }
        outputFile
    }

    /**
     * Exports selected (or all) pages of a PDF to high-resolution PNG or JPEG images offline.
     */
    suspend fun pdfToImages(
        inputFile: File,
        outputDir: File,
        baseName: String,
        pageIndices: List<Int>? = null,
        asPng: Boolean = true,
        scaleFactor: Float = 2.0f,
        password: String? = null
    ): List<File> = withContext(Dispatchers.IO) {
        outputDir.mkdirs()
        val renderFile = ensureUnlockedTempCopy(inputFile, password)
        val created = mutableListOf<File>()
        try {
            ParcelFileDescriptor.open(renderFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val targets = pageIndices?.filter { it in 0 until renderer.pageCount }
                        ?: (0 until renderer.pageCount).toList()

                    for (idx in targets) {
                        renderer.openPage(idx).use { page ->
                            val w = (page.width * scaleFactor).toInt().coerceIn(300, 2600)
                            val h = (page.height * scaleFactor).toInt().coerceIn(300, 3600)
                            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)

                            val ext = if (asPng) "png" else "jpg"
                            val outImg = File(outputDir, "${baseName}_page_${idx + 1}.$ext")
                            FileOutputStream(outImg).use { fos ->
                                if (asPng) {
                                    bmp.compress(Bitmap.CompressFormat.PNG, 100, fos)
                                } else {
                                    bmp.compress(Bitmap.CompressFormat.JPEG, 92, fos)
                                }
                            }
                            bmp.recycle()
                            created.add(outImg)
                        }
                    }
                }
            }
        } finally {
            if (renderFile != inputFile) renderFile.delete()
        }
        created
    }

    /**
     * Compresses / optimizes a PDF offline at selectable DPI & JPEG quality.
     */
    suspend fun compressPdf(
        inputFile: File,
        outputFile: File,
        level: CompressionLevel,
        password: String? = null
    ): CompressionResult = withContext(Dispatchers.IO) {
        val originalBytes = inputFile.length()
        val renderFile = ensureUnlockedTempCopy(inputFile, password)
        outputFile.parentFile?.mkdirs()

        try {
            ParcelFileDescriptor.open(renderFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    PDDocument().use { outDoc ->
                        val dpiScale = level.dpi / 72f
                        for (i in 0 until renderer.pageCount) {
                            renderer.openPage(i).use { rPage ->
                                val ptW = rPage.width.toFloat()
                                val ptH = rPage.height.toFloat()
                                val bmpW = (ptW * dpiScale).toInt().coerceIn(240, 2000)
                                val bmpH = (ptH * dpiScale).toInt().coerceIn(240, 2600)
                                val bmp = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.RGB_565)
                                bmp.eraseColor(Color.WHITE)
                                rPage.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                                val outPage = PDPage(PDRectangle(ptW, ptH))
                                outDoc.addPage(outPage)
                                val pdImg = JPEGFactory.createFromImage(outDoc, bmp, level.jpegQuality)
                                PDPageContentStream(outDoc, outPage).use { cs ->
                                    cs.drawImage(pdImg, 0f, 0f, ptW, ptH)
                                }
                                bmp.recycle()
                            }
                        }
                        outDoc.documentInformation.creator = AppBranding.PDF_CREATOR_TAG
                        outDoc.save(outputFile)
                    }
                }
            }
        } finally {
            if (renderFile != inputFile) renderFile.delete()
        }

        CompressionResult(
            outputPath = outputFile.absolutePath,
            originalBytes = originalBytes,
            compressedBytes = outputFile.length()
        )
    }

    /**
     * Encrypts a PDF with AES-128 password protection offline.
     */
    suspend fun protectPdf(
        inputFile: File,
        outputFile: File,
        userPassword: String,
        ownerPassword: String = userPassword,
        allowPrinting: Boolean = true,
        allowCopying: Boolean = false,
        currentPassword: String? = null
    ): File = withContext(Dispatchers.IO) {
        require(userPassword.isNotBlank()) { "Password cannot be empty." }
        outputFile.parentFile?.mkdirs()
        PDDocument.load(inputFile, currentPassword ?: "").use { doc ->
            val ap = AccessPermission().apply {
                setCanPrint(allowPrinting)
                setCanExtractContent(allowCopying)
                setCanModify(false)
            }
            val policy = StandardProtectionPolicy(ownerPassword, userPassword, ap).apply {
                encryptionKeyLength = 128
                permissions = ap
            }
            doc.protect(policy)
            doc.save(outputFile)
        }
        outputFile
    }

    /**
     * Removes password encryption from a PDF (requires valid current password).
     */
    suspend fun unlockPdf(
        inputFile: File,
        outputFile: File,
        password: String
    ): File = withContext(Dispatchers.IO) {
        outputFile.parentFile?.mkdirs()
        PDDocument.load(inputFile, password).use { doc ->
            doc.isAllSecurityToBeRemoved = true
            doc.save(outputFile)
        }
        outputFile
    }

    /**
     * Adds a diagonal or tiled text watermark (supports English & Arabic) across all pages.
     */
    suspend fun addWatermark(
        inputFile: File,
        outputFile: File,
        watermarkText: String,
        opacity: Float = 0.28f,
        fontSizePt: Float = 44f,
        rotationDeg: Float = -35f,
        colorArgb: Int = Color.rgb(185, 28, 28),
        tiled: Boolean = false,
        password: String? = null
    ): File = withContext(Dispatchers.IO) {
        require(watermarkText.isNotBlank()) { "Watermark text cannot be empty." }
        outputFile.parentFile?.mkdirs()
        val wmBitmap = renderWatermarkBitmap(watermarkText, fontSizePt * 2.5f, colorArgb, rotationDeg, opacity)

        PDDocument.load(inputFile, password ?: "").use { doc ->
            doc.isAllSecurityToBeRemoved = true
            val pdImg = LosslessFactory.createFromImage(doc, wmBitmap)
            for (i in 0 until doc.numberOfPages) {
                val page = doc.getPage(i)
                val box = page.cropBox ?: page.mediaBox
                val pageW = box.width
                val pageH = box.height

                PDPageContentStream(
                    doc,
                    page,
                    PDPageContentStream.AppendMode.APPEND,
                    true,
                    true
                ).use { cs ->
                    if (!tiled) {
                        val drawW = pageW * 0.78f
                        val drawH = drawW * (wmBitmap.height.toFloat() / wmBitmap.width.toFloat())
                        val x = box.lowerLeftX + (pageW - drawW) / 2f
                        val y = box.lowerLeftY + (pageH - drawH) / 2f
                        cs.drawImage(pdImg, x, y, drawW, drawH)
                    } else {
                        val drawW = pageW * 0.42f
                        val drawH = drawW * (wmBitmap.height.toFloat() / wmBitmap.width.toFloat())
                        for (row in 0..2) {
                            for (col in 0..1) {
                                val x = box.lowerLeftX + pageW * (0.06f + col * 0.48f)
                                val y = box.lowerLeftY + pageH * (0.12f + row * 0.30f)
                                cs.drawImage(pdImg, x, y, drawW, drawH)
                            }
                        }
                    }
                }
            }
            doc.documentInformation.creator = AppBranding.PDF_CREATOR_TAG
            doc.save(outputFile)
        }
        wmBitmap.recycle()
        outputFile
    }

    /**
     * Stamps formatted page numbers across all pages of a PDF.
     */
    suspend fun addPageNumbers(
        inputFile: File,
        outputFile: File,
        position: PageNumberPosition = PageNumberPosition.BOTTOM_CENTER,
        format: PageNumberFormat = PageNumberFormat.PAGE_X_OF_Y,
        fontSizePt: Float = 11f,
        startNumber: Int = 1,
        password: String? = null
    ): File = withContext(Dispatchers.IO) {
        outputFile.parentFile?.mkdirs()
        PDDocument.load(inputFile, password ?: "").use { doc ->
            doc.isAllSecurityToBeRemoved = true
            val total = doc.numberOfPages
            val font = PDType1Font.HELVETICA
            for (i in 0 until total) {
                val page = doc.getPage(i)
                val box = page.cropBox ?: page.mediaBox
                val label = PageRangeParser.formatPageNumber(startNumber + i, startNumber + total - 1, format)
                val textWidth = font.getStringWidth(label) / 1000f * fontSizePt

                val x = when (position) {
                    PageNumberPosition.BOTTOM_CENTER -> box.lowerLeftX + (box.width - textWidth) / 2f
                    PageNumberPosition.BOTTOM_RIGHT -> box.lowerLeftX + box.width - textWidth - 32f
                    PageNumberPosition.TOP_RIGHT -> box.lowerLeftX + box.width - textWidth - 32f
                }
                val y = when (position) {
                    PageNumberPosition.BOTTOM_CENTER, PageNumberPosition.BOTTOM_RIGHT -> box.lowerLeftY + 22f
                    PageNumberPosition.TOP_RIGHT -> box.lowerLeftY + box.height - 28f
                }

                PDPageContentStream(
                    doc,
                    page,
                    PDPageContentStream.AppendMode.APPEND,
                    true,
                    true
                ).use { cs ->
                    cs.beginText()
                    cs.setFont(font, fontSizePt)
                    cs.setNonStrokingColor(71, 85, 105)
                    cs.newLineAtOffset(x, y)
                    cs.showText(label)
                    cs.endText()
                }
            }
            doc.documentInformation.creator = AppBranding.PDF_CREATOR_TAG
            doc.save(outputFile)
        }
        outputFile
    }

    /**
     * Extracts text from a single page (0-based) or the entire PDF offline.
     */
    suspend fun extractText(
        inputFile: File,
        pageIndex: Int? = null,
        password: String? = null
    ): String = withContext(Dispatchers.IO) {
        PDDocument.load(inputFile, password ?: "").use { doc ->
            val stripper = PDFTextStripper()
            if (pageIndex != null && pageIndex in 0 until doc.numberOfPages) {
                stripper.startPage = pageIndex + 1
                stripper.endPage = pageIndex + 1
            }
            stripper.getText(doc).trim()
        }
    }

    /**
     * Searches for a keyword across all pages of a PDF offline and returns page hits & snippets.
     */
    suspend fun searchText(
        inputFile: File,
        query: String,
        password: String? = null
    ): List<SearchMatch> = withContext(Dispatchers.IO) {
        val needle = query.trim()
        if (needle.isEmpty()) return@withContext emptyList()
        val matches = mutableListOf<SearchMatch>()
        PDDocument.load(inputFile, password ?: "").use { doc ->
            val stripper = PDFTextStripper()
            for (i in 0 until doc.numberOfPages) {
                stripper.startPage = i + 1
                stripper.endPage = i + 1
                val pageText = stripper.getText(doc)
                val lower = pageText.lowercase()
                val lowerNeedle = needle.lowercase()
                var count = 0
                var pos = lower.indexOf(lowerNeedle)
                var firstPos = -1
                while (pos >= 0) {
                    if (firstPos == -1) firstPos = pos
                    count++
                    pos = lower.indexOf(lowerNeedle, pos + lowerNeedle.length)
                }
                if (count > 0 && firstPos >= 0) {
                    val start = (firstPos - 35).coerceAtLeast(0)
                    val end = (firstPos + needle.length + 55).coerceAtMost(pageText.length)
                    val snippet = pageText.substring(start, end)
                        .replace(Regex("\\s+"), " ")
                        .trim()
                    matches.add(SearchMatch(pageIndex = i, snippet = "...$snippet...", matchCount = count))
                }
            }
        }
        matches
    }

    suspend fun readMetadata(inputFile: File, password: String? = null): PdfMetadata =
        withContext(Dispatchers.IO) {
            PDDocument.load(inputFile, password ?: "").use { doc ->
                val info = doc.documentInformation
                PdfMetadata(
                    title = info.title ?: "",
                    author = info.author ?: "",
                    subject = info.subject ?: "",
                    keywords = info.keywords ?: "",
                    creator = info.creator ?: AppBranding.PDF_CREATOR_TAG,
                    producer = info.producer ?: AppBranding.APP_NAME,
                    pageCount = doc.numberOfPages,
                    fileSizeBytes = inputFile.length(),
                    isEncrypted = doc.isEncrypted
                )
            }
        }

    suspend fun updateMetadata(
        inputFile: File,
        outputFile: File,
        metadata: PdfMetadata,
        password: String? = null
    ): File = withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, "meta_${System.currentTimeMillis()}.pdf")
        PDDocument.load(inputFile, password ?: "").use { doc ->
            doc.isAllSecurityToBeRemoved = true
            doc.documentInformation.apply {
                title = metadata.title
                author = metadata.author
                subject = metadata.subject
                keywords = metadata.keywords
                creator = metadata.creator.ifBlank { AppBranding.PDF_CREATOR_TAG }
                producer = AppBranding.APP_NAME
            }
            doc.save(tempFile)
        }
        outputFile.parentFile?.mkdirs()
        tempFile.copyTo(outputFile, overwrite = true)
        tempFile.delete()
        outputFile
    }

    /**
     * Creates a brand-new offline PDF from scratch with optional title, body text (English/Arabic),
     * and page template (Blank, Lined, or Engineering Grid).
     */
    suspend fun createNewPdf(
        outputFile: File,
        title: String,
        bodyText: String,
        pageCount: Int = 1,
        template: PageTemplate = PageTemplate.BLANK
    ): File = withContext(Dispatchers.IO) {
        outputFile.parentFile?.mkdirs()
        val safePages = pageCount.coerceIn(1, 50)
        PDDocument().use { doc ->
            for (p in 0 until safePages) {
                val page = PDPage(PDRectangle.A4)
                doc.addPage(page)
                val box = page.mediaBox
                PDPageContentStream(doc, page).use { cs ->
                    drawPageTemplate(cs, box, template)
                    if (p == 0 && (title.isNotBlank() || bodyText.isNotBlank())) {
                        val content = buildString {
                            if (title.isNotBlank()) {
                                append(title.trim())
                                append("\n\n")
                            }
                            append(bodyText.trim())
                        }
                        val bmp = renderDocumentPageBitmap(title.trim(), bodyText.trim())
                        val img = LosslessFactory.createFromImage(doc, bmp)
                        val margin = 42f
                        val drawW = box.width - margin * 2f
                        val drawH = drawW * (bmp.height.toFloat() / bmp.width.toFloat())
                        val clampedH = drawH.coerceAtMost(box.height - margin * 2f)
                        cs.drawImage(img, margin, box.height - margin - clampedH, drawW, clampedH)
                        bmp.recycle()
                    }
                }
            }
            doc.documentInformation.apply {
                this.title = title.ifBlank { "Untitled Document" }
                this.author = AppBranding.DEVELOPER_NAME
                this.creator = AppBranding.PDF_CREATOR_TAG
                this.producer = AppBranding.APP_NAME
            }
            doc.save(outputFile)
        }
        outputFile
    }

    /**
     * Generates an offline QR Code PNG image file using ZXing for stamping onto any PDF page.
     */
    suspend fun generateQrCodeImageFile(content: String, sizePx: Int = 420): File =
        withContext(Dispatchers.IO) {
            val safe = content.ifBlank { AppBranding.PDF_CREATOR_TAG }
            val matrix = QRCodeWriter().encode(safe, BarcodeFormat.QR_CODE, sizePx, sizePx)
            val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            for (x in 0 until sizePx) {
                for (y in 0 until sizePx) {
                    bmp.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            val outFile = File(FileHelper.signaturesDir(context), "qr_${System.currentTimeMillis()}.png")
            FileOutputStream(outFile).use { fos ->
                bmp.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
            bmp.recycle()
            outFile
        }

    /**
     * Stamps corporate headers, footers, and legal Bates numbering across every page offline.
     */
    suspend fun addHeaderFooterAndBates(
        inputFile: File,
        outputFile: File,
        config: HeaderFooterConfig,
        password: String? = null
    ): File = withContext(Dispatchers.IO) {
        outputFile.parentFile?.mkdirs()
        PDDocument.load(inputFile, password ?: "").use { doc ->
            doc.isAllSecurityToBeRemoved = true
            val font = PDType1Font.HELVETICA
            val fontSize = 9.5f
            val total = doc.numberOfPages

            for (i in 0 until total) {
                val page = doc.getPage(i)
                val box = page.cropBox ?: page.mediaBox
                val ox = box.lowerLeftX
                val oy = box.lowerLeftY
                val w = box.width
                val h = box.height

                val batesText = if (config.batesPrefix.isNotBlank()) {
                    val numStr = (config.batesStart + i).toString().padStart(config.batesDigits.coerceIn(3, 10), '0')
                    "${config.batesPrefix}$numStr"
                } else ""

                PDPageContentStream(
                    doc,
                    page,
                    PDPageContentStream.AppendMode.APPEND,
                    true,
                    true
                ).use { cs ->
                    fun drawStamp(text: String, x: Float, y: Float) {
                        if (text.isBlank()) return
                        val safe = text.replace("•", "-").replace("©", "(c)")
                        if (ConverterCodec.containsArabic(safe)) {
                            val bmp = renderTextBitmap(
                                text = safe,
                                textSizePx = 28f,
                                colorArgb = Color.rgb(71, 85, 105),
                                isBold = false,
                                bgColorArgb = Color.TRANSPARENT,
                                borderColorArgb = null
                            )
                            val img = LosslessFactory.createFromImage(doc, bmp)
                            val dw = (bmp.width / 3f).coerceAtMost(w * 0.45f)
                            val dh = (bmp.height / 3f).coerceAtMost(24f)
                            cs.drawImage(img, x.coerceIn(ox + 12f, ox + w - dw - 12f), y, dw, dh)
                            bmp.recycle()
                        } else {
                            cs.beginText()
                            cs.setFont(font, fontSize)
                            cs.setNonStrokingColor(71, 85, 105)
                            cs.newLineAtOffset(x.coerceIn(ox + 18f, ox + w - 40f), y)
                            cs.showText(safe)
                            cs.endText()
                        }
                    }

                    val topY = oy + h - 24f
                    val botY = oy + 18f
                    drawStamp(config.headerLeft, ox + 28f, topY)
                    drawStamp(config.headerCenter, ox + w * 0.36f, topY)
                    drawStamp(config.headerRight, ox + w - 150f, topY)
                    drawStamp(config.footerLeft, ox + 28f, botY)
                    if (batesText.isNotBlank()) {
                        drawStamp(batesText, ox + w - 140f, botY)
                    }
                }
            }
            doc.documentInformation.creator = AppBranding.PDF_CREATOR_TAG
            doc.save(outputFile)
        }
        outputFile
    }

    /**
     * Converts a color PDF to crisp Black & White / Grayscale (saves ink & reduces size).
     */
    suspend fun convertToGrayscalePdf(
        inputFile: File,
        outputFile: File,
        dpi: Int = 140,
        password: String? = null
    ): CompressionResult = withContext(Dispatchers.IO) {
        val originalBytes = inputFile.length()
        val renderFile = ensureUnlockedTempCopy(inputFile, password)
        outputFile.parentFile?.mkdirs()

        val grayPaint = Paint().apply {
            val cm = ColorMatrix().apply { setSaturation(0f) }
            colorFilter = ColorMatrixColorFilter(cm)
        }

        try {
            ParcelFileDescriptor.open(renderFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    PDDocument().use { outDoc ->
                        val scale = dpi / 72f
                        for (i in 0 until renderer.pageCount) {
                            renderer.openPage(i).use { rPage ->
                                val ptW = rPage.width.toFloat()
                                val ptH = rPage.height.toFloat()
                                val bmpW = (ptW * scale).toInt().coerceIn(280, 2000)
                                val bmpH = (ptH * scale).toInt().coerceIn(280, 2600)
                                val colorBmp = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                                colorBmp.eraseColor(Color.WHITE)
                                rPage.render(colorBmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)

                                val grayBmp = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                                Canvas(grayBmp).drawBitmap(colorBmp, 0f, 0f, grayPaint)
                                colorBmp.recycle()

                                val outPage = PDPage(PDRectangle(ptW, ptH))
                                outDoc.addPage(outPage)
                                val pdImg = JPEGFactory.createFromImage(outDoc, grayBmp, 0.78f)
                                PDPageContentStream(outDoc, outPage).use { cs ->
                                    cs.drawImage(pdImg, 0f, 0f, ptW, ptH)
                                }
                                grayBmp.recycle()
                            }
                        }
                        outDoc.documentInformation.creator = AppBranding.PDF_CREATOR_TAG
                        outDoc.save(outputFile)
                    }
                }
            }
        } finally {
            if (renderFile != inputFile) renderFile.delete()
        }

        CompressionResult(outputFile.absolutePath, originalBytes, outputFile.length())
    }

    /**
     * Compares two PDF files page-by-page offline and returns a detailed diff summary.
     */
    suspend fun comparePdfs(fileA: File, fileB: File): PdfDiffSummary = withContext(Dispatchers.IO) {
        val pagesA = mutableListOf<String>()
        val pagesB = mutableListOf<String>()

        PDDocument.load(fileA).use { docA ->
            val stripper = PDFTextStripper()
            for (i in 1..docA.numberOfPages) {
                stripper.startPage = i; stripper.endPage = i
                pagesA.add(stripper.getText(docA).trim())
            }
        }
        PDDocument.load(fileB).use { docB ->
            val stripper = PDFTextStripper()
            for (i in 1..docB.numberOfPages) {
                stripper.startPage = i; stripper.endPage = i
                pagesB.add(stripper.getText(docB).trim())
            }
        }

        val maxPages = max(pagesA.size, pagesB.size)
        var identical = 0
        val changed = mutableListOf<Int>()
        val sb = StringBuilder()
        sb.append("PDF Comparison Report — ${AppBranding.CREDIT_LINE}\n")
        sb.append("Document A: ${fileA.name} (${pagesA.size} pages)\n")
        sb.append("Document B: ${fileB.name} (${pagesB.size} pages)\n")
        sb.append("--------------------------------------------------\n")

        for (i in 0 until maxPages) {
            val a = pagesA.getOrNull(i)
            val b = pagesB.getOrNull(i)
            if (a == b) {
                identical++
            } else {
                changed.add(i + 1)
                sb.append("\n[Page ${i + 1} Differs]\n")
                if (a == null) {
                    sb.append("  + Added page in Document B (${b?.length ?: 0} chars)\n")
                } else if (b == null) {
                    sb.append("  - Removed page from Document A (${a.length} chars)\n")
                } else {
                    val linesA = a.lines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                    val linesB = b.lines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                    val removed = (linesA - linesB).take(4)
                    val added = (linesB - linesA).take(4)
                    removed.forEach { sb.append("  - A: ${it.take(90)}\n") }
                    added.forEach { sb.append("  + B: ${it.take(90)}\n") }
                }
            }
        }

        if (changed.isEmpty()) {
            sb.append("\nAll $maxPages page(s) have identical text content.\n")
        }

        PdfDiffSummary(
            fileAPages = pagesA.size,
            fileBPages = pagesB.size,
            identicalPages = identical,
            changedPages = changed,
            reportText = sb.toString()
        )
    }

    /**
     * Generates a polished 3-page sample PDF on first launch so the user can test
     * every viewer, annotator, search, TTS, and organizer feature immediately offline.
     */
    suspend fun createSampleWelcomePdf(outputFile: File): File = withContext(Dispatchers.IO) {
        outputFile.parentFile?.mkdirs()
        PDDocument().use { doc ->
            // Page 1: Cover & Overview
            addStyledSamplePage(
                doc = doc,
                heading = "Welcome to PDF Pro Studio",
                subtitle = "100% Offline PDF Viewer & Pro Editor  |  Programmed by Ibrahim Jaber 2026",
                bullets = listOf(
                    "Full Offline Privacy: Zero internet permission required. Your documents stay on your device.",
                    "Pro Annotation Studio: Freehand Pen, Highlighter, Text Boxes, Shapes, Arrows, Stamps, Sticky Notes & Handwritten Signatures.",
                    "12 Offline Pro Tools: Merge, Split, Organize Pages, Images to PDF, PDF to Images, Compress, Protect (AES-128), Unlock, Watermark, Page Numbers, Extract Text & Metadata Editor.",
                    "Comfortable Reading: Pinch-to-Zoom, Day / Sepia / Night Modes, Bookmarks, Keyword Search & Offline Read-Aloud (TTS)."
                ),
                footer = "Page 1 of 3  •  PDF Pro Studio  •  Programmed by Ibrahim Jaber 2026"
            )

            // Page 2: Interactive Editor Playground
            addStyledSamplePage(
                doc = doc,
                heading = "Try the Pro Editor Tools Here",
                subtitle = "Tap the Edit icon in the top toolbar to unlock drawing, signing & annotations",
                bullets = listOf(
                    "1. Select the Pen or Highlighter tool and draw directly over this page.",
                    "2. Tap the Text tool and tap anywhere below to insert custom notes (English or Arabic).",
                    "3. Use the Stamp tool to place APPROVED, CONFIDENTIAL, or IBRAHIM JABER 2026 stamps.",
                    "4. Open the Signature Pad, draw your handwritten signature, and place it in the box below.",
                    "5. Tap Save to permanently embed all edits directly into the PDF file."
                ),
                footer = "Page 2 of 3  •  Interactive Sandbox  •  Programmed by Ibrahim Jaber 2026",
                includeSignatureBox = true
            )

            // Page 3: Pro Toolkit Quick Guide
            addStyledSamplePage(
                doc = doc,
                heading = "Complete Offline Pro Toolkit",
                subtitle = "Switch to the Pro Tools tab from the home screen to process any PDF",
                bullets = listOf(
                    "Merge & Split: Combine multiple reports or extract exact page ranges like 1-2, 3.",
                    "Page Organizer: Visually reorder, rotate 90°, duplicate, delete, or insert blank pages.",
                    "Compress PDF: Reduce file size offline with Light, Balanced, or Maximum compression.",
                    "Security: Lock sensitive PDFs with AES-128 passwords or unlock protected PDFs.",
                    "Export & Share: Convert PDF pages to crisp PNG/JPEG images, print, or share securely."
                ),
                footer = "Page 3 of 3  •  © 2026 Ibrahim Jaber. All Rights Reserved."
            )

            doc.documentInformation.apply {
                title = "PDF Pro Studio — Welcome Guide"
                author = AppBranding.DEVELOPER_NAME
                subject = "Offline PDF Viewer & Editor Guide"
                keywords = "PDF, Offline, Editor, Ibrahim Jaber, 2026"
                creator = AppBranding.PDF_CREATOR_TAG
                producer = AppBranding.APP_NAME
            }
            doc.save(outputFile)
        }
        outputFile
    }

    private fun addStyledSamplePage(
        doc: PDDocument,
        heading: String,
        subtitle: String,
        bullets: List<String>,
        footer: String,
        includeSignatureBox: Boolean = false
    ) {
        val page = PDPage(PDRectangle.A4)
        doc.addPage(page)
        val box = page.mediaBox
        val w = box.width
        val h = box.height

        PDPageContentStream(doc, page).use { cs ->
            // Top Navy Banner
            cs.setNonStrokingColor(15, 23, 42)
            cs.addRect(0f, h - 120f, w, 120f)
            cs.fill()

            // Crimson accent stripe
            cs.setNonStrokingColor(220, 38, 38)
            cs.addRect(0f, h - 126f, w, 6f)
            cs.fill()

            // Title text
            cs.beginText()
            cs.setFont(PDType1Font.HELVETICA_BOLD, 24f)
            cs.setNonStrokingColor(255, 255, 255)
            cs.newLineAtOffset(44f, h - 62f)
            cs.showText(heading)
            cs.endText()

            // Subtitle
            cs.beginText()
            cs.setFont(PDType1Font.HELVETICA, 10.5f)
            cs.setNonStrokingColor(203, 213, 225)
            cs.newLineAtOffset(44f, h - 92f)
            cs.showText(subtitle)
            cs.endText()

            // Body Bullets with simple word wrapping
            var cursorY = h - 172f
            val bodyFont = PDType1Font.HELVETICA
            val bodySize = 12f
            val maxLineWidth = w - 104f

            for (bullet in bullets) {
                // Accent dot
                cs.setNonStrokingColor(2, 132, 199)
                cs.addRect(44f, cursorY + 2f, 6f, 6f)
                cs.fill()

                val lines = wrapWinAnsiText(bullet, bodyFont, bodySize, maxLineWidth)
                cs.setNonStrokingColor(30, 41, 59)
                for (line in lines) {
                    cs.beginText()
                    cs.setFont(bodyFont, bodySize)
                    cs.newLineAtOffset(60f, cursorY)
                    cs.showText(line)
                    cs.endText()
                    cursorY -= 19f
                }
                cursorY -= 12f
            }

            if (includeSignatureBox) {
                val sigY = 120f
                cs.setStrokingColor(148, 163, 184)
                cs.setLineWidth(1.2f)
                cs.addRect(44f, sigY, w - 88f, 110f)
                cs.stroke()

                cs.beginText()
                cs.setFont(PDType1Font.HELVETICA_OBLIQUE, 10f)
                cs.setNonStrokingColor(100, 116, 139)
                cs.newLineAtOffset(56f, sigY + 90f)
                cs.showText("Signature & Stamp Practice Area (Use the Sign or Stamp tool here)")
                cs.endText()
            }

            // Footer divider & text
            cs.setStrokingColor(226, 232, 240)
            cs.setLineWidth(1f)
            cs.moveTo(44f, 52f)
            cs.lineTo(w - 44f, 52f)
            cs.stroke()

            cs.beginText()
            cs.setFont(PDType1Font.HELVETICA, 9.5f)
            cs.setNonStrokingColor(100, 116, 139)
            cs.newLineAtOffset(44f, 34f)
            cs.showText(footer)
            cs.endText()
        }
    }

    private fun wrapWinAnsiText(
        text: String,
        font: PDType1Font,
        fontSize: Float,
        maxWidth: Float
    ): List<String> {
        val safeText = text.replace("•", "-").replace("°", " deg").replace("©", "(c)")
        val words = safeText.split(" ")
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            val width = font.getStringWidth(candidate) / 1000f * fontSize
            if (width > maxWidth && current.isNotEmpty()) {
                lines.add(current.toString())
                current = StringBuilder(word)
            } else {
                current = StringBuilder(candidate)
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        return lines
    }

    private fun drawPageTemplate(cs: PDPageContentStream, box: PDRectangle, template: PageTemplate) {
        when (template) {
            PageTemplate.BLANK -> Unit
            PageTemplate.LINED -> {
                cs.setStrokingColor(203, 213, 225)
                cs.setLineWidth(0.6f)
                var y = box.height - 64f
                while (y >= 48f) {
                    cs.moveTo(42f, y)
                    cs.lineTo(box.width - 42f, y)
                    y -= 24f
                }
                cs.stroke()
            }

            PageTemplate.GRID -> {
                cs.setStrokingColor(226, 232, 240)
                cs.setLineWidth(0.5f)
                var x = 36f
                while (x <= box.width - 36f) {
                    cs.moveTo(x, 36f)
                    cs.lineTo(x, box.height - 36f)
                    x += 20f
                }
                var y = 36f
                while (y <= box.height - 36f) {
                    cs.moveTo(36f, y)
                    cs.lineTo(box.width - 36f, y)
                    y += 20f
                }
                cs.stroke()
            }
        }
    }

    private fun drawOvalPath(cs: PDPageContentStream, x: Float, y: Float, w: Float, h: Float) {
        val k = 0.55228475f
        val rx = w / 2f
        val ry = h / 2f
        val cx = x + rx
        val cy = y + ry
        val ox = rx * k
        val oy = ry * k

        cs.moveTo(cx, cy + ry)
        cs.curveTo(cx + ox, cy + ry, cx + rx, cy + oy, cx + rx, cy)
        cs.curveTo(cx + rx, cy - oy, cx + ox, cy - ry, cx, cy - ry)
        cs.curveTo(cx - ox, cy - ry, cx - rx, cy - oy, cx - rx, cy)
        cs.curveTo(cx - rx, cy + oy, cx - ox, cy + ry, cx, cy + ry)
    }

    private fun ensureUnlockedTempCopy(inputFile: File, password: String?): File {
        if (password.isNullOrEmpty()) return inputFile
        val tmp = File(context.cacheDir, "unlocked_${System.currentTimeMillis()}.pdf")
        PDDocument.load(inputFile, password).use { doc ->
            doc.isAllSecurityToBeRemoved = true
            doc.save(tmp)
        }
        return tmp
    }

    private fun decodeOrientedBitmap(file: File, maxDim: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val raw = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return null

        val orientation = runCatching {
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val rot = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (rot == 0f) return raw
        val matrix = Matrix().apply { postRotate(rot) }
        val rotated = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
        if (rotated != raw) raw.recycle()
        return rotated
    }

    private fun renderTextBitmap(
        text: String,
        textSizePx: Float,
        colorArgb: Int,
        isBold: Boolean,
        bgColorArgb: Int,
        borderColorArgb: Int?,
        maxWidthPx: Int = 900
    ): Bitmap {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorArgb
            textSize = textSizePx
            typeface = if (isBold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        val safeText = text.ifBlank { " " }
        val measuredW = safeText.lines().maxOfOrNull { paint.measureText(it) } ?: 60f
        val contentW = measuredW.toInt().coerceIn(48, maxWidthPx)
        val pad = if (bgColorArgb != Color.TRANSPARENT) 24 else 10

        val layout = StaticLayout.Builder.obtain(safeText, 0, safeText.length, paint, contentW)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(6f, 1.0f)
            .build()

        val bmpW = (contentW + pad * 2).coerceAtLeast(32)
        val bmpH = (layout.height + pad * 2).coerceAtLeast(32)
        val bmp = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        if (bgColorArgb != Color.TRANSPARENT) {
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = bgColorArgb
                style = Paint.Style.FILL
            }
            val rect = RectF(2f, 2f, bmpW - 2f, bmpH - 2f)
            canvas.drawRoundRect(rect, 14f, 14f, bgPaint)
            if (borderColorArgb != null) {
                val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = borderColorArgb
                    style = Paint.Style.STROKE
                    strokeWidth = 4f
                }
                canvas.drawRoundRect(rect, 14f, 14f, borderPaint)
            }
        }

        canvas.save()
        canvas.translate(pad.toFloat(), pad.toFloat())
        layout.draw(canvas)
        canvas.restore()
        return bmp
    }

    private fun renderStampBitmap(label: String, colorArgb: Int): Bitmap {
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorArgb
            textSize = 56f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val textW = textPaint.measureText(label)
        val bmpW = (textW + 96f).toInt().coerceAtLeast(240)
        val bmpH = 128
        val bmp = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorArgb
            style = Paint.Style.STROKE
            strokeWidth = 8f
        }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(26, Color.red(colorArgb), Color.green(colorArgb), Color.blue(colorArgb))
            style = Paint.Style.FILL
        }
        val rect = RectF(10f, 10f, bmpW - 10f, bmpH - 10f)
        canvas.drawRoundRect(rect, 18f, 18f, fillPaint)
        canvas.drawRoundRect(rect, 18f, 18f, borderPaint)

        val textY = bmpH / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(label, bmpW / 2f, textY, textPaint)
        return bmp
    }

    private fun renderWatermarkBitmap(
        text: String,
        textSizePx: Float,
        colorArgb: Int,
        rotationDeg: Float,
        opacity: Float
    ): Bitmap {
        val alpha = (opacity.coerceIn(0.05f, 0.9f) * 255).toInt()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(alpha, Color.red(colorArgb), Color.green(colorArgb), Color.blue(colorArgb))
            textSize = textSizePx
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val w = (paint.measureText(text) * 1.4f).toInt().coerceIn(400, 1400)
        val h = (w * 0.7f).toInt().coerceAtLeast(320)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.save()
        canvas.rotate(rotationDeg, w / 2f, h / 2f)
        val textY = h / 2f - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(text, w / 2f, textY, paint)
        canvas.restore()
        return bmp
    }

    private fun renderDocumentPageBitmap(title: String, body: String): Bitmap {
        val widthPx = 1240
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42)
            textSize = 52f
            typeface = Typeface.DEFAULT_BOLD
        }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 41, 59)
            textSize = 32f
            typeface = Typeface.DEFAULT
        }
        val titleLayout = if (title.isNotBlank()) {
            StaticLayout.Builder.obtain(title, 0, title.length, titlePaint, widthPx).build()
        } else null
        val bodyLayout = if (body.isNotBlank()) {
            StaticLayout.Builder.obtain(body, 0, body.length, bodyPaint, widthPx)
                .setLineSpacing(10f, 1.0f)
                .build()
        } else null

        val totalH = ((titleLayout?.height ?: 0) + (if (titleLayout != null) 32 else 0) + (bodyLayout?.height ?: 0) + 40)
            .coerceIn(120, 1750)
        val bmp = Bitmap.createBitmap(widthPx, totalH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        var y = 0f
        titleLayout?.let {
            canvas.save()
            canvas.translate(0f, y)
            it.draw(canvas)
            canvas.restore()
            y += it.height + 32f
        }
        bodyLayout?.let {
            canvas.save()
            canvas.translate(0f, y)
            it.draw(canvas)
            canvas.restore()
        }
        return bmp
    }
}
