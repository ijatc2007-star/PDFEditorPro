package com.ibrahimjaber.pdfpro.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrandingWatermark
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ibrahimjaber.pdfpro.data.LocalStrings
import com.ibrahimjaber.pdfpro.ui.theme.BrandAmber
import com.ibrahimjaber.pdfpro.ui.theme.BrandBlue
import com.ibrahimjaber.pdfpro.ui.theme.BrandCrimson
import com.ibrahimjaber.pdfpro.ui.theme.BrandEmerald
import com.ibrahimjaber.pdfpro.ui.theme.BrandPurple
import com.ibrahimjaber.pdfpro.ui.theme.BrandTeal

data class ProToolCardItem(
    val id: String,
    val title: String,
    val titleAr: String,
    val subtitle: String,
    val subtitleAr: String,
    val icon: ImageVector,
    val accent: Color
)

val ALL_PRO_TOOLS = listOf(
    ProToolCardItem("converter", "Format Converter", "محول الصيغ الشامل", "PDF ↔ Word, Excel, CSV, PPTX", "تحويل PDF ↔ وورد، إكسل، CSV، صور", Icons.Default.SwapHoriz, BrandBlue),
    ProToolCardItem("merge", "Merge PDFs", "دمج ملفات PDF", "Combine multiple PDFs into one", "دمج عدة ملفات PDF في ملف واحد", Icons.Default.CallMerge, BrandCrimson),
    ProToolCardItem("split", "Split PDF", "تقسيم PDF", "Extract ranges or single pages", "استخراج صفحات أو تقسيم كل صفحة", Icons.Default.CallSplit, BrandBlue),
    ProToolCardItem("organize", "Organize Pages", "ترتيب الصفحات", "Reorder, rotate, delete & insert", "إعادة ترتيب وتدوير وحذف وإضافة", Icons.Default.GridView, BrandPurple),
    ProToolCardItem("images_to_pdf", "Images to PDF", "صور إلى PDF", "Convert photos to crisp A4 PDF", "تحويل الصور إلى ملف PDF عالي الدقة", Icons.Default.Image, BrandEmerald),
    ProToolCardItem("pdf_to_images", "PDF to Images", "PDF إلى صور", "Export pages as PNG or JPEG", "تصدير الصفحات كصور PNG أو JPEG", Icons.Default.Collections, BrandTeal),
    ProToolCardItem("compress", "Compress PDF", "ضغط PDF", "Reduce PDF file size offline", "تقليل حجم ملف PDF بدون إنترنت", Icons.Default.Compress, BrandAmber),
    ProToolCardItem("grayscale", "Grayscale / B&W", "تحويل أبيض وأسود", "Ink-saving grayscale PDF optimizer", "تحويل للرمادي لتوفير الحبر وتصغير الحجم", Icons.Default.Contrast, BrandPurple),
    ProToolCardItem("protect", "Protect PDF", "تشفير وقفل PDF", "Encrypt with AES-128 password", "حماية بكلمة مرور AES-128", Icons.Default.Lock, BrandCrimson),
    ProToolCardItem("unlock", "Unlock PDF", "فك قفل PDF", "Remove PDF password lock", "إزالة كلمة المرور عن الملف", Icons.Default.LockOpen, BrandEmerald),
    ProToolCardItem("watermark", "Watermark PDF", "علامة مائية", "Diagonal or tiled text stamps", "إضافة علامة مائية عربية أو إنجليزية", Icons.Default.BrandingWatermark, BrandBlue),
    ProToolCardItem("page_numbers", "Page Numbers", "ترقيم الصفحات", "Stamp page numbers cleanly", "إضافة أرقام الصفحات تلقائياً", Icons.Default.FormatListNumbered, BrandPurple),
    ProToolCardItem("header_bates", "Headers & Bates", "ترويسة وترقيم Bates", "Corporate headers & legal Bates IDs", "ترويسة وتذييل وترقيم قانوني Bates", Icons.Default.ViewAgenda, BrandTeal),
    ProToolCardItem("compare", "Compare PDFs", "مقارنة ملفي PDF", "Page-by-page diff report offline", "مقارنة الفروقات بين ملفين صفحة بصفحة", Icons.Default.Compare, BrandCrimson),
    ProToolCardItem("extract_text", "Extract Text", "استخراج النصوص", "Copy or export PDF text to TXT", "استخراج ونسخ وحفظ نصوص PDF", Icons.Default.TextSnippet, BrandTeal),
    ProToolCardItem("metadata", "Metadata Editor", "محرر خصائص الملف", "Edit title, author & keywords", "تعديل العنوان والمؤلف والبيانات", Icons.Default.Info, BrandAmber)
)

@Composable
fun ToolsHubScreen(
    onSelectTool: (String) -> Unit
) {
    val s = LocalStrings.current

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item(span = { GridItemSpan(2) }) {
            Column(modifier = Modifier.padding(bottom = 4.dp)) {
                Text(
                    text = s.proToolsTitle,
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = s.proToolsSubtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(ALL_PRO_TOOLS, key = { it.id }) { tool ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectTool(tool.id) },
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(tool.accent.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = tool.icon,
                            contentDescription = if (s.isArabic) tool.titleAr else tool.title,
                            tint = tool.accent,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = if (s.isArabic) tool.titleAr else tool.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = if (s.isArabic) tool.subtitleAr else tool.subtitle,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
