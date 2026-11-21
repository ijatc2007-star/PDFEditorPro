package com.ibrahimjaber.pdfpro.engine

import java.text.Normalizer

enum class DetectedLanguage(val labelEn: String, val labelAr: String) {
    ARABIC("Arabic", "العربية"),
    ENGLISH("English", "الإنجليزية"),
    BILINGUAL("Arabic & English", "عربي وإنجليزي")
}

data class LanguageStats(
    val primary: DetectedLanguage,
    val arabicPercent: Int,
    val englishPercent: Int,
    val wordCount: Int,
    val estimatedReadMinutes: Int
)

/**
 * Specialized Arabic & English PDF text normalizer and language analyzer.
 * Handles Arabic Presentation Forms (NFKC), diacritic-insensitive search,
 * Hamza/Alef unification, and sentence segmentation for TTS & AI processing.
 * Programmed by Ibrahim Jaber 2026 | برمجة إبراهيم جابر 2026
 */
object ArabicTextProcessor {

    private val DIACRITICS_REGEX = Regex("[\u064B-\u065F\u0670\u0640]")
    private val SENTENCE_SPLIT_REGEX = Regex("(?<=[.!?؟\\n])\\s+")

    /**
     * Normalizes PDF-extracted Arabic Presentation Forms (U+FB50..U+FEFF) into standard
     * logical Unicode so Arabic displays, speaks (TTS), and searches cleanly.
     */
    fun cleanExtractedText(raw: String): String {
        if (raw.isEmpty()) return ""
        val nfkc = Normalizer.normalize(raw, Normalizer.Form.NFKC)
        return nfkc
            .replace("\u200F", "")
            .replace("\u200E", "")
            .replace("\u00A0", " ")
            .replace(Regex("[ \\t]{2,}"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    /**
     * Normalizes Arabic & English text for forgiving keyword search and AI TF-IDF matching:
     * - Strips Arabic Tashkeel (diacritics) and Tatweel
     * - Unifies Alef variants (أ إ آ -> ا), Teh Marbuta (ة -> ه), Alef Maksura (ى -> ي)
     * - Lowercases Latin letters
     */
    fun normalizeForSearch(text: String): String {
        val cleaned = cleanExtractedText(text)
        val noDiacritics = DIACRITICS_REGEX.replace(cleaned, "")
        val sb = StringBuilder(noDiacritics.length)
        for (ch in noDiacritics) {
            when (ch) {
                'أ', 'إ', 'آ', 'ٱ' -> sb.append('ا')
                'ة' -> sb.append('ه')
                'ى' -> sb.append('ي')
                'ؤ' -> sb.append('و')
                'ئ' -> sb.append('ي')
                else -> sb.append(ch.lowercaseChar())
            }
        }
        return sb.toString()
    }

    /**
     * Analyzes document text to compute Arabic vs English ratio, word count, and reading time.
     */
    fun analyzeLanguage(text: String): LanguageStats {
        var arCount = 0
        var enCount = 0
        for (ch in text) {
            when {
                ch in '\u0600'..'\u06FF' || ch in '\u0750'..'\u077F' -> arCount++
                ch in 'A'..'Z' || ch in 'a'..'z' -> enCount++
            }
        }
        val totalLetters = (arCount + enCount).coerceAtLeast(1)
        val arPct = ((arCount * 100.0) / totalLetters).toInt()
        val enPct = 100 - arPct
        val primary = when {
            arPct >= 65 -> DetectedLanguage.ARABIC
            enPct >= 65 -> DetectedLanguage.ENGLISH
            arCount == 0 && enCount == 0 -> DetectedLanguage.ENGLISH
            else -> DetectedLanguage.BILINGUAL
        }
        val words = text.trim().split(Regex("\\s+")).count { it.isNotBlank() }
        val mins = (words / 190.0).let { if (words == 0) 0 else kotlin.math.ceil(it).toInt().coerceAtLeast(1) }
        return LanguageStats(
            primary = primary,
            arabicPercent = arPct,
            englishPercent = enPct,
            wordCount = words,
            estimatedReadMinutes = mins
        )
    }

    /**
     * Splits bilingual Arabic/English text into clean sentences suitable for TTS or AI scoring.
     */
    fun splitSentences(text: String): List<String> {
        val cleaned = cleanExtractedText(text)
        if (cleaned.isBlank()) return emptyList()
        return cleaned.split(SENTENCE_SPLIT_REGEX)
            .map { it.replace(Regex("\\s+"), " ").trim() }
            .filter { it.length >= 12 }
    }

    /**
     * Splits text into TTS-friendly chunks under Android's 4000-char utterance limit.
     */
    fun chunkForTts(text: String, maxChunkChars: Int = 900): List<String> {
        val cleaned = cleanExtractedText(text)
        if (cleaned.isBlank()) return emptyList()
        val sentences = cleaned.split(SENTENCE_SPLIT_REGEX).map { it.trim() }.filter { it.isNotEmpty() }
        val chunks = mutableListOf<String>()
        val current = StringBuilder()
        for (sent in sentences) {
            if (current.length + sent.length + 1 > maxChunkChars && current.isNotEmpty()) {
                chunks.add(current.toString().trim())
                current.setLength(0)
            }
            if (sent.length > maxChunkChars) {
                var i = 0
                while (i < sent.length) {
                    chunks.add(sent.substring(i, (i + maxChunkChars).coerceAtMost(sent.length)))
                    i += maxChunkChars
                }
            } else {
                if (current.isNotEmpty()) current.append(' ')
                current.append(sent)
            }
        }
        if (current.isNotEmpty()) chunks.add(current.toString().trim())
        return chunks
    }
}
