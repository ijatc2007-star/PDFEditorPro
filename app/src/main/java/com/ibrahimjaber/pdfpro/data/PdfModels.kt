package com.ibrahimjaber.pdfpro.data

import java.util.UUID

object AppBranding {
    const val APP_NAME = "PDF Pro Studio"
    const val APP_VERSION = "1.0.0"
    const val DEVELOPER_NAME = "Ibrahim Jaber"
    const val COPYRIGHT_YEAR = "2026"
    const val CREDIT_LINE = "Programmed by Ibrahim Jaber 2026"
    const val COPYRIGHT_NOTICE = "© 2026 Ibrahim Jaber. All Rights Reserved."
    const val PDF_CREATOR_TAG = "PDF Pro Studio — Programmed by Ibrahim Jaber 2026"
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

enum class ReadingMode(val label: String) {
    DAY("Day"),
    SEPIA("Sepia"),
    NIGHT_INVERT("Night")
}

enum class SortOrder(val label: String) {
    RECENT("Recent"),
    NAME("Name"),
    SIZE("Size")
}

enum class CompressionLevel(
    val label: String,
    val description: String,
    val dpi: Int,
    val jpegQuality: Float
) {
    LOW("Light Compression", "Best quality (150 DPI, 85% quality)", 150, 0.85f),
    MEDIUM("Balanced", "Recommended size/clarity (120 DPI, 68% quality)", 120, 0.68f),
    HIGH("Maximum Compression", "Smallest file size (90 DPI, 48% quality)", 90, 0.48f)
}

enum class PageNumberPosition(val label: String) {
    BOTTOM_CENTER("Bottom Center"),
    BOTTOM_RIGHT("Bottom Right"),
    TOP_RIGHT("Top Right")
}

enum class PageNumberFormat(val label: String) {
    NUMBER_ONLY("1, 2, 3"),
    PAGE_X_OF_Y("Page 1 of 10"),
    DASH_X_DASH("- 1 -")
}

enum class PageTemplate(val label: String) {
    BLANK("Blank White"),
    LINED("Lined Notebook"),
    GRID("Engineering Grid")
}

enum class EditorTool(val label: String) {
    VIEW("Pan / Read"),
    PEN("Pen"),
    HIGHLIGHTER("Highlight"),
    TEXT("Text"),
    RECTANGLE("Box"),
    OVAL("Circle"),
    ARROW("Arrow"),
    STAMP("Stamp"),
    STICKY_NOTE("Note"),
    SIGNATURE("Sign")
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

enum class ShapeKind { RECTANGLE, OVAL, ARROW }

data class PageOperationSpec(
    val uid: String = UUID.randomUUID().toString(),
    val sourcePageIndex: Int?, // null means insert a new blank A4 page
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

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val defaultReadingMode: ReadingMode = ReadingMode.DAY
)
