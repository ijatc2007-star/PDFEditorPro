package com.ibrahimjaber.pdfpro.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.ibrahimjaber.pdfpro.data.AppBranding
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Pure Kotlin codecs for CSV, XML, HTML stripping, and Arabic RTL detection.
 * Unit-tested directly on JVM.
 */
object ConverterCodec {

    fun containsArabic(text: String): Boolean =
        text.any { ch -> ch in '\u0600'..'\u06FF' || ch in '\u0750'..'\u077F' || ch in '\u08A0'..'\u08FF' }

    fun escapeXml(raw: String): String = buildString(raw.length) {
        for (ch in raw) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                '\u0000' -> Unit
                else -> if (ch.code >= 0x20 || ch == '\n' || ch == '\r' || ch == '\t') append(ch)
            }
        }
    }

    fun escapeCsvField(field: String): String {
        val needsQuotes = field.contains(',') || field.contains('"') || field.contains('\n') || field.contains('\r')
        return if (needsQuotes) "\"${field.replace("\"", "\"\"")}\"" else field
    }

    fun parseCsv(content: String): List<List<String>> {
        val clean = content.removePrefix("\uFEFF")
        val rows = mutableListOf<List<String>>()
        val currentRow = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < clean.length) {
            val ch = clean[i]
            when {
                inQuotes -> {
                    if (ch == '"') {
                        if (i + 1 < clean.length && clean[i + 1] == '"') {
                            field.append('"')
                            i++
                        } else {
                            inQuotes = false
                        }
                    } else {
                        field.append(ch)
                    }
                }
                ch == '"' -> inQuotes = true
                ch == ',' -> {
                    currentRow.add(field.toString().trim())
                    field.setLength(0)
                }
                ch == '\r' -> Unit
                ch == '\n' -> {
                    currentRow.add(field.toString().trim())
                    field.setLength(0)
                    if (currentRow.any { it.isNotEmpty() }) {
                        rows.add(currentRow.toList())
                    }
                    currentRow.clear()
                }
                else -> field.append(ch)
            }
            i++
        }
        if (field.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow.add(field.toString().trim())
            if (currentRow.any { it.isNotEmpty() }) {
                rows.add(currentRow.toList())
            }
        }
        return rows
    }

    fun stripHtmlToPlainText(html: String): String {
        return html
            .replace(Regex("(?is)<(script|style)[^>]*>.*?</(script|style)>"), "")
            .replace(Regex("(?i)<(br|p|div|li|tr|h[1-6])[^>]*>"), "\n")
            .replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    fun excelColName(zeroBasedIndex: Int): String {
        var n = zeroBasedIndex + 1
        val sb = StringBuilder()
        while (n > 0) {
            val rem = (n - 1) % 26
            sb.insert(0, ('A'.code + rem).toChar())
            n = (n - 1) / 26
        }
        return sb.toString()
    }
}

/**
 * 100% Offline Universal Format Converter supporting bidirectional conversion:
 * PDF <-> Word (.docx), Excel (.xlsx), CSV (.csv), PowerPoint (.pptx), Images, HTML, TXT, Markdown
 * Plus CSV <-> Excel (.xlsx) with full Arabic RTL support.
 * Programmed by Ibrahim Jaber 2026.
 */
