package com.ibrahimjaber.pdfpro.engine

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Offline file import/export/share helpers using Storage Access Framework & FileProvider.
 */
object FileHelper {

    fun pdfsDir(context: Context): File = File(context.filesDir, "pdfs").apply { mkdirs() }
    fun exportsDir(context: Context): File = File(context.filesDir, "exports").apply { mkdirs() }
    fun signaturesDir(context: Context): File = File(context.filesDir, "signatures").apply { mkdirs() }

    fun queryDisplayName(context: Context, uri: Uri, fallback: String): String {
        val name = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
        }.getOrNull()
        return name?.takeIf { it.isNotBlank() } ?: fallback
    }

    fun importPdfFromUri(context: Context, uri: Uri): File? {
        val rawName = queryDisplayName(context, uri, "document_${System.currentTimeMillis()}.pdf")
        val cleanName = sanitizeFileName(rawName, ".pdf")
        val destFile = uniqueFile(pdfsDir(context), cleanName)
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return null
            destFile
        }.getOrNull()
    }

    fun copyImageFromUriToCache(context: Context, uri: Uri, index: Int): File? {
        val rawName = queryDisplayName(context, uri, "img_${System.currentTimeMillis()}_$index.jpg")
        val destFile = File(context.cacheDir, "import_${System.currentTimeMillis()}_${index}_${rawName.takeLast(24)}")
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return null
            destFile
        }.getOrNull()
    }

    fun exportFileToUri(context: Context, sourceFile: File, targetUri: Uri): Boolean {
        return runCatching {
            context.contentResolver.openOutputStream(targetUri)?.use { output ->
                sourceFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            } != null
        }.getOrDefault(false)
    }

    fun shareFile(context: Context, file: File, mimeType: String = "application/pdf") {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share ${file.name}"))
    }

    fun shareMultipleFiles(context: Context, files: List<File>, mimeType: String = "image/*") {
        if (files.isEmpty()) return
        if (files.size == 1) {
            shareFile(context, files.first(), mimeType)
            return
        }
        val uris = ArrayList<Uri>(files.map {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it)
        })
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = mimeType
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share ${files.size} files"))
    }

    fun uniqueFile(dir: File, desiredName: String): File {
        dir.mkdirs()
        var candidate = File(dir, desiredName)
        if (!candidate.exists()) return candidate
        val dot = desiredName.lastIndexOf('.')
        val stem = if (dot > 0) desiredName.substring(0, dot) else desiredName
        val ext = if (dot > 0) desiredName.substring(dot) else ""
        var counter = 1
        while (candidate.exists()) {
            candidate = File(dir, "${stem}_$counter$ext")
            counter++
        }
        return candidate
    }

    fun sanitizeFileName(raw: String, requiredExt: String): String {
        val cleaned = raw.trim()
            .replace(Regex("[^a-zA-Z0-9._\\-\\u0600-\\u06FF ]"), "_")
            .ifBlank { "document_${System.currentTimeMillis()}" }
        return if (cleaned.endsWith(requiredExt, ignoreCase = true)) cleaned else "$cleaned$requiredExt"
    }

    fun formatDate(timestampMs: Long): String {
        val sdf = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault())
        return sdf.format(Date(timestampMs))
    }

    fun clearTempCache(context: Context): Long {
        var freed = 0L
        context.cacheDir.listFiles()?.forEach { f ->
            if (f.isFile) {
                freed += f.length()
                f.delete()
            }
        }
        return freed
    }
}
