package com.ibrahimjaber.pdfpro.engine

import com.ibrahimjaber.pdfpro.data.AppBranding
import java.io.File
import java.io.FileInputStream

enum class TranslationDirection(val labelEn: String, val labelAr: String) {
    AUTO("Auto Detect (EN ↔ AR)", "تلقائي (عربي ↔ إنجليزي)"),
    EN_TO_AR("English → العربية", "إنجليزي ← عربي"),
    AR_TO_EN("العربية → English", "عربي ← إنجليزي")
}

data class StudyFlashcard(
    val question: String,
    val answer: String,
    val pageNumber: Int
)

data class LocalGgufModelInfo(
    val fileName: String,
    val filePath: String,
    val sizeBytes: Long,
    val isValidGgufHeader: Boolean,
    val ggufVersion: Int
)

/**
 * 100% Offline Bilingual Arabic <-> English Translator, Generative Study/Reply Engine,
 * and Local On-Device GGUF LLM Model Validator.
 * Programmed by Ibrahim Jaber 2026 | برمجة إبراهيم جابر 2026
 */
object OfflineTranslatorEngine {

    private val EN_TO_AR_PHRASES: LinkedHashMap<String, String> = linkedMapOf(
        "terms and conditions" to "الشروط والأحكام",
        "privacy policy" to "سياسة الخصوصية",
        "table of contents" to "جدول المحتويات",
        "executive summary" to "الملخص التنفيذي",
        "all rights reserved" to "جميع الحقوق محفوظة",
        "digital signature" to "التوقيع الرقمي",
        "payment terms" to "شروط الدفع",
        "total amount" to "المبلغ الإجمالي",
        "due date" to "تاريخ الاستحقاق",
        "effective date" to "تاريخ السريان",
        "full name" to "الاسم الكامل",
        "phone number" to "رقم الهاتف",
        "email address" to "البريد الإلكتروني",
        "action items" to "المهام المطلوبة",
        "confidential information" to "معلومات سرية",
        "pdf editor pro" to "PDF إيديتور برو",
        "programmed by" to "برمجة",
        "welcome to" to "مرحباً بك في",
        "page number" to "رقم الصفحة",
        "file size" to "حجم الملف",
        "password protection" to "الحماية بكلمة مرور",
        "offline mode" to "الوضع بدون إنترنت",
        "user agreement" to "اتفاقية المستخدم",
        "scope of work" to "نطاق العمل",
        "final report" to "التقرير النهائي",
        "security audit" to "التدقيق الأمني",
        "network security" to "أمن الشبكات"
    )

