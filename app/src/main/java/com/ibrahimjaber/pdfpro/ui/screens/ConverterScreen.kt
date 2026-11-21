package com.ibrahimjaber.pdfpro.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ibrahimjaber.pdfpro.PdfProApp
import com.ibrahimjaber.pdfpro.data.ConversionTarget
import com.ibrahimjaber.pdfpro.data.LocalStrings
import com.ibrahimjaber.pdfpro.data.PageRangeParser
import com.ibrahimjaber.pdfpro.data.PdfDocumentItem
import com.ibrahimjaber.pdfpro.engine.FileHelper
import com.ibrahimjaber.pdfpro.ui.theme.BrandCrimson
import com.ibrahimjaber.pdfpro.ui.theme.BrandEmerald
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ConverterScreen(
    app: PdfProApp,
    onOpenPdf: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = LocalStrings.current
    val libraryDocs by app.repository.documents.collectAsState()

    var activeTab by remember { mutableIntStateOf(0) } // 0 = From PDF, 1 = To PDF, 2 = Office <-> CSV
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var selectedImages by remember { mutableStateOf<List<File>>(emptyList()) }
    var fromPdfTarget by remember { mutableStateOf(ConversionTarget.DOCX) }
    var showLibraryPicker by remember { mutableStateOf(false) }

    var isConverting by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var resultFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var resultMime by remember { mutableStateOf("application/octet-stream") }
    var pendingExportFile by remember { mutableStateOf<File?>(null) }

    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val imported = FileHelper.importPdfFromUri(context, uri)
                if (imported != null) {
                    val info = app.pdfEngine.inspectDocument(imported)
                    app.repository.upsertDocument(
                        PdfDocumentItem(
                            name = imported.name,
                            path = imported.absolutePath,
                            sizeBytes = info.sizeBytes,
                            pageCount = info.pageCount,
                            isEncrypted = info.isEncrypted
                        )
                    )
                    selectedFile = imported
                    selectedImages = emptyList()
                    resultFiles = emptyList()
                    errorMsg = null
                }
            }
        }
    }

    val pickAnyFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val cached = FileHelper.copyAnyFileFromUriToCache(context, uri)
                if (cached != null) {
                    selectedFile = cached
                    selectedImages = emptyList()
                    resultFiles = emptyList()
                    errorMsg = null
                }
            }
        }
    }

    val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val files = uris.mapIndexedNotNull { idx, uri ->
                    FileHelper.copyImageFromUriToCache(context, uri, idx)
                }
                selectedImages = files
                selectedFile = null
                resultFiles = emptyList()
                errorMsg = null
            }
        }
    }

    val exportFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri: Uri? ->
        val src = pendingExportFile
        if (uri != null && src != null && src.exists()) {
            val ok = FileHelper.exportFileToUri(context, src, uri)
            Toast.makeText(
                context,
                if (ok) "Exported ${src.name}" else "Export failed",
                Toast.LENGTH_SHORT
            ).show()
        }
        pendingExportFile = null
    }

    val fromPdfTargets = remember {
        listOf(
            ConversionTarget.DOCX,
            ConversionTarget.XLSX,
            ConversionTarget.CSV,
            ConversionTarget.PPTX,
            ConversionTarget.PNG,
            ConversionTarget.JPEG,
            ConversionTarget.HTML,
            ConversionTarget.TXT
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text(s.converterTitle, style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = s.converterSubtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            TabRow(selectedTabIndex = activeTab) {
                Tab(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0; selectedFile = null; selectedImages = emptyList(); resultFiles = emptyList(); errorMsg = null },
                    text = { Text(s.fromPdfTab) }
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1; selectedFile = null; selectedImages = emptyList(); resultFiles = emptyList(); errorMsg = null },
                    text = { Text(s.toPdfTab) }
                )
                Tab(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2; selectedFile = null; selectedImages = emptyList(); resultFiles = emptyList(); errorMsg = null },
                    text = { Text(s.officeCrossTab) }
                )
            }
        }

        // Source Picker Card
        item {
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(s.selectSourceFile, style = MaterialTheme.typography.titleMedium)

                    when (activeTab) {
                        0 -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { pickPdf.launch(arrayOf("application/pdf")) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(s.fromDevice)
                                }
                                if (libraryDocs.isNotEmpty()) {
                                    OutlinedButton(
                                        onClick = { showLibraryPicker = true },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.PictureAsPdf, null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(s.fromLibrary)
                                    }
                                }
                            }
                        }

                        1 -> {
                            Text(
                                text = if (s.isArabic) {
                                    "يدعم تحويل: Word (.docx)، Excel (.xlsx)، CSV، PowerPoint (.pptx)، HTML، Markdown، نصوص TXT، والصور (JPG/PNG/WEBP)"
                                } else {
                                    "Supports: Word (.docx), Excel (.xlsx), CSV (.csv), PowerPoint (.pptx), HTML, Markdown (.md), Text (.txt), and Images (JPG/PNG/WEBP)"
                                },
                                fontSize = 12.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { pickAnyFile.launch(arrayOf("*/*")) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Description, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (s.isArabic) "ملف Word/Excel/CSV" else "Doc / Sheet / CSV")
                                }
                                OutlinedButton(
                                    onClick = { pickImages.launch(arrayOf("image/*")) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(if (s.isArabic) "صور متعددة" else "Pick Images")
                                }
                            }
                        }

                        2 -> {
                            Text(
                                text = if (s.isArabic) {
                                    "اختر ملف CSV لتحويله فوراً إلى Excel (.xlsx)، أو اختر ملف Excel (.xlsx) لتحويله إلى CSV عربي (UTF-8 BOM)."
                                } else {
                                    "Pick a .csv file to convert directly to Excel (.xlsx), or pick an .xlsx workbook to convert to Arabic-ready UTF-8 CSV."
                                },
                                fontSize = 12.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = { pickAnyFile.launch(arrayOf("*/*")) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.SwapHoriz, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (s.isArabic) "اختر ملف CSV أو Excel" else "Select .CSV or .XLSX File")
                            }
                        }
                    }

                    selectedFile?.let { f ->
                        Text(
                            text = "✓ ${f.name} (${PageRangeParser.formatFileSize(f.length())})",
                            color = BrandEmerald,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                    if (selectedImages.isNotEmpty()) {
                        Text(
                            text = "✓ ${selectedImages.size} image(s) selected",
                            color = BrandEmerald,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // Format Selection (For Tab 0: From PDF)
        if (activeTab == 0) {
            item {
                Card(shape = RoundedCornerShape(16.dp)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(s.targetFormat, style = MaterialTheme.typography.titleMedium)
                        fromPdfTargets.chunked(2).forEach { pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                pair.forEach { target ->
                                    FilterChip(
                                        selected = fromPdfTarget == target,
                                        onClick = { fromPdfTarget = target },
                                        label = {
                                            Text(
                                                if (s.isArabic) target.labelAr else target.labelEn,
                                                fontSize = 11.5.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Convert Button
        item {
            val canRun = !isConverting && (selectedFile != null || selectedImages.isNotEmpty())
            Button(
                enabled = canRun,
                onClick = {
                    scope.launch {
                        isConverting = true
                        errorMsg = null
                        resultFiles = emptyList()

                        runCatching {
                            val exportsDir = FileHelper.exportsDir(context)
                            val pdfsDir = FileHelper.pdfsDir(context)

                            when (activeTab) {
                                0 -> {
                                    val src = selectedFile!!
                                    val stem = src.nameWithoutExtension
                                    when (fromPdfTarget) {
                                        ConversionTarget.DOCX -> {
                                            val out = FileHelper.uniqueFile(exportsDir, "$stem.docx")
                                            app.converterEngine.pdfToDocx(src, out)
                                            resultFiles = listOf(out)
                                            resultMime = fromPdfTarget.mime
                                        }
                                        ConversionTarget.XLSX -> {
                                            val out = FileHelper.uniqueFile(exportsDir, "$stem.xlsx")
                                            app.converterEngine.pdfToXlsx(src, out)
                                            resultFiles = listOf(out)
                                            resultMime = fromPdfTarget.mime
                                        }
                                        ConversionTarget.CSV -> {
                                            val out = FileHelper.uniqueFile(exportsDir, "$stem.csv")
                                            app.converterEngine.pdfToCsv(src, out)
                                            resultFiles = listOf(out)
                                            resultMime = fromPdfTarget.mime
                                        }
                                        ConversionTarget.PPTX -> {
                                            val out = FileHelper.uniqueFile(exportsDir, "$stem.pptx")
                                            app.converterEngine.pdfToPptx(src, out)
                                            resultFiles = listOf(out)
                                            resultMime = fromPdfTarget.mime
                                        }
                                        ConversionTarget.HTML -> {
                                            val out = FileHelper.uniqueFile(exportsDir, "$stem.html")
                                            app.converterEngine.pdfToHtml(src, out)
                                            resultFiles = listOf(out)
                                            resultMime = fromPdfTarget.mime
                                        }
                                        ConversionTarget.TXT -> {
                                            val out = FileHelper.uniqueFile(exportsDir, "$stem.txt")
                                            app.converterEngine.pdfToTxt(src, out)
                                            resultFiles = listOf(out)
                                            resultMime = fromPdfTarget.mime
                                        }
                                        ConversionTarget.PNG -> {
                                            val imgs = app.pdfEngine.pdfToImages(src, exportsDir, stem, asPng = true)
                                            resultFiles = imgs
                                            resultMime = "image/png"
                                        }
                                        ConversionTarget.JPEG -> {
                                            val imgs = app.pdfEngine.pdfToImages(src, exportsDir, stem, asPng = false)
                                            resultFiles = imgs
                                            resultMime = "image/jpeg"
                                        }
                                        else -> Unit
                                    }
                                }

                                1 -> {
                                    val outPdf = if (selectedImages.isNotEmpty()) {
                                        val out = FileHelper.uniqueFile(pdfsDir, "Images_${System.currentTimeMillis()}.pdf")
                                        app.pdfEngine.imagesToPdf(selectedImages, out)
                                    } else {
                                        val src = selectedFile!!
                                        val out = FileHelper.uniqueFile(pdfsDir, "${src.nameWithoutExtension}.pdf")
                                        when (src.extension.lowercase()) {
                                            "docx" -> app.converterEngine.docxToPdf(src, out)
                                            "xlsx" -> app.converterEngine.xlsxToPdf(src, out)
                                            "csv" -> app.converterEngine.csvToPdf(src, out)
                                            "pptx" -> app.converterEngine.pptxToPdf(src, out)
                                            "jpg", "jpeg", "png", "webp", "bmp" -> app.pdfEngine.imagesToPdf(listOf(src), out)
                                            "html", "htm", "txt", "md", "json", "xml" -> app.converterEngine.textOrHtmlToPdf(src, out)
                                            else -> app.converterEngine.textOrHtmlToPdf(src, out)
                                        }
                                    }
                                    val info = app.pdfEngine.inspectDocument(outPdf)
                                    app.repository.upsertDocument(
                                        PdfDocumentItem(
                                            name = outPdf.name,
                                            path = outPdf.absolutePath,
                                            sizeBytes = info.sizeBytes,
                                            pageCount = info.pageCount
                                        )
                                    )
                                    resultFiles = listOf(outPdf)
                                    resultMime = "application/pdf"
                                }

                                2 -> {
                                    val src = selectedFile!!
                                    if (src.extension.equals("xlsx", ignoreCase = true)) {
                                        val out = FileHelper.uniqueFile(exportsDir, "${src.nameWithoutExtension}.csv")
                                        app.converterEngine.xlsxToCsv(src, out)
                                        resultFiles = listOf(out)
                                        resultMime = "text/csv"
                                    } else {
                                        val out = FileHelper.uniqueFile(exportsDir, "${src.nameWithoutExtension}.xlsx")
                                        app.converterEngine.csvToXlsx(src, out)
                                        resultFiles = listOf(out)
                                        resultMime = ConversionTarget.XLSX.mime
                                    }
                                }
                            }
                        }.onFailure { err ->
                            errorMsg = err.message ?: "Conversion failed"
                        }
                        isConverting = false
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (isConverting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text(s.processingOffline)
                } else {
                    Text(s.convertNow, fontWeight = FontWeight.Bold)
                }
            }
        }

        errorMsg?.let { err ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = BrandCrimson.copy(alpha = 0.12f))) {
                    Text(err, color = BrandCrimson, modifier = Modifier.padding(14.dp))
                }
            }
        }

        if (resultFiles.isNotEmpty()) {
            item {
                Card(shape = RoundedCornerShape(16.dp)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = BrandEmerald)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (s.isArabic) "تم التحويل بنجاح (${resultFiles.size} ملف)" else "Conversion Complete (${resultFiles.size} file${if (resultFiles.size == 1) "" else "s"})",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        if (resultFiles.size == 1) {
                            val file = resultFiles.first()
                            Text("${file.name} (${PageRangeParser.formatFileSize(file.length())})", fontSize = 13.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (file.extension.equals("pdf", ignoreCase = true)) {
                                    Button(onClick = { onOpenPdf(file.absolutePath) }) {
                                        Text(if (s.isArabic) "فتح" else "Open")
                                    }
                                }
                                OutlinedButton(onClick = {
                                    pendingExportFile = file
                                    exportFileLauncher.launch(file.name)
                                }) {
                                    Icon(Icons.Default.FileDownload, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(s.exportDevice)
                                }
                                OutlinedButton(onClick = { FileHelper.shareFile(context, file, resultMime) }) {
                                    Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(s.share)
                                }
                            }
                        } else {
                            Button(
                                onClick = { FileHelper.shareMultipleFiles(context, resultFiles, resultMime) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("${s.share} (${resultFiles.size})")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showLibraryPicker) {
        AlertDialog(
            onDismissRequest = { showLibraryPicker = false },
            title = { Text(s.fromLibrary) },
            text = {
                LazyColumn(modifier = Modifier.height(280.dp)) {
                    itemsIndexed(libraryDocs) { _, doc ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedFile = File(doc.path)
                                    selectedImages = emptyList()
                                    showLibraryPicker = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.PictureAsPdf, null, tint = BrandCrimson)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(doc.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                                Text(
                                    "${doc.pageCount} pages • ${PageRangeParser.formatFileSize(doc.sizeBytes)}",
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLibraryPicker = false }) { Text(s.cancel) }
            }
        )
    }
}
