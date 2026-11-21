package com.ibrahimjaber.pdfpro.engine

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class SavedTranslationRecord(
    val id: String = UUID.randomUUID().toString(),
    val sourceText: String,
    val translatedText: String,
    val directionLabel: String,
    val engineUsed: String, // "Neural Offline (ML Kit)" | "Local Cache" | "Built-in Dictionary"
    val sourceLabel: String = "Document",
    val timestamp: Long = System.currentTimeMillis()
)

data class SmartTranslationResult(
    val translatedText: String,
    val usedNeuralOfflineModel: Boolean,
    val fromLocalCache: Boolean,
    val effectiveDirection: TranslationDirection
)

/**
 * Downloadable Full Offline Arabic <-> English Neural Translation Engine & Local Translation Memory.
 *
 * - Downloads the complete Google ML Kit On-Device Neural Translation packs (~30 MB for Arabic & English)
 *   once over HTTPS when the user taps "Download Offline AR+EN Pack".
 * - After download, translates FULL grammatical sentences & entire PDF documents 100% OFFLINE.
 * - Saves every translation locally (JSON cache + Saved Translations History) for instant offline reuse.
 * - Falls back seamlessly to the built-in offline phrase/dictionary engine if models aren't downloaded yet.
 *
 * Programmed by Ibrahim Jaber 2026 | برمجة إبراهيم جابر 2026
 */
class NeuralTranslationManager(private val context: Context) {

    private val gson = Gson()
    private val modelManager: RemoteModelManager by lazy { RemoteModelManager.getInstance() }
    private val storageDir: File get() = File(context.filesDir, "translations").apply { mkdirs() }
    private val historyFile: File get() = File(storageDir, "saved_translations.json")
    private val cacheFile: File get() = File(storageDir, "translation_cache.json")

    private val cacheLock = Any()
    private var memoryCache: MutableMap<String, String>? = null

