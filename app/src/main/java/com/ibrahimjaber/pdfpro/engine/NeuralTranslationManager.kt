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
import kotlinx.coroutines.withTimeout
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class SavedTranslationRecord(
    val id: String = UUID.randomUUID().toString(),
    val sourceText: String,
    val translatedText: String,
    val directionLabel: String,
    val engineUsed: String,
    val sourceLabel: String = "Document",
    val timestamp: Long = System.currentTimeMillis()
)

data class SmartTranslationResult(
    val translatedText: String,
    val usedNeuralOfflineModel: Boolean,
    val usedFullArabEyesDict: Boolean = false,
    val fromLocalCache: Boolean,
    val effectiveDirection: TranslationDirection
)

data class OfflinePackStatus(
    val neuralModelReady: Boolean,
    val arabEyesWordCount: Int,
    val savedCacheCount: Int
) {
    val isAnyFullPackReady: Boolean get() = neuralModelReady || arabEyesWordCount >= 1000
}

data class DictionaryDownloadReport(
    val neuralReady: Boolean,
    val arabEyesWordCount: Int,
    val neuralMessage: String,
    val dictMessage: String
) {
    val succeeded: Boolean get() = neuralReady || arabEyesWordCount >= 1000
}

/**
 * Bulletproof Dual-Engine Offline Arabic <-> English Translator & Local Translation Memory (v5.1):
 *
 * 1. Direct HTTPS ArabEyes Complete Bilingual Dictionary (~87,400 EN <-> AR entries) downloaded straight
 *    to app storage (`filesDir/translations/arabeyes_en_ar.tsv`) — works on ALL Android devices
 *    including Huawei/Xiaomi/devices without Google Play Services.
 * 2. Google ML Kit On-Device Neural Translation Pack (Arabic <-> English full-sentence NMT ~30 MB) —
 *    properly downloads ONLY the Arabic remote model (English is ML Kit's built-in pivot language!)
 *    via `downloadModelIfNeeded(DownloadConditions.Builder().build())`.
 * 3. Automatic Local Translation Memory (`translation_cache.json`) & Saved Translations History
 *    (`saved_translations.json`) so every translated document or camera capture is saved locally forever.
 *
 * Programmed by Ibrahim Jaber 2026 | برمجة إبراهيم جابر 2026
 */
class NeuralTranslationManager(private val context: Context) {

    companion object {
        private const val ARABEYES_SQL_URL =
            "https://raw.githubusercontent.com/usefksa/engAraDictionaryFrom_ArabEyes/master/engAraDictionary.sql"
        private val SQL_ROW_REGEX = Regex("\\(\\d+,\\s*'([^']{1,60})',\\s*'([^']{1,160})'\\)")
    }

    private val gson = Gson()
    private val modelManager: RemoteModelManager by lazy { RemoteModelManager.getInstance() }
    private val storageDir: File get() = File(context.filesDir, "translations").apply { mkdirs() }
    private val historyFile: File get() = File(storageDir, "saved_translations.json")
    private val cacheFile: File get() = File(storageDir, "translation_cache.json")
    private val fullDictFile: File get() = File(storageDir, "arabeyes_en_ar.tsv")

