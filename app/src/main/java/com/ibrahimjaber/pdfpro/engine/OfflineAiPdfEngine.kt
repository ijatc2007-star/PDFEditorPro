package com.ibrahimjaber.pdfpro.engine

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.ln
import kotlin.math.min

enum class SummaryDepth(val sentencesCount: Int, val labelEn: String, val labelAr: String) {
    SHORT(3, "Quick (3 pts)", "سريع (3 نقاط)"),
    MEDIUM(5, "Balanced (5 pts)", "متوازن (5 نقاط)"),
    DETAILED(8, "Deep (8 pts)", "مفصّل (8 نقاط)")
}

data class AiHeadingItem(
    val title: String,
    val pageIndex: Int
)

data class AiPiiFinding(
    val category: String,
    val maskedValue: String,
    val pageIndex: Int
)

data class AiDocumentReport(
    val languageStats: LanguageStats,
    val summaryBullets: List<String>,
    val topKeywords: List<String>,
    val datesFound: List<String>,
    val amountsFound: List<String>,
    val emailsFound: List<String>,
    val phonesFound: List<String>,
    val urlsFound: List<String>,
    val actionItems: List<String>,
    val piiFindings: List<AiPiiFinding>,
    val autoHeadings: List<AiHeadingItem>
)

data class AiAnswerPassage(
    val pageIndex: Int,
    val text: String,
    val relevancePercent: Int
)

data class AiQaResponse(
    val question: String,
    val answerSummary: String,
    val passages: List<AiAnswerPassage>
)

/**
 * 100% Offline Bilingual AI PDF Engine (Arabic & English).
 * Runs completely on-device with ZERO internet permission and ZERO cloud API keys:
 * - Extractive TF-IDF + Position-Weighted Summarizer (Arabic & English)
 * - BM25 Semantic Q&A ("Ask Your PDF") with page citations
 * - Smart Entity & Action-Item Extractor (Dates, Currencies, Contacts, Obligations)
 * - Privacy PII Scanner (Emails, Phones, IBANs, Cards, IPs for Redaction review)
 * - Automatic Outline / Smart Bookmark Heading Detector
 *
 * Programmed by Ibrahim Jaber 2026 | برمجة إبراهيم جابر 2026
 */
object OfflineAiPdfEngine {

    private val EN_STOPWORDS = setOf(
        "the", "and", "for", "that", "this", "with", "from", "are", "was", "were", "have", "has",
        "had", "not", "but", "you", "your", "all", "can", "will", "into", "about", "more", "than",
        "also", "they", "their", "them", "been", "being", "which", "when", "where", "what", "who",
        "how", "its", "our", "out", "use", "using", "used", "any", "each", "other", "such", "only",
        "may", "should", "would", "could", "there", "here", "then", "these", "those", "page"
    )

    private val AR_STOPWORDS = setOf(
        "من", "في", "علي", "الى", "إلى", "عن", "مع", "هذا", "هذه", "ذلك", "تلك", "التي", "الذي",
        "الذين", "كان", "كانت", "يكون", "تكون", "ان", "إن", "أن", "لا", "لم", "لن", "ما", "هو",
        "هي", "هم", "نحن", "انت", "أنت", "او", "أو", "ثم", "بعد", "قبل", "بين", "عند", "حتي",
        "حتى", "كل", "بعض", "غير", "كما", "وقد", "فقد", "الي", "بها", "فيها", "عليه", "عليها",
        "منها", "منه", "لها", "له", "صفحه", "الصفحه"
    )

    private val EMAIL_REGEX = Regex("[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}")
    private val URL_REGEX = Regex("https?://[^\\s<>\"']+")
    private val PHONE_REGEX = Regex("(?:\\+?\\d{1,3}[\\s\\-]?)?(?:\\(?\\d{2,4}\\)?[\\s\\-]?){2,4}\\d{3,4}")
    private val DATE_REGEX = Regex(
        "\\b(?:\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}|\\d{1,2}[-/]\\d{1,2}[-/]\\d{2,4}|(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\s+\\d{1,2},?\\s+\\d{4})\\b",
        RegexOption.IGNORE_CASE
    )
    private val AMOUNT_REGEX = Regex(
        "(?:\\$|€|£|USD|EUR|SAR|AED|JOD|EGP)\\s?\\d[\\d,.]*|\\d[\\d,.]*\\s?(?:USD|EUR|SAR|AED|JOD|EGP|ريال|دينار|درهم|دولار|جنيه)",
        RegexOption.IGNORE_CASE
    )
    private val IBAN_REGEX = Regex("\\b[A-Z]{2}\\d{2}[A-Z0-9]{11,26}\\b")
    private val CARD_REGEX = Regex("\\b(?:\\d{4}[\\s\\-]?){3}\\d{4}\\b")
    private val IP_REGEX = Regex("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b")

    private val ACTION_HINTS = listOf(
        "must", "shall", "required", "deadline", "action", "verify", "submit", "approve", "ensure",
        "يجب", "يلزم", "مطلوب", "الموعد", "تقديم", "موافقه", "اعتماد", "تنفيذ", "التزام"
    )

