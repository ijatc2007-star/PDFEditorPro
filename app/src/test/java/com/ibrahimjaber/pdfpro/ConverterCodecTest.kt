package com.ibrahimjaber.pdfpro

import com.ibrahimjaber.pdfpro.data.AppLanguage
import com.ibrahimjaber.pdfpro.data.stringsFor
import com.ibrahimjaber.pdfpro.engine.ConverterCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConverterCodecTest {

    @Test
    fun containsArabic_detectsArabicAndLatinCorrectly() {
        assertTrue(ConverterCodec.containsArabic("برمجة إبراهيم جابر 2026"))
        assertTrue(ConverterCodec.containsArabic("PDF مرحبا"))
        assertFalse(ConverterCodec.containsArabic("Programmed by Ibrahim Jaber 2026"))
    }

    @Test
    fun parseCsv_handlesQuotesCommasAndUtf8BomArabic() {
        val csv = "\uFEFFName,Department,Notes\n\"Jaber, Ibrahim\",Engineering,\"ملاحظة \"\"مهمة\"\"\""
        val rows = ConverterCodec.parseCsv(csv)
        assertEquals(2, rows.size)
        assertEquals(listOf("Name", "Department", "Notes"), rows[0])
        assertEquals(listOf("Jaber, Ibrahim", "Engineering", "ملاحظة \"مهمة\""), rows[1])
    }

    @Test
    fun escapeXml_escapesSpecialXmlCharacters() {
        assertEquals("A &amp; B &lt;C&gt; &quot;D&quot;", ConverterCodec.escapeXml("A & B <C> \"D\""))
    }

    @Test
    fun stripHtmlToPlainText_removesScriptsAndTags() {
        val html = "<html><head><style>body{color:red}</style></head><body><h1>Hello</h1><p>Ibrahim &amp; Co</p></body></html>"
        val text = ConverterCodec.stripHtmlToPlainText(html)
        assertTrue(text.contains("Hello"))
        assertTrue(text.contains("Ibrahim & Co"))
        assertFalse(text.contains("color:red"))
    }

    @Test
    fun excelColName_computesColumnLettersProperly() {
        assertEquals("A", ConverterCodec.excelColName(0))
        assertEquals("Z", ConverterCodec.excelColName(25))
        assertEquals("AA", ConverterCodec.excelColName(26))
    }

    @Test
    fun arabicLocalizationContainsIbrahimJaber2026() {
        val ar = stringsFor(AppLanguage.ARABIC)
        assertTrue(ar.isArabic)
        assertTrue(ar.creditLine.contains("إبراهيم جابر 2026"))
    }
}
