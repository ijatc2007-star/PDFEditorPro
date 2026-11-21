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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import com.ibrahimjaber.pdfpro.data.LocalStrings
import com.ibrahimjaber.pdfpro.data.PageRangeParser
import com.ibrahimjaber.pdfpro.data.PdfDocumentItem
import com.ibrahimjaber.pdfpro.engine.DigitalSignatureInfo
import com.ibrahimjaber.pdfpro.engine.FileHelper
import com.ibrahimjaber.pdfpro.engine.FormFieldKind
import com.ibrahimjaber.pdfpro.engine.OcrLanguageMode
import com.ibrahimjaber.pdfpro.engine.OcrResult
import com.ibrahimjaber.pdfpro.engine.PdfFormFieldItem
import com.ibrahimjaber.pdfpro.ui.theme.BrandBlue
import com.ibrahimjaber.pdfpro.ui.theme.BrandCrimson
import com.ibrahimjaber.pdfpro.ui.theme.BrandEmerald
import kotlinx.coroutines.launch
import java.io.File

/**
 * Pillar 1 UI: Offline Arabic + English Scanned-PDF OCR Studio (Tesseract 5 LSTM).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrStudioScreen(
    app: PdfProApp,
    initialPath: String? = null,
    onBack: () -> Unit,
    onOpenPdf: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = LocalStrings.current
    val libraryDocs by app.repository.documents.collectAsState()

    var selectedFile by remember(initialPath) {
        mutableStateOf(initialPath?.takeIf { it.isNotBlank() }?.let { File(it) })
    }
    var showLibPicker by remember { mutableStateOf(false) }
    var ocrMode by remember { mutableStateOf(OcrLanguageMode.ARABIC_AND_ENGLISH) }
    var createSearchable by remember { mutableStateOf(true) }
    var isRunning by remember { mutableStateOf(false) }
    var progressLabel by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<OcrResult?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val imported = FileHelper.importPdfFromUri(context, uri)
                if (imported != null) {
                    val info = app.pdfEngine.inspectDocument(imported)
                    app.repository.upsertDocument(
                        PdfDocumentItem(name = imported.name, path = imported.absolutePath, sizeBytes = info.sizeBytes, pageCount = info.pageCount)
                    )
                    selectedFile = imported
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = {
                    Column {
                        Text(if (s.isArabic) "التعرف الضوئي OCR (عربي + إنجليزي)" else "Offline Arabic & English OCR")
                        Text("Tesseract 5 LSTM • 100% Offline", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.DocumentScanner, null, tint = BrandBlue)
                            Text(if (s.isArabic) "1. اختر ملف PDF ممسوح ضوئياً" else "1. Select Scanned PDF", style = MaterialTheme.typography.titleMedium)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { pickPdf.launch(arrayOf("application/pdf")) }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(s.fromDevice)
                            }
                            if (libraryDocs.isNotEmpty()) {
                                OutlinedButton(onClick = { showLibPicker = true }, modifier = Modifier.weight(1f)) {
                                    Text(s.fromLibrary)
                                }
                            }
                        }
                        selectedFile?.let {
                            Text("✓ ${it.name} (${PageRangeParser.formatFileSize(it.length())})", color = BrandEmerald, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            item {
                Card(shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(if (s.isArabic) "2. لغة التعرف الضوئي (OCR)" else "2. OCR Recognition Language", style = MaterialTheme.typography.titleMedium)
                        OcrLanguageMode.entries.forEach { m ->
                            FilterChip(
                                selected = ocrMode == m,
                                onClick = { ocrMode = m },
                                label = { Text(if (s.isArabic) m.labelAr else m.labelEn) }
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(if (s.isArabic) "إنشاء ملف PDF قابل للبحث" else "Generate Searchable PDF Copy")
                            Switch(checked = createSearchable, onCheckedChange = { createSearchable = it })
                        }

                        Button(
                            enabled = selectedFile != null && !isRunning,
                            onClick = {
                                val src = selectedFile ?: return@Button
                                scope.launch {
                                    isRunning = true
                                    errorMsg = null
                                    result = null
                                    runCatching {
                                        val outFile = if (createSearchable) {
                                            FileHelper.uniqueFile(FileHelper.pdfsDir(context), "${src.nameWithoutExtension}_OCR.pdf")
                                        } else null
                                        val res = app.ocrEngine.ocrScannedPdf(
                                            inputPdf = src,
                                            mode = ocrMode,
                                            createSearchablePdf = createSearchable,
                                            outputPdfFile = outFile,
                                            onPageProgress = { cur, tot -> progressLabel = "OCR Page $cur / $tot..." }
                                        )
                                        if (outFile != null && outFile.exists()) {
                                            val info = app.pdfEngine.inspectDocument(outFile)
                                            app.repository.upsertDocument(
                                                PdfDocumentItem(name = outFile.name, path = outFile.absolutePath, sizeBytes = info.sizeBytes, pageCount = info.pageCount)
                                            )
                                        }
                                        result = res
                                    }.onFailure { err ->
                                        errorMsg = err.message ?: "OCR failed"
                                    }
                                    isRunning = false
                                    progressLabel = ""
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            if (isRunning) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                                Spacer(Modifier.width(8.dp))
                                Text(progressLabel.ifBlank { "Running Offline OCR..." })
                            } else {
                                Text(if (s.isArabic) "بدء التعرف الضوئي أوفلاين" else "Run Offline OCR Now")
                            }
                        }
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

            result?.let { res ->
                item {
                    Card(shape = RoundedCornerShape(16.dp)) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.CheckCircle, null, tint = BrandEmerald)
                                Text("OCR Complete (${res.pageTexts.size} page(s))", style = MaterialTheme.typography.titleMedium)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                res.searchablePdfPath?.let { outPath ->
                                    Button(onClick = { onOpenPdf(outPath) }) {
                                        Text(if (s.isArabic) "فتح PDF القابل للبحث" else "Open Searchable PDF")
                                    }
                                }
                                OutlinedButton(onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("OCR Text", res.fullText))
                                    Toast.makeText(context, "Copied OCR text", Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Copy")
                                }
                            }
                            Text(res.fullText.take(5000), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    if (showLibPicker) {
        SimpleLibraryPickerDialog(libraryDocs, onDismiss = { showLibPicker = false }) { f ->
            selectedFile = f
            showLibPicker = false
        }
    }
}

/**
 * Pillar 3 UI: Interactive PDF AcroForm Filler & Flattener.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormFillerScreen(
    app: PdfProApp,
    initialPath: String? = null,
    onBack: () -> Unit,
    onOpenPdf: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = LocalStrings.current
    val libraryDocs by app.repository.documents.collectAsState()

    var selectedFile by remember(initialPath) {
        mutableStateOf(initialPath?.takeIf { it.isNotBlank() }?.let { File(it) })
    }
    var showLibPicker by remember { mutableStateOf(false) }
    var fields by remember { mutableStateOf<List<PdfFormFieldItem>>(emptyList()) }
    val textValues = remember { mutableStateMapOf<String, String>() }
    val checkValues = remember { mutableStateMapOf<String, Boolean>() }
    var flattenOnSave by remember { mutableStateOf(false) }
    var isBusy by remember { mutableStateOf(false) }

    LaunchedEffect(selectedFile?.absolutePath) {
        val f = selectedFile ?: return@LaunchedEffect
        isBusy = true
        runCatching { app.formEngine.readFormFields(f) }.onSuccess { list ->
            fields = list
            textValues.clear()
            checkValues.clear()
            list.forEach { item ->
                textValues[item.name] = item.value
                checkValues[item.name] = item.checked
            }
        }
        isBusy = false
    }

    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val imported = FileHelper.importPdfFromUri(context, uri)
                if (imported != null) {
                    val info = app.pdfEngine.inspectDocument(imported)
                    app.repository.upsertDocument(
                        PdfDocumentItem(name = imported.name, path = imported.absolutePath, sizeBytes = info.sizeBytes, pageCount = info.pageCount)
                    )
                    selectedFile = imported
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = {
                    Column {
                        Text(if (s.isArabic) "تعبئة نماذج PDF التفاعلية (AcroForm)" else "Interactive PDF Form Filler")
                        Text("Fill Text, Checkboxes, Dropdowns & Flatten", fontSize = 11.5.sp)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Assignment, null, tint = BrandBlue)
                            Text(if (s.isArabic) "اختر نموذج PDF أو أنشئ نموذجاً تجريبياً" else "Select Form PDF or Try Sample Form", style = MaterialTheme.typography.titleMedium)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { pickPdf.launch(arrayOf("application/pdf")) }, modifier = Modifier.weight(1f)) {
                                Text(s.fromDevice)
                            }
                            if (libraryDocs.isNotEmpty()) {
                                OutlinedButton(onClick = { showLibPicker = true }, modifier = Modifier.weight(1f)) {
                                    Text(s.fromLibrary)
                                }
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val sample = FileHelper.uniqueFile(FileHelper.pdfsDir(context), "Interactive_Form_Sample_2026.pdf")
                                    app.formEngine.createSampleInteractiveFormPdf(sample)
                                    val info = app.pdfEngine.inspectDocument(sample)
                                    app.repository.upsertDocument(
                                        PdfDocumentItem(name = sample.name, path = sample.absolutePath, sizeBytes = info.sizeBytes, pageCount = info.pageCount)
                                    )
                                    selectedFile = sample
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (s.isArabic) "إنشاء نموذج PDF تفاعلي تجريبي" else "Create Sample Interactive AcroForm PDF")
                        }
                        selectedFile?.let {
                            Text("✓ ${it.name} (${fields.size} interactive field(s))", color = BrandEmerald, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            if (isBusy) {
                item { CircularProgressIndicator() }
            } else if (selectedFile != null && fields.isEmpty()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Text(
                            text = if (s.isArabic) "لا يحتوي هذا الملف على حقول نماذج AcroForm تفاعلية. يمكنك استخدام أداة النص في المحرر أو تجربة النموذج التفاعلي أعلاه."
                            else "This PDF has no native AcroForm fields. You can still type anywhere using the Text tool in the Viewer, or try the Sample Interactive Form above.",
                            modifier = Modifier.padding(16.dp),
                            fontSize = 13.sp
                        )
                    }
                }
            } else if (fields.isNotEmpty()) {
                items(fields, key = { it.name }) { field ->
                    Card(shape = RoundedCornerShape(14.dp)) {
                        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            when (field.kind) {
                                FormFieldKind.CHECKBOX -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(field.label, fontWeight = FontWeight.SemiBold)
                                        Switch(
                                            checked = checkValues[field.name] ?: field.checked,
                                            onCheckedChange = { checkValues[field.name] = it },
                                            enabled = !field.readOnly
                                        )
                                    }
                                }
                                FormFieldKind.DROPDOWN, FormFieldKind.RADIO -> {
                                    Text(field.label, fontWeight = FontWeight.SemiBold)
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        field.options.take(5).forEach { opt ->
                                            FilterChip(
                                                selected = textValues[field.name] == opt,
                                                onClick = { if (!field.readOnly) textValues[field.name] = opt },
                                                label = { Text(opt, fontSize = 11.5.sp) }
                                            )
                                        }
                                    }
                                }
                                FormFieldKind.TEXT -> {
                                    OutlinedTextField(
                                        value = textValues[field.name] ?: "",
                                        onValueChange = { textValues[field.name] = it },
                                        label = { Text(field.label) },
                                        enabled = !field.readOnly,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Card(shape = RoundedCornerShape(16.dp)) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(if (s.isArabic) "تثبيت الحقول بشكل نهائي (Flatten)" else "Flatten & Lock Filled Form")
                                Switch(checked = flattenOnSave, onCheckedChange = { flattenOnSave = it })
                            }
                            Button(
                                onClick = {
                                    val src = selectedFile ?: return@Button
                                    scope.launch {
                                        isBusy = true
                                        runCatching {
                                            val out = FileHelper.uniqueFile(FileHelper.pdfsDir(context), "${src.nameWithoutExtension}_filled.pdf")
                                            app.formEngine.saveFilledForm(src, out, textValues.toMap(), checkValues.toMap(), flattenOnSave)
                                            val info = app.pdfEngine.inspectDocument(out)
                                            app.repository.upsertDocument(
                                                PdfDocumentItem(name = out.name, path = out.absolutePath, sizeBytes = info.sizeBytes, pageCount = info.pageCount)
                                            )
                                            Toast.makeText(context, "Saved filled form: ${out.name}", Toast.LENGTH_SHORT).show()
                                            onOpenPdf(out.absolutePath)
                                        }.onFailure { err ->
                                            Toast.makeText(context, "Error: ${err.message}", Toast.LENGTH_LONG).show()
                                        }
                                        isBusy = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Text(if (s.isArabic) "حفظ النموذج المعبأ" else "Save Filled Form PDF")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showLibPicker) {
        SimpleLibraryPickerDialog(libraryDocs, onDismiss = { showLibPicker = false }) { f ->
            selectedFile = f
            showLibPicker = false
        }
    }
}

/**
 * Pillar 5 UI: Cryptographic X.509 PKCS#12 Digital Certificate Generator, PDF Signer & Verifier.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DigitalSignatureScreen(
    app: PdfProApp,
    onBack: () -> Unit,
    onOpenPdf: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = LocalStrings.current
    val libraryDocs by app.repository.documents.collectAsState()

    var selectedPdf by remember { mutableStateOf<File?>(null) }
    var showLibPicker by remember { mutableStateOf(false) }
    var signerName by remember { mutableStateOf(AppBranding.DEVELOPER_NAME) }
    var organization by remember { mutableStateOf("PDF Editor Pro 2026") }
    var reason by remember { mutableStateOf("Verified & Approved Offline") }
    var certPassword by remember { mutableStateOf("2026") }
    var activeP12 by remember {
        mutableStateOf(app.digitalSignatureEngine.certsDir().listFiles()?.firstOrNull { it.extension == "p12" })
    }
    var verifiedSigs by remember { mutableStateOf<List<DigitalSignatureInfo>>(emptyList()) }
    var isWorking by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedPdf?.absolutePath) {
        val f = selectedPdf ?: return@LaunchedEffect
        verifiedSigs = runCatching { app.digitalSignatureEngine.inspectSignatures(f) }.getOrDefault(emptyList())
    }

    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val imported = FileHelper.importPdfFromUri(context, uri)
                if (imported != null) {
                    val info = app.pdfEngine.inspectDocument(imported)
                    app.repository.upsertDocument(
                        PdfDocumentItem(name = imported.name, path = imported.absolutePath, sizeBytes = info.sizeBytes, pageCount = info.pageCount)
                    )
                    selectedPdf = imported
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = {
                    Column {
                        Text(if (s.isArabic) "التوقيع الرقمي المشفّر (X.509 / PKCS#7)" else "Cryptographic Digital Signatures")
                        Text("RSA-2048 • SHA-256 PKCS#12 Offline Signing", fontSize = 11.5.sp)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Step 1: Select PDF & Inspect Existing Signatures
            item {
                Card(shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (s.isArabic) "1. اختر ملف PDF للتوقيع أو التحقق" else "1. Select PDF to Sign or Verify", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { pickPdf.launch(arrayOf("application/pdf")) }, modifier = Modifier.weight(1f)) { Text(s.fromDevice) }
                            if (libraryDocs.isNotEmpty()) {
                                OutlinedButton(onClick = { showLibPicker = true }, modifier = Modifier.weight(1f)) { Text(s.fromLibrary) }
                            }
                        }
                        selectedPdf?.let { Text("✓ ${it.name}", color = BrandEmerald, fontWeight = FontWeight.SemiBold) }
                    }
                }
            }

            if (verifiedSigs.isNotEmpty()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = BrandEmerald.copy(alpha = 0.12f)), shape = RoundedCornerShape(16.dp)) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.VerifiedUser, null, tint = BrandEmerald)
                                Text("Embedded Digital Signatures (${verifiedSigs.size})", fontWeight = FontWeight.Bold)
                            }
                            verifiedSigs.forEach { sig ->
                                Text("• Signer: ${sig.signerName} (${sig.signDate})", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text("  Reason: ${sig.reason} | Filter: ${sig.filter} / ${sig.subFilter}", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Step 2: Personal Certificate + Sign PDF
            item {
                Card(shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (s.isArabic) "2. شهادة X.509 والتوقيع التشفيري" else "2. X.509 Certificate & Cryptographic Sign", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(value = signerName, onValueChange = { signerName = it }, label = { Text("Signer Name (CN)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(value = organization, onValueChange = { organization = it }, label = { Text("Organization (O)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("Signing Reason") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(
                            value = certPassword,
                            onValueChange = { certPassword = it },
                            label = { Text("Certificate Password (min 4 chars)") },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        activeP12?.let {
                            Text("Active Certificate: ${it.name}", fontSize = 12.sp, color = BrandBlue, fontWeight = FontWeight.Medium)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                enabled = !isWorking,
                                onClick = {
                                    scope.launch {
                                        isWorking = true
                                        runCatching {
                                            val cert = app.digitalSignatureEngine.generatePersonalCertificate(signerName, organization, certPassword)
                                            activeP12 = cert
                                            statusMsg = "Generated RSA-2048 X.509 certificate: ${cert.name}"
                                        }.onFailure { statusMsg = "Cert error: ${it.message}" }
                                        isWorking = false
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("Generate .P12") }

                            Button(
                                enabled = !isWorking && selectedPdf != null,
                                onClick = {
                                    val src = selectedPdf ?: return@Button
                                    scope.launch {
                                        isWorking = true
                                        runCatching {
                                            val certFile = activeP12 ?: app.digitalSignatureEngine.generatePersonalCertificate(signerName, organization, certPassword).also { activeP12 = it }
                                            val out = FileHelper.uniqueFile(FileHelper.pdfsDir(context), "${src.nameWithoutExtension}_signed.pdf")
                                            app.digitalSignatureEngine.signPdf(src, out, certFile, certPassword, signerName, reason)
                                            val info = app.pdfEngine.inspectDocument(out)
                                            app.repository.upsertDocument(
                                                PdfDocumentItem(name = out.name, path = out.absolutePath, sizeBytes = info.sizeBytes, pageCount = info.pageCount)
                                            )
                                            verifiedSigs = app.digitalSignatureEngine.inspectSignatures(out)
                                            statusMsg = "Cryptographically signed: ${out.name}"
                                            onOpenPdf(out.absolutePath)
                                        }.onFailure { statusMsg = "Sign error: ${it.message}" }
                                        isWorking = false
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("Sign PDF (SHA-256)") }
                        }

                        statusMsg?.let { Text(it, fontSize = 12.5.sp, color = BrandEmerald) }
                    }
                }
            }
        }
    }

    if (showLibPicker) {
        SimpleLibraryPickerDialog(libraryDocs, onDismiss = { showLibPicker = false }) { f ->
            selectedPdf = f
            showLibPicker = false
        }
    }
}

@Composable
private fun SimpleLibraryPickerDialog(
    docs: List<PdfDocumentItem>,
    onDismiss: () -> Unit,
    onPicked: (File) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select PDF from Library") },
        text = {
            LazyColumn(modifier = Modifier.height(280.dp)) {
                itemsIndexed(docs) { _, doc ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onPicked(File(doc.path)) }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.PictureAsPdf, null, tint = BrandCrimson)
                        Spacer(Modifier.width(10.dp))
                        Text(doc.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