    suspend fun extractNormalizedPages(pdfFile: File, password: String? = null): List<String> =
        withContext(Dispatchers.IO) {
            val pages = mutableListOf<String>()
            PDDocument.load(pdfFile, password ?: "").use { doc ->
                val stripper = PDFTextStripper()
                for (i in 1..doc.numberOfPages) {
                    stripper.startPage = i
                    stripper.endPage = i
                    pages.add(ArabicTextProcessor.cleanExtractedText(stripper.getText(doc)))
                }
            }
            pages
        }

    /**
     * Runs complete offline AI analysis (Summary, Keywords, Entities, PII Audit, Auto-Headings).
     */
    fun analyzeDocument(pages: List<String>, depth: SummaryDepth = SummaryDepth.MEDIUM): AiDocumentReport {
        val fullText = pages.joinToString("\n\n")
        val langStats = ArabicTextProcessor.analyzeLanguage(fullText)

        // Sentence extraction with page tracking
        data class IndexedSentence(val idx: Int, val page: Int, val text: String, val tokens: List<String>)
        val allSentences = mutableListOf<IndexedSentence>()
        var globalIdx = 0
        pages.forEachIndexed { pageIdx, pageText ->
            for (s in ArabicTextProcessor.splitSentences(pageText)) {
                val toks = tokenize(s)
                if (toks.size >= 3) {
                    allSentences.add(IndexedSentence(globalIdx++, pageIdx, s, toks))
                }
            }
        }

        // Document frequency & TF-IDF scores
        val docFreq = mutableMapOf<String, Int>()
        val totalFreq = mutableMapOf<String, Int>()
        for (sent in allSentences) {
            val unique = sent.tokens.toSet()
            for (t in unique) docFreq[t] = (docFreq[t] ?: 0) + 1
            for (t in sent.tokens) totalFreq[t] = (totalFreq[t] ?: 0) + 1
        }
        val nDocs = allSentences.size.coerceAtLeast(1).toDouble()

        val idf = docFreq.mapValues { (_, df) -> ln((nDocs + 1.0) / (df + 1.0)) + 1.0 }
        val topKeywords = totalFreq.entries
            .sortedByDescending { (term, count) -> count * (idf[term] ?: 1.0) }
            .take(10)
            .map { it.key }

        // Extractive sentence scoring (TF-IDF density + position bias + heading/action relevance)
        val scored = allSentences.map { sent ->
            val tfidfSum = sent.tokens.sumOf { (totalFreq[it] ?: 1) * (idf[it] ?: 1.0) }
            val normalizedScore = tfidfSum / (sent.tokens.size.toDouble() + 2.0)
            val positionBoost = when {
                sent.idx == 0 -> 1.35
                sent.idx <= 3 -> 1.18
                sent.page == 0 -> 1.10
                else -> 1.0
            }
            val lengthPenalty = if (sent.text.length in 35..260) 1.12 else 0.85
            sent to (normalizedScore * positionBoost * lengthPenalty)
        }

        val selectedSummary = scored
            .sortedByDescending { it.second }
            .take(depth.sentencesCount)
            .sortedBy { it.first.idx }
            .map { it.first.text }

        // Smart Entity Extraction
        val dates = DATE_REGEX.findAll(fullText).map { it.value.trim() }.distinct().take(12).toList()
        val amounts = AMOUNT_REGEX.findAll(fullText).map { it.value.trim() }.distinct().take(12).toList()
        val emails = EMAIL_REGEX.findAll(fullText).map { it.value.trim() }.distinct().take(12).toList()
        val urls = URL_REGEX.findAll(fullText).map { it.value.trim() }.distinct().take(12).toList()
        val phones = PHONE_REGEX.findAll(fullText)
            .map { it.value.trim() }
            .filter { it.count { c -> c.isDigit() } in 8..15 }
            .distinct()
            .take(12)
            .toList()

        val actionItems = allSentences
            .filter { sent ->
                val norm = ArabicTextProcessor.normalizeForSearch(sent.text)
                ACTION_HINTS.any { hint -> norm.contains(hint) }
            }
            .map { "P.${it.page + 1}: ${it.text}" }
            .distinct()
            .take(8)

        // Privacy / PII Scanner per page
        val pii = mutableListOf<AiPiiFinding>()
        pages.forEachIndexed { pageIdx, pText ->
            EMAIL_REGEX.findAll(pText).take(4).forEach {
                pii.add(AiPiiFinding("Email", maskValue(it.value), pageIdx))
            }
            PHONE_REGEX.findAll(pText)
                .filter { it.value.count { c -> c.isDigit() } in 9..15 }
                .take(4)
                .forEach {
                    pii.add(AiPiiFinding("Phone", maskValue(it.value.trim()), pageIdx))
                }
            IBAN_REGEX.findAll(pText).take(3).forEach {
                pii.add(AiPiiFinding("IBAN", maskValue(it.value), pageIdx))
            }
            CARD_REGEX.findAll(pText).take(3).forEach {
                pii.add(AiPiiFinding("Card Number", maskValue(it.value), pageIdx))
            }
            IP_REGEX.findAll(pText).take(3).forEach {
                pii.add(AiPiiFinding("IP Address", it.value, pageIdx))
            }
        }

        // Auto-Outline / Smart Headings Detector
        val headings = detectHeadings(pages)

        return AiDocumentReport(
            languageStats = langStats,
            summaryBullets = selectedSummary,
            topKeywords = topKeywords,
            datesFound = dates,
            amountsFound = amounts,
            emailsFound = emails,
            phonesFound = phones,
            urlsFound = urls,
            actionItems = actionItems,
            piiFindings = pii.distinctBy { "${it.category}_${it.maskedValue}_${it.pageIndex}" }.take(25),
            autoHeadings = headings
        )
    }

