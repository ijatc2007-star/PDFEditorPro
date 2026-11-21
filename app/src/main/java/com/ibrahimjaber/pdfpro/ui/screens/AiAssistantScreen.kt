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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.ibrahimjaber.pdfpro.engine.OfflineAiPdfEngine
import com.ibrahimjaber.pdfpro.engine.SummaryDepth
import com.ibrahimjaber.pdfpro.ui.theme.BrandBlue
import com.ibrahimjaber.pdfpro.ui.theme.BrandCrimson
import com.ibrahimjaber.pdfpro.ui.theme.BrandEmerald
import com.ibrahimjaber.pdfpro.ui.theme.BrandNavy
import com.ibrahimjaber.pdfpro.ui.theme.BrandPurple
import kotlinx.coroutines.launch
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
    var activeTab by remember { mutableIntStateOf(0) } // 0=Summary, 1=Ask PDF, 2=Insights & PII, 3=Auto Outline
    var summaryDepth by remember { mutableStateOf(SummaryDepth.MEDIUM) }

    var pagesText by remember { mutableStateOf<List<String>>(emptyList()) }
    var report by remember { mutableStateOf<AiDocumentReport?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // Chat Q&A state
    var questionInput by remember { mutableStateOf("") }
    val qaHistory = remember { mutableStateListOf<AiQaResponse>() }

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
                    selectedFile = imported
                }
            }
        }
    }

    LaunchedEffect(selectedFile?.absolutePath, summaryDepth) {
        val file = selectedFile ?: return@LaunchedEffect
        if (!file.exists()) return@LaunchedEffect
        isAnalyzing = true
        errorMsg = null
        runCatching {
            val extracted = OfflineAiPdfEngine.extractNormalizedPages(file)
            pagesText = extracted
            report = OfflineAiPdfEngine.analyzeDocument(extracted, summaryDepth)
            qaHistory.clear()
        }.onFailure { err ->
            errorMsg = err.message ?: "Unable to analyze PDF"
        }
        isAnalyzing = false
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
                            Text(if (s.isArabic) "الذكاء الاصطناعي للملفات (أوفلاين)" else "Offline AI PDF Studio")
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
                                text = if (s.isArabic) "الذكاء الاصطناعي لـ PDF — عربي وإنجليزي" else "Offline AI PDF Assistant (EN & AR)",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )
                        }
                        Text(
                            text = if (s.isArabic) {
                                "تلخيص ذكي • اسأل الملف • كشف البيانات الحساسة (PII) • فهرسة تلقائية — 100% بدون إنترنت • ${AppBranding.CREDIT_LINE_AR}"
                            } else {
                                "AI Summarizer • Ask Your PDF • PII Privacy Audit • Smart Outline — 100% On-Device Offline • ${AppBranding.CREDIT_LINE}"
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
                                text = "✓ ${it.name}",
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

            // 4 AI Mode Tabs
            item {
                TabRow(selectedTabIndex = activeTab) {
                    Tab(selected = activeTab == 0, onClick = { activeTab = 0 }, text = { Text(if (s.isArabic) "ملخص AI" else "Summary", fontSize = 12.sp) })
                    Tab(selected = activeTab == 1, onClick = { activeTab = 1 }, text = { Text(if (s.isArabic) "اسأل الملف" else "Ask PDF", fontSize = 12.sp) })
                    Tab(selected = activeTab == 2, onClick = { activeTab = 2 }, text = { Text(if (s.isArabic) "بيانات/PII" else "Insights", fontSize = 12.sp) })
                    Tab(selected = activeTab == 3, onClick = { activeTab = 3 }, text = { Text(if (s.isArabic) "فهرس ذكي" else "Outline", fontSize = 12.sp) })
                }
            }

            if (isAnalyzing) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(10.dp))
                            Text(if (s.isArabic) "جاري التحليل الذكي أوفلاين..." else "Analyzing PDF on-device offline...")
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
                    // TAB 0: AI SUMMARY & DOCUMENT ANALYTICS
                    0 -> {
                        item {
                            Card(shape = RoundedCornerShape(16.dp)) {
                                Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = if (s.isArabic) "تحليل اللغة والقراءة" else "Document Language & Reading Analytics",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    val ls = rep.languageStats
                                    Text(
                                        text = "${if (s.isArabic) ls.primary.labelAr else ls.primary.labelEn}  •  AR ${ls.arabicPercent}% / EN ${ls.englishPercent}%  •  ${ls.wordCount} ${if (s.isArabic) "كلمة" else "words"}  •  ~${ls.estimatedReadMinutes} ${if (s.isArabic) "دقيقة قراءة" else "min read"}",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        SummaryDepth.entries.forEach { d ->
                                            FilterChip(
                                                selected = summaryDepth == d,
                                                onClick = { summaryDepth = d },
                                                label = { Text(if (s.isArabic) d.labelAr else d.labelEn, fontSize = 11.5.sp) }
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
                                                    activeTab = 1
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
                                                        append("Language: ${rep.languageStats.primary.labelEn} | Words: ${rep.languageStats.wordCount}\n\n")
                                                        rep.summaryBullets.forEachIndexed { i, b -> append("${i + 1}. $b\n\n") }
                                                        if (rep.topKeywords.isNotEmpty()) {
                                                            append("Key Topics: ${rep.topKeywords.joinToString(", ")}\n")
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
                                                        PdfDocumentItem(
                                                            name = out.name,
                                                            path = out.absolutePath,
                                                            sizeBytes = info.sizeBytes,
                                                            pageCount = info.pageCount
                                                        )
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
                    }

                    // TAB 1: ASK YOUR PDF (OFFLINE SEMANTIC Q&A)
                    1 -> {
                        item {
                            Card(shape = RoundedCornerShape(16.dp)) {
                                Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = if (s.isArabic) "اسأل أي سؤال عن محتوى الملف (عربي أو إنجليزي)" else "Ask Any Question About This PDF (EN / AR)",
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
                                                val res = OfflineAiPdfEngine.askQuestion(pagesText, questionInput.trim())
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

                    // TAB 2: SMART INSIGHTS & PII PRIVACY SCANNER
                    2 -> {
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

                    // TAB 3: AUTO OUTLINE & SMART BOOKMARKS
                    3 -> {
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