    private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { result ->
            if (cont.isActive) cont.resume(result)
        }
        addOnFailureListener { exc ->
            if (cont.isActive) cont.resumeWithException(exc)
        }
        addOnCanceledListener {
            if (cont.isActive) cont.cancel()
        }
    }

    /**
     * Checks whether the complete offline Arabic & English neural models are downloaded locally.
     */
    suspend fun areOfflineModelsReady(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val arModel = TranslateRemoteModel.Builder(TranslateLanguage.ARABIC).build()
            val enModel = TranslateRemoteModel.Builder(TranslateLanguage.ENGLISH).build()
            val hasAr = modelManager.isModelDownloaded(arModel).awaitTask()
            val hasEn = modelManager.isModelDownloaded(enModel).awaitTask()
            hasAr && hasEn
        }.getOrDefault(false)
    }

    /**
     * Downloads the complete Arabic + English on-device neural translation models (~30 MB total)
     * from the internet once so all future translations (PDFs + Live Camera) are accurate, full-sentence, and 100% offline.
     */
    suspend fun downloadArabicEnglishModels(
        wifiOnly: Boolean = false,
        onStatus: ((String) -> Unit)? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val conditions = DownloadConditions.Builder().apply {
                if (wifiOnly) requireWifi()
            }.build()

            onStatus?.invoke("Downloading Arabic neural translation pack...")
            val arModel = TranslateRemoteModel.Builder(TranslateLanguage.ARABIC).build()
            modelManager.download(arModel, conditions).awaitTask()

            onStatus?.invoke("Downloading English neural translation pack...")
            val enModel = TranslateRemoteModel.Builder(TranslateLanguage.ENGLISH).build()
            modelManager.download(enModel, conditions).awaitTask()

            // Warm up both translator directions to verify local model readiness
            onStatus?.invoke("Verifying offline Arabic ↔ English engine...")
            val enToAr = Translation.getClient(
                TranslatorOptions.Builder()
                    .setSourceLanguage(TranslateLanguage.ENGLISH)
                    .setTargetLanguage(TranslateLanguage.ARABIC)
                    .build()
            )
            try {
                enToAr.downloadModelIfNeeded(conditions).awaitTask()
            } finally {
                enToAr.close()
            }
            onStatus?.invoke("Complete Arabic & English offline dictionary ready!")
        }
    }

    /**
     * Deletes downloaded neural language packs to free device storage.
     */
    suspend fun deleteOfflineModels(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val arModel = TranslateRemoteModel.Builder(TranslateLanguage.ARABIC).build()
            val enModel = TranslateRemoteModel.Builder(TranslateLanguage.ENGLISH).build()
            runCatching { modelManager.deleteDownloadedModel(arModel).awaitTask() }
            runCatching { modelManager.deleteDownloadedModel(enModel).awaitTask() }
        }
    }

    /**
     * Translates arbitrary text or multi-paragraph documents 100% offline:
     * 1. Checks local saved cache first (instant 0ms recall).
     * 2. Uses downloaded full-sentence ML Kit Neural AR <-> EN model if installed.
     * 3. Falls back cleanly to OfflineTranslatorEngine lexicon if not yet downloaded.
     * 4. Saves translation locally into both cache and Saved Translations History.
     */
    suspend fun translateSmartOffline(
        text: String,
        direction: TranslationDirection = TranslationDirection.AUTO,
        sourceLabel: String = "Document",
        saveToHistory: Boolean = true,
        onChunkProgress: ((current: Int, total: Int) -> Unit)? = null
    ): SmartTranslationResult = withContext(Dispatchers.IO) {
        val cleaned = ArabicTextProcessor.cleanExtractedText(text)
        if (cleaned.isBlank()) {
            return@withContext SmartTranslationResult("", false, false, TranslationDirection.EN_TO_AR)
        }

        val effectiveDir = when (direction) {
            TranslationDirection.AUTO -> {
                val stats = ArabicTextProcessor.analyzeLanguage(cleaned)
                if (stats.arabicPercent >= 45) TranslationDirection.AR_TO_EN else TranslationDirection.EN_TO_AR
            }
            else -> direction
        }

        val key = cacheKey(effectiveDir.name, cleaned)
        val cached = lookupCache(key)
        if (cached != null) {
            return@withContext SmartTranslationResult(
                translatedText = cached,
                usedNeuralOfflineModel = true,
                fromLocalCache = true,
                effectiveDirection = effectiveDir
            )
        }

        val neuralReady = areOfflineModelsReady()
        val translated: String
        val usedNeural: Boolean

        if (neuralReady) {
            val srcLang = if (effectiveDir == TranslationDirection.EN_TO_AR) TranslateLanguage.ENGLISH else TranslateLanguage.ARABIC
            val dstLang = if (effectiveDir == TranslationDirection.EN_TO_AR) TranslateLanguage.ARABIC else TranslateLanguage.ENGLISH
            val translator = Translation.getClient(
                TranslatorOptions.Builder()
                    .setSourceLanguage(srcLang)
                    .setTargetLanguage(dstLang)
                    .build()
            )
            try {
                // Split into manageable paragraphs (~700 chars max) for crisp neural translation on large PDFs
                val chunks = splitIntoChunks(cleaned, maxChars = 700)
                val outChunks = ArrayList<String>(chunks.size)
                for ((idx, chunk) in chunks.withIndex()) {
                    currentCoroutineContext().ensureActive()
                    onChunkProgress?.invoke(idx + 1, chunks.size)
                    if (chunk.isBlank()) {
                        outChunks.add("")
                    } else {
                        val tr = runCatching { translator.translate(chunk).awaitTask() }
                            .getOrElse { OfflineTranslatorEngine.translate(chunk, effectiveDir) }
                        outChunks.add(tr)
                    }
                }
                translated = outChunks.joinToString("\n")
                usedNeural = true
            } finally {
                translator.close()
            }
        } else {
            translated = OfflineTranslatorEngine.translate(cleaned, effectiveDir)
            usedNeural = false
        }

        // Persist locally so future lookups are instant and strictly offline
        if (usedNeural && translated.isNotBlank()) {
            storeCache(key, translated)
        }
        if (saveToHistory && translated.isNotBlank()) {
            appendHistoryRecord(
                SavedTranslationRecord(
                    sourceText = cleaned.take(1500),
                    translatedText = translated.take(2500),
                    directionLabel = if (effectiveDir == TranslationDirection.EN_TO_AR) "EN → AR" else "AR → EN",
                    engineUsed = if (usedNeural) "Neural Offline (AR+EN Pack)" else "Built-in Offline Dictionary",
                    sourceLabel = sourceLabel
                )
            )
        }

        SmartTranslationResult(
            translatedText = translated,
            usedNeuralOfflineModel = usedNeural,
            fromLocalCache = false,
            effectiveDirection = effectiveDir
        )
    }

    /**
     * Loads locally saved translations history (newest first).
     */
    fun loadSavedTranslations(): List<SavedTranslationRecord> {
        return synchronized(cacheLock) {
            if (!historyFile.exists()) return@synchronized emptyList()
            runCatching {
                val type = object : TypeToken<List<SavedTranslationRecord>>() {}.type
                gson.fromJson<List<SavedTranslationRecord>>(historyFile.readText(), type) ?: emptyList()
            }.getOrDefault(emptyList())
        }
    }

    fun deleteSavedTranslation(recordId: String) {
        synchronized(cacheLock) {
            val updated = loadSavedTranslations().filterNot { it.id == recordId }
            runCatching { historyFile.writeText(gson.toJson(updated)) }
        }
    }

    fun clearSavedTranslations() {
        synchronized(cacheLock) {
            runCatching { historyFile.delete() }
            runCatching { cacheFile.delete() }
            memoryCache?.clear()
        }
    }

    fun getLocalCacheCount(): Int {
        return synchronized(cacheLock) { ensureCacheLoaded().size }
    }

    private fun appendHistoryRecord(record: SavedTranslationRecord) {
        synchronized(cacheLock) {
            val current = loadSavedTranslations().toMutableList()
            // Deduplicate identical consecutive translations
            current.removeAll { it.sourceText == record.sourceText && it.directionLabel == record.directionLabel }
            current.add(0, record)
            val trimmed = current.take(100)
            runCatching { historyFile.writeText(gson.toJson(trimmed)) }
        }
    }

    private fun lookupCache(key: String): String? = synchronized(cacheLock) {
        ensureCacheLoaded()[key]
    }

    private fun storeCache(key: String, value: String) = synchronized(cacheLock) {
        val map = ensureCacheLoaded()
        map[key] = value
        if (map.size > 400) {
            val oldestKey = map.keys.firstOrNull()
            if (oldestKey != null) map.remove(oldestKey)
        }
        runCatching { cacheFile.writeText(gson.toJson(map)) }
    }

    private fun ensureCacheLoaded(): MutableMap<String, String> {
        memoryCache?.let { return it }
        val loaded = if (cacheFile.exists()) {
            runCatching {
                val type = object : TypeToken<LinkedHashMap<String, String>>() {}.type
                gson.fromJson<LinkedHashMap<String, String>>(cacheFile.readText(), type) ?: linkedMapOf()
            }.getOrDefault(linkedMapOf())
        } else {
            linkedMapOf()
        }
        memoryCache = loaded
        return loaded
    }

    private fun cacheKey(dir: String, text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest("$dir|$text".toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(32)
    }

    private fun splitIntoChunks(text: String, maxChars: Int): List<String> {
        val result = mutableListOf<String>()
        val buf = StringBuilder()
        for (line in text.lines()) {
            if (buf.length + line.length + 1 > maxChars && buf.isNotEmpty()) {
                result.add(buf.toString())
                buf.clear()
            }
            if (line.length > maxChars) {
                if (buf.isNotEmpty()) {
                    result.add(buf.toString())
                    buf.clear()
                }
                var start = 0
                while (start < line.length) {
                    val end = (start + maxChars).coerceAtMost(line.length)
                    result.add(line.substring(start, end))
                    start = end
                }
            } else {
                if (buf.isNotEmpty()) buf.append('\n')
                buf.append(line)
            }
        }
        if (buf.isNotEmpty()) result.add(buf.toString())
        return result
    }
}
