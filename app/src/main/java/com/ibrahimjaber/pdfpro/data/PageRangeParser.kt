package com.ibrahimjaber.pdfpro.data

/**
 * Parses 1-based user page range expressions (e.g., "1-3, 5, 8-10") into 0-based page indices.
 */
object PageRangeParser {

    /**
     * Parses a comma-separated list of page numbers and ranges into distinct 0-based indices
     * preserving user order.
     *
     * @param expression 1-based expression like "1-3, 5, 7-9"
     * @param totalPages Total pages in the source PDF (>= 1)
     * @throws IllegalArgumentException if expression is blank, malformed, or out of bounds
     */
    fun parseToZeroBasedIndices(expression: String, totalPages: Int): List<Int> {
        require(totalPages > 0) { "Document has no pages." }
        val trimmed = expression.trim()
        require(trimmed.isNotEmpty()) { "Enter at least one page number or range (e.g. 1-3, 5)." }

        val result = LinkedHashSet<Int>()
        val tokens = trimmed.split(",")
        for (rawToken in tokens) {
            val token = rawToken.trim()
            if (token.isEmpty()) continue

            if (token.contains("-")) {
                val parts = token.split("-")
                require(parts.size == 2) { "Invalid range '$token'. Use start-end format like 2-5." }
                val start = parts[0].trim().toIntOrNull()
                    ?: throw IllegalArgumentException("Invalid page number in '$token'.")
                val end = parts[1].trim().toIntOrNull()
                    ?: throw IllegalArgumentException("Invalid page number in '$token'.")
                require(start in 1..totalPages && end in 1..totalPages) {
                    "Range '$token' is out of bounds (1..$totalPages)."
                }
                require(start <= end) {
                    "Start page must be <= end page in '$token'."
                }
                for (p in start..end) {
                    result.add(p - 1)
                }
            } else {
                val page = token.toIntOrNull()
                    ?: throw IllegalArgumentException("Invalid page number '$token'.")
                require(page in 1..totalPages) {
                    "Page $page is out of bounds (1..$totalPages)."
                }
                result.add(page - 1)
            }
        }

        require(result.isNotEmpty()) { "No valid pages found in '$expression'." }
        return result.toList()
    }

    /**
     * Formats a byte count into a concise human-readable string (B, KB, MB, GB).
     */
    fun formatFileSize(bytes: Long): String {
        if (bytes < 0L) return "0 B"
        if (bytes < 1024L) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return String.format("%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024.0) return String.format("%.2f MB", mb)
        val gb = mb / 1024.0
        return String.format("%.2f GB", gb)
    }

    /**
     * Formats page number label according to chosen format.
     */
    fun formatPageNumber(pageNum: Int, totalPages: Int, format: PageNumberFormat): String {
        return when (format) {
            PageNumberFormat.NUMBER_ONLY -> "$pageNum"
            PageNumberFormat.PAGE_X_OF_Y -> "Page $pageNum of $totalPages"
            PageNumberFormat.DASH_X_DASH -> "- $pageNum -"
        }
    }
}
