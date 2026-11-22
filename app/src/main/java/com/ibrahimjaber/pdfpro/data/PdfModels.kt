package com.ibrahimjaber.pdfpro.data

import java.util.UUID

object AppBranding {
    const val APP_NAME = "PDF Editor Pro"
    const val APP_NAME_AR = "PDF إيديتور برو"
    const val APP_VERSION = "5.1.0"
    const val DEVELOPER_NAME = "Ibrahim Jaber"
    const val DEVELOPER_NAME_AR = "إبراهيم جابر"
    const val COPYRIGHT_YEAR = "2026"
    const val CREDIT_LINE = "Programmed by Ibrahim Jaber 2026"
    const val CREDIT_LINE_AR = "برمجة إبراهيم جابر 2026"
    const val COPYRIGHT_NOTICE = "© 2026 Ibrahim Jaber. All Rights Reserved."
    const val COPYRIGHT_NOTICE_AR = "© 2026 إبراهيم جابر. جميع الحقوق محفوظة."
    const val PDF_CREATOR_TAG = "PDF Pro Studio — Programmed by Ibrahim Jaber 2026"
}

enum class AppLanguage(val code: String, val displayName: String, val isRtl: Boolean) {
    ENGLISH("en", "English", false),
    ARABIC("ar", "العربية", true)
}

data class PdfDocumentItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val pageCount: Int,
    val lastOpenedAt: Long = System.currentTimeMillis(),
    val lastPageIndex: Int = 0,
    val isFavorite: Boolean = false,
    val isEncrypted: Boolean = false
)

data class PdfBookmark(
    val id: String = UUID.randomUUID().toString(),
    val docPath: String,
    val pageIndex: Int,
    val title: String,
    val createdAt: Long = System.currentTimeMillis()
)

data class SavedSignature(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val imagePath: String,
    val createdAt: Long = System.currentTimeMillis()
)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class ReadingMode(val label: String, val labelAr: String) {
    DAY("Day", "نهاري"),
    SEPIA("Sepia", "ورقي دافئ"),
    NIGHT_INVERT("Night", "ليلي")
}

enum class SortOrder(val label: String, val labelAr: String) {
    RECENT("Recent", "الأحدث"),
    NAME("Name", "الاسم"),
    SIZE("Size", "الحجم")
}

enum class CompressionLevel(
    val label: String,
    val labelAr: String,
    val description: String,
    val descriptionAr: String,
    val dpi: Int,
    val jpegQuality: Float
) {
    LOW("Light Compression", "ضغط خفيف", "Best quality (150 DPI, 85% quality)", "أعلى جودة (150 DPI، جودة 85%)", 150, 0.85f),
    MEDIUM("Balanced", "متوازن (موصى به)", "Recommended size/clarity (120 DPI, 68% quality)", "توازن ممتاز بين الحجم والوضوح", 120, 0.68f),
    HIGH("Maximum Compression", "ضغط أقصى", "Smallest file size (90 DPI, 48% quality)", "أصغر حجم ممكن للملف", 90, 0.48f)
}

enum class PageNumberPosition(val label: String, val labelAr: String) {
    BOTTOM_CENTER("Bottom Center", "أسفل الوسط"),
    BOTTOM_RIGHT("Bottom Right", "أسفل اليمين"),
    TOP_RIGHT("Top Right", "أعلى اليمين")
}

enum class PageNumberFormat(val label: String) {
    NUMBER_ONLY("1, 2, 3"),
    PAGE_X_OF_Y("Page 1 of 10"),
    DASH_X_DASH("- 1 -")
}

enum class PageTemplate(val label: String, val labelAr: String) {
    BLANK("Blank White", "ورقة بيضاء"),
    LINED("Lined Notebook", "مسطّر"),
    GRID("Engineering Grid", "شبكة هندسية")
}

enum class EditorTool(val label: String, val labelAr: String) {
    VIEW("Pan / Read", "تصفح"),
    PEN("Pen", "قلم"),
    HIGHLIGHTER("Highlight", "تظليل"),
    UNDERLINE("Underline", "تسطير"),
    STRIKETHROUGH("Strike", "شطب"),
    TEXT("Text", "نص"),
    REDACT("Redact", "طمس سري"),
    WHITEOUT("Whiteout", "ممحاة بيضاء"),
    RECTANGLE("Box", "مربع"),
    OVAL("Circle", "دائرة"),
    ARROW("Arrow", "سهم"),
    STAMP("Stamp", "ختم"),
    STICKY_NOTE("Note", "ملاحظة"),
    IMAGE_STAMP("Image", "صورة"),
    QR_CODE("QR Code", "رمز QR"),
    SIGNATURE("Sign", "توقيع")
}

