package com.ibrahimjaber.pdfpro.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ibrahimjaber.pdfpro.PdfProApp
import com.ibrahimjaber.pdfpro.data.AppBranding
import com.ibrahimjaber.pdfpro.data.EditorTool
import com.ibrahimjaber.pdfpro.data.LocalStrings
import com.ibrahimjaber.pdfpro.data.NormPoint
import com.ibrahimjaber.pdfpro.data.PdfAnnotation
import com.ibrahimjaber.pdfpro.data.PdfDocumentItem
import com.ibrahimjaber.pdfpro.data.ReadingMode
import com.ibrahimjaber.pdfpro.data.SavedSignature
import com.ibrahimjaber.pdfpro.data.SearchMatch
import com.ibrahimjaber.pdfpro.data.ShapeKind
import com.ibrahimjaber.pdfpro.engine.FileHelper
import com.ibrahimjaber.pdfpro.engine.PdfPrintHelper
import com.ibrahimjaber.pdfpro.ui.theme.BrandBlue
import com.ibrahimjaber.pdfpro.ui.theme.BrandCrimson
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private val EDITOR_COLORS = listOf(
    Color(0xFFDC2626), // Crimson
    Color(0xFF0284C7), // Royal Blue
    Color(0xFF059669), // Emerald
    Color(0xFFFACC15), // Yellow Highlighter
    Color(0xFF0F172A), // Ink Black
    Color(0xFF7C3AED), // Purple
    Color(0xFFD97706)  // Amber
)

