package com.ibrahimjaber.pdfpro

import com.ibrahimjaber.pdfpro.data.PageNumberFormat
import com.ibrahimjaber.pdfpro.data.PageRangeParser
import org.junit.Assert.assertEquals
import org.junit.Test

class PageRangeParserTest {

    @Test
    fun parseMixedSingleAndRanges_returnsZeroBasedOrderedDistinct() {
        val result = PageRangeParser.parseToZeroBasedIndices("1-3, 5, 8-10", totalPages = 12)
        assertEquals(listOf(0, 1, 2, 4, 7, 8, 9), result)
    }

    @Test
    fun parseOverlappingRanges_deduplicatesPreservingOrder() {
        val result = PageRangeParser.parseToZeroBasedIndices("2-4, 3-5, 2", totalPages = 6)
        assertEquals(listOf(1, 2, 3, 4), result)
    }

    @Test(expected = IllegalArgumentException::class)
    fun parseOutOfBoundsPage_throwsException() {
        PageRangeParser.parseToZeroBasedIndices("1-5", totalPages = 3)
    }

    @Test(expected = IllegalArgumentException::class)
    fun parseReversedRange_throwsException() {
        PageRangeParser.parseToZeroBasedIndices("5-2", totalPages = 10)
    }

    @Test(expected = IllegalArgumentException::class)
    fun parseBlankExpression_throwsException() {
        PageRangeParser.parseToZeroBasedIndices("   ", totalPages = 5)
    }

    @Test
    fun formatFileSize_returnsReadableUnits() {
        assertEquals("512 B", PageRangeParser.formatFileSize(512L))
        assertEquals("2.0 KB", PageRangeParser.formatFileSize(2048L))
        assertEquals("1.50 MB", PageRangeParser.formatFileSize(1572864L))
    }

    @Test
    fun formatPageNumber_matchesAllFormats() {
        assertEquals("3", PageRangeParser.formatPageNumber(3, 10, PageNumberFormat.NUMBER_ONLY))
        assertEquals("Page 3 of 10", PageRangeParser.formatPageNumber(3, 10, PageNumberFormat.PAGE_X_OF_Y))
        assertEquals("- 3 -", PageRangeParser.formatPageNumber(3, 10, PageNumberFormat.DASH_X_DASH))
    }
}
