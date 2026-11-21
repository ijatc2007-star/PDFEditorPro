package com.ibrahimjaber.pdfpro.ui.screens

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ibrahimjaber.pdfpro.PdfProApp
import com.ibrahimjaber.pdfpro.data.PageOperationSpec
import com.ibrahimjaber.pdfpro.data.PdfDocumentItem
import com.ibrahimjaber.pdfpro.engine.FileHelper
import com.ibrahimjaber.pdfpro.ui.theme.BrandBlue
import com.ibrahimjaber.pdfpro.ui.theme.BrandCrimson
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageOrganizerScreen(
    app: PdfProApp,
    filePath: String,
    onBack: () -> Unit,
    onOpenSavedPdf: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pdfFile = remember(filePath) { File(filePath) }

    val pageSpecs = remember { mutableStateListOf<PageOperationSpec>() }
    val thumbnails = remember { mutableStateMapOf<Int, Bitmap>() }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(filePath) {
        isLoading = true
        val info = app.pdfEngine.inspectDocument(pdfFile)
        pageSpecs.clear()
        for (i in 0 until info.pageCount) {
            pageSpecs.add(PageOperationSpec(sourcePageIndex = i))
        }
        isLoading = false
        for (i in 0 until info.pageCount) {
            val bmp = app.renderManager.renderSingleThumbnail(pdfFile, i, widthPx = 280)
            if (bmp != null) {
                thumbnails[i] = bmp
            }
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
                        Text("Page Organizer", style = MaterialTheme.typography.titleMedium)
                        Text("${pageSpecs.size} pages • ${pdfFile.name}", fontSize = 11.5.sp)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        pageSpecs.add(PageOperationSpec(sourcePageIndex = null))
                        Toast.makeText(context, "Blank A4 page added at end", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Blank Page")
                    }
                    Button(
                        enabled = pageSpecs.isNotEmpty() && !isSaving,
                        onClick = {
                            scope.launch {
                                isSaving = true
                                runCatching {
                                    val outFile = FileHelper.uniqueFile(
                                        FileHelper.pdfsDir(context),
                                        "${pdfFile.nameWithoutExtension}_organized.pdf"
                                    )
                                    app.pdfEngine.organizePages(pdfFile, outFile, pageSpecs.toList())
                                    val info = app.pdfEngine.inspectDocument(outFile)
                                    app.repository.upsertDocument(
                                        PdfDocumentItem(
                                            name = outFile.name,
                                            path = outFile.absolutePath,
                                            sizeBytes = info.sizeBytes,
                                            pageCount = info.pageCount
                                        )
                                    )
                                    Toast.makeText(context, "Saved ${outFile.name}", Toast.LENGTH_SHORT).show()
                                    onOpenSavedPdf(outFile.absolutePath)
                                }.onFailure {
                                    Toast.makeText(context, "Error: ${it.message}", Toast.LENGTH_LONG).show()
                                }
                                isSaving = false
                            }
                        },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.Save, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Save")
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading || isSaving) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(8.dp))
                    Text(if (isSaving) "Building organized PDF..." else "Loading page thumbnails...")
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                itemsIndexed(pageSpecs, key = { _, item -> item.uid }) { index, spec ->
                    val thumb = spec.sourcePageIndex?.let { thumbnails[it] }
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(0.72f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White),
                                contentAlignment = Alignment.Center
                            ) {
                                if (spec.sourcePageIndex == null) {
                                    Text("Blank A4 Page", color = Color.Gray, fontSize = 12.sp)
                                } else if (thumb != null) {
                                    Image(
                                        bitmap = thumb.asImageBitmap(),
                                        contentDescription = "Page ${index + 1}",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer(rotationZ = spec.extraRotationDeg.toFloat())
                                    )
                                } else {
                                    CircularProgressIndicator(modifier = Modifier.size(22.dp))
                                }
                            }

                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Page ${index + 1}" +
                                    if (spec.extraRotationDeg != 0) " (${spec.extraRotationDeg}°)" else "",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.5.sp
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                IconButton(
                                    onClick = {
                                        if (index > 0) {
                                            val item = pageSpecs.removeAt(index)
                                            pageSpecs.add(index - 1, item)
                                        }
                                    },
                                    enabled = index > 0,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.ChevronLeft, contentDescription = "Move earlier", modifier = Modifier.size(18.dp))
                                }
                                IconButton(
                                    onClick = {
                                        pageSpecs[index] = spec.copy(extraRotationDeg = (spec.extraRotationDeg + 90) % 360)
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.RotateRight, contentDescription = "Rotate 90°", tint = BrandBlue, modifier = Modifier.size(18.dp))
                                }
                                IconButton(
                                    onClick = {
                                        pageSpecs.add(index + 1, PageOperationSpec(sourcePageIndex = spec.sourcePageIndex, extraRotationDeg = spec.extraRotationDeg))
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate", modifier = Modifier.size(17.dp))
                                }
                                IconButton(
                                    onClick = {
                                        if (pageSpecs.size > 1) {
                                            pageSpecs.removeAt(index)
                                        } else {
                                            Toast.makeText(context, "PDF must keep at least 1 page", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = BrandCrimson, modifier = Modifier.size(18.dp))
                                }
                                IconButton(
                                    onClick = {
                                        if (index < pageSpecs.lastIndex) {
                                            val item = pageSpecs.removeAt(index)
                                            pageSpecs.add(index + 1, item)
                                        }
                                    },
                                    enabled = index < pageSpecs.lastIndex,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.ChevronRight, contentDescription = "Move later", modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
