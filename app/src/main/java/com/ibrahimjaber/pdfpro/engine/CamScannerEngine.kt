package com.ibrahimjaber.pdfpro.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.RectF
import androidx.exifinterface.media.ExifInterface
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
    val autoCropped: Boolean = false,
    val cropLeft: Float = 0f,
    val cropTop: Float = 0f,
    val cropRight: Float = 1f,
    val cropBottom: Float = 1f
)

/**
 * 100% Offline CamScanner Pro Image Enhancement & Document Export Engine (v5.2).
 * - Automatic EXIF orientation normalization (fixes sideways camera captures)
 * - Smooth bilinear-interpolated illumination shadow removal (zero blocky artifacts!)
 * - True CamScanner Magic Color (shadow flattening + white-paper pop + vivid ink)
 * - Crisp Adaptive B&W preserving Arabic diacritics & thin strokes
 * - Manual precision crop + safe opt-in auto-trim + multi-page Searchable OCR PDF export
 *
 * Programmed by Ibrahim Jaber 2026 | برمجة إبراهيم جابر 2026
 */
object CamScannerEngine {

    /**
     * Pure-Kotlin crop rect clamping helper (unit-testable without Android framework).
     */
    fun clampCropRect(left: Float, top: Float, right: Float, bottom: Float): FloatArray {
        val l = left.coerceIn(0f, 0.45f)
        val t = top.coerceIn(0f, 0.45f)
        val r = right.coerceIn(l + 0.15f, 1f)
        val b = bottom.coerceIn(t + 0.15f, 1f)
        return floatArrayOf(l, t, r, b)
    }

    /**
     * Loads a memory-safe bitmap scaled down to maxDimension pixels on the longest edge
     * and automatically normalizes EXIF camera orientation so pages are always upright.
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
        val raw = BitmapFactory.decodeFile(filePath, decodeOpts) ?: return null

        val exifRot = runCatching {
            when (ExifInterface(filePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrDefault(0f)

        if (exifRot == 0f) return raw
        val matrix = Matrix().apply { postRotate(exifRot) }
        return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true).also {
            if (it !== raw) raw.recycle()
        }
    }

    /**
     * Conservative document margin detector: only trims obvious dark outer desk borders
     * without ever chopping into document content.
     */
    fun detectDocumentBounds(bitmap: Bitmap): RectF {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 80 || h < 80) return RectF(0f, 0f, 1f, 1f)

        val stepX = (w / 100).coerceAtLeast(1)
        val stepY = (h / 100).coerceAtLeast(1)
        val gw = w / stepX
        val gh = h / stepY

        val rowBright = FloatArray(gh)
        val colBright = FloatArray(gw)

        for (gy in 0 until gh) {
            val py = (gy * stepY).coerceAtMost(h - 1)
            for (gx in 0 until gw) {
                val px = (gx * stepX).coerceAtMost(w - 1)
                val c = bitmap.getPixel(px, py)
                val l = ((Color.red(c) * 77 + Color.green(c) * 150 + Color.blue(c) * 29) shr 8).toFloat()
                rowBright[gy] += l / gw
                colBright[gx] += l / gh
            }
        }
        val centerBright = rowBright.slice((gh / 4)..(gh * 3 / 4)).average().toFloat()
        val cutThresh = (centerBright * 0.62f).coerceIn(45f, 130f)

        var top = 0
        while (top < gh / 4 && rowBright[top] < cutThresh) top++
        var bottom = gh - 1
        while (bottom > gh * 3 / 4 && rowBright[bottom] < cutThresh) bottom--
        var left = 0
        while (left < gw / 4 && colBright[left] < cutThresh) left++
        var right = gw - 1
        while (right > gw * 3 / 4 && colBright[right] < cutThresh) right--

