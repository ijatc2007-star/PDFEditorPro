package com.ibrahimjaber.pdfpro.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.RectF
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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class ScanFilter(val labelEn: String, val labelAr: String) {
    MAGIC_COLOR("Magic Color", "سحري ملون"),
    CRISP_BW("Crisp B&W", "أبيض وأسود حاد"),
    SHADOW_REMOVE("No Shadow", "إزالة الظل"),
    GRAYSCALE("Grayscale", "رمادي نقي"),
    ORIGINAL("Original", "الأصل")
}

data class ScannedPageItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val rawImagePath: String,
    val enhancedImagePath: String,
    val filter: ScanFilter = ScanFilter.MAGIC_COLOR,
    val rotationDegrees: Int = 0,
    val autoCropped: Boolean = true
)

/**
 * 100% Offline CamScanner Pro Image Enhancement & Document Export Engine.
 * - Automatic document border detection & background trimming
 * - 5 Pro Document Filters (Magic Color, Crisp B&W Adaptive Threshold, Shadow Removal, Grayscale, Original)
 * - Multi-page A4 PDF assembly with optional embedded Offline Arabic + English OCR searchable text layer
 *
 * Programmed by Ibrahim Jaber 2026 | برمجة إبراهيم جابر 2026
 */
object CamScannerEngine {

    /**
     * Loads a memory-safe bitmap scaled down to maxDimension pixels on the longest edge.
     */
    fun decodeScaledBitmap(filePath: String, maxDimension: Int = 1800): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(filePath, opts)
        if (opts.outWidth <= 0 || opts.outHeight <= 0) return null

        var sample = 1
        val longest = max(opts.outWidth, opts.outHeight)
        while (longest / sample > maxDimension) {
            sample *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(filePath, decodeOpts)
    }

    /**
     * Detects the probable paper document rectangle inside a photo (normalized 0f..1f)
     * by locating bright paper rows/columns vs darker desk/table margins.
     */
    fun detectDocumentBounds(bitmap: Bitmap): RectF {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 64 || h < 64) return RectF(0f, 0f, 1f, 1f)

        val stepX = (w / 120).coerceAtLeast(1)
        val stepY = (h / 120).coerceAtLeast(1)
        val gridW = w / stepX
        val gridH = h / stepY

        val lum = IntArray(gridW * gridH)
        var sum = 0L
        for (gy in 0 until gridH) {
            val py = (gy * stepY).coerceAtMost(h - 1)
            for (gx in 0 until gridW) {
                val px = (gx * stepX).coerceAtMost(w - 1)
                val c = bitmap.getPixel(px, py)
                val l = (Color.red(c) * 77 + Color.green(c) * 150 + Color.blue(c) * 29) shr 8
                lum[gy * gridW + gx] = l
                sum += l
            }
        }
        val meanLum = (sum / max(1, lum.size)).toInt()
        val threshold = (meanLum * 0.85f).toInt().coerceIn(85, 195)

        var minX = gridW
        var maxX = 0
        var minY = gridH
        var maxY = 0
        var paperCount = 0

        for (gy in 0 until gridH) {
            for (gx in 0 until gridW) {
                if (lum[gy * gridW + gx] >= threshold) {
                    if (gx < minX) minX = gx
                    if (gx > maxX) maxX = gx
                    if (gy < minY) minY = gy
                    if (gy > maxY) maxY = gy
                    paperCount++
                }
            }
        }

        // Only crop if the detected paper occupies a reasonable region (20%..94% of image)
        if (paperCount < (gridW * gridH * 0.15)) return RectF(0f, 0f, 1f, 1f)
        val left = (minX.toFloat() / gridW).coerceIn(0f, 0.30f)
        val top = (minY.toFloat() / gridH).coerceIn(0f, 0.30f)
        val right = ((maxX + 1).toFloat() / gridW).coerceIn(0.70f, 1f)
        val bottom = ((maxY + 1).toFloat() / gridH).coerceIn(0.70f, 1f)

