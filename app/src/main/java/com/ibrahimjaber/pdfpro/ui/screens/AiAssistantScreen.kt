package com.ibrahimjaber.pdfpro.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ibrahimjaber.pdfpro.PdfProApp
import com.ibrahimjaber.pdfpro.data.AppBranding
import com.ibrahimjaber.pdfpro.data.LocalStrings
import com.ibrahimjaber.pdfpro.data.PageRangeParser
import com.ibrahimjaber.pdfpro.data.PageTemplate
import com.ibrahimjaber.pdfpro.data.PdfDocumentItem
import com.ibrahimjaber.pdfpro.engine.AiDocumentReport
import com.ibrahimjaber.pdfpro.engine.AiQaResponse
import com.ibrahimjaber.pdfpro.engine.FileHelper
import com.ibrahimjaber.pdfpro.engine.LargeDocIndex
import com.ibrahimjaber.pdfpro.engine.LocalGgufModelInfo
import com.ibrahimjaber.pdfpro.engine.OfflineAiPdfEngine
import com.ibrahimjaber.pdfpro.engine.OfflineTranslatorEngine
import com.ibrahimjaber.pdfpro.engine.SavedTranslationRecord
import com.ibrahimjaber.pdfpro.engine.SummaryDepth
import com.ibrahimjaber.pdfpro.engine.TranslationDirection
import com.ibrahimjaber.pdfpro.ui.theme.BrandBlue
import com.ibrahimjaber.pdfpro.ui.theme.BrandCrimson
import com.ibrahimjaber.pdfpro.ui.theme.BrandEmerald
import com.ibrahimjaber.pdfpro.ui.theme.BrandNavy
import com.ibrahimjaber.pdfpro.ui.theme.BrandPurple
import com.ibrahimjaber.pdfpro.ui.theme.BrandTeal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AiAssistantScreen(
    app: PdfProApp,
    initialFilePath: String? = null,
    onBack: (() -> Unit)? = null,
    onOpenPdfAtPage: (path: String, pageIndex: Int) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = LocalStrings.current
    val libraryDocs by app.repository.documents.collectAsState()

    var selectedFile by remember(initialFilePath) {
        mutableStateOf(
            initialFilePath?.takeIf { it.isNotBlank() }?.let { File(it) }
                ?: libraryDocs.firstOrNull()?.let { File(it.path) }
        )
    }
    var showLibraryPicker by remember { mutableStateOf(false) }
    var activeTab by remember { mutableIntStateOf(0) } // 0=Summary, 1=Translate & Dict, 2=Ask PDF, 3=Insights & PII, 4=Auto Outline
    var summaryDepth by remember { mutableStateOf(SummaryDepth.MEDIUM) }

    // Large-PDF Streaming & Page-Range State
    var totalDocPages by remember { mutableIntStateOf(0) }
    var rangeStartInput by remember { mutableStateOf("1") }
    var rangeEndInput by remember { mutableStateOf("") }
    var appliedRangeStart by remember { mutableIntStateOf(1) }
    var appliedRangeEnd by remember { mutableStateOf<Int?>(null) }

    var pagesText by remember { mutableStateOf<List<String>>(emptyList()) }
    var largeDocIndex by remember { mutableStateOf<LargeDocIndex?>(null) }
    var report by remember { mutableStateOf<AiDocumentReport?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var analysisProgress by remember { mutableFloatStateOf(0f) }
    var analysisStageText by remember { mutableStateOf("") }
    var activeAnalysisJob by remember { mutableStateOf<Job?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // Chat Q&A state
    var questionInput by remember { mutableStateOf("") }
    val qaHistory = remember { mutableStateListOf<AiQaResponse>() }

    // Downloadable Offline Neural Dictionary & Saved Translations state
    var neuralPackReady by remember { mutableStateOf(false) }
    var isDownloadingPack by remember { mutableStateOf(false) }
    var packDownloadMsg by remember { mutableStateOf("") }
    var isTranslating by remember { mutableStateOf(false) }
    var translateProgress by remember { mutableFloatStateOf(0f) }
    var translateEngineLabel by remember { mutableStateOf("") }
    var translateDir by remember { mutableStateOf(TranslationDirection.AUTO) }
    var translatedPreview by remember { mutableStateOf("") }
    var savedTranslations by remember { mutableStateOf<List<SavedTranslationRecord>>(emptyList()) }
    var generatedReply by remember { mutableStateOf("") }
    var ggufInfo by remember { mutableStateOf<LocalGgufModelInfo?>(null) }

    LaunchedEffect(Unit) {
        neuralPackReady = app.translationManager.areOfflineModelsReady()
        savedTranslations = app.translationManager.loadSavedTranslations()
    }

    val pickGgufLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val cached = FileHelper.copyAnyFileFromUriToCache(context, uri)
                if (cached != null) {
                    ggufInfo = OfflineTranslatorEngine.inspectLocalGgufModel(cached)
                }
            }
        }
    }

    val pickPdfLauncher = rememberLauncherForActivityResult(
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
                    appliedRangeStart = 1
                    appliedRangeEnd = null
                    rangeStartInput = "1"
                    rangeEndInput = ""
                    selectedFile = imported
                }
            }
        }
    }

    // Streaming Large-Document Analyzer (supports 500–2,000+ pages smoothly with live progress & Cancel)
    LaunchedEffect(selectedFile?.absolutePath, summaryDepth, appliedRangeStart, appliedRangeEnd) {
        val file = selectedFile ?: return@LaunchedEffect
        if (!file.exists()) return@LaunchedEffect

        activeAnalysisJob?.cancel()
        val job = launch {
            isAnalyzing = true
            analysisProgress = 0.02f
            analysisStageText = if (s.isArabic) "جاري فحص عدد صفحات الملف..." else "Inspecting PDF page count..."
            errorMsg = null

            try {
                val docPages = OfflineAiPdfEngine.inspectPageCount(file)
                totalDocPages = docPages
                if (rangeEndInput.isBlank()) {
                    rangeEndInput = docPages.toString()
                }
                val effEnd = appliedRangeEnd ?: docPages

                val batch = OfflineAiPdfEngine.extractPagesStreaming(
                    pdfFile = file,
                    startPage = appliedRangeStart,
                    endPage = effEnd,
                    onProgress = { cur, tot ->
                        analysisProgress = 0.05f + 0.60f * (cur.toFloat() / tot.coerceAtLeast(1))
                        analysisStageText = if (s.isArabic) {
                            "قراءة الصفحة $cur من $tot (إجمالي الملف $docPages صفحة)..."
                        } else {
                            "Extracting page $cur / $tot (Book total: $docPages pages)..."
                        }
                    }
                )
                pagesText = batch.pages

                analysisStageText = if (s.isArabic) "بناء الفهرس السريع للبحث والأسئلة..." else "Building fast inverted search index..."
                analysisProgress = 0.70f
                val idx = withContext(Dispatchers.Default) {
                    OfflineAiPdfEngine.buildLargeDocIndex(batch.pages, startPageNumber = batch.startPageNumber)
                }
                largeDocIndex = idx

                val rep = withContext(Dispatchers.Default) {
                    OfflineAiPdfEngine.analyzeLargeDocument(
                        pages = batch.pages,
                        startPageNumber = batch.startPageNumber,
                        totalDocumentPages = batch.totalDocumentPages,
                        depth = summaryDepth,
                        onStageProgress = { stage, frac ->
                            analysisProgress = 0.70f + 0.30f * frac
                            analysisStageText = stage
                        }
                    )
                }
                report = rep
                qaHistory.clear()
            } catch (ce: CancellationException) {
                throw ce
            } catch (err: Throwable) {
                errorMsg = err.message ?: "Unable to analyze PDF"
            } finally {
                isAnalyzing = false
            }
        }
        activeAnalysisJob = job
    }

    Scaffold(
        topBar = {
            if (onBack != null) {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    title = {
                        Column {
                            Text(if (s.isArabic) "استوديو الذكاء الاصطناعي للملفات الكبيرة" else "Large-PDF AI Studio (v5.0)")
                            selectedFile?.let {
                                Text(it.name, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // AI Hero Banner
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(BrandNavy, BrandPurple, BrandBlue)
                            )
                        )
                        .padding(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFFFDE047))
                            Text(
                                text = if (s.isArabic) "استوديو AI للملفات الكبيرة + القاموس العصبي" else "Large-PDF AI Studio + Neural Translator",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.5.sp
                            )
                        }
                        Text(
                            text = if (s.isArabic) {
                                "تلخيص الكتب الكبيرة (Map-Reduce) • بحث فوري عبر 1000+ صفحة • قاموس عربي/إنجليزي كامل أوفلاين • ${AppBranding.CREDIT_LINE_AR}"
                            } else {
                                "Map-Reduce Large-Book Summarizer • Instant 1,000+ Page Q&A • Downloadable Offline AR↔EN Neural Pack • ${AppBranding.CREDIT_LINE}"
                            },
                            color = Color(0xFFE0F2FE),
                            fontSize = 12.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { pickPdfLauncher.launch(arrayOf("application/pdf")) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(s.fromDevice, fontSize = 12.sp)
                            }
                            if (libraryDocs.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = { showLibraryPicker = true },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.PictureAsPdf, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(s.fromLibrary, color = Color.White, fontSize = 12.sp)
                                }
                            }
                        }
                        selectedFile?.let {
                            Text(
                                text = "✓ ${it.name}" + if (totalDocPages > 0) "  ($totalDocPages ${if (s.isArabic) "صفحة" else "pages"})" else "",
                                color = Color(0xFF86EFAC),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Large-Document Page Range Selector (always available when PDF has > 15 pages)
            if (totalDocPages > 15 && selectedFile != null) {
                item {
                    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = if (s.isArabic) "⚡ وضع الملفات الكبيرة ($totalDocPages صفحة) — اختر النطاق:" else "⚡ Large-PDF Mode ($totalDocPages pages) — Select Page Range:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp,
                                color = BrandPurple
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                AssistChip(
                                    onClick = {
                                        appliedRangeStart = 1
                                        appliedRangeEnd = null
                                        rangeStartInput = "1"
                                        rangeEndInput = totalDocPages.toString()
                                    },
                                    label = { Text(if (s.isArabic) "الكتاب كامل (1–$totalDocPages)" else "Full Book (1–$totalDocPages)", fontSize = 11.sp) }
                                )
                                AssistChip(
                                    onClick = {
                                        appliedRangeStart = 1
                                        appliedRangeEnd = minOf(50, totalDocPages)
                                        rangeStartInput = "1"
                                        rangeEndInput = minOf(50, totalDocPages).toString()
                                    },
                                    label = { Text("1–${minOf(50, totalDocPages)}", fontSize = 11.sp) }
                                )
                                if (totalDocPages > 50) {
                                    AssistChip(
                                        onClick = {
                                            appliedRangeStart = 51
                                            appliedRangeEnd = minOf(120, totalDocPages)
                                            rangeStartInput = "51"
                                            rangeEndInput = minOf(120, totalDocPages).toString()
                                        },
                                        label = { Text("51–${minOf(120, totalDocPages)}", fontSize = 11.sp) }
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = rangeStartInput,
                                    onValueChange = { rangeStartInput = it.filter(Char::isDigit) },
                                    label = { Text(if (s.isArabic) "من صفحة" else "From", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = rangeEndInput,
                                    onValueChange = { rangeEndInput = it.filter(Char::isDigit) },
                                    label = { Text(if (s.isArabic) "إلى صفحة" else "To", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                Button(onClick = {
                                    val st = rangeStartInput.toIntOrNull()?.coerceIn(1, totalDocPages) ?: 1
                                    val en = rangeEndInput.toIntOrNull()?.coerceIn(st, totalDocPages) ?: totalDocPages
                                    appliedRangeStart = st
                                    appliedRangeEnd = en
                                }) {
                                    Text(if (s.isArabic) "تحليل" else "Analyze", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            // 5 AI Mode Tabs
            item {
                ScrollableTabRow(selectedTabIndex = activeTab, edgePadding = 4.dp) {
                    Tab(selected = activeTab == 0, onClick = { activeTab = 0 }, text = { Text(if (s.isArabic) "ملخص AI" else "Summary", fontSize = 12.sp) })
                    Tab(selected = activeTab == 1, onClick = { activeTab = 1 }, text = { Text(if (s.isArabic) "القاموس والترجمة" else "Translate & Dict", fontSize = 12.sp) })
                    Tab(selected = activeTab == 2, onClick = { activeTab = 2 }, text = { Text(if (s.isArabic) "اسأل الملف" else "Ask PDF", fontSize = 12.sp) })
                    Tab(selected = activeTab == 3, onClick = { activeTab = 3 }, text = { Text(if (s.isArabic) "بيانات/PII" else "Insights", fontSize = 12.sp) })
                    Tab(selected = activeTab == 4, onClick = { activeTab = 4 }, text = { Text(if (s.isArabic) "فهرس ذكي" else "Outline", fontSize = 12.sp) })
                }
            }

            if (isAnalyzing) {
                item {
                    Card(shape = RoundedCornerShape(16.dp)) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = analysisStageText.ifBlank { if (s.isArabic) "جاري التحليل الذكي أوفلاين..." else "Analyzing PDF on-device..." },
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.5.sp
                            )
                            LinearProgressIndicator(
                                progress = { analysisProgress.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text("${(analysisProgress * 100).toInt()}%", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedButton(onClick = { activeAnalysisJob?.cancel(); isAnalyzing = false }) {
                                Text(s.cancel)
                            }
                        }
                    }
                }
            } else if (errorMsg != null) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = BrandCrimson.copy(alpha = 0.12f))) {
                        Text(errorMsg ?: "Error", color = BrandCrimson, modifier = Modifier.padding(14.dp))
                    }
                }
            } else if (report != null && selectedFile != null) {
                val rep = report!!
                val file = selectedFile!!

                when (activeTab) {
                    // TAB 0: AI SUMMARY & LARGE-BOOK CHAPTER BREAKDOWN
                    0 -> {
                        item {
                            Card(shape = RoundedCornerShape(16.dp)) {
                                Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = if (s.isArabic) "تحليل اللغة وحجم الملف" else "Document Language & Large-Book Analytics",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    val ls = rep.languageStats
                                    Text(
                                        text = "${if (s.isArabic) ls.primary.labelAr else ls.primary.labelEn}  •  AR ${ls.arabicPercent}% / EN ${ls.englishPercent}%  •  ${ls.wordCount} ${if (s.isArabic) "كلمة" else "words"}  •  ~${ls.estimatedReadMinutes} ${if (s.isArabic) "دقيقة قراءة" else "min read"}",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (rep.usedMapReduce) {
                                        Text(
                                            text = if (s.isArabic) "⚡ تم التلخيص بمحرك Map-Reduce للملفات الكبيرة (${rep.analyzedPageCount} صفحة من ${rep.totalDocumentPages})" else "⚡ Processed with Large-Book Map-Reduce Engine (${rep.analyzedPageCount} of ${rep.totalDocumentPages} pages)",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = BrandEmerald
                                        )
                                    }
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        SummaryDepth.entries.forEach { d ->
                                            FilterChip(
                                                selected = summaryDepth == d,
                                                onClick = { summaryDepth = d },
                                                label = { Text(if (s.isArabic) d.labelAr else d.labelEn, fontSize = 11.sp) }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Card(shape = RoundedCornerShape(16.dp)) {
                                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(
                                        text = if (s.isArabic) "الملخص التنفيذي بالذكاء الاصطناعي" else "AI Executive Summary",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    if (rep.summaryBullets.isEmpty()) {
                                        Text(if (s.isArabic) "لم يتم العثور على نص قابل للتلخيص." else "No selectable text found to summarize.")
                                    } else {
                                        rep.summaryBullets.forEachIndexed { idx, bullet ->
                                            Text("${idx + 1}. $bullet", fontSize = 13.5.sp, lineHeight = 20.sp)
                                        }
                                    }

                                    if (rep.topKeywords.isNotEmpty()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(if (s.isArabic) "أبرز المواضيع والكلمات:" else "Top Topics:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            rep.topKeywords.forEach { kw ->
                                                AssistChip(onClick = {
                                                    questionInput = kw
                                                    activeTab = 2
                                                }, label = { Text(kw, fontSize = 11.5.sp) })
                                            }
                                        }
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(onClick = {
                                            val text = rep.summaryBullets.mapIndexed { i, b -> "${i + 1}. $b" }.joinToString("\n\n")
                                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            cm.setPrimaryClip(ClipData.newPlainText("AI Summary", text))
                                            Toast.makeText(context, "Copied AI Summary", Toast.LENGTH_SHORT).show()
                                        }) {
                                            Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(if (s.isArabic) "نسخ" else "Copy")
                                        }

                                        Button(onClick = {
                                            scope.launch {
                                                runCatching {
                                                    val summaryBody = buildString {
                                                        append("AI Summary of: ${file.name}\n")
                                                        append("Pages analyzed: ${rep.analyzedPageCount}/${rep.totalDocumentPages} | Words: ${rep.languageStats.wordCount}\n\n")
                                                        rep.summaryBullets.forEachIndexed { i, b -> append("${i + 1}. $b\n\n") }
                                                        if (rep.chapterSummaries.isNotEmpty()) {
                                                            append("\n--- Section Breakdown ---\n")
                                                            rep.chapterSummaries.forEach { ch ->
                                                                append("\nPages ${ch.startPage}-${ch.endPage}: ${ch.headline}\n")
                                                                ch.bullets.forEach { append("  - $it\n") }
                                                            }
                                                        }
                                                    }
                                                    val out = FileHelper.uniqueFile(
                                                        FileHelper.pdfsDir(context),
                                                        "${file.nameWithoutExtension}_AI_Summary.pdf"
                                                    )
                                                    app.pdfEngine.createNewPdf(
                                                        outputFile = out,
                                                        title = "AI Summary — ${file.nameWithoutExtension}",
                                                        bodyText = summaryBody,
                                                        pageCount = 1,
                                                        template = PageTemplate.BLANK
                                                    )
                                                    val info = app.pdfEngine.inspectDocument(out)
                                                    app.repository.upsertDocument(
                                                        PdfDocumentItem(name = out.name, path = out.absolutePath, sizeBytes = info.sizeBytes, pageCount = info.pageCount)
                                                    )
                                                    Toast.makeText(context, "Saved ${out.name}", Toast.LENGTH_SHORT).show()
                                                    onOpenPdfAtPage(out.absolutePath, 0)
                                                }
                                            }
                                        }) {
                                            Icon(Icons.Default.Save, null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(if (s.isArabic) "حفظ كـ PDF" else "Save Summary PDF")
                                        }
                                    }
                                }
                            }
                        }

                        // Chapter-by-Chapter Map-Reduce Breakdown for Large Books
                        if (rep.chapterSummaries.isNotEmpty()) {
                            item {
                                Card(shape = RoundedCornerShape(16.dp)) {
                                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text(
                                            text = if (s.isArabic) "ملخص الأقسام والفصول (${rep.chapterSummaries.size} أقسام)" else "Chapter-by-Chapter Breakdown (${rep.chapterSummaries.size} Sections)",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        rep.chapterSummaries.forEach { ch ->
                                            Surface(
                                                color = MaterialTheme.colorScheme.surfaceVariant,
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { onOpenPdfAtPage(file.absolutePath, ch.startPage - 1) }
                                            ) {
                                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Text(
                                                        text = "P.${ch.startPage}–${ch.endPage}: ${ch.headline}",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.5.sp,
                                                        color = BrandBlue
                                                    )
                                                    ch.bullets.forEach { b ->
                                                        Text("• $b", fontSize = 12.5.sp, lineHeight = 18.sp)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // TAB 1: DOWNLOADABLE FULL OFFLINE AR<->EN NEURAL DICTIONARY + TRANSLATE + SAVED HISTORY
                    1 -> {
                        // Offline Neural Language Pack Manager Card
                        item {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (neuralPackReady) BrandEmerald.copy(alpha = 0.12f) else BrandBlue.copy(alpha = 0.10f)
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = if (s.isArabic) "القاموس العصبي الكامل (عربي ↔ إنجليزي) — أوفلاين" else "Downloadable Complete AR ↔ EN Neural Dictionary",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = if (neuralPackReady) {
                                            if (s.isArabic) "✓ القاموس العصبي الكامل (عربي + إنجليزي) محمّل وجاهز! الترجمة كاملة وصحيحة 100% بدون إنترنت للملفات والكاميرا."
                                            else "✓ Complete Arabic & English Neural Translation Pack is installed locally! Full grammatical translation works 100% offline for PDFs & Live Camera."
                                        } else {
                                            if (s.isArabic) "نزّل القاموس العربي والإنجليزي الكامل مرة واحدة من الإنترنت (~30 ميجابايت) لتعمل الترجمة الكاملة والصحيحة أوفلاين دائماً وحفظ الترجمات محلياً."
                                            else "Download the full Arabic & English on-device neural pack once (~30 MB over HTTPS) for accurate, full-sentence offline translation across PDFs and Live Camera."
                                        },
                                        fontSize = 12.5.sp
                                    )

                                    if (isDownloadingPack) {
                                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                        Text(packDownloadMsg.ifBlank { "Downloading..." }, fontSize = 12.sp, color = BrandBlue)
                                    } else {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(
                                                onClick = {
                                                    scope.launch {
                                                        isDownloadingPack = true
                                                        val res = app.translationManager.downloadArabicEnglishModels(wifiOnly = false) { msg ->
                                                            packDownloadMsg = msg
                                                        }
                                                        isDownloadingPack = false
                                                        if (res.isSuccess) {
                                                            neuralPackReady = true
                                                            Toast.makeText(
                                                                context,
                                                                if (s.isArabic) "تم تنزيل القاموس الكامل وحفظه أوفلاين بنجاح!" else "Complete AR+EN Offline Dictionary Pack Ready!",
                                                                Toast.LENGTH_LONG
                                                            ).show()
                                                        } else {
                                                            Toast.makeText(context, res.exceptionOrNull()?.message ?: "Download failed — check internet connection", Toast.LENGTH_LONG).show()
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.weight(1f),
                                                colors = ButtonDefaults.buttonColors(containerColor = if (neuralPackReady) BrandTeal else BrandBlue)
                                            ) {
                                                Icon(Icons.Default.CloudDownload, null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text(
                                                    if (neuralPackReady) (if (s.isArabic) "تحديث / التحقق من القاموس" else "Verify / Re-Sync Pack")
                                                    else (if (s.isArabic) "تنزيل القاموس العربي والإنجليزي (~30MB)" else "Download AR+EN Pack (~30MB)"),
                                                    fontSize = 11.5.sp
                                                )
                                            }
                                            if (neuralPackReady) {
                                                OutlinedButton(onClick = {
                                                    scope.launch {
                                                        app.translationManager.deleteOfflineModels()
                                                        neuralPackReady = false
                                                        Toast.makeText(context, "Removed downloaded neural packs", Toast.LENGTH_SHORT).show()
                                                    }
                                                }) {
                                                    Icon(Icons.Default.Delete, null, tint = BrandCrimson, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Document Translation Controls
                        item {
                            Card(shape = RoundedCornerShape(16.dp)) {
                                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(
                                        text = if (s.isArabic) "ترجمة الملف بالكامل وحفظها محلياً" else "Translate Document & Save Locally",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        TranslationDirection.entries.forEach { dir ->
                                            FilterChip(
                                                selected = translateDir == dir,
                                                onClick = { translateDir = dir },
                                                label = { Text(if (s.isArabic) dir.labelAr else dir.labelEn, fontSize = 11.sp) }
                                            )
                                        }
                                    }

                                    if (isTranslating) {
                                        LinearProgressIndicator(progress = { translateProgress }, modifier = Modifier.fillMaxWidth())
                                        Text("${(translateProgress * 100).toInt()}% ...", fontSize = 12.sp)
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            enabled = !isTranslating,
                                            onClick = {
                                                scope.launch {
                                                    isTranslating = true
                                                    translateProgress = 0.05f
                                                    val raw = pagesText.joinToString("\n\n").take(35_000)
                                                    val res = app.translationManager.translateSmartOffline(
                                                        text = raw,
                                                        direction = translateDir,
                                                        sourceLabel = file.name,
                                                        saveToHistory = true,
                                                        onChunkProgress = { c, tot ->
                                                            translateProgress = c.toFloat() / tot.coerceAtLeast(1)
                                                        }
                                                    )
                                                    translatedPreview = res.translatedText
                                                    translateEngineLabel = when {
                                                        res.fromLocalCache -> if (s.isArabic) "✓ مسترجع فوراً من الذاكرة المحلية" else "✓ Instant recall from local cache"
                                                        res.usedNeuralOfflineModel -> if (s.isArabic) "✓ مترجم بالقاموس العصبي الكامل (أوفلاين) ومحفوظ محلياً" else "✓ Translated via Full Neural Offline Pack & Saved Locally"
                                                        else -> if (s.isArabic) "مترجم بالقاموس المدمج — نزّل القاموس العصبي أعلاه لترجمة كاملة" else "Built-in Dictionary — Download Neural Pack above for full sentences"
                                                    }
                                                    savedTranslations = app.translationManager.loadSavedTranslations()
                                                    isTranslating = false
                                                }
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(if (s.isArabic) "ترجمة الملف وحفظه محلياً" else "Translate & Save Locally")
                                        }
                                        if (translatedPreview.isNotBlank()) {
                                            OutlinedButton(onClick = {
                                                scope.launch {
                                                    runCatching {
                                                        val out = FileHelper.uniqueFile(
                                                            FileHelper.pdfsDir(context),
                                                            "${file.nameWithoutExtension}_Translated.pdf"
                                                        )
                                                        app.pdfEngine.createNewPdf(
                                                            outputFile = out,
                                                            title = "Translated — ${file.nameWithoutExtension}",
                                                            bodyText = translatedPreview,
                                                            pageCount = 1,
                                                            template = PageTemplate.BLANK
                                                        )
                                                        val info = app.pdfEngine.inspectDocument(out)
                                                        app.repository.upsertDocument(
                                                            PdfDocumentItem(name = out.name, path = out.absolutePath, sizeBytes = info.sizeBytes, pageCount = info.pageCount)
                                                        )
                                                        onOpenPdfAtPage(out.absolutePath, 0)
                                                    }
                                                }
                                            }) { Text("PDF") }
                                        }
                                    }

                                    if (translateEngineLabel.isNotBlank()) {
                                        Text(translateEngineLabel, fontSize = 12.sp, color = BrandEmerald, fontWeight = FontWeight.SemiBold)
                                    }
                                    if (translatedPreview.isNotBlank()) {
                                        Text(translatedPreview.take(6000), fontSize = 13.sp, lineHeight = 20.sp)
                                    }
                                }
                            }
                        }

                        // Saved Local Translations History Card
                        if (savedTranslations.isNotEmpty()) {
                            item {
                                Card(shape = RoundedCornerShape(16.dp)) {
                                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Icon(Icons.Default.History, null, tint = BrandPurple, modifier = Modifier.size(18.dp))
                                                Text(
                                                    text = if (s.isArabic) "الترجمات المحفوظة محلياً (${savedTranslations.size})" else "Saved Local Translations (${savedTranslations.size})",
                                                    style = MaterialTheme.typography.titleMedium
                                                )
                                            }
                                            TextButton(onClick = {
                                                app.translationManager.clearSavedTranslations()
                                                savedTranslations = emptyList()
                                            }) { Text(if (s.isArabic) "مسح الكل" else "Clear", color = BrandCrimson, fontSize = 11.5.sp) }
                                        }

                                        savedTranslations.take(10).forEach { rec ->
                                            Surface(
                                                color = MaterialTheme.colorScheme.surfaceVariant,
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { translatedPreview = rec.translatedText }
                                            ) {
                                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                        Text("${rec.sourceLabel} • ${rec.directionLabel}", fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = BrandBlue)
                                                        Text(rec.engineUsed, fontSize = 10.5.sp, color = BrandEmerald)
                                                    }
                                                    Text(rec.translatedText.take(180), fontSize = 12.5.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Generative Reply Drafter & Study Flashcards
                        item {
                            Card(shape = RoundedCornerShape(16.dp)) {
                                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(
                                        text = if (s.isArabic) "توليد رد رسمي وبطاقات مراجعة (أوفلاين)" else "Offline Executive Reply & Study Flashcards",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = { generatedReply = OfflineTranslatorEngine.draftFormalReply(rep, file.name, inArabic = false) },
                                            modifier = Modifier.weight(1f)
                                        ) { Text("Draft Reply (EN)", fontSize = 11.5.sp) }
                                        Button(
                                            onClick = { generatedReply = OfflineTranslatorEngine.draftFormalReply(rep, file.name, inArabic = true) },
                                            modifier = Modifier.weight(1f)
                                        ) { Text("مسودة رد (عربي)", fontSize = 11.5.sp) }
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            val cards = OfflineTranslatorEngine.generateStudyFlashcards(pagesText)
                                            generatedReply = cards.mapIndexed { i, c ->
                                                "Card ${i + 1} (P.${c.pageNumber})\nQ: ${c.question}\nA: ${c.answer}"
                                            }.joinToString("\n\n")
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text(if (s.isArabic) "توليد بطاقات مراجعة دراسية (Flashcards)" else "Generate Study Flashcards Deck") }

                                    if (generatedReply.isNotBlank()) {
                                        Text(generatedReply, fontSize = 13.sp, lineHeight = 19.sp)
                                    }
                                }
                            }
                        }

                        // Local On-Device GGUF Model Manager
                        item {
                            Card(shape = RoundedCornerShape(16.dp)) {
                                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = if (s.isArabic) "نماذج الذكاء الاصطناعي المحلية (GGUF LLM)" else "Local On-Device LLM Model Slot (.gguf)",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = if (s.isArabic) "استورد أي نموذج محلي (.gguf مثل Qwen2.5 أو Gemma3) من ذاكرة جهازك بدون إنترنت للتحقق منه."
                                        else "Import any local .gguf model (e.g. Qwen 2.5 / Gemma 3 Arabic-English) from device storage offline to validate its header.",
                                        fontSize = 12.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    OutlinedButton(
                                        onClick = { pickGgufLauncher.launch(arrayOf("*/*")) },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text(if (s.isArabic) "استيراد ملف نموذج .gguf محلي" else "Import & Validate Local .gguf Model") }
                                    ggufInfo?.let { m ->
                                        Text(
                                            text = if (m.isValidGgufHeader) {
                                                "✓ Valid GGUF v${m.ggufVersion}: ${m.fileName} (${PageRangeParser.formatFileSize(m.sizeBytes)})"
                                            } else {
                                                "✗ Selected file is not a valid GGUF model header: ${m.fileName}"
                                            },
                                            color = if (m.isValidGgufHeader) BrandEmerald else BrandCrimson,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.5.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // TAB 2: ASK YOUR PDF (ULTRA-FAST INVERTED-INDEX BM25 Q&A FOR 1,000+ PAGES)
                    2 -> {
                        item {
                            Card(shape = RoundedCornerShape(16.dp)) {
                                Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = if (s.isArabic) "اسأل أي سؤال عبر ${rep.analyzedPageCount} صفحة (فهرسة فورية)" else "Ask Any Question Across ${rep.analyzedPageCount} Pages (Indexed BM25)",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = questionInput,
                                            onValueChange = { questionInput = it },
                                            placeholder = {
                                                Text(if (s.isArabic) "مثال: ما هي المزايا أو التواريخ أو المبالغ؟" else "e.g. What are the key requirements or dates?")
                                            },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true
                                        )
                                        Button(onClick = {
                                            if (questionInput.isNotBlank()) {
                                                val q = questionInput.trim()
                                                val idx = largeDocIndex
                                                val res = if (idx != null) {
                                                    OfflineAiPdfEngine.askQuestionIndexed(idx, q)
                                                } else {
                                                    OfflineAiPdfEngine.askQuestion(pagesText, q)
                                                }
                                                qaHistory.add(0, res)
                                                questionInput = ""
                                            }
                                        }) {
                                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Ask")
                                        }
                                    }
                                }
                            }
                        }

                        items(qaHistory) { qa ->
                            Card(shape = RoundedCornerShape(14.dp)) {
                                Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Q: ${qa.question}", fontWeight = FontWeight.Bold, color = BrandBlue)
                                    if (qa.passages.isEmpty()) {
                                        Text(qa.answerSummary, fontSize = 13.sp)
                                    } else {
                                        qa.passages.forEach { p ->
                                            Surface(
                                                color = MaterialTheme.colorScheme.surfaceVariant,
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { onOpenPdfAtPage(file.absolutePath, p.pageIndex) }
                                            ) {
                                                Column(modifier = Modifier.padding(10.dp)) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text(
                                                            text = if (s.isArabic) "صفحة ${p.pageIndex + 1} (اضغط للانتقال)" else "Page ${p.pageIndex + 1} (Tap to open)",
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 12.sp,
                                                            color = BrandPurple
                                                        )
                                                        Text("${p.relevancePercent}%", fontSize = 11.5.sp, color = BrandEmerald, fontWeight = FontWeight.Bold)
                                                    }
                                                    Spacer(Modifier.height(4.dp))
                                                    Text(p.text, fontSize = 13.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // TAB 3: SMART INSIGHTS & PII PRIVACY SCANNER
                    3 -> {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (rep.piiFindings.isEmpty()) BrandEmerald.copy(alpha = 0.12f) else BrandCrimson.copy(alpha = 0.10f)
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(
                                            Icons.Default.PrivacyTip,
                                            null,
                                            tint = if (rep.piiFindings.isEmpty()) BrandEmerald else BrandCrimson
                                        )
                                        Text(
                                            text = if (s.isArabic) {
                                                "فحص الخصوصية والبيانات الحساسة (${rep.piiFindings.size})"
                                            } else {
                                                "AI Sensitive Data / PII Redaction Audit (${rep.piiFindings.size})"
                                            },
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                    }
                                    if (rep.piiFindings.isEmpty()) {
                                        Text(
                                            if (s.isArabic) "ممتاز! لم يتم العثور على أرقام بطاقات أو IBAN أو إيميلات مكشوفة."
                                            else "Clean! No exposed emails, phone numbers, IBANs, credit cards, or IP addresses detected.",
                                            fontSize = 13.sp
                                        )
                                    } else {
                                        rep.piiFindings.forEach { finding ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { onOpenPdfAtPage(file.absolutePath, finding.pageIndex) }
                                                    .padding(vertical = 4.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("• ${finding.category}: ${finding.maskedValue}", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                                Text("Page ${finding.pageIndex + 1} →", color = BrandCrimson, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            InsightListCard(
                                title = if (s.isArabic) "المهام والالتزامات المستخرجة" else "Detected Action Items & Obligations",
                                items = rep.actionItems
                            )
                        }
                        item {
                            InsightListCard(
                                title = if (s.isArabic) "المبالغ والعملات" else "Amounts & Currencies",
                                items = rep.amountsFound
                            )
                        }
                        item {
                            InsightListCard(
                                title = if (s.isArabic) "التواريخ والمواعيد" else "Dates & Deadlines",
                                items = rep.datesFound
                            )
                        }
                        item {
                            InsightListCard(
                                title = if (s.isArabic) "البريد والهواتف والروابط" else "Emails, Phones & Links",
                                items = rep.emailsFound + rep.phonesFound + rep.urlsFound
                            )
                        }
                    }

                    // TAB 4: AUTO OUTLINE & SMART BOOKMARKS
                    4 -> {
                        item {
                            Card(shape = RoundedCornerShape(16.dp)) {
                                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (s.isArabic) "الفهرس الذكي (${rep.autoHeadings.size})" else "AI Auto-Detected Outline (${rep.autoHeadings.size})",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        if (rep.autoHeadings.isNotEmpty()) {
                                            Button(onClick = {
                                                rep.autoHeadings.forEach { h ->
                                                    app.repository.addBookmark(file.absolutePath, h.pageIndex, h.title.take(40))
                                                }
                                                Toast.makeText(
                                                    context,
                                                    if (s.isArabic) "تم حفظ جميع العناوين في الإشارات المرجعية" else "Saved all headings as Bookmarks!",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }) {
                                                Icon(Icons.Default.BookmarkAdd, null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text(if (s.isArabic) "حفظ كفهرس" else "Bookmark All", fontSize = 11.5.sp)
                                            }
                                        }
                                    }

                                    if (rep.autoHeadings.isEmpty()) {
                                        Text(if (s.isArabic) "لم يتم اكتشاف عناوين فصول واضحة." else "No clear section headings detected.")
                                    } else {
                                        rep.autoHeadings.forEach { h ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { onOpenPdfAtPage(file.absolutePath, h.pageIndex) }
                                                    .padding(vertical = 8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(h.title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium, fontSize = 13.5.sp)
                                                Text("P.${h.pageIndex + 1}", color = BrandBlue, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                                            }
                                        }
                                    }
                                }
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
                                    appliedRangeStart = 1
                                    appliedRangeEnd = null
                                    rangeStartInput = "1"
                                    rangeEndInput = ""
                                    selectedFile = File(doc.path)
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

@Composable
private fun InsightListCard(title: String, items: List<String>) {
    Card(shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("$title (${items.size})", style = MaterialTheme.typography.titleMedium)
            if (items.isEmpty()) {
                Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            } else {
                items.forEach { item ->
                    Text("• $item", fontSize = 13.sp)
                }
            }
        }
    }
}
