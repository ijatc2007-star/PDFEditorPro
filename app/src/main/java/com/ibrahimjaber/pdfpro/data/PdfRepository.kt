package com.ibrahimjaber.pdfpro.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Offline local repository persisting library metadata, favorites, bookmarks,
 * saved handwritten signatures, and app settings in app-private storage.
 */
class PdfRepository(private val context: Context) {

    private val gson = Gson()
    private val storeDir = File(context.filesDir, "store").apply { mkdirs() }
    private val docsFile = File(storeDir, "documents.json")
    private val bookmarksFile = File(storeDir, "bookmarks.json")
    private val signaturesFile = File(storeDir, "signatures.json")
    private val settingsFile = File(storeDir, "settings.json")

    private val _documents = MutableStateFlow<List<PdfDocumentItem>>(emptyList())
    val documents: StateFlow<List<PdfDocumentItem>> = _documents.asStateFlow()

    private val _bookmarks = MutableStateFlow<List<PdfBookmark>>(emptyList())
    val bookmarks: StateFlow<List<PdfBookmark>> = _bookmarks.asStateFlow()

    private val _signatures = MutableStateFlow<List<SavedSignature>>(emptyList())
    val signatures: StateFlow<List<SavedSignature>> = _signatures.asStateFlow()

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    init {
        reloadAll()
    }

    fun reloadAll() {
        _documents.value = loadList<PdfDocumentItem>(docsFile)
            .filter { File(it.path).exists() }
            .sortedByDescending { it.lastOpenedAt }
        _bookmarks.value = loadList(bookmarksFile)
        _signatures.value = loadList<SavedSignature>(signaturesFile)
            .filter { File(it.imagePath).exists() }
            .sortedByDescending { it.createdAt }
        _settings.value = loadSettings()
    }

    @Synchronized
    fun upsertDocument(item: PdfDocumentItem) {
        val current = _documents.value.toMutableList()
        val idx = current.indexOfFirst { it.path == item.path }
        if (idx >= 0) {
            val existing = current[idx]
            current[idx] = item.copy(
                id = existing.id,
                isFavorite = existing.isFavorite || item.isFavorite
            )
        } else {
            current.add(0, item)
        }
        val sorted = current.sortedByDescending { it.lastOpenedAt }
        _documents.value = sorted
        saveJson(docsFile, sorted)
    }

    @Synchronized
    fun updateLastPage(path: String, pageIndex: Int) {
        val updated = _documents.value.map { doc ->
            if (doc.path == path) {
                doc.copy(lastPageIndex = pageIndex, lastOpenedAt = System.currentTimeMillis())
            } else doc
        }.sortedByDescending { it.lastOpenedAt }
        _documents.value = updated
        saveJson(docsFile, updated)
    }

    @Synchronized
    fun toggleFavorite(path: String) {
        val updated = _documents.value.map { doc ->
            if (doc.path == path) doc.copy(isFavorite = !doc.isFavorite) else doc
        }
        _documents.value = updated
        saveJson(docsFile, updated)
    }

    @Synchronized
    fun renameDocument(path: String, newDisplayName: String): PdfDocumentItem? {
        val oldFile = File(path)
        if (!oldFile.exists()) return null
        val sanitized = newDisplayName.trim()
            .replace(Regex("[^a-zA-Z0-9._\\-\\u0600-\\u06FF ]"), "_")
            .let { if (it.endsWith(".pdf", ignoreCase = true)) it else "$it.pdf" }
        val newFile = File(oldFile.parentFile, sanitized)
        if (newFile.exists() && newFile.absolutePath != oldFile.absolutePath) return null
        if (!oldFile.renameTo(newFile)) return null

        var renamed: PdfDocumentItem? = null
        val updated = _documents.value.map { doc ->
            if (doc.path == path) {
                doc.copy(name = newFile.name, path = newFile.absolutePath).also { renamed = it }
            } else doc
        }
        _documents.value = updated
        saveJson(docsFile, updated)

        val updatedBm = _bookmarks.value.map { bm ->
            if (bm.docPath == path) bm.copy(docPath = newFile.absolutePath) else bm
        }
        _bookmarks.value = updatedBm
        saveJson(bookmarksFile, updatedBm)
        return renamed
    }

    @Synchronized
    fun deleteDocument(path: String, deleteFileOnDisk: Boolean = true) {
        if (deleteFileOnDisk) {
            runCatching { File(path).delete() }
        }
        val updated = _documents.value.filterNot { it.path == path }
        _documents.value = updated
        saveJson(docsFile, updated)

        val updatedBm = _bookmarks.value.filterNot { it.docPath == path }
        _bookmarks.value = updatedBm
        saveJson(bookmarksFile, updatedBm)
    }

    fun getDocumentByPath(path: String): PdfDocumentItem? =
        _documents.value.firstOrNull { it.path == path }

    @Synchronized
    fun addBookmark(docPath: String, pageIndex: Int, title: String) {
        val current = _bookmarks.value.toMutableList()
        val exists = current.any { it.docPath == docPath && it.pageIndex == pageIndex }
        if (!exists) {
            current.add(0, PdfBookmark(docPath = docPath, pageIndex = pageIndex, title = title))
            _bookmarks.value = current
            saveJson(bookmarksFile, current)
        }
    }

    @Synchronized
    fun removeBookmark(id: String) {
        val updated = _bookmarks.value.filterNot { it.id == id }
        _bookmarks.value = updated
        saveJson(bookmarksFile, updated)
    }

    fun bookmarksFor(docPath: String): List<PdfBookmark> =
        _bookmarks.value.filter { it.docPath == docPath }.sortedBy { it.pageIndex }

    @Synchronized
    fun addSignature(sig: SavedSignature) {
        val updated = listOf(sig) + _signatures.value
        _signatures.value = updated
        saveJson(signaturesFile, updated)
    }

    @Synchronized
    fun deleteSignature(id: String) {
        val target = _signatures.value.firstOrNull { it.id == id }
        target?.let { runCatching { File(it.imagePath).delete() } }
        val updated = _signatures.value.filterNot { it.id == id }
        _signatures.value = updated
        saveJson(signaturesFile, updated)
    }

    @Synchronized
    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val updated = transform(_settings.value)
        _settings.value = updated
        saveJson(settingsFile, updated)
    }

    private inline fun <reified T> loadList(file: File): List<T> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val type = object : TypeToken<List<T>>() {}.type
            gson.fromJson<List<T>>(file.readText(), type) ?: emptyList()
        }.getOrDefault(emptyList())
    }

    private fun loadSettings(): AppSettings {
        if (!settingsFile.exists()) return AppSettings()
        return runCatching {
            gson.fromJson(settingsFile.readText(), AppSettings::class.java) ?: AppSettings()
        }.getOrDefault(AppSettings())
    }

    private fun saveJson(file: File, value: Any) {
        runCatching {
            file.writeText(gson.toJson(value))
        }
    }
}