    private val EN_TO_AR_WORDS: Map<String, String> = mapOf(
        "agreement" to "اتفاقية", "contract" to "عقد", "document" to "مستند", "file" to "ملف",
        "page" to "صفحة", "pages" to "صفحات", "report" to "تقرير", "summary" to "ملخص",
        "project" to "مشروع", "company" to "شركة", "client" to "عميل", "customer" to "عميل",
        "service" to "خدمة", "services" to "خدمات", "system" to "نظام", "application" to "تطبيق",
        "security" to "الأمان", "privacy" to "الخصوصية", "offline" to "بدون إنترنت", "online" to "متصل",
        "password" to "كلمة المرور", "encrypted" to "مشفّر", "signature" to "توقيع", "approved" to "معتمد",
        "confidential" to "سري للغاية", "draft" to "مسودة", "urgent" to "عاجل", "verified" to "موثّق",
        "invoice" to "فاتورة", "payment" to "الدفع", "budget" to "الميزانية", "price" to "السعر",
        "cost" to "التكلفة", "amount" to "المبلغ", "total" to "الإجمالي", "tax" to "الضريبة",
        "date" to "التاريخ", "deadline" to "الموعد النهائي", "delivery" to "التسليم", "schedule" to "الجدول",
        "department" to "القسم", "manager" to "المدير", "director" to "المدير العام", "employee" to "موظف",
        "author" to "المؤلف", "title" to "العنوان", "subject" to "الموضوع", "notes" to "ملاحظات",
        "note" to "ملاحظة", "section" to "القسم", "chapter" to "الفصل", "introduction" to "مقدمة",
        "conclusion" to "الخاتمة", "requirements" to "المتطلبات", "required" to "مطلوب", "must" to "يجب",
        "shall" to "يلزم", "should" to "ينبغي", "important" to "مهم", "warning" to "تحذير",
        "error" to "خطأ", "success" to "نجاح", "completed" to "مكتمل", "pending" to "قيد الانتظار",
        "name" to "الاسم", "address" to "العنوان", "email" to "البريد الإلكتروني", "phone" to "الهاتف",
        "text" to "النص", "image" to "صورة", "images" to "صور", "table" to "جدول",
        "data" to "البيانات", "information" to "معلومات", "search" to "بحث", "edit" to "تحرير",
        "save" to "حفظ", "export" to "تصدير", "import" to "استيراد", "convert" to "تحويل",
        "merge" to "دمج", "split" to "تقسيم", "compress" to "ضغط", "print" to "طباعة",
        "share" to "مشاركة", "protect" to "حماية", "unlock" to "فك القفل", "watermark" to "علامة مائية",
        "translation" to "الترجمة", "translate" to "ترجمة", "arabic" to "العربية", "english" to "الإنجليزية",
        "welcome" to "مرحباً", "guide" to "دليل", "manual" to "دليل المستخدم", "tools" to "أدوات",
        "features" to "المزايا", "support" to "الدعم", "version" to "الإصدار", "update" to "تحديث",
        "network" to "الشبكة", "router" to "الموجّه", "firewall" to "جدار الحماية", "backup" to "نسخ احتياطي",
        "device" to "الجهاز", "user" to "المستخدم", "admin" to "المسؤول", "access" to "الوصول",
        "rights" to "الحقوق", "legal" to "قانوني", "policy" to "سياسة", "standard" to "معيار",
        "quality" to "الجودة", "fast" to "سريع", "secure" to "آمن", "free" to "مجاني",
        "new" to "جديد", "open" to "فتح", "close" to "إغلاق", "delete" to "حذف",
        "add" to "إضافة", "create" to "إنشاء", "form" to "نموذج", "field" to "حقل",
        "yes" to "نعم", "no" to "لا", "and" to "و", "or" to "أو", "with" to "مع",
        "without" to "بدون", "for" to "لـ", "from" to "من", "to" to "إلى", "in" to "في",
        "on" to "على", "by" to "بواسطة", "before" to "قبل", "after" to "بعد", "all" to "جميع"
    )

    private val AR_TO_EN_PHRASES: Map<String, String> by lazy {
        EN_TO_AR_PHRASES.entries.associate { (en, ar) ->
            ArabicTextProcessor.normalizeForSearch(ar) to en
        }
    }

    private val AR_TO_EN_WORDS: Map<String, String> by lazy {
        val map = mutableMapOf<String, String>()
        for ((en, ar) in EN_TO_AR_WORDS) {
            val normAr = ArabicTextProcessor.normalizeForSearch(ar)
            map.putIfAbsent(normAr, en)
            if (normAr.startsWith("ال") && normAr.length > 3) {
                map.putIfAbsent(normAr.removePrefix("ال"), en)
            }
        }
        map["ابراهيم"] = "Ibrahim"
        map["جابر"] = "Jaber"
        map["ريال"] = "SAR"
        map["دينار"] = "Dinar"
        map["درهم"] = "AED"
        map["دولار"] = "USD"
        map["سعودي"] = "Saudi"
        map["النهائي"] = "final"
        map["نهائي"] = "final"
        map["شروط"] = "terms"
        map["تسليم"] = "delivery"
        map["قيمه"] = "value"
        map["اجماليه"] = "total"
        map["تطوير"] = "development"
        map["يعمل"] = "works"
        map["كامل"] = "complete"
        map
    }

