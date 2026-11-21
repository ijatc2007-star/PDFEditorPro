package com.ibrahimjaber.pdfpro.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ibrahimjaber.pdfpro.PdfProApp
import com.ibrahimjaber.pdfpro.data.AppBranding
import com.ibrahimjaber.pdfpro.data.CompressionLevel
import com.ibrahimjaber.pdfpro.data.CompressionResult
import com.ibrahimjaber.pdfpro.data.HeaderFooterConfig
import com.ibrahimjaber.pdfpro.data.LocalStrings
import com.ibrahimjaber.pdfpro.data.PageNumberFormat
import com.ibrahimjaber.pdfpro.data.PageNumberPosition
import com.ibrahimjaber.pdfpro.data.PageRangeParser
import com.ibrahimjaber.pdfpro.data.PdfDocumentItem
import com.ibrahimjaber.pdfpro.data.PdfMetadata
import com.ibrahimjaber.pdfpro.engine.FileHelper
import com.ibrahimjaber.pdfpro.ui.theme.BrandBlue
import com.ibrahimjaber.pdfpro.ui.theme.BrandCrimson
import com.ibrahimjaber.pdfpro.ui.theme.BrandEmerald
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProToolWorkspaceScreen(
    app: PdfProApp,
    toolId: String,
    onBack: () -> Unit,
    onOpenPdf: (String) -> Unit,
    onOpenOrganizer: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = LocalStrings.current
    val libraryDocs by app.repository.documents.collectAsState()
    val toolMeta = remember(toolId) {
        ALL_PRO_TOOLS.firstOrNull { it.id == toolId } ?: ALL_PRO_TOOLS.first()
    }
    val needsMultiplePdfs = toolId == "merge" || toolId == "compare"

    // Shared tool state
    val selectedPdfFiles = remember { mutableStateListOf<File>() }
    val selectedImageFiles = remember { mutableStateListOf<File>() }
    var showLibraryPicker by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var statusError by remember { mutableStateOf<String?>(null) }

    // Result state
    var resultPdfFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var resultImageFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var compressionResult by remember { mutableStateOf<CompressionResult?>(null) }
    var extractedTextResult by remember { mutableStateOf<String?>(null) }
    var exportPendingFile by remember { mutableStateOf<File?>(null) }

    // Tool-specific option states
    var outputName by remember { mutableStateOf("PDFPro_${toolId}_2026") }
    var splitEveryPage by remember { mutableStateOf(false) }
    var splitRange by remember { mutableStateOf("1-2") }
    var exportAsPng by remember { mutableStateOf(true) }
    var compressionLevel by remember { mutableStateOf(CompressionLevel.MEDIUM) }
    var userPassword by remember { mutableStateOf("") }
    var allowPrinting by remember { mutableStateOf(true) }
    var allowCopying by remember { mutableStateOf(false) }
    var watermarkText by remember { mutableStateOf("CONFIDENTIAL — IBRAHIM JABER 2026") }
    var watermarkOpacity by remember { mutableFloatStateOf(0.28f) }
    var watermarkTiled by remember { mutableStateOf(false) }
    var pageNumPos by remember { mutableStateOf(PageNumberPosition.BOTTOM_CENTER) }
    var pageNumFormat by remember { mutableStateOf(PageNumberFormat.PAGE_X_OF_Y) }
    var metaTitle by remember { mutableStateOf("") }
    var metaAuthor by remember { mutableStateOf(AppBranding.DEVELOPER_NAME) }
    var metaSubject by remember { mutableStateOf("") }
    var metaKeywords by remember { mutableStateOf("") }
    var metaCreator by remember { mutableStateOf(AppBranding.PDF_CREATOR_TAG) }
    var headerLeft by remember { mutableStateOf("") }
    var headerCenter by remember { mutableStateOf("CONFIDENTIAL") }
    var footerLeft by remember { mutableStateOf(AppBranding.CREDIT_LINE) }
    var batesPrefix by remember { mutableStateOf("DOC-") }
    var batesStart by remember { mutableStateOf("1") }

    // Auto-load metadata when metadata tool picks a file
    LaunchedEffect(toolId, selectedPdfFiles.firstOrNull()?.absolutePath) {
        val file = selectedPdfFiles.firstOrNull()
        if (toolId == "metadata" && file != null) {
            runCatching { app.pdfEngine.readMetadata(file) }.onSuccess { meta ->
                metaTitle = meta.title
                metaAuthor = meta.author.ifBlank { AppBranding.DEVELOPER_NAME }
                metaSubject = meta.subject
                metaKeywords = meta.keywords
                metaCreator = meta.creator.ifBlank { AppBranding.PDF_CREATOR_TAG }
            }
        }
    }

    val pickSinglePdf = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
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
                    if (toolId == "organize") {
                        onOpenOrganizer(imported.absolutePath)
                    } else {
                        if (!needsMultiplePdfs) selectedPdfFiles.clear()
                        selectedPdfFiles.add(imported)
                    }
                }
            }
        }
    }

    val pickMultiplePdfs = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            scope.launch {
                for (uri in uris) {
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
                        selectedPdfFiles.add(imported)
                    }
                }
            }
        }
    }

    val pickMultipleImages = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            scope.launch {
                uris.forEachIndexed { idx, uri ->
                    FileHelper.copyImageFromUriToCache(context, uri, idx)?.let { f ->
                        selectedImageFiles.add(f)
                    }
                }
            }
        }
    }

    val exportDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? ->
        val src = exportPendingFile
        if (uri != null && src != null && src.exists()) {
            val ok = FileHelper.exportFileToUri(context, src, uri)
            Toast.makeText(context, if (ok) "Exported ${src.name}" else "Export failed", Toast.LENGTH_SHORT).show()
        }
        exportPendingFile = null
    }

    val exportTxtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        val txt = extractedTextResult
        if (uri != null && txt != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(txt.toByteArray()) }
            }.onSuccess {
                Toast.makeText(context, "Saved text file!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    suspend fun registerOutputPdfs(files: List<File>) {
        for (f in files) {
            val info = app.pdfEngine.inspectDocument(f)
            app.repository.upsertDocument(
                PdfDocumentItem(
                    name = f.name,
                    path = f.absolutePath,
                    sizeBytes = info.sizeBytes,
                    pageCount = info.pageCount,
                    isEncrypted = info.isEncrypted
                )
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Column {
                        Text(if (s.isArabic) toolMeta.titleAr else toolMeta.title, style = MaterialTheme.typography.titleMedium)
                        Text(if (s.isArabic) toolMeta.subtitleAr else toolMeta.subtitle, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // File / Image Picker Section
            item {
                Card(shape = RoundedCornerShape(16.dp)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = if (toolId == "images_to_pdf") "1. Select Images" else s.selectSourceFile,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    when {
                                        toolId == "images_to_pdf" -> pickMultipleImages.launch(arrayOf("image/*"))
                                        needsMultiplePdfs -> pickMultiplePdfs.launch(arrayOf("application/pdf"))
                                        else -> pickSinglePdf.launch(arrayOf("application/pdf"))
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (toolId == "images_to_pdf") "From Gallery" else s.fromDevice)
                            }

                            if (toolId != "images_to_pdf" && libraryDocs.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = { showLibraryPicker = true },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.PictureAsPdf, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("From Library")
                                }
                            }
                        }

                        if (toolId == "organize") {
                            Text(
                                text = "Choose any PDF above to launch the visual thumbnail Page Organizer (Reorder, Rotate 90°, Duplicate, Delete & Add Blank Pages).",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Selected Items List
            if (toolId == "images_to_pdf" && selectedImageFiles.isNotEmpty()) {
                itemsIndexed(selectedImageFiles) { idx, img ->
                    SelectedItemRow(
                        label = "${idx + 1}. ${img.name}",
                        subtitle = PageRangeParser.formatFileSize(img.length()),
                        canMoveUp = idx > 0,
                        canMoveDown = idx < selectedImageFiles.lastIndex,
                        onMoveUp = {
                            val item = selectedImageFiles.removeAt(idx)
                            selectedImageFiles.add(idx - 1, item)
                        },
                        onMoveDown = {
                            val item = selectedImageFiles.removeAt(idx)
                            selectedImageFiles.add(idx + 1, item)
                        },
                        onRemove = { selectedImageFiles.removeAt(idx) }
                    )
                }
            } else if (selectedPdfFiles.isNotEmpty()) {
                itemsIndexed(selectedPdfFiles) { idx, pdf ->
                    SelectedItemRow(
                        label = if (toolId == "merge") "${idx + 1}. ${pdf.name}" else pdf.name,
                        subtitle = PageRangeParser.formatFileSize(pdf.length()),
                        canMoveUp = toolId == "merge" && idx > 0,
                        canMoveDown = toolId == "merge" && idx < selectedPdfFiles.lastIndex,
                        onMoveUp = {
                            val item = selectedPdfFiles.removeAt(idx)
                            selectedPdfFiles.add(idx - 1, item)
                        },
                        onMoveDown = {
                            val item = selectedPdfFiles.removeAt(idx)
                            selectedPdfFiles.add(idx + 1, item)
                        },
                        onRemove = { selectedPdfFiles.removeAt(idx) }
                    )
                }
            }

            // Tool-Specific Configuration Card
            if (toolId != "organize") {
                item {
                    Card(shape = RoundedCornerShape(16.dp)) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("2. Configure Options", style = MaterialTheme.typography.titleMedium)

                            if (toolId !in listOf("extract_text", "pdf_to_images")) {
                                OutlinedTextField(
                                    value = outputName,
                                    onValueChange = { outputName = it },
                                    label = { Text("Output PDF Name") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            when (toolId) {
                                "split" -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Split every page into separate PDFs")
                                        Switch(checked = splitEveryPage, onCheckedChange = { splitEveryPage = it })
                                    }
                                    if (!splitEveryPage) {
                                        OutlinedTextField(
                                            value = splitRange,
                                            onValueChange = { splitRange = it },
                                            label = { Text("Page Range (e.g. 1-3, 5, 7-9)") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }

                                "pdf_to_images" -> {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        FilterChip(
                                            selected = exportAsPng,
                                            onClick = { exportAsPng = true },
                                            label = { Text("PNG (Lossless)") }
                                        )
                                        FilterChip(
                                            selected = !exportAsPng,
                                            onClick = { exportAsPng = false },
                                            label = { Text("JPEG (Smaller)") }
                                        )
                                    }
                                }

                                "compress" -> {
                                    CompressionLevel.entries.forEach { level ->
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { compressionLevel = level },
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (compressionLevel == level) {
                                                    MaterialTheme.colorScheme.primaryContainer
                                                } else MaterialTheme.colorScheme.surfaceVariant
                                            )
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Text(level.label, fontWeight = FontWeight.Bold)
                                                Text(level.description, fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }

                                "protect" -> {
                                    OutlinedTextField(
                                        value = userPassword,
                                        onValueChange = { userPassword = it },
                                        label = { Text("Set PDF Password (AES-128)") },
                                        visualTransformation = PasswordVisualTransformation(),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(checked = allowPrinting, onCheckedChange = { allowPrinting = it })
                                        Text("Allow Printing")
                                        Spacer(Modifier.width(12.dp))
                                        Checkbox(checked = allowCopying, onCheckedChange = { allowCopying = it })
                                        Text("Allow Text Copy")
                                    }
                                }

                                "unlock" -> {
                                    OutlinedTextField(
                                        value = userPassword,
                                        onValueChange = { userPassword = it },
                                        label = { Text("Current PDF Password") },
                                        visualTransformation = PasswordVisualTransformation(),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                "watermark" -> {
                                    OutlinedTextField(
                                        value = watermarkText,
                                        onValueChange = { watermarkText = it },
                                        label = { Text("Watermark Text (English / Arabic)") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Text("Opacity: ${(watermarkOpacity * 100).toInt()}%", fontSize = 13.sp)
                                    Slider(
                                        value = watermarkOpacity,
                                        onValueChange = { watermarkOpacity = it },
                                        valueRange = 0.1f..0.75f
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Tiled pattern (repeat across page)")
                                        Switch(checked = watermarkTiled, onCheckedChange = { watermarkTiled = it })
                                    }
                                }

                                "page_numbers" -> {
                                    Text("Position:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        PageNumberPosition.entries.forEach { pos ->
                                            FilterChip(
                                                selected = pageNumPos == pos,
                                                onClick = { pageNumPos = pos },
                                                label = { Text(pos.label, fontSize = 11.sp) }
                                            )
                                        }
                                    }
                                    Text("Format:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        PageNumberFormat.entries.forEach { fmt ->
                                            FilterChip(
                                                selected = pageNumFormat == fmt,
                                                onClick = { pageNumFormat = fmt },
                                                label = { Text(fmt.label, fontSize = 11.sp) }
                                            )
                                        }
                                    }
                                }

                                "metadata" -> {
                                    OutlinedTextField(value = metaTitle, onValueChange = { metaTitle = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                                    OutlinedTextField(value = metaAuthor, onValueChange = { metaAuthor = it }, label = { Text("Author") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                                    OutlinedTextField(value = metaSubject, onValueChange = { metaSubject = it }, label = { Text("Subject") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                                    OutlinedTextField(value = metaKeywords, onValueChange = { metaKeywords = it }, label = { Text("Keywords") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                                    OutlinedTextField(value = metaCreator, onValueChange = { metaCreator = it }, label = { Text("Creator") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                                }

                                "header_bates" -> {
                                    OutlinedTextField(value = headerLeft, onValueChange = { headerLeft = it }, label = { Text("Header Left (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                                    OutlinedTextField(value = headerCenter, onValueChange = { headerCenter = it }, label = { Text("Header Center") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                                    OutlinedTextField(value = footerLeft, onValueChange = { footerLeft = it }, label = { Text("Footer Left") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                                    OutlinedTextField(value = batesPrefix, onValueChange = { batesPrefix = it }, label = { Text("Bates Prefix (e.g. DOC- / CASE-)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                                    OutlinedTextField(value = batesStart, onValueChange = { batesStart = it.filter { ch -> ch.isDigit() } }, label = { Text("Bates Start Number") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                                }

                                "grayscale" -> {
                                    Text(
                                        text = if (s.isArabic) "تحويل جميع صفحات PDF إلى أبيض وأسود / رمادي عالي الوضوح لتوفير الحبر وتقليل الحجم."
                                        else "Converts every page into crisp Black & White / Grayscale to save printer ink and reduce file size.",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                "compare" -> {
                                    Text(
                                        text = if (s.isArabic) "اختر ملفي PDF أعلاه (الملف 1 والملف 2) لإصدار تقرير مقارنة شامل بالفروقات."
                                        else "Select 2 PDF files above (Document A and Document B) to generate an offline page-by-page diff report.",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(Modifier.height(4.dp))
                            Button(
                                enabled = !isProcessing && (
                                    (toolId == "images_to_pdf" && selectedImageFiles.isNotEmpty()) ||
                                        (needsMultiplePdfs && selectedPdfFiles.size >= 2) ||
                                        (toolId != "images_to_pdf" && !needsMultiplePdfs && selectedPdfFiles.isNotEmpty())
                                    ),
                                onClick = {
                                    scope.launch {
                                        isProcessing = true
                                        statusError = null
                                        resultPdfFiles = emptyList()
                                        resultImageFiles = emptyList()
                                        compressionResult = null
                                        extractedTextResult = null

                                        runCatching {
                                            val pdfsDir = FileHelper.pdfsDir(context)
                                            val exportsDir = FileHelper.exportsDir(context)
                                            val safePdfName = FileHelper.sanitizeFileName(outputName, ".pdf")

                                            when (toolId) {
                                                "merge" -> {
                                                    val out = FileHelper.uniqueFile(pdfsDir, safePdfName)
                                                    app.pdfEngine.mergePdfs(selectedPdfFiles.toList(), out)
                                                    registerOutputPdfs(listOf(out))
                                                    resultPdfFiles = listOf(out)
                                                }

                                                "split" -> {
                                                    val src = selectedPdfFiles.first()
                                                    val outs = app.pdfEngine.splitPdf(
                                                        inputFile = src,
                                                        outputDir = pdfsDir,
                                                        baseName = outputName.ifBlank { src.nameWithoutExtension },
                                                        rangeExpression = splitRange,
                                                        splitEveryPage = splitEveryPage
                                                    )
                                                    registerOutputPdfs(outs)
                                                    resultPdfFiles = outs
                                                }

                                                "images_to_pdf" -> {
                                                    val out = FileHelper.uniqueFile(pdfsDir, safePdfName)
                                                    app.pdfEngine.imagesToPdf(selectedImageFiles.toList(), out)
                                                    registerOutputPdfs(listOf(out))
                                                    resultPdfFiles = listOf(out)
                                                }

                                                "pdf_to_images" -> {
                                                    val src = selectedPdfFiles.first()
                                                    val imgs = app.pdfEngine.pdfToImages(
                                                        inputFile = src,
                                                        outputDir = exportsDir,
                                                        baseName = src.nameWithoutExtension,
                                                        asPng = exportAsPng
                                                    )
                                                    resultImageFiles = imgs
                                                }

                                                "compress" -> {
                                                    val src = selectedPdfFiles.first()
                                                    val out = FileHelper.uniqueFile(pdfsDir, safePdfName)
                                                    val res = app.pdfEngine.compressPdf(src, out, compressionLevel)
                                                    registerOutputPdfs(listOf(out))
                                                    compressionResult = res
                                                    resultPdfFiles = listOf(out)
                                                }

                                                "grayscale" -> {
                                                    val src = selectedPdfFiles.first()
                                                    val out = FileHelper.uniqueFile(pdfsDir, safePdfName)
                                                    val res = app.pdfEngine.convertToGrayscalePdf(src, out)
                                                    registerOutputPdfs(listOf(out))
                                                    compressionResult = res
                                                    resultPdfFiles = listOf(out)
                                                }

                                                "protect" -> {
                                                    val src = selectedPdfFiles.first()
                                                    val out = FileHelper.uniqueFile(pdfsDir, safePdfName)
                                                    app.pdfEngine.protectPdf(
                                                        inputFile = src,
                                                        outputFile = out,
                                                        userPassword = userPassword,
                                                        allowPrinting = allowPrinting,
                                                        allowCopying = allowCopying
                                                    )
                                                    registerOutputPdfs(listOf(out))
                                                    resultPdfFiles = listOf(out)
                                                }

                                                "unlock" -> {
                                                    val src = selectedPdfFiles.first()
                                                    val out = FileHelper.uniqueFile(pdfsDir, safePdfName)
                                                    app.pdfEngine.unlockPdf(src, out, userPassword)
                                                    registerOutputPdfs(listOf(out))
                                                    resultPdfFiles = listOf(out)
                                                }

                                                "watermark" -> {
                                                    val src = selectedPdfFiles.first()
                                                    val out = FileHelper.uniqueFile(pdfsDir, safePdfName)
                                                    app.pdfEngine.addWatermark(
                                                        inputFile = src,
                                                        outputFile = out,
                                                        watermarkText = watermarkText,
                                                        opacity = watermarkOpacity,
                                                        tiled = watermarkTiled
                                                    )
                                                    registerOutputPdfs(listOf(out))
                                                    resultPdfFiles = listOf(out)
                                                }

                                                "page_numbers" -> {
                                                    val src = selectedPdfFiles.first()
                                                    val out = FileHelper.uniqueFile(pdfsDir, safePdfName)
                                                    app.pdfEngine.addPageNumbers(
                                                        inputFile = src,
                                                        outputFile = out,
                                                        position = pageNumPos,
                                                        format = pageNumFormat
                                                    )
                                                    registerOutputPdfs(listOf(out))
                                                    resultPdfFiles = listOf(out)
                                                }

                                                "header_bates" -> {
                                                    val src = selectedPdfFiles.first()
                                                    val out = FileHelper.uniqueFile(pdfsDir, safePdfName)
                                                    app.pdfEngine.addHeaderFooterAndBates(
                                                        inputFile = src,
                                                        outputFile = out,
                                                        config = HeaderFooterConfig(
                                                            headerLeft = headerLeft,
                                                            headerCenter = headerCenter,
                                                            footerLeft = footerLeft,
                                                            batesPrefix = batesPrefix,
                                                            batesStart = batesStart.toIntOrNull()?.coerceAtLeast(1) ?: 1
                                                        )
                                                    )
                                                    registerOutputPdfs(listOf(out))
                                                    resultPdfFiles = listOf(out)
                                                }

                                                "compare" -> {
                                                    val diff = app.pdfEngine.comparePdfs(selectedPdfFiles[0], selectedPdfFiles[1])
                                                    extractedTextResult = diff.reportText
                                                }

                                                "extract_text" -> {
                                                    val src = selectedPdfFiles.first()
                                                    extractedTextResult = app.pdfEngine.extractText(src)
                                                }

                                                "metadata" -> {
                                                    val src = selectedPdfFiles.first()
                                                    val out = FileHelper.uniqueFile(pdfsDir, safePdfName)
                                                    app.pdfEngine.updateMetadata(
                                                        inputFile = src,
                                                        outputFile = out,
                                                        metadata = PdfMetadata(
                                                            title = metaTitle,
                                                            author = metaAuthor,
                                                            subject = metaSubject,
                                                            keywords = metaKeywords,
                                                            creator = metaCreator
                                                        )
                                                    )
                                                    registerOutputPdfs(listOf(out))
                                                    resultPdfFiles = listOf(out)
                                                }
                                            }
                                        }.onFailure { err ->
                                            statusError = err.message ?: "Operation failed"
                                        }
                                        isProcessing = false
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (isProcessing) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                                    Spacer(Modifier.width(8.dp))
                                    Text(s.processingOffline)
                                } else {
                                    Text(if (s.isArabic) "تشغيل ${toolMeta.titleAr}" else "Run ${toolMeta.title}")
                                }
                            }
                        }
                    }
                }
            }

            // Error banner
            statusError?.let { err ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = BrandCrimson.copy(alpha = 0.12f)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            text = "Error: $err",
                            color = BrandCrimson,
                            modifier = Modifier.padding(14.dp),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Compression Savings Card
            compressionResult?.let { cr ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = BrandEmerald.copy(alpha = 0.12f)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, null, tint = BrandEmerald)
                                Spacer(Modifier.width(8.dp))
                                Text("Compression Complete (${cr.savedPercent}% smaller)", fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Original: ${PageRangeParser.formatFileSize(cr.originalBytes)}  →  Compressed: ${PageRangeParser.formatFileSize(cr.compressedBytes)}",
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // PDF Output Results
            if (resultPdfFiles.isNotEmpty()) {
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
                                    "Created ${resultPdfFiles.size} PDF file${if (resultPdfFiles.size == 1) "" else "s"}!",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            resultPdfFiles.forEach { f ->
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        "${f.name} (${PageRangeParser.formatFileSize(f.length())})",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.5.sp
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(onClick = { onOpenPdf(f.absolutePath) }) {
                                            Text("Open")
                                        }
                                        OutlinedButton(onClick = {
                                            exportPendingFile = f
                                            exportDocLauncher.launch(f.name)
                                        }) {
                                            Icon(Icons.Default.FileDownload, null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Export")
                                        }
                                        OutlinedButton(onClick = { FileHelper.shareFile(context, f) }) {
                                            Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Share")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Image Export Results
            if (resultImageFiles.isNotEmpty()) {
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
                                Text("Exported ${resultImageFiles.size} Page Images!", style = MaterialTheme.typography.titleMedium)
                            }
                            Button(
                                onClick = {
                                    FileHelper.shareMultipleFiles(
                                        context,
                                        resultImageFiles,
                                        if (exportAsPng) "image/png" else "image/jpeg"
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Share All ${resultImageFiles.size} Images")
                            }
                        }
                    }
                }
            }

            // Extracted Text Result
            extractedTextResult?.let { txt ->
                item {
                    Card(shape = RoundedCornerShape(16.dp)) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("Extracted Text (${txt.length} chars)", style = MaterialTheme.typography.titleMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("PDF Extracted Text", txt))
                                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Copy All")
                                }
                                OutlinedButton(onClick = { exportTxtLauncher.launch("extracted_text.txt") }) {
                                    Icon(Icons.Default.FileDownload, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Save .TXT")
                                }
                            }
                            Text(
                                text = txt.ifBlank { "No selectable text found in this PDF." }.take(6000),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    // Library Picker Dialog
    if (showLibraryPicker) {
        AlertDialog(
            onDismissRequest = { showLibraryPicker = false },
            title = { Text("Choose PDF from Library") },
            text = {
                LazyColumn(modifier = Modifier.height(280.dp)) {
                    itemsIndexed(libraryDocs) { _, doc ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showLibraryPicker = false
                                    if (toolId == "organize") {
                                        onOpenOrganizer(doc.path)
                                    } else {
                                        if (!needsMultiplePdfs) selectedPdfFiles.clear()
                                        selectedPdfFiles.add(File(doc.path))
                                    }
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.PictureAsPdf, null, tint = BrandCrimson)
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
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
                TextButton(onClick = { showLibraryPicker = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SelectedItemRow(
    label: String,
    subtitle: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    Card(shape = RoundedCornerShape(12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.5.sp)
                Text(subtitle, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (canMoveUp) {
                IconButton(onClick = onMoveUp, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = "Up", tint = BrandBlue, modifier = Modifier.size(18.dp))
                }
            }
            if (canMoveDown) {
                IconButton(onClick = onMoveDown, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = "Down", tint = BrandBlue, modifier = Modifier.size(18.dp))
                }
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Remove", tint = BrandCrimson, modifier = Modifier.size(18.dp))
            }
        }
    }
}