    private val cacheLock = Any()
    private var memoryCache: MutableMap<String, String>? = null
    private var loadedEnToAr: HashMap<String, String>? = null
    private var loadedArToEn: HashMap<String, String>? = null
    private var cachedDictCount: Int = -1

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
     * Checks ML Kit Arabic neural model status (English is built-in pivot in ML Kit — never check/download EN separately!).
     */
    suspend fun isNeuralModelReady(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val arModel = TranslateRemoteModel.Builder(TranslateLanguage.ARABIC).build()
            modelManager.isModelDownloaded(arModel).awaitTask()
        }.getOrDefault(false)
    }

    /**
     * Counts locally downloaded ArabEyes EN <-> AR dictionary entries.
     */
    suspend fun getDownloadedDictionaryWordCount(): Int = withContext(Dispatchers.IO) {
        if (cachedDictCount >= 0 && fullDictFile.exists()) return@withContext cachedDictCount
        if (!fullDictFile.exists() || fullDictFile.length() < 1024L) {
            cachedDictCount = 0
            return@withContext 0
        }
        val cnt = runCatching {
            var lines = 0
            BufferedReader(InputStreamReader(FileInputStream(fullDictFile), Charsets.UTF_8)).use { br ->
                while (br.readLine() != null) lines++
            }
            lines
        }.getOrDefault(0)
        cachedDictCount = cnt
        cnt
    }

    /**
     * Returns combined offline status (ML Kit Neural Pack + 87,400-word ArabEyes Dictionary + Saved Cache).
     */
    suspend fun getOfflinePackStatus(): OfflinePackStatus = withContext(Dispatchers.IO) {
        OfflinePackStatus(
            neuralModelReady = isNeuralModelReady(),
            arabEyesWordCount = getDownloadedDictionaryWordCount(),
            savedCacheCount = getLocalCacheCount()
        )
    }

    /**
     * Backward-compatible boolean check: true if either the ML Kit Neural Pack OR the 87k ArabEyes Dictionary is installed.
     */
    suspend fun areOfflineModelsReady(): Boolean = getOfflinePackStatus().isAnyFullPackReady

    /**
     * Dual-Engine Offline Pack Downloader:
     * 1. Downloads the complete 87,400-word ArabEyes EN <-> AR dictionary directly over HTTPS (works on ALL phones).
     * 2. Downloads Google ML Kit On-Device Arabic <-> English Neural Translation model (~30 MB) via `downloadModelIfNeeded`.
     * Never fails silently — returns exact status for both engines.
     */
    suspend fun downloadCompleteOfflinePacks(
        wifiOnly: Boolean = false,
        onStatus: ((String) -> Unit)? = null
    ): DictionaryDownloadReport = withContext(Dispatchers.IO) {
        // STEP 1: Direct HTTPS ArabEyes 87,400-word Dictionary (No Google Play Services required!)
        var wordCount = getDownloadedDictionaryWordCount()
        var dictMsg = if (wordCount >= 1000) "ArabEyes dictionary ready ($wordCount entries)" else ""

        if (wordCount < 1000) {
            onStatus?.invoke("Downloading 87,000-word Arabic ↔ English dictionary (HTTPS)...")
            val dictRes = downloadArabEyesDictionaryHttps(onStatus)
            if (dictRes.isSuccess) {
                wordCount = dictRes.getOrDefault(0)
                dictMsg = "✓ Downloaded $wordCount English ↔ Arabic dictionary entries!"
            } else {
                dictMsg = "Dictionary download error: ${dictRes.exceptionOrNull()?.message ?: "Check internet connection"}"
            }
        }

        // STEP 2: Google ML Kit On-Device Neural Translation Model (AR <-> EN full-sentence NMT)
        var neuralOk = isNeuralModelReady()
        var neuralMsg = if (neuralOk) "✓ Neural AR↔EN full-sentence model ready" else ""

        if (!neuralOk) {
            onStatus?.invoke("Downloading Neural full-sentence AR ↔ EN model (~30 MB)...")
            val mlRes = runCatching {
                val conditions = DownloadConditions.Builder().apply {
                    if (wifiOnly) requireWifi()
                }.build()

                // IMPORTANT: Use Translator.downloadModelIfNeeded (NOT RemoteModelManager.download(ENGLISH)!)
                val enToAr = Translation.getClient(
                    TranslatorOptions.Builder()
                        .setSourceLanguage(TranslateLanguage.ENGLISH)
                        .setTargetLanguage(TranslateLanguage.ARABIC)
                        .build()
                )
                try {
                    withTimeout(90_000L) {
                        enToAr.downloadModelIfNeeded(conditions).awaitTask()
                    }
                } finally {
                    enToAr.close()
                }
                isNeuralModelReady()
            }
            if (mlRes.isSuccess && mlRes.getOrDefault(false)) {
                neuralOk = true
                neuralMsg = "✓ Neural AR↔EN full-sentence model downloaded!"
            } else {
                val errDetail = mlRes.exceptionOrNull()?.localizedMessage ?: "Google Play Services DownloadManager unavailable"
                neuralMsg = "Neural pack skipped ($errDetail)"
            }
        }

        onStatus?.invoke(
            when {
                neuralOk && wordCount >= 1000 -> "✓ Full Neural Pack + $wordCount-word AR↔EN Dictionary Ready Offline!"
                neuralOk -> "✓ Full Neural AR↔EN Pack Ready Offline!"
                wordCount >= 1000 -> "✓ $wordCount-word AR↔EN Offline Dictionary Ready!"
                else -> "Download failed — please check internet connection."
            }
        )

        DictionaryDownloadReport(
            neuralReady = neuralOk,
            arabEyesWordCount = wordCount,
            neuralMessage = neuralMsg,
            dictMessage = dictMsg
        )
    }

    /**
     * Backward-compatible wrapper returning Result<Unit>.
     */
    suspend fun downloadArabicEnglishModels(
        wifiOnly: Boolean = false,
        onStatus: ((String) -> Unit)? = null
    ): Result<Unit> {
        val rep = downloadCompleteOfflinePacks(wifiOnly = wifiOnly, onStatus = onStatus)
        return if (rep.succeeded) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("${rep.dictMessage} | ${rep.neuralMessage}"))
        }
    }

    private fun downloadArabEyesDictionaryHttps(onStatus: ((String) -> Unit)?): Result<Int> = runCatching {
        val conn = (URL(ARABEYES_SQL_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "PDFEditorPro/5.1 Android")
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code from dictionary server")
            }
            val tmpFile = File(storageDir, "arabeyes_en_ar.tsv.tmp")
            var count = 0
            BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { reader ->
                BufferedWriter(OutputStreamWriter(FileOutputStream(tmpFile), Charsets.UTF_8)).use { writer ->
                    var line = reader.readLine()
                    while (line != null) {
                        val m = SQL_ROW_REGEX.find(line)
                        if (m != null) {
                            val en = m.groupValues[1].trim().replace('\t', ' ')
                            val ar = m.groupValues[2].trim().replace('\t', ' ')
                            if (en.isNotEmpty() && ar.isNotEmpty()) {
                                writer.write(en)
                                writer.write('\t'.code)
                                writer.write(ar)
                                writer.newLine()
                                count++
                                if (count % 15000 == 0) {
                                    onStatus?.invoke("Saved $count English ↔ Arabic words locally...")
                                }
                            }
                        }
                        line = reader.readLine()
                    }
                }
            }
            if (count < 1000) {
                tmpFile.delete()
                throw IllegalStateException("Dictionary response incomplete ($count rows)")
            }
            if (fullDictFile.exists()) fullDictFile.delete()
            tmpFile.renameTo(fullDictFile)

            synchronized(cacheLock) {
                cachedDictCount = count
                loadedEnToAr = null
                loadedArToEn = null
            }
            count
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Deletes downloaded neural language pack & ArabEyes dictionary file to free storage.
     */
    suspend fun deleteOfflineModels(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val arModel = TranslateRemoteModel.Builder(TranslateLanguage.ARABIC).build()
            runCatching { modelManager.deleteDownloadedModel(arModel).awaitTask() }
            if (fullDictFile.exists()) fullDictFile.delete()
            synchronized(cacheLock) {
                cachedDictCount = 0
                loadedEnToAr = null
                loadedArToEn = null
            }
            Unit
        }
    }

    /**
     * Translates arbitrary text or multi-paragraph documents 100% OFFLINE:
     * 1. Instant 0ms Local Cache lookup.
     * 2. Downloaded Google ML Kit Neural AR <-> EN model (full grammatical sentences).
     * 3. Downloaded 87,400-word ArabEyes Local Dictionary + Phrase Engine.
     * 4. Built-in Offline Lexicon fallback.
     * Always saves translations locally for offline history & recall.
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
            return@withContext SmartTranslationResult("", false, false, false, TranslationDirection.EN_TO_AR)
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
                usedFullArabEyesDict = true,
                fromLocalCache = true,
                effectiveDirection = effectiveDir
            )
        }

        val neuralReady = isNeuralModelReady()
        val hasFullDict = getDownloadedDictionaryWordCount() >= 1000
        val translated: String
        var usedNeural = false
        var usedDict = false

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
                val chunks = splitIntoChunks(cleaned, maxChars = 700)
                val outChunks = ArrayList<String>(chunks.size)
                for ((idx, chunk) in chunks.withIndex()) {
                    currentCoroutineContext().ensureActive()
                    onChunkProgress?.invoke(idx + 1, chunks.size)
                    if (chunk.isBlank()) {
                        outChunks.add("")
                    } else {
                        val tr = runCatching { translator.translate(chunk).awaitTask() }
                            .getOrElse { translateWithFullArabEyesOrBuiltin(chunk, effectiveDir) }
                        outChunks.add(tr)
                    }
                }
                translated = outChunks.joinToString("\n")
                usedNeural = true
            } finally {
                translator.close()
            }
        } else if (hasFullDict) {
            translated = translateWithFullArabEyesOrBuiltin(cleaned, effectiveDir)
            usedDict = true
        } else {
            translated = OfflineTranslatorEngine.translate(cleaned, effectiveDir)
        }

        if ((usedNeural || usedDict) && translated.isNotBlank()) {
            storeCache(key, translated)
        }
        if (saveToHistory && translated.isNotBlank()) {
            val engineTag = when {
                usedNeural -> "Neural Offline (AR+EN Pack)"
                usedDict -> "ArabEyes 87k Offline Dictionary"
                else -> "Built-in Offline Dictionary"
            }
            appendHistoryRecord(
                SavedTranslationRecord(
                    sourceText = cleaned.take(1500),
                    translatedText = translated.take(2500),
                    directionLabel = if (effectiveDir == TranslationDirection.EN_TO_AR) "EN → AR" else "AR → EN",
                    engineUsed = engineTag,
                    sourceLabel = sourceLabel
                )
            )
        }

        SmartTranslationResult(
            translatedText = translated,
            usedNeuralOfflineModel = usedNeural,
            usedFullArabEyesDict = usedDict,
            fromLocalCache = false,
            effectiveDirection = effectiveDir
        )
    }

    private fun ensureFullDictLoaded(): Pair<HashMap<String, String>, HashMap<String, String>> {
        synchronized(cacheLock) {
            val e2a = loadedEnToAr
            val a2e = loadedArToEn
            if (e2a != null && a2e != null) return e2a to a2e

            val enMap = HashMap<String, String>(95_000)
            val arMap = HashMap<String, String>(95_000)
            if (fullDictFile.exists()) {
                runCatching {
                    BufferedReader(InputStreamReader(FileInputStream(fullDictFile), Charsets.UTF_8)).use { br ->
                        var line = br.readLine()
                        while (line != null) {
                            val tab = line.indexOf('\t')
                            if (tab > 0 && tab < line.length - 1) {
                                val en = line.substring(0, tab).trim().lowercase()
                                val ar = line.substring(tab + 1).trim()
                                if (en.isNotEmpty() && ar.isNotEmpty()) {
                                    enMap.putIfAbsent(en, ar)
                                    val firstAr = ar.split("::", ";", "،", ",").first().trim()
                                    val normAr = ArabicTextProcessor.normalizeForSearch(firstAr)
                                    if (normAr.isNotEmpty()) {
                                        arMap.putIfAbsent(normAr, en)
                                        if (normAr.startsWith("ال") && normAr.length > 3) {
                                            arMap.putIfAbsent(normAr.removePrefix("ال"), en)
                                        }
                                    }
                                }
                            }
                            line = br.readLine()
                        }
                    }
                }
            }
            loadedEnToAr = enMap
            loadedArToEn = arMap
            return enMap to arMap
        }
    }

    private fun translateWithFullArabEyesOrBuiltin(text: String, direction: TranslationDirection): String {
        val (enMap, arMap) = ensureFullDictLoaded()
        if (enMap.isEmpty()) return OfflineTranslatorEngine.translate(text, direction)

        return text.lines().joinToString("\n") { line ->
            if (line.isBlank()) ""
            else if (direction == TranslationDirection.EN_TO_AR) {
                Regex("[A-Za-z]+(?:['-][A-Za-z]+)?").replace(line) { m ->
                    val w = m.value.lowercase()
                    val hit = enMap[w]
                        ?: enMap[w.removeSuffix("s")]
                        ?: enMap[w.removeSuffix("es")]
                        ?: enMap[w.removeSuffix("ed")]
                        ?: enMap[w.removeSuffix("ing")]
                        ?: enMap[w.removeSuffix("ly")]
                    hit?.split("::", ";")?.first()?.trim()
                        ?: OfflineTranslatorEngine.translate(m.value, TranslationDirection.EN_TO_AR)
                }
            } else {
                Regex("[\u0600-\u06FF]+").replace(line) { m ->
                    val norm = ArabicTextProcessor.normalizeForSearch(m.value)
                    arMap[norm]
                        ?: (if (norm.startsWith("ال") && norm.length > 3) arMap[norm.removePrefix("ال")] else null)
                        ?: (if (norm.startsWith("و") && norm.length > 3) arMap[norm.removePrefix("و")]?.let { "and $it" } else null)
                        ?: (if (norm.startsWith("ب") && norm.length > 3) arMap[norm.removePrefix("ب")]?.let { "with $it" } else null)
                        ?: (if (norm.startsWith("ل") && norm.length > 3) arMap[norm.removePrefix("ل")]?.let { "for $it" } else null)
                        ?: OfflineTranslatorEngine.translate(m.value, TranslationDirection.AR_TO_EN)
                }
            }
        }
    }

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