    /**
     * Translates text 100% offline between English and Arabic preserving line structure.
     */
    fun translate(text: String, direction: TranslationDirection = TranslationDirection.AUTO): String {
        val cleaned = ArabicTextProcessor.cleanExtractedText(text)
        if (cleaned.isBlank()) return ""

        val effectiveDir = when (direction) {
            TranslationDirection.AUTO -> {
                val stats = ArabicTextProcessor.analyzeLanguage(cleaned)
                if (stats.arabicPercent >= 45) TranslationDirection.AR_TO_EN else TranslationDirection.EN_TO_AR
            }
            else -> direction
        }

        return cleaned.lines().joinToString("\n") { line ->
            if (line.isBlank()) ""
            else if (effectiveDir == TranslationDirection.EN_TO_AR) translateLineEnToAr(line)
            else translateLineArToEn(line)
        }
    }

    private fun translateLineEnToAr(line: String): String {
        var working = line
        for ((enPhrase, arPhrase) in EN_TO_AR_PHRASES) {
            working = working.replace(Regex("\\b${Regex.escape(enPhrase)}\\b", RegexOption.IGNORE_CASE), arPhrase)
        }
        return Regex("[A-Za-z]+").replace(working) { match ->
            val word = match.value.lowercase()
            EN_TO_AR_WORDS[word]
                ?: EN_TO_AR_WORDS[word.removeSuffix("s")]
                ?: EN_TO_AR_WORDS[word.removeSuffix("ed")]
                ?: EN_TO_AR_WORDS[word.removeSuffix("ing")]
                ?: match.value
        }
    }

    private fun translateLineArToEn(line: String): String {
        var working = line
        val normLine = ArabicTextProcessor.normalizeForSearch(line)
        for ((normArPhrase, enPhrase) in AR_TO_EN_PHRASES) {
            if (normLine.contains(normArPhrase)) {
                working = working.replace(normArPhrase, enPhrase)
            }
        }
        return Regex("[\u0600-\u06FF]+").replace(working) { match ->
            val norm = ArabicTextProcessor.normalizeForSearch(match.value)
            AR_TO_EN_WORDS[norm]
                ?: (if (norm.startsWith("ال") && norm.length > 3) AR_TO_EN_WORDS[norm.removePrefix("ال")] else null)
                ?: (if (norm.startsWith("و") && norm.length > 3) AR_TO_EN_WORDS[norm.removePrefix("و")]?.let { "and $it" } else null)
                ?: (if (norm.startsWith("ب") && norm.length > 3) AR_TO_EN_WORDS[norm.removePrefix("ب")]?.let { "with $it" } else null)
                ?: (if (norm.startsWith("ل") && norm.length > 3) AR_TO_EN_WORDS[norm.removePrefix("ل")]?.let { "for $it" } else null)
                ?: match.value
        }
    }

    /**
     * Generates structured Study Flashcards (Q&A deck) from any PDF offline.
     */
    fun generateStudyFlashcards(pages: List<String>, maxCards: Int = 8): List<StudyFlashcard> {
        val report = OfflineAiPdfEngine.analyzeDocument(pages, SummaryDepth.DETAILED)
        val cards = mutableListOf<StudyFlashcard>()

        report.autoHeadings.take(4).forEach { h ->
            val pageText = pages.getOrNull(h.pageIndex).orEmpty()
            val sents = ArabicTextProcessor.splitSentences(pageText)
            val context = sents.firstOrNull { !it.equals(h.title, ignoreCase = true) } ?: sents.firstOrNull() ?: ""
            if (context.isNotBlank()) {
                val isAr = ConverterCodec.containsArabic(h.title)
                val q = if (isAr) "ما المقصود بـ: ${h.title}؟" else "What are the key details of '${h.title}'?"
                cards.add(StudyFlashcard(q, context, h.pageIndex + 1))
            }
        }

        report.summaryBullets.forEachIndexed { idx, bullet ->
            if (cards.size >= maxCards) return@forEachIndexed
            val isAr = ConverterCodec.containsArabic(bullet)
            val q = if (isAr) "نقطة مراجعة رئيسية #${idx + 1}" else "Key Takeaway Review #${idx + 1}"
            cards.add(StudyFlashcard(q, bullet, 1))
        }

        return cards.take(maxCards)
    }

