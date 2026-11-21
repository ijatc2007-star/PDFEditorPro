package com.ibrahimjaber.pdfpro.ui.screens

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LaptopWindows
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ibrahimjaber.pdfpro.PdfProApp
import com.ibrahimjaber.pdfpro.data.AppBranding
import com.ibrahimjaber.pdfpro.data.AppLanguage
import com.ibrahimjaber.pdfpro.data.LocalStrings
import com.ibrahimjaber.pdfpro.data.PageRangeParser
import com.ibrahimjaber.pdfpro.data.ReadingMode
import com.ibrahimjaber.pdfpro.data.SavedSignature
import com.ibrahimjaber.pdfpro.data.ThemeMode
import com.ibrahimjaber.pdfpro.engine.FileHelper
import com.ibrahimjaber.pdfpro.ui.theme.BrandBlue
import com.ibrahimjaber.pdfpro.ui.theme.BrandCrimson
import com.ibrahimjaber.pdfpro.ui.theme.BrandNavy

@Composable
fun SettingsAboutScreen(app: PdfProApp) {
    val context = LocalContext.current
    val s = LocalStrings.current
    val settings by app.repository.settings.collectAsState()
    val signatures by app.repository.signatures.collectAsState()
    var showSigPad by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Developer & App Credit Hero Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(BrandNavy, Color(0xFF1E3A5F), BrandBlue)
                        )
                    )
                    .padding(20.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Code,
                            contentDescription = null,
                            tint = Color(0xFF7DD3FC),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = s.appName,
                                color = Color.White,
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = "v${AppBranding.APP_VERSION} • Android + Windows PC Edition",
                                color = Color(0xFFCBD5E1),
                                fontSize = 12.5.sp
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Surface(
                        color = Color.White.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "${AppBranding.CREDIT_LINE}  |  ${AppBranding.CREDIT_LINE_AR}",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.5.sp
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = s.copyrightNotice,
                                color = Color(0xFFE0F2FE),
                                fontSize = 12.5.sp
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = Color(0xFF4ADE80),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = s.offlineBadge,
                            color = Color(0xFFDCFCE7),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.LaptopWindows,
                            contentDescription = null,
                            tint = Color(0xFF7DD3FC),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = s.pcEditionNote,
                            color = Color(0xFFE0F2FE),
                            fontSize = 11.5.sp
                        )
                    }
                }
            }
        }

        // Language & Appearance Settings
        item {
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Language, contentDescription = null, tint = BrandCrimson)
                        Spacer(Modifier.width(8.dp))
                        Text(s.languageLabel, style = MaterialTheme.typography.titleMedium)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AppLanguage.entries.forEach { lang ->
                            FilterChip(
                                selected = settings.language == lang,
                                onClick = {
                                    app.repository.updateSettings { it.copy(language = lang) }
                                },
                                label = { Text(lang.displayName, fontWeight = FontWeight.SemiBold) }
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Palette, contentDescription = null, tint = BrandBlue)
                        Spacer(Modifier.width(8.dp))
                        Text(s.settingsAppearance, style = MaterialTheme.typography.titleMedium)
                    }

                    Text(s.appThemeLabel, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = settings.themeMode == mode,
                                onClick = {
                                    app.repository.updateSettings { it.copy(themeMode = mode) }
                                },
                                label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) }
                            )
                        }
                    }

                    Text(s.defaultReadingMode, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReadingMode.entries.forEach { mode ->
                            FilterChip(
                                selected = settings.defaultReadingMode == mode,
                                onClick = {
                                    app.repository.updateSettings { it.copy(defaultReadingMode = mode) }
                                },
                                label = { Text(if (s.isArabic) mode.labelAr else mode.label) }
                            )
                        }
                    }
                }
            }
        }

        // Saved Signatures Manager
        item {
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Draw, contentDescription = null, tint = BrandCrimson)
                            Spacer(Modifier.width(8.dp))
                            Text("${s.savedSignatures} (${signatures.size})", style = MaterialTheme.typography.titleMedium)
                        }
                        Button(onClick = { showSigPad = true }) {
                            Text(if (s.isArabic) "جديد" else "New")
                        }
                    }
                }
            }
        }

        items(signatures, key = { it.id }) { sig ->
            val bmp = remember(sig.imagePath) { BitmapFactory.decodeFile(sig.imagePath) }
            Card(shape = RoundedCornerShape(14.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = sig.name,
                            modifier = Modifier
                                .width(110.dp)
                                .height(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White)
                                .padding(4.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(sig.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            FileHelper.formatDate(sig.createdAt),
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { app.repository.deleteSignature(sig.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = s.delete, tint = BrandCrimson)
                    }
                }
            }
        }

        // Offline Storage & Privacy Card
        item {
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF059669))
                        Spacer(Modifier.width(8.dp))
                        Text(s.privacyTitle, style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        text = s.privacyBody,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = {
                            val freed = FileHelper.clearTempCache(context)
                            Toast.makeText(
                                context,
                                "Cleared ${PageRangeParser.formatFileSize(freed)}",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(s.clearCache)
                    }
                }
            }
        }
    }

    if (showSigPad) {
        SignaturePadDialog(
            onDismiss = { showSigPad = false },
            onSaveSignature = { filePath, label ->
                app.repository.addSignature(SavedSignature(name = label, imagePath = filePath))
                showSigPad = false
                Toast.makeText(context, "Signature saved offline!", Toast.LENGTH_SHORT).show()
            }
        )
    }
}