enum class ConversionTarget(val ext: String, val mime: String, val labelEn: String, val labelAr: String) {
    PDF("pdf", "application/pdf", "PDF Document (.pdf)", "مستند PDF (.pdf)"),
    DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "Word Document (.docx)", "مستند وورد Word (.docx)"),
    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "Excel Workbook (.xlsx)", "ملف إكسل Excel (.xlsx)"),
    CSV("csv", "text/csv", "CSV Spreadsheet (.csv)", "جدول بيانات CSV (.csv)"),
    PPTX("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation", "PowerPoint (.pptx)", "عرض بوربوينت (.pptx)"),
    HTML("html", "text/html", "Web Page (.html)", "صفحة ويب HTML (.html)"),
    TXT("txt", "text/plain", "Plain Text (.txt)", "نص عادي TXT (.txt)"),
    PNG("png", "image/png", "PNG Images (.png)", "صور PNG (.png)"),
    JPEG("jpg", "image/jpeg", "JPEG Images (.jpg)", "صور JPEG (.jpg)")
}

data class NormPoint(val x: Float, val y: Float)

sealed class PdfAnnotation {
    abstract val id: String
    abstract val pageIndex: Int

    data class Freehand(
        override val id: String = UUID.randomUUID().toString(),
        override val pageIndex: Int,
        val points: List<NormPoint>,
        val colorArgb: Int,
        val strokeWidthNorm: Float,
        val isHighlighter: Boolean = false
    ) : PdfAnnotation()

    data class TextBox(
        override val id: String = UUID.randomUUID().toString(),
        override val pageIndex: Int,
        val x: Float,
        val y: Float,
        val text: String,
        val fontSizePt: Float = 16f,
        val colorArgb: Int,
        val isBold: Boolean = false
    ) : PdfAnnotation()

    data class Shape(
        override val id: String = UUID.randomUUID().toString(),
        override val pageIndex: Int,
        val kind: ShapeKind,
        val startX: Float,
        val startY: Float,
        val endX: Float,
        val endY: Float,
        val colorArgb: Int,
        val strokeWidthNorm: Float = 0.004f
    ) : PdfAnnotation()

    /**
     * Solid opaque rectangle for Redaction (blackout sensitive PII) or Whiteout patch.
     */
    data class MaskRect(
        override val id: String = UUID.randomUUID().toString(),
        override val pageIndex: Int,
        val startX: Float,
        val startY: Float,
        val endX: Float,
        val endY: Float,
        val fillColorArgb: Int,
        val isRedaction: Boolean = true
    ) : PdfAnnotation()

    data class Stamp(
        override val id: String = UUID.randomUUID().toString(),
        override val pageIndex: Int,
        val x: Float,
        val y: Float,
        val label: String,
        val colorArgb: Int
    ) : PdfAnnotation()

    data class StickyNote(
        override val id: String = UUID.randomUUID().toString(),
        override val pageIndex: Int,
        val x: Float,
        val y: Float,
        val text: String,
        val colorArgb: Int
    ) : PdfAnnotation()

    data class Signature(
        override val id: String = UUID.randomUUID().toString(),
        override val pageIndex: Int,
        val x: Float,
        val y: Float,
        val widthNorm: Float = 0.28f,
        val heightNorm: Float = 0.11f,
        val imagePath: String
    ) : PdfAnnotation()
}

enum class ShapeKind { RECTANGLE, OVAL, ARROW, LINE }

data class PageOperationSpec(
    val uid: String = UUID.randomUUID().toString(),
    val sourcePageIndex: Int?,
    val extraRotationDeg: Int = 0
)

data class PdfMetadata(
    val title: String = "",
    val author: String = "",
    val subject: String = "",
    val keywords: String = "",
    val creator: String = AppBranding.PDF_CREATOR_TAG,
    val producer: String = AppBranding.APP_NAME,
    val pageCount: Int = 0,
    val fileSizeBytes: Long = 0L,
    val isEncrypted: Boolean = false
)

data class SearchMatch(
    val pageIndex: Int,
    val snippet: String,
    val matchCount: Int
)

data class CompressionResult(
    val outputPath: String,
    val originalBytes: Long,
    val compressedBytes: Long
) {
    val savedBytes: Long get() = (originalBytes - compressedBytes).coerceAtLeast(0L)
    val savedPercent: Int
        get() = if (originalBytes > 0L) {
            (((originalBytes - compressedBytes).toDouble() / originalBytes.toDouble()) * 100.0)
                .toInt()
                .coerceIn(0, 99)
        } else 0
}

data class HeaderFooterConfig(
    val headerLeft: String = "",
    val headerCenter: String = "",
    val headerRight: String = "",
    val footerLeft: String = AppBranding.CREDIT_LINE,
    val batesPrefix: String = "",
    val batesStart: Int = 1,
    val batesDigits: Int = 6
)

data class PdfDiffSummary(
    val fileAPages: Int,
    val fileBPages: Int,
    val identicalPages: Int,
    val changedPages: List<Int>,
    val reportText: String
)

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val defaultReadingMode: ReadingMode = ReadingMode.DAY,
    val language: AppLanguage = AppLanguage.ENGLISH
)