class DocumentConverterEngine(
    private val context: Context,
    private val pdfEngine: PdfEngine
) {

    // =========================================================================
    // FROM PDF CONVERSIONS
    // =========================================================================

    suspend fun pdfToDocx(inputPdf: File, outputDocx: File, password: String? = null): File =
        withContext(Dispatchers.IO) {
            outputDocx.parentFile?.mkdirs()
            val pageTexts = extractPagesText(inputPdf, password)
            val bodyXml = buildString {
                pageTexts.forEachIndexed { pageIdx, pageText ->
                    val paragraphs = pageText.lines()
                    for (line in paragraphs) {
                        val trimmed = line.trim()
                        if (trimmed.isEmpty()) continue
                        val rtlXml = if (ConverterCodec.containsArabic(trimmed)) {
                            "<w:pPr><w:bidi/><w:jc w:val=\"right\"/></w:pPr>"
                        } else ""
                        val runRtl = if (ConverterCodec.containsArabic(trimmed)) "<w:rPr><w:rtl/></w:rPr>" else ""
                        append("<w:p>$rtlXml<w:r>$runRtl<w:t xml:space=\"preserve\">")
                        append(ConverterCodec.escapeXml(trimmed))
                        append("</w:t></w:r></w:p>")
                    }
                    if (pageIdx < pageTexts.lastIndex) {
                        append("<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>")
                    }
                }
                if (isEmpty()) {
                    append("<w:p><w:r><w:t>Converted by ${ConverterCodec.escapeXml(AppBranding.PDF_CREATOR_TAG)}</w:t></w:r></w:p>")
                }
            }

            val documentXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:body>
    $bodyXml
    <w:sectPr>
      <w:pgSz w:w="11906" w:h="16838"/>
      <w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440"/>
    </w:sectPr>
  </w:body>
</w:document>"""

            val contentTypes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>"""

            val rootRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

            ZipOutputStream(FileOutputStream(outputDocx)).use { zip ->
                writeZipEntry(zip, "[Content_Types].xml", contentTypes)
                writeZipEntry(zip, "_rels/.rels", rootRels)
                writeZipEntry(zip, "word/document.xml", documentXml)
            }
            outputDocx
        }

    suspend fun pdfToXlsx(inputPdf: File, outputXlsx: File, password: String? = null): File =
        withContext(Dispatchers.IO) {
            val rows = extractPdfTabularRows(inputPdf, password)
            writeRowsToXlsx(rows, outputXlsx)
        }

    suspend fun pdfToCsv(inputPdf: File, outputCsv: File, password: String? = null): File =
        withContext(Dispatchers.IO) {
            outputCsv.parentFile?.mkdirs()
            val rows = extractPdfTabularRows(inputPdf, password)
            FileOutputStream(outputCsv).use { fos ->
                // UTF-8 BOM ensures Microsoft Excel displays Arabic perfectly
                fos.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                val sb = StringBuilder()
                for (row in rows) {
                    sb.append(row.joinToString(",") { ConverterCodec.escapeCsvField(it) })
                    sb.append("\r\n")
                }
                fos.write(sb.toString().toByteArray(StandardCharsets.UTF_8))
            }
            outputCsv
        }

    suspend fun pdfToPptx(inputPdf: File, outputPptx: File, password: String? = null): File =
        withContext(Dispatchers.IO) {
            outputPptx.parentFile?.mkdirs()
            val tempImgDir = File(context.cacheDir, "pptx_slides_${System.currentTimeMillis()}").apply { mkdirs() }
            try {
                val slideImgs = pdfEngine.pdfToImages(
                    inputFile = inputPdf,
                    outputDir = tempImgDir,
                    baseName = "slide",
                    asPng = false,
                    scaleFactor = 1.6f,
                    password = password
                )
                require(slideImgs.isNotEmpty()) { "PDF has no pages to convert to PowerPoint." }

                val slideCount = slideImgs.size
                val contentTypes = buildString {
                    append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
                    append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
                    append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
                    append("""<Default Extension="xml" ContentType="application/xml"/>""")
                    append("""<Default Extension="jpeg" ContentType="image/jpeg"/>""")
                    append("""<Override PartName="/ppt/presentation.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"/>""")
                    for (i in 1..slideCount) {
                        append("""<Override PartName="/ppt/slides/slide$i.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>""")
                    }
                    append("</Types>")
                }

                val rootRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="ppt/presentation.xml"/>
</Relationships>"""

                val presRels = buildString {
                    append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
                    append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
                    for (i in 1..slideCount) {
                        append("""<Relationship Id="rId$i" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide$i.xml"/>""")
                    }
                    append("</Relationships>")
                }

                val presXml = buildString {
                    append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
                    append("""<p:presentation xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">""")
                    append("<p:sldIdLst>")
                    for (i in 1..slideCount) {
                        append("""<p:sldId id="${255 + i}" r:id="rId$i"/>""")
                    }
                    append("</p:sldIdLst>")
                    append("""<p:sldSz cx="9144000" cy="6858000" type="screen4x3"/>""")
                    append("""<p:notesSz cx="6858000" cy="9144000"/>""")
                    append("</p:presentation>")
                }

                ZipOutputStream(FileOutputStream(outputPptx)).use { zip ->
                    writeZipEntry(zip, "[Content_Types].xml", contentTypes)
                    writeZipEntry(zip, "_rels/.rels", rootRels)
                    writeZipEntry(zip, "ppt/_rels/presentation.xml.rels", presRels)
                    writeZipEntry(zip, "ppt/presentation.xml", presXml)

                    slideImgs.forEachIndexed { idx, imgFile ->
                        val n = idx + 1
                        val slideRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="../media/image$n.jpeg"/>
</Relationships>"""

                        val slideXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
  <p:cSld>
    <p:spTree>
      <p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>
      <p:grpSpPr/>
      <p:pic>
        <p:nvPicPr><p:cNvPr id="2" name="Page $n"/><p:cNvPicPr/><p:nvPr/></p:nvPicPr>
        <p:blipFill><a:blip r:embed="rId1"/><a:stretch><a:fillRect/></a:stretch></p:blipFill>
        <p:spPr>
          <a:xfrm><a:off x="0" y="0"/><a:ext cx="9144000" cy="6858000"/></a:xfrm>
          <a:prstGeom prst="rect"><a:avLst/></a:prstGeom>
        </p:spPr>
      </p:pic>
    </p:spTree>
  </p:cSld>
</p:sld>"""

                        writeZipEntry(zip, "ppt/slides/_rels/slide$n.xml.rels", slideRels)
                        writeZipEntry(zip, "ppt/slides/slide$n.xml", slideXml)
                        zip.putNextEntry(ZipEntry("ppt/media/image$n.jpeg"))
                        zip.write(imgFile.readBytes())
                        zip.closeEntry()
                    }
                }
                outputPptx
            } finally {
                tempImgDir.deleteRecursively()
            }
        }

    suspend fun pdfToHtml(inputPdf: File, outputHtml: File, password: String? = null): File =
        withContext(Dispatchers.IO) {
            outputHtml.parentFile?.mkdirs()
            val pages = extractPagesText(inputPdf, password)
            val html = buildString {
                append("""<!DOCTYPE html><html lang="en"><head><meta charset="UTF-8">""")
                append("<title>${ConverterCodec.escapeXml(inputPdf.nameWithoutExtension)}</title>")
                append("<style>body{font-family:system-ui,sans-serif;max-width:860px;margin:24px auto;padding:0 16px;line-height:1.6;color:#1e293b;background:#f8fafc}")
                append(".page{background:#fff;border-radius:10px;padding:24px;margin-bottom:20px;box-shadow:0 1px 4px rgba(0,0,0,.08)}")
                append(".pg-hdr{font-size:12px;color:#64748b;border-bottom:1px solid #e2e8f0;padding-bottom:6px;margin-bottom:12px}</style></head><body>")
                pages.forEachIndexed { idx, txt ->
                    val dir = if (ConverterCodec.containsArabic(txt)) "rtl" else "auto"
                    append("""<div class="page" dir="$dir"><div class="pg-hdr">Page ${idx + 1} / ${pages.size}</div>""")
                    txt.lines().forEach { line ->
                        if (line.isNotBlank()) {
                            append("<p>${ConverterCodec.escapeXml(line.trim())}</p>")
                        }
                    }
                    append("</div>")
                }
                append("""<footer style="text-align:center;color:#64748b;font-size:12px">${AppBranding.PDF_CREATOR_TAG}</footer></body></html>""")
            }
            outputHtml.writeText(html, StandardCharsets.UTF_8)
            outputHtml
        }

    suspend fun pdfToTxt(inputPdf: File, outputTxt: File, password: String? = null): File =
        withContext(Dispatchers.IO) {
            outputTxt.parentFile?.mkdirs()
            val text = pdfEngine.extractText(inputPdf, null, password)
            outputTxt.writeText(text, StandardCharsets.UTF_8)
            outputTxt
        }

    // =========================================================================
    // TO PDF CONVERSIONS (Full Arabic RTL + English Unicode Support!)
    // =========================================================================

    suspend fun docxToPdf(inputDocx: File, outputPdf: File): File = withContext(Dispatchers.IO) {
        val paragraphs = extractParagraphsFromDocx(inputDocx)
        require(paragraphs.isNotEmpty()) { "Could not read text from this Word (.docx) file." }
        renderParagraphsToPdf(
            title = inputDocx.nameWithoutExtension,
            paragraphs = paragraphs,
            outputPdf = outputPdf
        )
    }

    suspend fun xlsxToPdf(inputXlsx: File, outputPdf: File): File = withContext(Dispatchers.IO) {
        val rows = extractRowsFromXlsx(inputXlsx)
        require(rows.isNotEmpty()) { "No worksheet rows found in this Excel (.xlsx) file." }
        renderTableToPdf(title = inputXlsx.nameWithoutExtension, rows = rows, outputPdf = outputPdf)
    }

    suspend fun csvToPdf(inputCsv: File, outputPdf: File): File = withContext(Dispatchers.IO) {
        val rows = ConverterCodec.parseCsv(inputCsv.readText(StandardCharsets.UTF_8))
        require(rows.isNotEmpty()) { "CSV file is empty." }
        renderTableToPdf(title = inputCsv.nameWithoutExtension, rows = rows, outputPdf = outputPdf)
    }

    suspend fun pptxToPdf(inputPptx: File, outputPdf: File): File = withContext(Dispatchers.IO) {
        val slides = extractSlidesFromPptx(inputPptx)
        require(slides.isNotEmpty()) { "No slide text found in this PowerPoint (.pptx) file." }
        val flattened = mutableListOf<String>()
        slides.forEachIndexed { idx, slideLines ->
            flattened.add("--- Slide ${idx + 1} ---")
            flattened.addAll(slideLines)
            flattened.add("")
        }
        renderParagraphsToPdf(
            title = inputPptx.nameWithoutExtension,
            paragraphs = flattened,
            outputPdf = outputPdf
        )
    }

    suspend fun textOrHtmlToPdf(inputFile: File, outputPdf: File): File = withContext(Dispatchers.IO) {
        val raw = inputFile.readText(StandardCharsets.UTF_8)
        val text = if (inputFile.extension.equals("html", true) || inputFile.extension.equals("htm", true)) {
            ConverterCodec.stripHtmlToPlainText(raw)
        } else {
            raw
        }
        val lines = text.lines()
        renderParagraphsToPdf(
            title = inputFile.nameWithoutExtension,
            paragraphs = lines.ifEmpty { listOf(" ") },
            outputPdf = outputPdf
        )
    }

    // =========================================================================
    // CROSS-FORMAT OFFICE CONVERSIONS (CSV <-> EXCEL)
    // =========================================================================

    suspend fun csvToXlsx(inputCsv: File, outputXlsx: File): File = withContext(Dispatchers.IO) {
        val rows = ConverterCodec.parseCsv(inputCsv.readText(StandardCharsets.UTF_8))
        require(rows.isNotEmpty()) { "CSV file is empty." }
        writeRowsToXlsx(rows, outputXlsx)
    }

    suspend fun xlsxToCsv(inputXlsx: File, outputCsv: File): File = withContext(Dispatchers.IO) {
        outputCsv.parentFile?.mkdirs()
        val rows = extractRowsFromXlsx(inputXlsx)
        require(rows.isNotEmpty()) { "Excel file has no rows." }
        FileOutputStream(outputCsv).use { fos ->
            fos.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
            val sb = StringBuilder()
            for (row in rows) {
                sb.append(row.joinToString(",") { ConverterCodec.escapeCsvField(it) })
                sb.append("\r\n")
            }
            fos.write(sb.toString().toByteArray(StandardCharsets.UTF_8))
        }
        outputCsv
    }

    // =========================================================================
    // INTERNAL OOXML PARSERS & PAGINATED UNICODE/ARABIC PDF RENDERERS
    // =========================================================================

    private fun extractPagesText(inputPdf: File, password: String?): List<String> {
        val result = mutableListOf<String>()
        PDDocument.load(inputPdf, password ?: "").use { doc ->
            val stripper = PDFTextStripper()
            for (i in 1..doc.numberOfPages) {
                stripper.startPage = i
                stripper.endPage = i
                result.add(stripper.getText(doc))
            }
        }
        return result
    }

    private fun extractPdfTabularRows(inputPdf: File, password: String?): List<List<String>> {
        val pages = extractPagesText(inputPdf, password)
        val rows = mutableListOf<List<String>>()
        rows.add(listOf("Page", "Column 1", "Column 2", "Column 3", "Column 4"))
        pages.forEachIndexed { idx, txt ->
            txt.lines().forEach { line ->
                val clean = line.trim()
                if (clean.isNotEmpty()) {
                    val cols = clean.split(Regex("\\t+|\\s{2,}")).map { it.trim() }.filter { it.isNotEmpty() }
                    val row = mutableListOf("Page ${idx + 1}")
                    row.addAll(cols)
                    rows.add(row)
                }
            }
        }
        return rows
    }

    private fun writeRowsToXlsx(rows: List<List<String>>, outputXlsx: File): File {
        outputXlsx.parentFile?.mkdirs()
        val sheetData = buildString {
            rows.forEachIndexed { rIdx, row ->
                val rowNum = rIdx + 1
                append("""<row r="$rowNum">""")
                row.forEachIndexed { cIdx, cellVal ->
                    val ref = "${ConverterCodec.excelColName(cIdx)}$rowNum"
                    append("""<c r="$ref" t="inlineStr"><is><t>${ConverterCodec.escapeXml(cellVal)}</t></is></c>""")
                }
                append("</row>")
            }
        }

        val sheetXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <sheetData>$sheetData</sheetData>
</worksheet>"""

        val workbookXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets><sheet name="Sheet1" sheetId="1" r:id="rId1"/></sheets>
</workbook>"""

        val workbookRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
</Relationships>"""

        val rootRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

        val contentTypes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>"""

        ZipOutputStream(FileOutputStream(outputXlsx)).use { zip ->
            writeZipEntry(zip, "[Content_Types].xml", contentTypes)
            writeZipEntry(zip, "_rels/.rels", rootRels)
            writeZipEntry(zip, "xl/_rels/workbook.xml.rels", workbookRels)
            writeZipEntry(zip, "xl/workbook.xml", workbookXml)
            writeZipEntry(zip, "xl/worksheets/sheet1.xml", sheetXml)
        }
        return outputXlsx
    }

    private fun extractParagraphsFromDocx(docxFile: File): List<String> {
        val docXml = readZipEntryText(docxFile, "word/document.xml") ?: return emptyList()
        val paragraphs = mutableListOf<String>()
        val parser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }.newPullParser()
        parser.setInput(InputStreamReader(ByteArrayInputStream(docXml.toByteArray(StandardCharsets.UTF_8))))

        var event = parser.eventType
        val currentPara = StringBuilder()
        var inText = false
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "t" -> inText = true
                        "tab" -> currentPara.append("    ")
                        "br" -> currentPara.append("\n")
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inText) currentPara.append(parser.text)
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "t" -> inText = false
                        "p" -> {
                            paragraphs.add(currentPara.toString().trim())
                            currentPara.setLength(0)
                        }
                    }
                }
            }
            event = parser.next()
        }
        return paragraphs
    }

    private fun extractRowsFromXlsx(xlsxFile: File): List<List<String>> {
        val sharedStrings = mutableListOf<String>()
        readZipEntryText(xlsxFile, "xl/sharedStrings.xml")?.let { sstXml ->
            val p = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }.newPullParser()
            p.setInput(InputStreamReader(ByteArrayInputStream(sstXml.toByteArray(StandardCharsets.UTF_8))))
            var ev = p.eventType
            val sb = StringBuilder()
            var inT = false
            while (ev != XmlPullParser.END_DOCUMENT) {
                when (ev) {
                    XmlPullParser.START_TAG -> if (p.name == "t") inT = true
                    XmlPullParser.TEXT -> if (inT) sb.append(p.text)
                    XmlPullParser.END_TAG -> {
                        if (p.name == "t") inT = false
                        if (p.name == "si") {
                            sharedStrings.add(sb.toString())
                            sb.setLength(0)
                        }
                    }
                }
                ev = p.next()
            }
        }

        val sheetXml = readZipEntryText(xlsxFile, "xl/worksheets/sheet1.xml") ?: return emptyList()
        val rows = mutableListOf<List<String>>()
        val p = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }.newPullParser()
        p.setInput(InputStreamReader(ByteArrayInputStream(sheetXml.toByteArray(StandardCharsets.UTF_8))))

        var ev = p.eventType
        var currentRow = mutableListOf<String>()
        var cellType: String? = null
        val cellText = StringBuilder()
        var capture = false
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> {
                    when (p.name) {
                        "row" -> currentRow = mutableListOf()
                        "c" -> {
                            cellType = p.getAttributeValue(null, "t")
                            cellText.setLength(0)
                        }
                        "v", "t" -> capture = true
                    }
                }
                XmlPullParser.TEXT -> if (capture) cellText.append(p.text)
                XmlPullParser.END_TAG -> {
                    when (p.name) {
                        "v", "t" -> capture = false
                        "c" -> {
                            val raw = cellText.toString().trim()
                            val value = if (cellType == "s") {
                                raw.toIntOrNull()?.let { sharedStrings.getOrNull(it) } ?: raw
                            } else raw
                            currentRow.add(value)
                        }
                        "row" -> if (currentRow.any { it.isNotEmpty() }) rows.add(currentRow.toList())
                    }
                }
            }
            ev = p.next()
        }
        return rows
    }

    private fun extractSlidesFromPptx(pptxFile: File): List<List<String>> {
        val slidesMap = sortedMapOf<Int, List<String>>()
        ZipInputStream(FileInputStream(pptxFile)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val match = Regex("ppt/slides/slide(\\d+)\\.xml").matchEntire(entry.name)
                if (match != null) {
                    val slideNum = match.groupValues[1].toInt()
                    val xmlBytes = zis.readBytes()
                    val lines = mutableListOf<String>()
                    val p = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }.newPullParser()
                    p.setInput(InputStreamReader(ByteArrayInputStream(xmlBytes)))
                    var ev = p.eventType
                    var inT = false
                    val buf = StringBuilder()
                    while (ev != XmlPullParser.END_DOCUMENT) {
                        when (ev) {
                            XmlPullParser.START_TAG -> if (p.name == "t") inT = true
                            XmlPullParser.TEXT -> if (inT) buf.append(p.text)
                            XmlPullParser.END_TAG -> {
                                if (p.name == "t") inT = false
                                if (p.name == "p") {
                                    val s = buf.toString().trim()
                                    if (s.isNotEmpty()) lines.add(s)
                                    buf.setLength(0)
                                }
                            }
                        }
                        ev = p.next()
                    }
                    slidesMap[slideNum] = lines
                }
                entry = zis.nextEntry
            }
        }
        return slidesMap.values.toList()
    }

    private fun renderParagraphsToPdf(
        title: String,
        paragraphs: List<String>,
        outputPdf: File
    ): File {
        outputPdf.parentFile?.mkdirs()
        val pageW = 1240
        val pageH = 1754
        val margin = 90
        val contentW = pageW - margin * 2

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42)
            textSize = 42f
            typeface = Typeface.DEFAULT_BOLD
        }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 41, 59)
            textSize = 27f
            typeface = Typeface.DEFAULT
        }
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(100, 116, 139)
            textSize = 20f
        }

        PDDocument().use { doc ->
            var currentBmp = Bitmap.createBitmap(pageW, pageH, Bitmap.Config.ARGB_8888)
            var canvas = Canvas(currentBmp).apply { drawColor(Color.WHITE) }
            var cursorY = margin.toFloat()
            var pageNum = 1

            fun flushPage() {
                canvas.drawText(
                    "Page $pageNum  •  ${AppBranding.PDF_CREATOR_TAG}",
                    margin.toFloat(),
                    (pageH - 42).toFloat(),
                    footerPaint
                )
                val page = PDPage(PDRectangle.A4)
                doc.addPage(page)
                val pdImg = JPEGFactory.createFromImage(doc, currentBmp, 0.88f)
                PDPageContentStream(doc, page).use { cs ->
                    cs.drawImage(pdImg, 0f, 0f, page.mediaBox.width, page.mediaBox.height)
                }
                currentBmp.recycle()
            }

            // Title header on page 1
            if (title.isNotBlank()) {
                val align = if (ConverterCodec.containsArabic(title)) Layout.Alignment.ALIGN_OPPOSITE else Layout.Alignment.ALIGN_NORMAL
                val tl = StaticLayout.Builder.obtain(title, 0, title.length, titlePaint, contentW)
                    .setAlignment(align)
                    .build()
                canvas.save()
                canvas.translate(margin.toFloat(), cursorY)
                tl.draw(canvas)
                canvas.restore()
                cursorY += tl.height + 28f
            }

            for (para in paragraphs) {
                val text = para.ifBlank { " " }
                val align = if (ConverterCodec.containsArabic(text)) Layout.Alignment.ALIGN_OPPOSITE else Layout.Alignment.ALIGN_NORMAL
                val layout = StaticLayout.Builder.obtain(text, 0, text.length, bodyPaint, contentW)
                    .setAlignment(align)
                    .setLineSpacing(8f, 1.0f)
                    .build()

                if (cursorY + layout.height > pageH - margin - 40) {
                    flushPage()
                    pageNum++
                    currentBmp = Bitmap.createBitmap(pageW, pageH, Bitmap.Config.ARGB_8888)
                    canvas = Canvas(currentBmp).apply { drawColor(Color.WHITE) }
                    cursorY = margin.toFloat()
                }
                canvas.save()
                canvas.translate(margin.toFloat(), cursorY)
                layout.draw(canvas)
                canvas.restore()
                cursorY += layout.height + 16f
            }

            flushPage()
            doc.documentInformation.title = title
            doc.documentInformation.creator = AppBranding.PDF_CREATOR_TAG
            doc.save(outputPdf)
        }
        return outputPdf
    }

    private fun renderTableToPdf(
        title: String,
        rows: List<List<String>>,
        outputPdf: File
    ): File {
        outputPdf.parentFile?.mkdirs()
        val maxCols = rows.maxOfOrNull { it.size }?.coerceIn(1, 10) ?: 1
        val isWide = maxCols > 5
        val pageW = if (isWide) 1754 else 1240
        val pageH = if (isWide) 1240 else 1754
        val pageRect = if (isWide) PDRectangle(PDRectangle.A4.height, PDRectangle.A4.width) else PDRectangle.A4
        val margin = 64
        val tableW = pageW - margin * 2
        val colW = tableW.toFloat() / maxCols.toFloat()
        val rowH = 54f

        val headerBg = Paint().apply { color = Color.rgb(15, 23, 42); style = Paint.Style.FILL }
        val zebraBg = Paint().apply { color = Color.rgb(241, 245, 249); style = Paint.Style.FILL }
        val gridPaint = Paint().apply { color = Color.rgb(203, 213, 225); style = Paint.Style.STROKE; strokeWidth = 1.5f }
        val headerText = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
        }
        val cellText = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 41, 59)
            textSize = 21f
        }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42)
            textSize = 34f
            typeface = Typeface.DEFAULT_BOLD
        }

        PDDocument().use { doc ->
            var bmp = Bitmap.createBitmap(pageW, pageH, Bitmap.Config.ARGB_8888)
            var canvas = Canvas(bmp).apply { drawColor(Color.WHITE) }
            var y = margin.toFloat()
            canvas.drawText(title, margin.toFloat(), y + 32f, titlePaint)
            y += 56f

            fun flush() {
                val page = PDPage(pageRect)
                doc.addPage(page)
                val img = JPEGFactory.createFromImage(doc, bmp, 0.88f)
                PDPageContentStream(doc, page).use { cs ->
                    cs.drawImage(img, 0f, 0f, page.mediaBox.width, page.mediaBox.height)
                }
                bmp.recycle()
            }

            rows.forEachIndexed { rIdx, row ->
                if (y + rowH > pageH - margin) {
                    flush()
                    bmp = Bitmap.createBitmap(pageW, pageH, Bitmap.Config.ARGB_8888)
                    canvas = Canvas(bmp).apply { drawColor(Color.WHITE) }
                    y = margin.toFloat()
                }
                val isHeader = rIdx == 0
                val rowRect = RectF(margin.toFloat(), y, (margin + tableW).toFloat(), y + rowH)
                if (isHeader) {
                    canvas.drawRect(rowRect, headerBg)
                } else if (rIdx % 2 == 0) {
                    canvas.drawRect(rowRect, zebraBg)
                }
                canvas.drawRect(rowRect, gridPaint)

                for (c in 0 until maxCols) {
                    val x = margin + c * colW
                    canvas.drawLine(x, y, x, y + rowH, gridPaint)
                    val raw = row.getOrNull(c).orEmpty().take(38)
                    if (raw.isNotEmpty()) {
                        val paint = if (isHeader) headerText else cellText
                        val cellLayout = StaticLayout.Builder.obtain(raw, 0, raw.length, paint, (colW - 14f).toInt().coerceAtLeast(24))
                            .setAlignment(if (ConverterCodec.containsArabic(raw)) Layout.Alignment.ALIGN_OPPOSITE else Layout.Alignment.ALIGN_NORMAL)
                            .setMaxLines(2)
                            .build()
                        canvas.save()
                        canvas.translate(x + 7f, y + 8f)
                        cellLayout.draw(canvas)
                        canvas.restore()
                    }
                }
                y += rowH
            }

            flush()
            doc.documentInformation.title = title
            doc.documentInformation.creator = AppBranding.PDF_CREATOR_TAG
            doc.save(outputPdf)
        }
        return outputPdf
    }

    private fun readZipEntryText(zipFile: File, targetEntryName: String): String? {
        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == targetEntryName) {
                    return zis.readBytes().toString(StandardCharsets.UTF_8)
                }
                entry = zis.nextEntry
            }
        }
        return null
    }

    private fun writeZipEntry(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(StandardCharsets.UTF_8))
        zip.closeEntry()
    }
}