        val areaRatio = (right - left) * (bottom - top)
        return if (areaRatio in 0.25f..0.95f) RectF(left, top, right, bottom) else RectF(0f, 0f, 1f, 1f)
    }

    /**
     * Applies CamScanner Pro filter pipeline (optional auto-crop, rotation, and filter enhancement)
     * and writes the enhanced JPEG to outputFile.
     */
    suspend fun processScanPage(
        rawFilePath: String,
        outputFile: File,
        filter: ScanFilter = ScanFilter.MAGIC_COLOR,
        autoCrop: Boolean = true,
        rotationDegrees: Int = 0
    ): File = withContext(Dispatchers.IO) {
        val base = decodeScaledBitmap(rawFilePath, maxDimension = 1800)
            ?: throw IllegalStateException("Cannot decode scanned image: $rawFilePath")

        var working = base
        try {
            if (rotationDegrees % 360 != 0) {
                val matrix = Matrix().apply { postRotate((rotationDegrees % 360).toFloat()) }
                val rotated = Bitmap.createBitmap(working, 0, 0, working.width, working.height, matrix, true)
                if (rotated !== working) {
                    working.recycle()
                    working = rotated
                }
            }

            if (autoCrop) {
                val bounds = detectDocumentBounds(working)
                val cx = (bounds.left * working.width).roundToInt().coerceIn(0, working.width - 10)
                val cy = (bounds.top * working.height).roundToInt().coerceIn(0, working.height - 10)
                val cw = ((bounds.width()) * working.width).roundToInt().coerceIn(10, working.width - cx)
                val ch = ((bounds.height()) * working.height).roundToInt().coerceIn(10, working.height - cy)
                if (cw < working.width - 12 || ch < working.height - 12) {
                    val cropped = Bitmap.createBitmap(working, cx, cy, cw, ch)
                    if (cropped !== working) {
                        working.recycle()
                        working = cropped
                    }
                }
            }

            val filtered = applyFilter(working, filter)
            outputFile.parentFile?.mkdirs()
            FileOutputStream(outputFile).use { fos ->
                filtered.compress(Bitmap.CompressFormat.JPEG, 90, fos)
            }
            if (filtered !== working) filtered.recycle()
        } finally {
            if (!working.isRecycled) working.recycle()
        }
        outputFile
    }

    /**
     * Fast pure-Kotlin pixel enhancement filters for document scanning.
     */
    fun applyFilter(source: Bitmap, filter: ScanFilter): Bitmap {
        if (filter == ScanFilter.ORIGINAL) {
            return source.copy(Bitmap.Config.ARGB_8888, false)
        }

        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)

        when (filter) {
            ScanFilter.GRAYSCALE -> {
                for (i in pixels.indices) {
                    val c = pixels[i]
                    val lum = (Color.red(c) * 77 + Color.green(c) * 150 + Color.blue(c) * 29) shr 8
                    val boosted = (((lum - 128) * 1.28f) + 136f).roundToInt().coerceIn(0, 255)
                    pixels[i] = Color.rgb(boosted, boosted, boosted)
                }
            }

            ScanFilter.MAGIC_COLOR -> {
                // Sample 95th percentile white-point & 5th percentile black-point for true CamScanner white-page pop
                val hist = IntArray(256)
                for (i in pixels.indices step 4) {
                    val c = pixels[i]
                    val lum = (Color.red(c) * 77 + Color.green(c) * 150 + Color.blue(c) * 29) shr 8
                    hist[lum]++
                }
                val totalSamples = hist.sum().coerceAtLeast(1)
                var acc = 0
                var blackPt = 20
                var whitePt = 230
                for (v in 0..255) {
                    acc += hist[v]
                    if (acc >= totalSamples * 0.03f && blackPt == 20) blackPt = v
                    if (acc >= totalSamples * 0.88f) {
                        whitePt = v.coerceAtLeast(blackPt + 40)
                        break
                    }
                }
                val invSpan = 255f / (whitePt - blackPt).coerceAtLeast(1)

                for (i in pixels.indices) {
                    val c = pixels[i]
                    var r = ((Color.red(c) - blackPt) * invSpan).roundToInt().coerceIn(0, 255)
                    var g = ((Color.green(c) - blackPt) * invSpan).roundToInt().coerceIn(0, 255)
                    var b = ((Color.blue(c) - blackPt) * invSpan).roundToInt().coerceIn(0, 255)
                    val lum = (r * 77 + g * 150 + b * 29) shr 8
                    if (lum > 205) {
                        // Crisp white background whitening
                        r = min(255, r + 18)
                        g = min(255, g + 18)
                        b = min(255, b + 18)
                    } else {
                        // Vivid ink contrast boost
                        r = (((r - 128) * 1.22f) + 124f).roundToInt().coerceIn(0, 255)
                        g = (((g - 128) * 1.22f) + 124f).roundToInt().coerceIn(0, 255)
                        b = (((b - 128) * 1.22f) + 124f).roundToInt().coerceIn(0, 255)
                    }
                    pixels[i] = Color.rgb(r, g, b)
                }
            }

            ScanFilter.SHADOW_REMOVE -> {
                // Estimate local illumination background using a downsampled coarse grid and divide out shadows
                val grid = 24
                val cellW = max(1, w / grid)
                val cellH = max(1, h / grid)
                val bgR = FloatArray(grid * grid)
                val bgG = FloatArray(grid * grid)
                val bgB = FloatArray(grid * grid)

                for (gy in 0 until grid) {
                    val y0 = gy * cellH
                    val y1 = min(h, y0 + cellH)
                    for (gx in 0 until grid) {
                        val x0 = gx * cellW
                        val x1 = min(w, x0 + cellW)
                        var maxR = 60
                        var maxG = 60
                        var maxB = 60
                        var y = y0
                        while (y < y1) {
                            var x = x0
                            while (x < x1) {
                                val c = pixels[y * w + x]
                                val r = Color.red(c)
                                val g = Color.green(c)
                                val b = Color.blue(c)
                                if (r > maxR) maxR = r
                                if (g > maxG) maxG = g
                                if (b > maxB) maxB = b
                                x += 4
                            }
                            y += 4
                        }
                        bgR[gy * grid + gx] = maxR.toFloat()
                        bgG[gy * grid + gx] = maxG.toFloat()
                        bgB[gy * grid + gx] = maxB.toFloat()
                    }
                }

                for (y in 0 until h) {
                    val gy = (y / cellH).coerceIn(0, grid - 1)
                    for (x in 0 until w) {
                        val gx = (x / cellW).coerceIn(0, grid - 1)
                        val idx = gy * grid + gx
                        val c = pixels[y * w + x]
                        val nr = ((Color.red(c) / bgR[idx]) * 255f).roundToInt().coerceIn(0, 255)
                        val ng = ((Color.green(c) / bgG[idx]) * 255f).roundToInt().coerceIn(0, 255)
                        val nb = ((Color.blue(c) / bgB[idx]) * 255f).roundToInt().coerceIn(0, 255)
                        pixels[y * w + x] = Color.rgb(nr, ng, nb)
                    }
                }
            }

            ScanFilter.CRISP_BW -> {
                // Integral-image adaptive local mean thresholding (razor-sharp white paper + black text)
                val lum = IntArray(w * h)
                val integral = LongArray((w + 1) * (h + 1))
                for (y in 0 until h) {
                    var rowSum = 0L
                    for (x in 0 until w) {
                        val c = pixels[y * w + x]
                        val l = (Color.red(c) * 77 + Color.green(c) * 150 + Color.blue(c) * 29) shr 8
                        lum[y * w + x] = l
                        rowSum += l
                        integral[(y + 1) * (w + 1) + (x + 1)] = integral[y * (w + 1) + (x + 1)] + rowSum
                    }
                }
                val radius = (min(w, h) / 28).coerceIn(10, 32)
                for (y in 0 until h) {
                    val y1 = max(0, y - radius)
                    val y2 = min(h - 1, y + radius)
                    for (x in 0 until w) {
                        val x1 = max(0, x - radius)
                        val x2 = min(w - 1, x + radius)
                        val area = ((x2 - x1 + 1) * (y2 - y1 + 1)).toLong()
                        val sum = integral[(y2 + 1) * (w + 1) + (x2 + 1)] -
                            integral[y1 * (w + 1) + (x2 + 1)] -
                            integral[(y2 + 1) * (w + 1) + x1] +
                            integral[y1 * (w + 1) + x1]
                        val localMean = (sum / area).toInt()
                        val v = if (lum[y * w + x] < localMean - 14) 0 else 255
                        pixels[y * w + x] = Color.rgb(v, v, v)
                    }
                }
            }

            ScanFilter.ORIGINAL -> Unit
        }

        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, w, 0, 0, w, h)
        }
    }

    /**
     * Combines multiple CamScanner enhanced pages into a multi-page A4 PDF,
     * optionally running offline Arabic+English Tesseract OCR on every page.
     */
    suspend fun exportPagesToPdf(
        pages: List<ScannedPageItem>,
        outputPdf: File,
        runOfflineOcr: Boolean = false,
        ocrEngine: OfflineOcrEngine? = null,
        ocrLang: OcrLanguageMode = OcrLanguageMode.ARABIC_AND_ENGLISH,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): Pair<File, String> = withContext(Dispatchers.IO) {
        val doc = PDDocument()
        val ocrTexts = mutableListOf<String>()
        try {
            pages.forEachIndexed { idx, item ->
                onProgress?.invoke(idx + 1, pages.size)
                val bmp = decodeScaledBitmap(item.enhancedImagePath, maxDimension = 1800) ?: return@forEachIndexed
                try {
                    val isLandscape = bmp.width > bmp.height
                    val pageRect = if (isLandscape) PDRectangle(PDRectangle.A4.height, PDRectangle.A4.width) else PDRectangle.A4
                    val pdPage = PDPage(pageRect)
                    doc.addPage(pdPage)

                    val pdImg = JPEGFactory.createFromImage(doc, bmp, 0.88f)
                    val margin = 12f
                    val availW = pageRect.width - margin * 2
                    val availH = pageRect.height - margin * 2
                    val scale = min(availW / bmp.width.toFloat(), availH / bmp.height.toFloat())
                    val drawW = bmp.width * scale
                    val drawH = bmp.height * scale
                    val dx = (pageRect.width - drawW) / 2f
                    val dy = (pageRect.height - drawH) / 2f

                    var pageOcrText = ""
                    if (runOfflineOcr && ocrEngine != null) {
                        pageOcrText = ocrEngine.ocrBitmap(bmp, ocrLang)
                        if (pageOcrText.isNotBlank()) {
                            ocrTexts.add("--- Page ${idx + 1} ---\n$pageOcrText")
                        }
                    }

                    PDPageContentStream(doc, pdPage).use { cs ->
                        cs.drawImage(pdImg, dx, dy, drawW, drawH)
                        val asciiLines = pageOcrText.lines()
                            .map { it.filter { ch -> ch.code in 32..126 }.trim() }
                            .filter { it.isNotEmpty() }
                        if (asciiLines.isNotEmpty()) {
                            cs.beginText()
                            cs.setRenderingMode(RenderingMode.NEITHER)
                            cs.setFont(PDType1Font.HELVETICA, 9f)
                            var y = pageRect.height - 24f
                            for (line in asciiLines.take(65)) {
                                cs.newLineAtOffset(20f, y)
                                cs.showText(line.take(105))
                                cs.newLineAtOffset(-20f, -y)
                                y -= 10.5f
                                if (y < 14f) break
                            }
                            cs.endText()
                        }
                    }
                } finally {
                    bmp.recycle()
                }
            }

            outputPdf.parentFile?.mkdirs()
            doc.documentInformation.apply {
                title = outputPdf.nameWithoutExtension
                author = AppBranding.DEVELOPER_NAME
                creator = AppBranding.PDF_CREATOR_TAG
                subject = if (runOfflineOcr) "CamScanner Pro OCR PDF" else "CamScanner Pro PDF"
            }
            doc.save(outputPdf)
        } finally {
            doc.close()
        }
        outputPdf to ocrTexts.joinToString("\n\n")
    }
}