    /**
     * Drafts an offline formal executive memo / reply letter directly from AI report insights.
     */
    fun draftFormalReply(report: AiDocumentReport, fileName: String, inArabic: Boolean): String {
        return if (inArabic) {
            buildString {
                appendLine("الموضوع: مراجعة والرد على المستند ($fileName)")
                appendLine("تحية طيبة وبعد،")
                appendLine()
                appendLine("تمت مراجعة المستند المرفق (${report.languageStats.wordCount} كلمة) واستخلاص النقاط الرئيسية التالية:")
                report.summaryBullets.take(4).forEachIndexed { i, b -> appendLine("${i + 1}. $b") }
                if (report.datesFound.isNotEmpty()) {
                    appendLine("\nالمواعيد والتواريخ المهمة: ${report.datesFound.take(4).joinToString("، ")}")
                }
                if (report.amountsFound.isNotEmpty()) {
                    appendLine("القيم والمبالغ المذكورة: ${report.amountsFound.take(4).joinToString("، ")}")
                }
                if (report.actionItems.isNotEmpty()) {
                    appendLine("\nالإجراءات المطلوبة:")
                    report.actionItems.take(3).forEach { appendLine("- $it") }
                }
                appendLine("\nوتفضلوا بقبول فائق الاحترام،")
                appendLine(AppBranding.DEVELOPER_NAME_AR)
            }
        } else {
            buildString {
                appendLine("Subject: Executive Review & Response — $fileName")
                appendLine("Dear Colleague,")
                appendLine()
                appendLine("Following a review of '$fileName' (${report.languageStats.wordCount} words), here are the key points:")
                report.summaryBullets.take(4).forEachIndexed { i, b -> appendLine("${i + 1}. $b") }
                if (report.datesFound.isNotEmpty()) {
                    appendLine("\nKey Dates: ${report.datesFound.take(4).joinToString(", ")}")
                }
                if (report.amountsFound.isNotEmpty()) {
                    appendLine("Key Amounts: ${report.amountsFound.take(4).joinToString(", ")}")
                }
                if (report.actionItems.isNotEmpty()) {
                    appendLine("\nAction Items:")
                    report.actionItems.take(3).forEach { appendLine("- $it") }
                }
                appendLine("\nBest regards,")
                appendLine(AppBranding.DEVELOPER_NAME)
            }
        }
    }

    /**
     * Validates a user-imported local .gguf on-device LLM model file (checks real GGUF magic header).
     */
    fun inspectLocalGgufModel(file: File): LocalGgufModelInfo {
        var valid = false
        var version = 0
        if (file.exists() && file.length() >= 8L) {
            runCatching {
                FileInputStream(file).use { fis ->
                    val header = ByteArray(8)
                    if (fis.read(header) == 8) {
                        valid = header[0] == 'G'.code.toByte() &&
                            header[1] == 'G'.code.toByte() &&
                            header[2] == 'U'.code.toByte() &&
                            header[3] == 'F'.code.toByte()
                        if (valid) {
                            version = (header[4].toInt() and 0xFF) or
                                ((header[5].toInt() and 0xFF) shl 8) or
                                ((header[6].toInt() and 0xFF) shl 16) or
                                ((header[7].toInt() and 0xFF) shl 24)
                        }
                    }
                }
            }
        }
        return LocalGgufModelInfo(
            fileName = file.name,
            filePath = file.absolutePath,
            sizeBytes = if (file.exists()) file.length() else 0L,
            isValidGgufHeader = valid,
            ggufVersion = version
        )
    }
}