    /**
     * Offline BM25 Semantic Q&A ("Ask Your PDF") supporting both Arabic & English questions.
     */
    fun askQuestion(pages: List<String>, question: String): AiQaResponse {
        val qTokens = tokenize(question)
        if (qTokens.isEmpty()) {
            return AiQaResponse(question, "Please enter a clearer question with keywords.", emptyList())
        }

        data class Passage(val page: Int, val text: String, val tokens: List<String>)
        val passages = mutableListOf<Passage>()
        pages.forEachIndexed { pageIdx, pText ->
            val sentences = ArabicTextProcessor.splitSentences(pText)
            for (s in sentences) {
                val toks = tokenize(s)
                if (toks.isNotEmpty()) passages.add(Passage(pageIdx, s, toks))
            }
        }
        if (passages.isEmpty()) {
            return AiQaResponse(question, "No selectable text was found in this PDF.", emptyList())
        }

        val avgLen = passages.map { it.tokens.size }.average().coerceAtLeast(1.0)
        val n = passages.size.toDouble()
        val k1 = 1.5
        val b = 0.75

        val dfMap = qTokens.associateWith { qt ->
            passages.count { p -> p.tokens.any { it == qt || it.contains(qt) || qt.contains(it) } }
        }

        val scored = passages.map { p ->
            val dl = p.tokens.size.toDouble()
            var score = 0.0
            for (qt in qTokens) {
                val tf = p.tokens.count { it == qt || (qt.length >= 4 && (it.contains(qt) || qt.contains(it))) }.toDouble()
                if (tf > 0) {
                    val df = (dfMap[qt] ?: 0).toDouble()
                    val idf = ln((n - df + 0.5) / (df + 0.5) + 1.0)
                    score += idf * ((tf * (k1 + 1.0)) / (tf + k1 * (1.0 - b + b * (dl / avgLen))))
                }
            }
            p to score
        }.filter { it.second > 0.0 }
            .sortedByDescending { it.second }
            .take(5)

        if (scored.isEmpty()) {
            val isAr = ConverterCodec.containsArabic(question)
            val msg = if (isAr) {
                "لم يتم العثور على فقرة مطابقة مباشرة في نص هذا الملف. جرّب كلمات مفتاحية أخرى."
            } else {
                "No direct matching passage found in this PDF. Try different keywords."
            }
            return AiQaResponse(question, msg, emptyList())
        }

        val maxScore = scored.first().second.coerceAtLeast(0.001)
        val hits = scored.map { (p, sc) ->
            AiAnswerPassage(
                pageIndex = p.page,
                text = p.text,
                relevancePercent = ((sc / maxScore) * 100.0).toInt().coerceIn(25, 100)
            )
        }

        val answerSummary = hits.take(2).joinToString("\n\n") { "[Page ${it.pageIndex + 1}] ${it.text}" }
        return AiQaResponse(question = question, answerSummary = answerSummary, passages = hits)
    }

    private fun detectHeadings(pages: List<String>): List<AiHeadingItem> {
        val result = mutableListOf<AiHeadingItem>()
        val headingPattern = Regex("^(?:\\d+(?:\\.\\d+)*[\\.)]?\\s+|(?:Chapter|Section|Part|الفصل|الباب|القسم|البند|المادة)\\s+).+", RegexOption.IGNORE_CASE)

        pages.forEachIndexed { pageIdx, pText ->
            val lines = pText.lines().map { it.trim() }.filter { it.length in 5..85 }
            for ((lineIdx, line) in lines.withIndex()) {
                val looksLikeHeading = headingPattern.matches(line) ||
                    (lineIdx == 0 && !line.endsWith(".") && line.split(" ").size <= 10)
                if (looksLikeHeading && result.none { it.title.equals(line, ignoreCase = true) }) {
                    result.add(AiHeadingItem(title = line, pageIndex = pageIdx))
                }
                if (result.size >= 25) return result
            }
        }
        return result
    }

    private fun tokenize(text: String): List<String> {
        val norm = ArabicTextProcessor.normalizeForSearch(text)
        return norm.split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length >= 2 && it !in EN_STOPWORDS && it !in AR_STOPWORDS }
    }

    private fun maskValue(raw: String): String {
        val clean = raw.trim()
        if (clean.length <= 6) return "***"
        return "${clean.take(3)}***${clean.takeLast(min(3, clean.length - 3))}"
    }
}
