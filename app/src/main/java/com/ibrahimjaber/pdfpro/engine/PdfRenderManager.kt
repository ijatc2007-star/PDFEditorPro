package com.ibrahimjaber.pdfpro.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Thread-safe offline PDF page renderer using Android's native PdfRenderer with
 * automatic PDFBox password unlock fallback and LRU Bitmap caching.
 */
class PdfRenderManager(private val context: Context) {

    private val mutex = Mutex()
    private var currentPath: String? = null
    private var currentPassword: String? = null
    private var unlockedTempFile: File? = null
    private var pfd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null

    private val cache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 1024L / 6L).toInt().coerceIn(16 * 1024, 96 * 1024)
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    suspend fun open(file: File, password: String? = null): Int = withContext(Dispatchers.IO) {
        mutex.withLock {
            closeLocked()
            val targetFile = if (!password.isNullOrEmpty()) {
                val tmp = File(context.cacheDir, "render_unlock_${System.currentTimeMillis()}.pdf")
                PDDocument.load(file, password).use { doc ->
                    doc.isAllSecurityToBeRemoved = true
                    doc.save(tmp)
                }
                unlockedTempFile = tmp
                tmp
            } else {
                file
            }

            val descriptor = ParcelFileDescriptor.open(targetFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val pdfRenderer = PdfRenderer(descriptor)
            pfd = descriptor
            renderer = pdfRenderer
            currentPath = file.absolutePath
            currentPassword = password
            cache.evictAll()
            pdfRenderer.pageCount
        }
    }

    suspend fun renderPage(
        pageIndex: Int,
        targetWidthPx: Int = 1280,
        cacheTag: String = "page"
    ): Bitmap? = withContext(Dispatchers.IO) {
        val key = "${currentPath}_${cacheTag}_${pageIndex}_$targetWidthPx"
        cache.get(key)?.takeIf { !it.isRecycled }?.let { return@withContext it }

        mutex.withLock {
            val r = renderer ?: return@withContext null
            if (pageIndex !in 0 until r.pageCount) return@withContext null
            r.openPage(pageIndex).use { page ->
                val aspect = page.height.toFloat() / page.width.toFloat().coerceAtLeast(1f)
                val w = targetWidthPx.coerceIn(180, 2200)
                val h = (w * aspect).toInt().coerceIn(180, 3200)
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                cache.put(key, bmp)
                bmp
            }
        }
    }

    suspend fun renderSingleThumbnail(
        file: File,
        pageIndex: Int,
        widthPx: Int = 280,
        password: String? = null
    ): Bitmap? = withContext(Dispatchers.IO) {
        val tmp = if (!password.isNullOrEmpty()) {
            File(context.cacheDir, "thumb_${System.currentTimeMillis()}.pdf").also { out ->
                PDDocument.load(file, password).use { doc ->
                    doc.isAllSecurityToBeRemoved = true
                    doc.save(out)
                }
            }
        } else null

        val source = tmp ?: file
        try {
            ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { r ->
                    if (pageIndex !in 0 until r.pageCount) return@withContext null
                    r.openPage(pageIndex).use { page ->
                        val aspect = page.height.toFloat() / page.width.toFloat().coerceAtLeast(1f)
                        val w = widthPx.coerceIn(120, 600)
                        val h = (w * aspect).toInt().coerceIn(120, 900)
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bmp
                    }
                }
            }
        } catch (_: Exception) {
            null
        } finally {
            tmp?.delete()
        }
    }

    suspend fun clearCache() = withContext(Dispatchers.IO) {
        mutex.withLock {
            cache.evictAll()
        }
    }

    suspend fun close() = withContext(Dispatchers.IO) {
        mutex.withLock {
            closeLocked()
        }
    }

    private fun closeLocked() {
        runCatching { renderer?.close() }
        runCatching { pfd?.close() }
        runCatching { unlockedTempFile?.delete() }
        renderer = null
        pfd = null
        unlockedTempFile = null
        currentPath = null
        currentPassword = null
        cache.evictAll()
    }
}