        val nl = (left.toFloat() / gw).coerceIn(0f, 0.25f)
        val nt = (top.toFloat() / gh).coerceIn(0f, 0.25f)
        val nr = ((right + 1).toFloat() / gw).coerceIn(0.75f, 1f)
        val nb = ((bottom + 1).toFloat() / gh).coerceIn(0.75f, 1f)
        return RectF(nl, nt, nr, nb)
    }

    /**
     * Processes a raw camera/gallery image with rotation, crop (manual or auto), and CamScanner filter.
     */
    suspend fun processScanPage(
        rawFilePath: String,
        outputFile: File,
        filter: ScanFilter = ScanFilter.MAGIC_COLOR,
        autoCrop: Boolean = false,
        rotationDegrees: Int = 0,
        cropLeft: Float = 0f,
        cropTop: Float = 0f,
        cropRight: Float = 1f,
        cropBottom: Float = 1f
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

            val effRect = if (autoCrop) {
                detectDocumentBounds(working)
            } else {
                val c = clampCropRect(cropLeft, cropTop, cropRight, cropBottom)
                RectF(c[0], c[1], c[2], c[3])
            }

            val cx = (effRect.left * working.width).roundToInt().coerceIn(0, working.width - 16)
            val cy = (effRect.top * working.height).roundToInt().coerceIn(0, working.height - 16)
            val cw = (effRect.width() * working.width).roundToInt().coerceIn(16, working.width - cx)
            val ch = (effRect.height() * working.height).roundToInt().coerceIn(16, working.height - cy)
            if (cw < working.width - 8 || ch < working.height - 8) {
                val cropped = Bitmap.createBitmap(working, cx, cy, cw, ch)
                if (cropped !== working) {
                    working.recycle()
                    working = cropped
                }
            }

            val filtered = applyFilter(working, filter)
            outputFile.parentFile?.mkdirs()
            FileOutputStream(outputFile).use { fos ->
                filtered.compress(Bitmap.CompressFormat.JPEG, 92, fos)
            }
            if (filtered !== working) filtered.recycle()
        } finally {
            if (!working.isRecycled) working.recycle()
        }
        outputFile
    }

    /**
     * Smooth bilinear-interpolated illumination background normalizer (eliminates shadows with ZERO blocky squares!).
     */
    private fun smoothFlattenIllumination(pixels: IntArray, w: Int, h: Int) {
        val grid = 16
        val bgR = FloatArray((grid + 1) * (grid + 1))
        val bgG = FloatArray((grid + 1) * (grid + 1))
        val bgB = FloatArray((grid + 1) * (grid + 1))
        val cellW = max(1, w / grid)
        val cellH = max(1, h / grid)

        // Sample 90th-percentile-like bright paper illumination at each grid vertex
        for (gy in 0..grid) {
            val yCenter = (gy * h / grid).coerceIn(0, h - 1)
            val y0 = max(0, yCenter - cellH / 2)
            val y1 = min(h, yCenter + cellH / 2 + 1)
            for (gx in 0..grid) {
                val xCenter = (gx * w / grid).coerceIn(0, w - 1)
                val x0 = max(0, xCenter - cellW / 2)
                val x1 = min(w, xCenter + cellW / 2 + 1)
                var maxR = 75
                var maxG = 75
                var maxB = 75
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
                val idx = gy * (grid + 1) + gx
                bgR[idx] = maxR.toFloat()
                bgG[idx] = maxG.toFloat()
                bgB[idx] = maxB.toFloat()
            }
        }

        // Bilinear interpolation per pixel -> silky-smooth shadow removal!
        val stride = grid + 1
        val invW = grid.toFloat() / max(1, w - 1)
        val invH = grid.toFloat() / max(1, h - 1)
        for (y in 0 until h) {
            val fy = y * invH
            val y0 = fy.toInt().coerceIn(0, grid - 1)
            val dy = fy - y0
            val wy0 = 1f - dy
            val row0 = y0 * stride
            val row1 = (y0 + 1) * stride
            for (x in 0 until w) {
                val fx = x * invW
                val x0 = fx.toInt().coerceIn(0, grid - 1)
                val dx = fx - x0
                val wx0 = 1f - dx

                val i00 = row0 + x0
                val i10 = i00 + 1
                val i01 = row1 + x0
                val i11 = i01 + 1

                val br = (bgR[i00] * wx0 + bgR[i10] * dx) * wy0 + (bgR[i01] * wx0 + bgR[i11] * dx) * dy
                val bg = (bgG[i00] * wx0 + bgG[i10] * dx) * wy0 + (bgG[i01] * wx0 + bgG[i11] * dx) * dy
                val bb = (bgB[i00] * wx0 + bgB[i10] * dx) * wy0 + (bgB[i01] * wx0 + bgB[i11] * dx) * dy

                val c = pixels[y * w + x]
                val nr = ((Color.red(c) / br) * 255f).roundToInt().coerceIn(0, 255)
                val ng = ((Color.green(c) / bg) * 255f).roundToInt().coerceIn(0, 255)
                val nb = ((Color.blue(c) / bb) * 255f).roundToInt().coerceIn(0, 255)
                pixels[y * w + x] = Color.rgb(nr, ng, nb)
            }
        }
    }

    /**
     * Fast pure-Kotlin CamScanner Pro pixel enhancement filters.
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
                smoothFlattenIllumination(pixels, w, h)
                for (i in pixels.indices) {
                    val c = pixels[i]
                    val lum = (Color.red(c) * 77 + Color.green(c) * 150 + Color.blue(c) * 29) shr 8
                    val boosted = (((lum - 128) * 1.25f) + 138f).roundToInt().coerceIn(0, 255)
                    pixels[i] = Color.rgb(boosted, boosted, boosted)
                }
            }

            ScanFilter.SHADOW_REMOVE -> {
                smoothFlattenIllumination(pixels, w, h)
                for (i in pixels.indices) {
                    val c = pixels[i]
                    val r = (((Color.red(c) - 128) * 1.12f) + 134f).roundToInt().coerceIn(0, 255)
                    val g = (((Color.green(c) - 128) * 1.12f) + 134f).roundToInt().coerceIn(0, 255)
                    val b = (((Color.blue(c) - 128) * 1.12f) + 134f).roundToInt().coerceIn(0, 255)
                    pixels[i] = Color.rgb(r, g, b)
                }
            }

            ScanFilter.MAGIC_COLOR -> {
                // True CamScanner Magic Color: smooth shadow removal + pure-white background pop + rich ink contrast
                smoothFlattenIllumination(pixels, w, h)
                for (i in pixels.indices) {
                    val c = pixels[i]
                    var r = Color.red(c)
                    var g = Color.green(c)
                    var b = Color.blue(c)
                    val lum = (r * 77 + g * 150 + b * 29) shr 8
                    if (lum > 212) {
                        // Clean bright white paper background
                        r = min(255, r + 32)
                        g = min(255, g + 32)
                        b = min(255, b + 32)
                    } else {
                        // Deep, crisp ink & colorful stamps/headers
                        r = (((r - 128) * 1.38f) + 120f).roundToInt().coerceIn(0, 255)
                        g = (((g - 128) * 1.38f) + 120f).roundToInt().coerceIn(0, 255)
                        b = (((b - 128) * 1.38f) + 120f).roundToInt().coerceIn(0, 255)
                    }
                    pixels[i] = Color.rgb(r, g, b)
                }
            }

            ScanFilter.CRISP_BW -> {
                // Smooth shadow normalization BEFORE adaptive thresholding preserves fine Arabic strokes & diacritics
                smoothFlattenIllumination(pixels, w, h)
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
                val radius = (min(w, h) / 30).coerceIn(10, 28)
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
                        val pxLum = lum[y * w + x]
                        val v = if (pxLum < localMean - 11 && pxLum < 210) 0 else 255
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

                    val pdImg = JPEGFactory.createFromImage(doc, bmp, 0.90f)
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