private val PRESET_STAMPS = listOf(
    "APPROVED",
    "CONFIDENTIAL",
    "DRAFT",
    "URGENT",
    "VERIFIED",
    "IBRAHIM JABER 2026",
    "معتمد",
    "سري للغاية",
    "عاجل",
    "إبراهيم جابر 2026"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerEditorScreen(
    app: PdfProApp,
    filePath: String,
    onBack: () -> Unit,
    onOpenOrganizer: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val strings = LocalStrings.current
    val settings by app.repository.settings.collectAsState()
    val allBookmarks by app.repository.bookmarks.collectAsState()
    val savedSignatures by app.repository.signatures.collectAsState()

    val pdfFile = remember(filePath) { File(filePath) }
    val docBookmarks = remember(allBookmarks, filePath) {
        allBookmarks.filter { it.docPath == filePath }.sortedBy { it.pageIndex }
    }

    var pageCount by remember { mutableIntStateOf(0) }
    var currentPage by remember {
        mutableIntStateOf(app.repository.getDocumentByPath(filePath)?.lastPageIndex ?: 0)
    }
    var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var password by remember { mutableStateOf<String?>(null) }
    var needsPassword by remember { mutableStateOf(false) }
    var renderVersion by remember { mutableIntStateOf(0) }

    // Reading controls
    var readingMode by remember { mutableStateOf(settings.defaultReadingMode) }
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    // Editor state
    var editMode by remember { mutableStateOf(false) }
    var activeTool by remember { mutableStateOf(EditorTool.VIEW) }
    var selectedColor by remember { mutableStateOf(EDITOR_COLORS[0]) }
    var strokeWidthFactor by remember { mutableFloatStateOf(0.005f) }
    var selectedStamp by remember { mutableStateOf(PRESET_STAMPS[0]) }
    var selectedSignaturePath by remember { mutableStateOf<String?>(null) }
    var selectedOverlayImagePath by remember { mutableStateOf<String?>(null) }
    var pendingQrTap by remember { mutableStateOf<NormPoint?>(null) }

    val pickOverlayImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val copied = FileHelper.copyImageFromUriToCache(context, uri, 0)
                if (copied != null) {
                    selectedOverlayImagePath = copied.absolutePath
                    Toast.makeText(
                        context,
                        if (strings.isArabic) "اضغط على الصفحة لوضع الصورة" else "Tap anywhere on the page to place the image",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    val annotations = remember { mutableStateListOf<PdfAnnotation>() }
    val redoStack = remember { mutableStateListOf<PdfAnnotation>() }
    var activeFreehand by remember { mutableStateOf<List<NormPoint>>(emptyList()) }
    var activeShapeStart by remember { mutableStateOf<NormPoint?>(null) }
    var activeShapeEnd by remember { mutableStateOf<NormPoint?>(null) }

    // Dialog states
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var showBookmarksDialog by remember { mutableStateOf(false) }
    var showExtractTextDialog by remember { mutableStateOf(false) }
    var extractedPageText by remember { mutableStateOf("") }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showStampPicker by remember { mutableStateOf(false) }
    var showSignaturePicker by remember { mutableStateOf(false) }
    var showSignaturePad by remember { mutableStateOf(false) }
    var pendingTextTap by remember { mutableStateOf<NormPoint?>(null) }
    var pendingNoteTap by remember { mutableStateOf<NormPoint?>(null) }

    // Offline Text-to-Speech
    var isSpeaking by remember { mutableStateOf(false) }
    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }

    DisposableEffect(Unit) {
        val tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsEngine?.language = Locale.getDefault()
            }
        }
        ttsEngine = tts
        onDispose {
            tts.stop()
            tts.shutdown()
            scope.launch { app.renderManager.close() }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? ->
        if (uri != null) {
            val ok = FileHelper.exportFileToUri(context, pdfFile, uri)
            Toast.makeText(
                context,
                if (ok) "Exported ${pdfFile.name}" else "Export failed",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // Open PDF document
    LaunchedEffect(filePath, password, renderVersion) {
        isLoading = true
        errorMessage = null
        try {
            val info = app.pdfEngine.inspectDocument(pdfFile, password)
            if (info.isEncrypted && info.pageCount == 0) {
                needsPassword = true
                isLoading = false
                return@LaunchedEffect
            }
            val total = app.renderManager.open(pdfFile, password)
            pageCount = total
            if (currentPage >= total) currentPage = (total - 1).coerceAtLeast(0)
            app.repository.upsertDocument(
                PdfDocumentItem(
                    name = pdfFile.name,
                    path = pdfFile.absolutePath,
                    sizeBytes = pdfFile.length(),
                    pageCount = total,
                    lastPageIndex = currentPage,
                    isEncrypted = info.isEncrypted
                )
            )
            pageBitmap = app.renderManager.renderPage(currentPage, targetWidthPx = 1400)
            needsPassword = false
        } catch (e: Exception) {
            if (e.message?.contains("password", ignoreCase = true) == true) {
                needsPassword = true
            } else {
                errorMessage = e.message ?: "Unable to open PDF file"
            }
        } finally {
            isLoading = false
        }
    }

    // Render current page when page index changes
    LaunchedEffect(currentPage, pageCount, renderVersion) {
        if (pageCount > 0 && currentPage in 0 until pageCount) {
            pageBitmap = app.renderManager.renderPage(currentPage, targetWidthPx = 1400)
            app.repository.updateLastPage(filePath, currentPage)
            zoomScale = 1f
            panOffset = Offset.Zero
            if (isSpeaking) {
                ttsEngine?.stop()
                isSpeaking = false
            }
        }
    }

    val isCurrentBookmarked = docBookmarks.any { it.pageIndex == currentPage }
    val pageColorFilter = remember(readingMode) {
        when (readingMode) {
            ReadingMode.DAY -> null
            ReadingMode.SEPIA -> ColorFilter.colorMatrix(
                ColorMatrix(
                    floatArrayOf(
                        0.95f, 0.05f, 0f, 0f, 0f,
                        0f, 0.90f, 0f, 0f, 0f,
                        0f, 0f, 0.76f, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
            ReadingMode.NIGHT_INVERT -> ColorFilter.colorMatrix(
                ColorMatrix(
                    floatArrayOf(
                        -1f, 0f, 0f, 0f, 240f,
                        0f, -1f, 0f, 0f, 240f,
                        0f, 0f, -1f, 0f, 245f,
                        0f, 0f, 0f, 1f, 0f
                    )
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
                        Text(
                            text = pdfFile.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = if (pageCount > 0) {
                                "Page ${currentPage + 1} / $pageCount" +
                                    if (annotations.isNotEmpty()) " • ${annotations.size} unsaved" else ""
                            } else "Offline Viewer",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showSearchDialog = true }) {
                        Icon(Icons.Default.Search, contentDescription = "Search in PDF")
                    }
                    IconButton(onClick = {
                        editMode = !editMode
                        activeTool = if (editMode) EditorTool.PEN else EditorTool.VIEW
                    }) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Toggle Editor",
                            tint = if (editMode) BrandCrimson else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (annotations.isNotEmpty()) {
                        IconButton(onClick = { showSaveDialog = true }) {
                            Icon(Icons.Default.Save, contentDescription = "Save Edits", tint = BrandBlue)
                        }
                    }
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = showOverflowMenu, onDismissRequest = { showOverflowMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Reading Mode: ${readingMode.label}") },
                                leadingIcon = { Icon(Icons.Default.DarkMode, null) },
                                onClick = {
                                    showOverflowMenu = false
                                    val modes = ReadingMode.entries
                                    readingMode = modes[(modes.indexOf(readingMode) + 1) % modes.size]
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (isCurrentBookmarked) "Remove Bookmark" else "Bookmark Page") },
                                leadingIcon = {
                                    Icon(
                                        if (isCurrentBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                        null
                                    )
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    if (isCurrentBookmarked) {
                                        docBookmarks.firstOrNull { it.pageIndex == currentPage }
                                            ?.let { app.repository.removeBookmark(it.id) }
                                    } else {
                                        app.repository.addBookmark(filePath, currentPage, "Page ${currentPage + 1}")
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("View Bookmarks (${docBookmarks.size})") },
                                leadingIcon = { Icon(Icons.Default.Bookmarks, null) },
                                onClick = { showOverflowMenu = false; showBookmarksDialog = true }
                            )
                            DropdownMenuItem(
                                text = { Text(if (isSpeaking) "Stop Read Aloud" else "Read Page Aloud (TTS)") },
                                leadingIcon = {
                                    Icon(
                                        if (isSpeaking) Icons.Default.StopCircle else Icons.Default.RecordVoiceOver,
                                        null
                                    )
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    if (isSpeaking) {
                                        ttsEngine?.stop()
                                        isSpeaking = false
                                    } else {
                                        scope.launch {
                                            val text = runCatching {
                                                app.pdfEngine.extractText(pdfFile, currentPage, password)
                                            }.getOrDefault("")
                                            if (text.isBlank()) {
                                                Toast.makeText(context, "No selectable text on this page", Toast.LENGTH_SHORT).show()
                                            } else {
                                                isSpeaking = true
                                                ttsEngine?.speak(text.take(3500), TextToSpeech.QUEUE_FLUSH, null, "pdf_page")
                                            }
                                        }
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Extract Page Text") },
                                leadingIcon = { Icon(Icons.Default.TextFields, null) },
                                onClick = {
                                    showOverflowMenu = false
                                    scope.launch {
                                        extractedPageText = runCatching {
                                            app.pdfEngine.extractText(pdfFile, currentPage, password)
                                        }.getOrDefault("Could not extract text.")
                                        showExtractTextDialog = true
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Organize / Rotate Pages") },
                                leadingIcon = { Icon(Icons.Default.GridView, null) },
                                onClick = { showOverflowMenu = false; onOpenOrganizer(filePath) }
                            )
                            DropdownMenuItem(
                                text = { Text("Export Copy to Device") },
                                leadingIcon = { Icon(Icons.Default.FileDownload, null) },
                                onClick = { showOverflowMenu = false; exportLauncher.launch(pdfFile.name) }
                            )
                            DropdownMenuItem(
                                text = { Text("Print PDF") },
                                leadingIcon = { Icon(Icons.Default.Print, null) },
                                onClick = { showOverflowMenu = false; PdfPrintHelper.printPdf(context, pdfFile) }
                            )
                            DropdownMenuItem(
                                text = { Text("Share PDF") },
                                leadingIcon = { Icon(Icons.Default.Share, null) },
                                onClick = { showOverflowMenu = false; FileHelper.shareFile(context, pdfFile) }
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 4.dp) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Pro Editor Toolbar
                    if (editMode) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                EditorTool.entries.forEach { tool ->
                                    FilterChip(
                                        selected = activeTool == tool,
                                        onClick = {
                                            activeTool = tool
                                            if (tool == EditorTool.HIGHLIGHTER) {
                                                selectedColor = Color(0xFFFACC15)
                                                strokeWidthFactor = 0.022f
                                            } else if (tool == EditorTool.PEN && strokeWidthFactor > 0.015f) {
                                                strokeWidthFactor = 0.005f
                                            }
                                            if (tool == EditorTool.STAMP) showStampPicker = true
                                            if (tool == EditorTool.SIGNATURE) showSignaturePicker = true
                                            if (tool == EditorTool.IMAGE_STAMP) pickOverlayImageLauncher.launch(arrayOf("image/*"))
                                            if (tool == EditorTool.QR_CODE) {
                                                Toast.makeText(
                                                    context,
                                                    if (strings.isArabic) "اضغط على الصفحة لإنشاء ووضع رمز QR" else "Tap anywhere on the page to place a QR Code",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        },
                                        label = {
                                            Text(
                                                if (strings.isArabic) tool.labelAr else tool.label,
                                                fontSize = 11.5.sp
                                            )
                                        }
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    EDITOR_COLORS.forEach { c ->
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(c)
                                                .border(
                                                    width = if (selectedColor == c) 2.5.dp else 1.dp,
                                                    color = if (selectedColor == c) MaterialTheme.colorScheme.primary else Color.Gray,
                                                    shape = CircleShape
                                                )
                                                .clickable { selectedColor = c }
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            if (annotations.isNotEmpty()) {
                                                val removed = annotations.removeAt(annotations.lastIndex)
                                                redoStack.add(removed)
                                            }
                                        },
                                        enabled = annotations.isNotEmpty(),
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                                    }
                                    IconButton(
                                        onClick = {
                                            if (redoStack.isNotEmpty()) {
                                                annotations.add(redoStack.removeAt(redoStack.lastIndex))
                                            }
                                        },
                                        enabled = redoStack.isNotEmpty(),
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                                    }
                                    Button(
                                        onClick = { showSaveDialog = true },
                                        enabled = annotations.isNotEmpty() && !isSaving,
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Text(strings.save, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }

                    // Page Scrubber Bar
                    if (pageCount > 0) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { if (currentPage > 0) currentPage-- },
                                enabled = currentPage > 0
                            ) {
                                Icon(Icons.Default.ChevronLeft, contentDescription = "Previous page")
                            }

                            if (pageCount > 1) {
                                Slider(
                                    value = currentPage.toFloat(),
                                    onValueChange = { currentPage = it.roundToInt().coerceIn(0, pageCount - 1) },
                                    valueRange = 0f..(pageCount - 1).toFloat(),
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                Spacer(Modifier.weight(1f))
                                Text("Page 1 of 1", fontSize = 12.sp)
                                Spacer(Modifier.weight(1f))
                            }

                            IconButton(
                                onClick = { if (currentPage < pageCount - 1) currentPage++ },
                                enabled = currentPage < pageCount - 1
                            ) {
                                Icon(Icons.Default.ChevronRight, contentDescription = "Next page")
                            }

                            if (zoomScale > 1.05f) {
                                IconButton(onClick = { zoomScale = 1f; panOffset = Offset.Zero }) {
                                    Icon(Icons.Default.ZoomOutMap, contentDescription = "Reset Zoom")
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    when (readingMode) {
                        ReadingMode.NIGHT_INVERT -> Color(0xFF090D16)
                        ReadingMode.SEPIA -> Color(0xFFE7DEC8)
                        ReadingMode.DAY -> Color(0xFFCBD5E1)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoading || isSaving -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(10.dp))
                        Text(if (isSaving) "Embedding annotations offline..." else "Rendering PDF...")
                    }
                }

                errorMessage != null -> {
                    Text(
                        text = errorMessage ?: "Failed to load PDF",
                        color = BrandCrimson,
                        modifier = Modifier.padding(24.dp)
                    )
                }

                pageBitmap != null -> {
                    val bmp = pageBitmap!!
                    val pageAspect = bmp.width.toFloat() / bmp.height.toFloat().coerceAtLeast(1f)
                    val pageAnnotations = annotations.filter { it.pageIndex == currentPage }
                    val signatureBitmaps = remember(pageAnnotations) {
                        pageAnnotations
                            .filterIsInstance<PdfAnnotation.Signature>()
                            .associate { it.imagePath to BitmapFactory.decodeFile(it.imagePath) }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                            .pointerInput(activeTool, editMode) {
                                if (!editMode || activeTool == EditorTool.VIEW) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        val nextZoom = (zoomScale * zoom).coerceIn(1f, 5f)
                                        zoomScale = nextZoom
                                        panOffset = if (nextZoom > 1f) {
                                            Offset(panOffset.x + pan.x, panOffset.y + pan.y)
                                        } else {
                                            Offset.Zero
                                        }
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(pageAspect)
                                .graphicsLayer(
                                    scaleX = zoomScale,
                                    scaleY = zoomScale,
                                    translationX = panOffset.x,
                                    translationY = panOffset.y
                                )
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.White)
                                .pointerInput(
                                    activeTool,
                                    editMode,
                                    selectedColor,
                                    strokeWidthFactor,
                                    selectedStamp,
                                    selectedSignaturePath,
                                    selectedOverlayImagePath,
                                    currentPage
                                ) {
                                    if (!editMode) return@pointerInput
                                    when (activeTool) {
                                        EditorTool.PEN, EditorTool.HIGHLIGHTER -> {
                                            detectDragGestures(
                                                onDragStart = { offset ->
                                                    val nx = (offset.x / size.width).coerceIn(0f, 1f)
                                                    val ny = (offset.y / size.height).coerceIn(0f, 1f)
                                                    activeFreehand = listOf(NormPoint(nx, ny))
                                                },
                                                onDrag = { change, _ ->
                                                    change.consume()
                                                    val nx = (change.position.x / size.width).coerceIn(0f, 1f)
                                                    val ny = (change.position.y / size.height).coerceIn(0f, 1f)
                                                    activeFreehand = activeFreehand + NormPoint(nx, ny)
                                                },
                                                onDragEnd = {
                                                    if (activeFreehand.size >= 2) {
                                                        annotations.add(
                                                            PdfAnnotation.Freehand(
                                                                pageIndex = currentPage,
                                                                points = activeFreehand,
                                                                colorArgb = selectedColor.toArgb(),
                                                                strokeWidthNorm = if (activeTool == EditorTool.HIGHLIGHTER) 0.022f else strokeWidthFactor,
                                                                isHighlighter = activeTool == EditorTool.HIGHLIGHTER
                                                            )
                                                        )
                                                        redoStack.clear()
                                                    }
                                                    activeFreehand = emptyList()
                                                }
                                            )
                                        }

                                        EditorTool.RECTANGLE,
                                        EditorTool.OVAL,
                                        EditorTool.ARROW,
                                        EditorTool.UNDERLINE,
                                        EditorTool.STRIKETHROUGH,
                                        EditorTool.REDACT,
                                        EditorTool.WHITEOUT -> {
                                            detectDragGestures(
                                                onDragStart = { offset ->
                                                    val pt = NormPoint(
                                                        (offset.x / size.width).coerceIn(0f, 1f),
                                                        (offset.y / size.height).coerceIn(0f, 1f)
                                                    )
                                                    activeShapeStart = pt
                                                    activeShapeEnd = pt
                                                },
                                                onDrag = { change, _ ->
                                                    change.consume()
                                                    val nx = (change.position.x / size.width).coerceIn(0f, 1f)
                                                    val ny = if (activeTool == EditorTool.UNDERLINE || activeTool == EditorTool.STRIKETHROUGH) {
                                                        activeShapeStart?.y ?: (change.position.y / size.height).coerceIn(0f, 1f)
                                                    } else {
                                                        (change.position.y / size.height).coerceIn(0f, 1f)
                                                    }
                                                    activeShapeEnd = NormPoint(nx, ny)
                                                },
                                                onDragEnd = {
                                                    val s = activeShapeStart
                                                    val e = activeShapeEnd
                                                    if (s != null && e != null) {
                                                        when (activeTool) {
                                                            EditorTool.REDACT -> {
                                                                annotations.add(
                                                                    PdfAnnotation.MaskRect(
                                                                        pageIndex = currentPage,
                                                                        startX = s.x,
                                                                        startY = s.y,
                                                                        endX = e.x,
                                                                        endY = e.y,
                                                                        fillColorArgb = android.graphics.Color.BLACK,
                                                                        isRedaction = true
                                                                    )
                                                                )
                                                            }
                                                            EditorTool.WHITEOUT -> {
                                                                annotations.add(
                                                                    PdfAnnotation.MaskRect(
                                                                        pageIndex = currentPage,
                                                                        startX = s.x,
                                                                        startY = s.y,
                                                                        endX = e.x,
                                                                        endY = e.y,
                                                                        fillColorArgb = android.graphics.Color.WHITE,
                                                                        isRedaction = false
                                                                    )
                                                                )
                                                            }
                                                            else -> {
                                                                val kind = when (activeTool) {
                                                                    EditorTool.RECTANGLE -> ShapeKind.RECTANGLE
                                                                    EditorTool.OVAL -> ShapeKind.OVAL
                                                                    EditorTool.UNDERLINE, EditorTool.STRIKETHROUGH -> ShapeKind.LINE
                                                                    else -> ShapeKind.ARROW
                                                                }
                                                                val col = if (activeTool == EditorTool.STRIKETHROUGH) {
                                                                    Color(0xFFDC2626).toArgb()
                                                                } else {
                                                                    selectedColor.toArgb()
                                                                }
                                                                annotations.add(
                                                                    PdfAnnotation.Shape(
                                                                        pageIndex = currentPage,
                                                                        kind = kind,
                                                                        startX = s.x,
                                                                        startY = s.y,
                                                                        endX = e.x,
                                                                        endY = e.y,
                                                                        colorArgb = col,
                                                                        strokeWidthNorm = strokeWidthFactor
                                                                    )
                                                                )
                                                            }
                                                        }
                                                        redoStack.clear()
                                                    }
                                                    activeShapeStart = null
                                                    activeShapeEnd = null
                                                }
                                            )
                                        }

                                        EditorTool.TEXT,
                                        EditorTool.STAMP,
                                        EditorTool.STICKY_NOTE,
                                        EditorTool.SIGNATURE,
                                        EditorTool.IMAGE_STAMP,
                                        EditorTool.QR_CODE -> {
                                            detectTapGestures { offset ->
                                                val pt = NormPoint(
                                                    (offset.x / size.width).coerceIn(0f, 1f),
                                                    (offset.y / size.height).coerceIn(0f, 1f)
                                                )
                                                when (activeTool) {
                                                    EditorTool.TEXT -> pendingTextTap = pt
                                                    EditorTool.STICKY_NOTE -> pendingNoteTap = pt
                                                    EditorTool.QR_CODE -> pendingQrTap = pt
                                                    EditorTool.STAMP -> {
                                                        annotations.add(
                                                            PdfAnnotation.Stamp(
                                                                pageIndex = currentPage,
                                                                x = pt.x,
                                                                y = pt.y,
                                                                label = selectedStamp,
                                                                colorArgb = selectedColor.toArgb()
                                                            )
                                                        )
                                                        redoStack.clear()
                                                    }
                                                    EditorTool.IMAGE_STAMP -> {
                                                        val imgPath = selectedOverlayImagePath
                                                        if (imgPath != null) {
                                                            annotations.add(
                                                                PdfAnnotation.Signature(
                                                                    pageIndex = currentPage,
                                                                    x = pt.x,
                                                                    y = pt.y,
                                                                    widthNorm = 0.28f,
                                                                    heightNorm = 0.20f,
                                                                    imagePath = imgPath
                                                                )
                                                            )
                                                            redoStack.clear()
                                                        } else {
                                                            pickOverlayImageLauncher.launch(arrayOf("image/*"))
                                                        }
                                                    }
                                                    EditorTool.SIGNATURE -> {
                                                        val sigPath = selectedSignaturePath ?: savedSignatures.firstOrNull()?.imagePath
                                                        if (sigPath != null) {
                                                            annotations.add(
                                                                PdfAnnotation.Signature(
                                                                    pageIndex = currentPage,
                                                                    x = pt.x,
                                                                    y = pt.y,
                                                                    imagePath = sigPath
                                                                )
                                                            )
                                                            redoStack.clear()
                                                        } else {
                                                            showSignaturePad = true
                                                        }
                                                    }
                                                    else -> Unit
                                                }
                                            }
                                        }

                                        EditorTool.VIEW -> Unit
                                    }
                                }
                        ) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "PDF Page ${currentPage + 1}",
                                contentScale = ContentScale.FillBounds,
                                colorFilter = pageColorFilter,
                                modifier = Modifier.fillMaxSize()
                            )

                            // Live Interactive Annotation Overlay
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val w = size.width
                                val h = size.height

                                fun drawFreehandList(points: List<NormPoint>, color: Color, widthNorm: Float, highlighter: Boolean) {
                                    if (points.size < 2) return
                                    val path = Path().apply {
                                        moveTo(points.first().x * w, points.first().y * h)
                                        for (i in 1 until points.size) {
                                            lineTo(points[i].x * w, points[i].y * h)
                                        }
                                    }
                                    drawPath(
                                        path = path,
                                        color = if (highlighter) color.copy(alpha = 0.36f) else color,
                                        style = Stroke(
                                            width = (widthNorm * w).coerceAtLeast(3f),
                                            cap = StrokeCap.Round,
                                            join = StrokeJoin.Round
                                        )
                                    )
                                }

                                fun drawShapePreview(kind: ShapeKind, sx: Float, sy: Float, ex: Float, ey: Float, color: Color, widthNorm: Float) {
                                    val x1 = sx * w
                                    val y1 = sy * h
                                    val x2 = ex * w
                                    val y2 = ey * h
                                    val stroke = Stroke(width = (widthNorm * w).coerceAtLeast(3f), cap = StrokeCap.Round)
                                    when (kind) {
                                        ShapeKind.RECTANGLE -> {
                                            drawRect(
                                                color = color,
                                                topLeft = Offset(min(x1, x2), min(y1, y2)),
                                                size = Size(max(4f, kotlin.math.abs(x2 - x1)), max(4f, kotlin.math.abs(y2 - y1))),
                                                style = stroke
                                            )
                                        }
                                        ShapeKind.OVAL -> {
                                            drawOval(
                                                color = color,
                                                topLeft = Offset(min(x1, x2), min(y1, y2)),
                                                size = Size(max(4f, kotlin.math.abs(x2 - x1)), max(4f, kotlin.math.abs(y2 - y1))),
                                                style = stroke
                                            )
                                        }
                                        ShapeKind.LINE -> {
                                            drawLine(color, Offset(x1, y1), Offset(x2, y2), strokeWidth = stroke.width, cap = StrokeCap.Round)
                                        }
                                        ShapeKind.ARROW -> {
                                            drawLine(color, Offset(x1, y1), Offset(x2, y2), strokeWidth = stroke.width, cap = StrokeCap.Round)
                                            val angle = atan2((y2 - y1).toDouble(), (x2 - x1).toDouble())
                                            val head = (w * 0.03f).coerceIn(12f, 28f)
                                            val wing = Math.toRadians(28.0)
                                            drawLine(
                                                color,
                                                Offset(x2, y2),
                                                Offset(x2 - (head * cos(angle - wing)).toFloat(), y2 - (head * sin(angle - wing)).toFloat()),
                                                strokeWidth = stroke.width,
                                                cap = StrokeCap.Round
                                            )
                                            drawLine(
                                                color,
                                                Offset(x2, y2),
                                                Offset(x2 - (head * cos(angle + wing)).toFloat(), y2 - (head * sin(angle + wing)).toFloat()),
                                                strokeWidth = stroke.width,
                                                cap = StrokeCap.Round
                                            )
                                        }
                                    }
                                }

                                for (ann in pageAnnotations) {
                                    when (ann) {
                                        is PdfAnnotation.Freehand -> drawFreehandList(
                                            ann.points,
                                            Color(ann.colorArgb),
                                            ann.strokeWidthNorm,
                                            ann.isHighlighter
                                        )
                                        is PdfAnnotation.Shape -> drawShapePreview(
                                            ann.kind,
                                            ann.startX,
                                            ann.startY,
                                            ann.endX,
                                            ann.endY,
                                            Color(ann.colorArgb),
                                            ann.strokeWidthNorm
                                        )
                                        is PdfAnnotation.MaskRect -> {
                                            val x1 = ann.startX * w
                                            val y1 = ann.startY * h
                                            val x2 = ann.endX * w
                                            val y2 = ann.endY * h
                                            drawRect(
                                                color = Color(ann.fillColorArgb),
                                                topLeft = Offset(min(x1, x2), min(y1, y2)),
                                                size = Size(max(8f, kotlin.math.abs(x2 - x1)), max(8f, kotlin.math.abs(y2 - y1)))
                                            )
                                        }
                                        is PdfAnnotation.Signature -> {
                                            val sigBmp = signatureBitmaps[ann.imagePath]
                                            if (sigBmp != null) {
                                                val dw = (ann.widthNorm * w).roundToInt().coerceAtLeast(16)
                                                val dh = (ann.heightNorm * h).roundToInt().coerceAtLeast(16)
                                                val dx = (ann.x * w - dw / 2f).roundToInt()
                                                val dy = (ann.y * h - dh / 2f).roundToInt()
                                                drawImage(
                                                    image = sigBmp.asImageBitmap(),
                                                    dstOffset = IntOffset(dx, dy),
                                                    dstSize = IntSize(dw, dh)
                                                )
                                            }
                                        }
                                        is PdfAnnotation.Stamp -> {
                                            val sw = w * 0.28f
                                            val sh = sw * 0.38f
                                            val left = ann.x * w - sw / 2f
                                            val top = ann.y * h - sh / 2f
                                            drawRect(
                                                color = Color(ann.colorArgb).copy(alpha = 0.14f),
                                                topLeft = Offset(left, top),
                                                size = Size(sw, sh)
                                            )
                                            drawRect(
                                                color = Color(ann.colorArgb),
                                                topLeft = Offset(left, top),
                                                size = Size(sw, sh),
                                                style = Stroke(width = 3f)
                                            )
                                        }
                                        is PdfAnnotation.StickyNote -> {
                                            drawRect(
                                                color = Color(ann.colorArgb),
                                                topLeft = Offset(ann.x * w, ann.y * h),
                                                size = Size(w * 0.28f, h * 0.08f)
                                            )
                                        }
                                        is PdfAnnotation.TextBox -> {
                                            drawCircle(
                                                color = Color(ann.colorArgb),
                                                radius = 6f,
                                                center = Offset(ann.x * w, ann.y * h)
                                            )
                                        }
                                    }
                                }

                                // In-progress gesture previews
                                if (activeFreehand.size >= 2) {
                                    drawFreehandList(
                                        activeFreehand,
                                        selectedColor,
                                        if (activeTool == EditorTool.HIGHLIGHTER) 0.022f else strokeWidthFactor,
                                        activeTool == EditorTool.HIGHLIGHTER
                                    )
                                }
                                val s = activeShapeStart
                                val e = activeShapeEnd
                                if (s != null && e != null) {
                                    if (activeTool == EditorTool.REDACT || activeTool == EditorTool.WHITEOUT) {
                                        val x1 = s.x * w
                                        val y1 = s.y * h
                                        val x2 = e.x * w
                                        val y2 = e.y * h
                                        drawRect(
                                            color = if (activeTool == EditorTool.REDACT) Color.Black.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.9f),
                                            topLeft = Offset(min(x1, x2), min(y1, y2)),
                                            size = Size(max(6f, kotlin.math.abs(x2 - x1)), max(6f, kotlin.math.abs(y2 - y1)))
                                        )
                                    } else {
                                        val kind = when (activeTool) {
                                            EditorTool.RECTANGLE -> ShapeKind.RECTANGLE
                                            EditorTool.OVAL -> ShapeKind.OVAL
                                            EditorTool.UNDERLINE, EditorTool.STRIKETHROUGH -> ShapeKind.LINE
                                            else -> ShapeKind.ARROW
                                        }
                                        drawShapePreview(kind, s.x, s.y, e.x, e.y, selectedColor, strokeWidthFactor)
                                    }
                                }
                            }

                            // Crisp Compose labels for live TextBox / Stamp / StickyNote previews
                            pageAnnotations.forEach { ann ->
                                when (ann) {
                                    is PdfAnnotation.TextBox -> {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(
                                                    start = (ann.x * 260).dp.coerceIn(0.dp, 260.dp),
                                                    top = (ann.y * 360).dp.coerceIn(0.dp, 360.dp)
                                                )
                                        ) {
                                            Text(
                                                text = ann.text,
                                                color = Color(ann.colorArgb),
                                                fontWeight = if (ann.isBold) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                    is PdfAnnotation.Stamp -> {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(
                                                    start = ((ann.x - 0.12f) * 260).dp.coerceIn(0.dp, 240.dp),
                                                    top = ((ann.y - 0.03f) * 360).dp.coerceIn(0.dp, 340.dp)
                                                )
                                        ) {
                                            Text(
                                                text = ann.label,
                                                color = Color(ann.colorArgb),
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                    is PdfAnnotation.StickyNote -> {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(
                                                    start = (ann.x * 260).dp.coerceIn(0.dp, 240.dp),
                                                    top = (ann.y * 360).dp.coerceIn(0.dp, 340.dp)
                                                )
                                        ) {
                                            Text(
                                                text = ann.text,
                                                color = Color(0xFF1E293B),
                                                fontSize = 10.sp,
                                                maxLines = 2,
                                                modifier = Modifier.padding(4.dp)
                                            )
                                        }
                                    }
                                    else -> Unit
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // QR Code Stamp Dialog (Offline ZXing)
    pendingQrTap?.let { tapPt ->
        var qrText by remember { mutableStateOf(AppBranding.PDF_CREATOR_TAG) }
        AlertDialog(
            onDismissRequest = { pendingQrTap = null },
            title = { Text(if (strings.isArabic) "إنشاء وختم رمز QR" else "Insert Offline QR Code Stamp") },
            text = {
                OutlinedTextField(
                    value = qrText,
                    onValueChange = { qrText = it },
                    label = { Text(if (strings.isArabic) "الرابط أو النص أو رقم المعاملة" else "Text, URL, or Document ID") },
                    minLines = 2
                )
            },
            confirmButton = {
                Button(onClick = {
                    val content = qrText.trim()
                    pendingQrTap = null
                    scope.launch {
                        runCatching {
                            val qrFile = app.pdfEngine.generateQrCodeImageFile(content)
                            annotations.add(
                                PdfAnnotation.Signature(
                                    pageIndex = currentPage,
                                    x = tapPt.x,
                                    y = tapPt.y,
                                    widthNorm = 0.20f,
                                    heightNorm = 0.145f,
                                    imagePath = qrFile.absolutePath
                                )
                            )
                            redoStack.clear()
                        }.onFailure {
                            Toast.makeText(context, "QR error: ${it.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) { Text(if (strings.isArabic) "إدراج QR" else "Place QR") }
            },
            dismissButton = {
                TextButton(onClick = { pendingQrTap = null }) { Text(strings.cancel) }
            }
        )
    }

    // Password Dialog for Encrypted PDFs
    if (needsPassword) {
        var pwdInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = onBack,
            title = { Text("Password Protected PDF") },
            text = {
                OutlinedTextField(
                    value = pwdInput,
                    onValueChange = { pwdInput = it },
                    label = { Text("Enter PDF Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = { password = pwdInput }) { Text("Unlock") }
            },
            dismissButton = {
                TextButton(onClick = onBack) { Text("Cancel") }
            }
        )
    }

    // Insert Text Box Dialog
    pendingTextTap?.let { tapPt ->
        var textInput by remember { mutableStateOf("") }
        var isBold by remember { mutableStateOf(false) }
        var sizePt by remember { mutableFloatStateOf(16f) }
        AlertDialog(
            onDismissRequest = { pendingTextTap = null },
            title = { Text("Insert Text Annotation") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        label = { Text("Text (English / Arabic)") },
                        minLines = 2
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isBold, onCheckedChange = { isBold = it })
                        Text("Bold")
                        Spacer(Modifier.width(12.dp))
                        Text("Size: ${sizePt.toInt()}pt")
                    }
                    Slider(value = sizePt, onValueChange = { sizePt = it }, valueRange = 10f..36f)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (textInput.isNotBlank()) {
                            annotations.add(
                                PdfAnnotation.TextBox(
                                    pageIndex = currentPage,
                                    x = tapPt.x,
                                    y = tapPt.y,
                                    text = textInput.trim(),
                                    fontSizePt = sizePt,
                                    colorArgb = selectedColor.toArgb(),
                                    isBold = isBold
                                )
                            )
                            redoStack.clear()
                        }
                        pendingTextTap = null
                    }
                ) { Text("Place Text") }
            },
            dismissButton = {
                TextButton(onClick = { pendingTextTap = null }) { Text("Cancel") }
            }
        )
    }

    // Insert Sticky Note Dialog
    pendingNoteTap?.let { tapPt ->
        var noteText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pendingNoteTap = null },
            title = { Text("Add Sticky Note") },
            text = {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Note comment") },
                    minLines = 3
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (noteText.isNotBlank()) {
                        annotations.add(
                            PdfAnnotation.StickyNote(
                                pageIndex = currentPage,
                                x = tapPt.x,
                                y = tapPt.y,
                                text = noteText.trim(),
                                colorArgb = android.graphics.Color.rgb(254, 240, 138)
                            )
                        )
                        redoStack.clear()
                    }
                    pendingNoteTap = null
                }) { Text("Add Note") }
            },
            dismissButton = {
                TextButton(onClick = { pendingNoteTap = null }) { Text("Cancel") }
            }
        )
    }

    // Stamp Picker Dialog
    if (showStampPicker) {
        AlertDialog(
            onDismissRequest = { showStampPicker = false },
            title = { Text("Choose Stamp (then tap PDF page)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PRESET_STAMPS.forEach { stamp ->
                        OutlinedButton(
                            onClick = {
                                selectedStamp = stamp
                                showStampPicker = false
                                Toast.makeText(context, "Tap anywhere on the page to stamp '$stamp'", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stamp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showStampPicker = false }) { Text("Close") }
            }
        )
    }

    // Signature Picker Dialog
    if (showSignaturePicker) {
        AlertDialog(
            onDismissRequest = { showSignaturePicker = false },
            title = { Text("Select or Draw Signature") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { showSignaturePicker = false; showSignaturePad = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Draw, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Draw New Signature")
                    }
                    if (savedSignatures.isNotEmpty()) {
                        Text("Saved Signatures:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        savedSignatures.forEach { sig ->
                            OutlinedButton(
                                onClick = {
                                    selectedSignaturePath = sig.imagePath
                                    showSignaturePicker = false
                                    Toast.makeText(context, "Tap the PDF page to place your signature", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(sig.name)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSignaturePicker = false }) { Text("Close") }
            }
        )
    }

    // Interactive Signature Pad Dialog
    if (showSignaturePad) {
        SignaturePadDialog(
            onDismiss = { showSignaturePad = false },
            onSaveSignature = { sigPath, label ->
                app.repository.addSignature(SavedSignature(name = label, imagePath = sigPath))
                selectedSignaturePath = sigPath
                showSignaturePad = false
                Toast.makeText(context, "Signature ready! Tap the PDF page to place it.", Toast.LENGTH_LONG).show()
            }
        )
    }

    // Save Annotations Dialog
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save PDF Edits") },
            text = {
                Text("Embed ${annotations.size} annotation(s) permanently into the PDF offline?")
            },
            confirmButton = {
                Button(onClick = {
                    showSaveDialog = false
                    scope.launch {
                        isSaving = true
                        runCatching {
                            app.pdfEngine.saveAnnotations(pdfFile, pdfFile, annotations.toList(), password)
                            annotations.clear()
                            redoStack.clear()
                            renderVersion++
                            Toast.makeText(context, "Saved edits to ${pdfFile.name}", Toast.LENGTH_SHORT).show()
                        }.onFailure {
                            Toast.makeText(context, "Save failed: ${it.message}", Toast.LENGTH_LONG).show()
                        }
                        isSaving = false
                    }
                }) { Text("Save Over Original") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSaveDialog = false
                    scope.launch {
                        isSaving = true
                        runCatching {
                            val copyFile = FileHelper.uniqueFile(
                                FileHelper.pdfsDir(context),
                                "${pdfFile.nameWithoutExtension}_edited.pdf"
                            )
                            app.pdfEngine.saveAnnotations(pdfFile, copyFile, annotations.toList(), password)
                            val info = app.pdfEngine.inspectDocument(copyFile)
                            app.repository.upsertDocument(
                                PdfDocumentItem(
                                    name = copyFile.name,
                                    path = copyFile.absolutePath,
                                    sizeBytes = info.sizeBytes,
                                    pageCount = info.pageCount
                                )
                            )
                            annotations.clear()
                            redoStack.clear()
                            Toast.makeText(context, "Saved copy: ${copyFile.name}", Toast.LENGTH_LONG).show()
                        }.onFailure {
                            Toast.makeText(context, "Save failed: ${it.message}", Toast.LENGTH_LONG).show()
                        }
                        isSaving = false
                    }
                }) { Text("Save Copy") }
            }
        )
    }

    // Offline Search in PDF Dialog
    if (showSearchDialog) {
        var query by remember { mutableStateOf("") }
        var searching by remember { mutableStateOf(false) }
        var results by remember { mutableStateOf<List<SearchMatch>>(emptyList()) }
        var searchedOnce by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showSearchDialog = false },
            title = { Text("Offline Search in PDF") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            label = { Text("Keyword") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(onClick = {
                            scope.launch {
                                searching = true
                                searchedOnce = true
                                results = runCatching {
                                    app.pdfEngine.searchText(pdfFile, query, password)
                                }.getOrDefault(emptyList())
                                searching = false
                            }
                        }) { Text("Find") }
                    }

                    if (searching) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                    } else if (searchedOnce && results.isEmpty()) {
                        Text("No matches found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else if (results.isNotEmpty()) {
                        LazyColumn(modifier = Modifier.height(220.dp)) {
                            items(results) { hit ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            currentPage = hit.pageIndex
                                            showSearchDialog = false
                                        }
                                        .padding(vertical = 8.dp)
                                ) {
                                    Text(
                                        "Page ${hit.pageIndex + 1} (${hit.matchCount} hit${if (hit.matchCount == 1) "" else "s"})",
                                        fontWeight = FontWeight.Bold,
                                        color = BrandBlue
                                    )
                                    Text(hit.snippet, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSearchDialog = false }) { Text("Close") }
            }
        )
    }

    // Bookmarks Dialog
    if (showBookmarksDialog) {
        AlertDialog(
            onDismissRequest = { showBookmarksDialog = false },
            title = { Text("Bookmarks") },
            text = {
                if (docBookmarks.isEmpty()) {
                    Text("No bookmarks yet. Use the menu to bookmark any page.")
                } else {
                    LazyColumn(modifier = Modifier.height(220.dp)) {
                        items(docBookmarks, key = { it.id }) { bm ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        currentPage = bm.pageIndex
                                        showBookmarksDialog = false
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Page ${bm.pageIndex + 1} — ${bm.title}", modifier = Modifier.weight(1f))
                                IconButton(onClick = { app.repository.removeBookmark(bm.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove", tint = BrandCrimson)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBookmarksDialog = false }) { Text("Close") }
            }
        )
    }

    // Extract Page Text Dialog
    if (showExtractTextDialog) {
        AlertDialog(
            onDismissRequest = { showExtractTextDialog = false },
            title = { Text("Page ${currentPage + 1} Extracted Text") },
            text = {
                Column(
                    modifier = Modifier
                        .height(280.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(extractedPageText.ifBlank { "No selectable text found on this page." }, fontSize = 13.sp)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("PDF Text", extractedPageText))
                    Toast.makeText(context, "Copied text to clipboard", Toast.LENGTH_SHORT).show()
                    showExtractTextDialog = false
                }) {
                    Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Copy")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExtractTextDialog = false }) { Text("Close") }
            }
        )
    }
}

/**
 * Reusable interactive handwritten Signature Pad dialog saving transparent PNGs locally.
 */
@Composable
fun SignaturePadDialog(
    onDismiss: () -> Unit,
    onSaveSignature: (filePath: String, label: String) -> Unit
) {
    val context = LocalContext.current
    var label by remember { mutableStateOf(AppBranding.DEVELOPER_NAME) }
    var inkColor by remember { mutableStateOf(Color(0xFF0F172A)) }
    val strokes = remember { mutableStateListOf<List<NormPoint>>() }
    var currentStroke by remember { mutableStateOf<List<NormPoint>>(emptyList()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Draw Handwritten Signature") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Signature Label") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(Color(0xFF0F172A), Color(0xFF1D4ED8), Color(0xFFDC2626)).forEach { c ->
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(c)
                                .border(
                                    width = if (inkColor == c) 2.5.dp else 1.dp,
                                    color = if (inkColor == c) BrandBlue else Color.Gray,
                                    shape = CircleShape
                                )
                                .clickable { inkColor = c }
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { strokes.clear(); currentStroke = emptyList() }) {
                        Text("Clear")
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .border(1.5.dp, Color(0xFFCBD5E1), RoundedCornerShape(12.dp))
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    currentStroke = listOf(
                                        NormPoint(
                                            (offset.x / size.width).coerceIn(0f, 1f),
                                            (offset.y / size.height).coerceIn(0f, 1f)
                                        )
                                    )
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    currentStroke = currentStroke + NormPoint(
                                        (change.position.x / size.width).coerceIn(0f, 1f),
                                        (change.position.y / size.height).coerceIn(0f, 1f)
                                    )
                                },
                                onDragEnd = {
                                    if (currentStroke.size >= 2) {
                                        strokes.add(currentStroke)
                                    }
                                    currentStroke = emptyList()
                                }
                            )
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val all = strokes + listOf(currentStroke)
                        for (pts in all) {
                            if (pts.size < 2) continue
                            val path = Path().apply {
                                moveTo(pts.first().x * size.width, pts.first().y * size.height)
                                for (i in 1 until pts.size) {
                                    lineTo(pts[i].x * size.width, pts[i].y * size.height)
                                }
                            }
                            drawPath(
                                path = path,
                                color = inkColor,
                                style = Stroke(width = 7f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = strokes.isNotEmpty(),
                onClick = {
                    val bmpW = 900
                    val bmpH = 360
                    val bmp = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                    val canvas = AndroidCanvas(bmp)
                    val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                        color = inkColor.toArgb()
                        style = AndroidPaint.Style.STROKE
                        strokeWidth = 12f
                        strokeCap = AndroidPaint.Cap.ROUND
                        strokeJoin = AndroidPaint.Join.ROUND
                    }
                    for (pts in strokes) {
                        if (pts.size < 2) continue
                        val p = AndroidPath().apply {
                            moveTo(pts.first().x * bmpW, pts.first().y * bmpH)
                            for (i in 1 until pts.size) {
                                lineTo(pts[i].x * bmpW, pts[i].y * bmpH)
                            }
                        }
                        canvas.drawPath(p, paint)
                    }
                    val outFile = File(FileHelper.signaturesDir(context), "sig_${System.currentTimeMillis()}.png")
                    FileOutputStream(outFile).use { fos ->
                        bmp.compress(Bitmap.CompressFormat.PNG, 100, fos)
                    }
                    bmp.recycle()
                    onSaveSignature(outFile.absolutePath, label.ifBlank { "Signature" })
                }
            ) { Text("Save Signature") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
