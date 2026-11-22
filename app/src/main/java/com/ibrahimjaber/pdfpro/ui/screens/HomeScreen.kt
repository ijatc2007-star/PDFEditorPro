package com.ibrahimjaber.pdfpro.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ibrahimjaber.pdfpro.PdfProApp
import com.ibrahimjaber.pdfpro.data.AppBranding
import com.ibrahimjaber.pdfpro.data.AppLanguage
import com.ibrahimjaber.pdfpro.data.LocalStrings
import com.ibrahimjaber.pdfpro.data.PageRangeParser
import com.ibrahimjaber.pdfpro.data.PageTemplate
import com.ibrahimjaber.pdfpro.data.PdfDocumentItem
import com.ibrahimjaber.pdfpro.data.SortOrder
import com.ibrahimjaber.pdfpro.engine.FileHelper
import com.ibrahimjaber.pdfpro.engine.PdfPrintHelper
import com.ibrahimjaber.pdfpro.ui.theme.BrandBlue
import com.ibrahimjaber.pdfpro.ui.theme.BrandCrimson
import com.ibrahimjaber.pdfpro.ui.theme.BrandNavy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun HomeScreen(
    app: PdfProApp,
    onOpenPdf: (String) -> Unit,
    onOrganizePdf: (String) -> Unit,
    onOpenTool: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = LocalStrings.current
    val settings by app.repository.settings.collectAsState()
    val documents by app.repository.documents.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var favoritesOnly by remember { mutableStateOf(false) }
    var sortOrder by remember { mutableStateOf(SortOrder.RECENT) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<PdfDocumentItem?>(null) }
    var deleteTarget by remember { mutableStateOf<PdfDocumentItem?>(null) }
    var exportSource by remember { mutableStateOf<File?>(null) }

    val openPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val imported = withContext(Dispatchers.IO) { FileHelper.importPdfFromUri(context, uri) }
                if (imported != null) {
                    val info = app.pdfEngine.inspectDocument(imported)
                    val item = PdfDocumentItem(
                        name = imported.name,
                        path = imported.absolutePath,
                        sizeBytes = info.sizeBytes,
                        pageCount = info.pageCount,
                        isEncrypted = info.isEncrypted
                    )
                    app.repository.upsertDocument(item)
                    onOpenPdf(item.path)
                } else {
                    Toast.makeText(context, "Could not import selected PDF", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? ->
        val src = exportSource
        if (uri != null && src != null && src.exists()) {
            val ok = FileHelper.exportFileToUri(context, src, uri)
            Toast.makeText(
                context,
                if (ok) "Exported ${src.name}" else "Export failed",
                Toast.LENGTH_SHORT
            ).show()
        }
        exportSource = null
    }

    val filteredDocs = remember(documents, searchQuery, favoritesOnly, sortOrder) {
        documents
            .filter { doc ->
                (!favoritesOnly || doc.isFavorite) &&
                    (searchQuery.isBlank() || doc.name.contains(searchQuery.trim(), ignoreCase = true))
            }
            .let { list ->
                when (sortOrder) {
                    SortOrder.RECENT -> list.sortedByDescending { it.lastOpenedAt }
                    SortOrder.NAME -> list.sortedBy { it.name.lowercase() }
                    SortOrder.SIZE -> list.sortedByDescending { it.sizeBytes }
                }
            }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Branded Hero Banner
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(BrandNavy, Color(0xFF1E3A5F), BrandCrimson)
                        )
                    )
                    .padding(20.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = Color.White.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Default.VerifiedUser,
                                    contentDescription = null,
                                    tint = Color(0xFF7DD3FC),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = s.offlineBadge,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Surface(
                            color = Color.White.copy(alpha = 0.22f),
                            shape = RoundedCornerShape(999.dp),
                            modifier = Modifier.clickable {
                                val next = if (settings.language == AppLanguage.ARABIC) AppLanguage.ENGLISH else AppLanguage.ARABIC
                                app.repository.updateSettings { it.copy(language = next) }
                            }
                        ) {
                            Text(
                                text = if (s.isArabic) "EN" else "عربي",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = s.appName,
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = s.creditLine,
                        color = Color(0xFFE0F2FE),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { openPdfLauncher.launch(arrayOf("application/pdf")) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(s.openPdf)
                        }
                        OutlinedButton(
                            onClick = { showCreateDialog = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(s.createPdf, color = Color.White)
                        }
                    }
                }
            }
        }

        // v5.0 Flagship Camera Shortcuts (CamScanner Pro + Instant Camera Translate)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickChipCard(
                    modifier = Modifier.weight(1f),
                    title = if (s.isArabic) "ماسح CamScanner" else "CamScanner Pro",
                    icon = Icons.Default.CameraAlt,
                    tint = Color(0xFF059669),
                    onClick = { onOpenTool("cam_scanner") }
                )
                QuickChipCard(
                    modifier = Modifier.weight(1f),
                    title = if (s.isArabic) "ترجمة بالكاميرا" else "Camera Translate",
                    icon = Icons.Default.Translate,
                    tint = Color(0xFF0D9488),
                    onClick = { onOpenTool("camera_translate") }
                )
            }
        }

        // Quick Shortcut Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickChipCard(
                    modifier = Modifier.weight(1f),
                    title = s.convertAny,
                    icon = Icons.Default.Image,
                    tint = BrandBlue,
                    onClick = { onOpenTool("converter") }
                )
                QuickChipCard(
                    modifier = Modifier.weight(1f),
                    title = s.mergeQuick,
                    icon = Icons.Default.CreateNewFolder,
                    tint = BrandCrimson,
                    onClick = { onOpenTool("merge") }
                )
                QuickChipCard(
                    modifier = Modifier.weight(1f),
                    title = s.sampleGuide,
                    icon = Icons.Default.Description,
                    tint = Color(0xFF059669),
                    onClick = {
                        scope.launch {
                            val outFile = File(FileHelper.pdfsDir(context), "PDF_Pro_Studio_Welcome_2026.pdf")
                            if (!outFile.exists()) {
                                app.pdfEngine.createSampleWelcomePdf(outFile)
                            }
                            val info = app.pdfEngine.inspectDocument(outFile)
                            app.repository.upsertDocument(
                                PdfDocumentItem(
                                    name = outFile.name,
                                    path = outFile.absolutePath,
                                    sizeBytes = info.sizeBytes,
                                    pageCount = info.pageCount
                                )
                            )
                            onOpenPdf(outFile.absolutePath)
                        }
                    }
                )
            }
        }

        // Search + Filters
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(s.searchLibraryHint) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !favoritesOnly,
                        onClick = { favoritesOnly = false },
                        label = { Text("All (${documents.size})") }
                    )
                    FilterChip(
                        selected = favoritesOnly,
                        onClick = { favoritesOnly = true },
                        label = { Text("Favorites") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Favorite,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }

                Box {
                    TextButton(onClick = { showSortMenu = true }) {
                        Icon(Icons.Default.Sort, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(sortOrder.label)
                    }
                    DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                        SortOrder.entries.forEach { order ->
                            DropdownMenuItem(
                                text = { Text("Sort by ${order.label}") },
                                onClick = {
                                    sortOrder = order
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                }
            }
        }

        if (filteredDocs.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.PictureAsPdf,
                            contentDescription = null,
                            tint = BrandCrimson,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = if (documents.isEmpty()) "Your Offline Library Is Ready" else "No Matching PDFs",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Open any PDF from your device, create a new document, or launch the Sample Guide PDF to test all Pro Editor tools.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(filteredDocs, key = { it.path }) { doc ->
                PdfLibraryCard(
                    doc = doc,
                    onClick = { onOpenPdf(doc.path) },
                    onToggleFavorite = { app.repository.toggleFavorite(doc.path) },
                    onOrganize = { onOrganizePdf(doc.path) },
                    onRename = { renameTarget = doc },
                    onExport = {
                        exportSource = File(doc.path)
                        exportLauncher.launch(doc.name)
                    },
                    onShare = { FileHelper.shareFile(context, File(doc.path)) },
                    onPrint = { PdfPrintHelper.printPdf(context, File(doc.path)) },
                    onDelete = { deleteTarget = doc }
                )
            }
        }
    }

    // Create New PDF Dialog
    if (showCreateDialog) {
        CreateNewPdfDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { fileName, title, body, pageCount, template ->
                showCreateDialog = false
                scope.launch {
                    runCatching {
                        val safeName = FileHelper.sanitizeFileName(fileName, ".pdf")
                        val outFile = FileHelper.uniqueFile(FileHelper.pdfsDir(context), safeName)
                        app.pdfEngine.createNewPdf(outFile, title, body, pageCount, template)
                        val info = app.pdfEngine.inspectDocument(outFile)
                        app.repository.upsertDocument(
                            PdfDocumentItem(
                                name = outFile.name,
                                path = outFile.absolutePath,
                                sizeBytes = info.sizeBytes,
                                pageCount = info.pageCount
                            )
                        )
                        onOpenPdf(outFile.absolutePath)
                    }.onFailure {
                        Toast.makeText(context, "Error: ${it.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }

    // Rename Dialog
    renameTarget?.let { target ->
        var newName by remember(target.path) { mutableStateOf(target.name.removeSuffix(".pdf")) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename PDF") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("New file name") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    val res = app.repository.renameDocument(target.path, newName)
                    if (res == null) {
                        Toast.makeText(context, "Could not rename file", Toast.LENGTH_SHORT).show()
                    }
                    renameTarget = null
                }) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel") }
            }
        )
    }

    // Delete Confirmation Dialog
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete PDF?") },
            text = { Text("Remove '${target.name}' from PDF Pro Studio's offline library?") },
            confirmButton = {
                Button(onClick = {
                    app.repository.deleteDocument(target.path, deleteFileOnDisk = true)
                    deleteTarget = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun QuickChipCard(
    modifier: Modifier,
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1)
        }
    }
}

@Composable
private fun PdfLibraryCard(
    doc: PdfDocumentItem,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOrganize: () -> Unit,
    onRename: () -> Unit,
    onExport: () -> Unit,
    onShare: () -> Unit,
    onPrint: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(BrandCrimson.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (doc.isEncrypted) Icons.Default.Lock else Icons.Default.PictureAsPdf,
                    contentDescription = null,
                    tint = BrandCrimson,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = doc.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                val pageInfo = if (doc.isEncrypted && doc.pageCount == 0) {
                    "Locked PDF"
                } else {
                    "${doc.pageCount} page${if (doc.pageCount == 1) "" else "s"}"
                }
                Text(
                    text = "$pageInfo • ${PageRangeParser.formatFileSize(doc.sizeBytes)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.5.sp
                )
                Text(
                    text = FileHelper.formatDate(doc.lastOpenedAt),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.5.sp
                )
            }

            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (doc.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (doc.isFavorite) BrandCrimson else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More actions")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Open & Edit") },
                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                        onClick = { menuExpanded = false; onClick() }
                    )
                    DropdownMenuItem(
                        text = { Text("Organize Pages") },
                        leadingIcon = { Icon(Icons.Default.GridView, null) },
                        onClick = { menuExpanded = false; onOrganize() }
                    )
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Default.Description, null) },
                        onClick = { menuExpanded = false; onRename() }
                    )
                    DropdownMenuItem(
                        text = { Text("Export to Device") },
                        leadingIcon = { Icon(Icons.Default.FileDownload, null) },
                        onClick = { menuExpanded = false; onExport() }
                    )
                    DropdownMenuItem(
                        text = { Text("Share") },
                        leadingIcon = { Icon(Icons.Default.Share, null) },
                        onClick = { menuExpanded = false; onShare() }
                    )
                    DropdownMenuItem(
                        text = { Text("Print") },
                        leadingIcon = { Icon(Icons.Default.Print, null) },
                        onClick = { menuExpanded = false; onPrint() }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = BrandCrimson) },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = BrandCrimson) },
                        onClick = { menuExpanded = false; onDelete() }
                    )
                }
            }
        }
    }
}

@Composable
private fun CreateNewPdfDialog(
    onDismiss: () -> Unit,
    onCreate: (fileName: String, title: String, body: String, pages: Int, template: PageTemplate) -> Unit
) {
    var fileName by remember { mutableStateOf("New_Document_2026") }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var pagesText by remember { mutableStateOf("1") }
    var template by remember { mutableStateOf(PageTemplate.BLANK) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New Offline PDF") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text("File Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Document Heading (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("Body Text (English / Arabic)") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pagesText,
                    onValueChange = { pagesText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Number of Pages (1-50)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PageTemplate.entries.forEach { t ->
                        FilterChip(
                            selected = template == t,
                            onClick = { template = t },
                            label = { Text(t.label.substringBefore(" "), fontSize = 11.sp) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val pages = pagesText.toIntOrNull()?.coerceIn(1, 50) ?: 1
                onCreate(fileName.ifBlank { "New_Document" }, title, body, pages, template)
            }) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
