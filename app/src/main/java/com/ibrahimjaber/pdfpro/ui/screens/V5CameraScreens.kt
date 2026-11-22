package com.ibrahimjaber.pdfpro.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.util.Size
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ibrahimjaber.pdfpro.PdfProApp
import com.ibrahimjaber.pdfpro.data.LocalStrings
import com.ibrahimjaber.pdfpro.data.PageTemplate
import com.ibrahimjaber.pdfpro.data.PdfDocumentItem
import com.ibrahimjaber.pdfpro.engine.CamScannerEngine
import com.ibrahimjaber.pdfpro.engine.ConverterCodec
import com.ibrahimjaber.pdfpro.engine.FileHelper
import com.ibrahimjaber.pdfpro.engine.OcrLanguageMode
import com.ibrahimjaber.pdfpro.engine.OfflinePackStatus
import com.ibrahimjaber.pdfpro.engine.ScanFilter
import com.ibrahimjaber.pdfpro.engine.ScannedPageItem
import com.ibrahimjaber.pdfpro.engine.TranslationDirection
import com.ibrahimjaber.pdfpro.ui.theme.BrandBlue
import com.ibrahimjaber.pdfpro.ui.theme.BrandCrimson
import com.ibrahimjaber.pdfpro.ui.theme.BrandEmerald
import com.ibrahimjaber.pdfpro.ui.theme.BrandNavy
import com.ibrahimjaber.pdfpro.ui.theme.BrandPurple
import com.ibrahimjaber.pdfpro.ui.theme.BrandTeal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

private fun fastDocumentResolutionSelector(): ResolutionSelector {
    return ResolutionSelector.Builder()
        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
        .setResolutionStrategy(
            ResolutionStrategy(
                Size(1600, 1200),
                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
            )
        )
        .build()
}

// =============================================================================
// SCREEN 1: CAMSCANNER PRO v5.2 (FAST IN-APP CAMERAX + TAP-TO-FOCUS + MANUAL CROP + SMOOTH FILTERS)
// =============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CamScannerScreen(
    app: PdfProApp,
    onBack: () -> Unit,
    onOpenPdf: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val s = LocalStrings.current

    val scannedPages = remember { mutableStateListOf<ScannedPageItem>() }
    var selectedIndex by remember { mutableIntStateOf(0) }
    var activeFilter by remember { mutableStateOf(ScanFilter.MAGIC_COLOR) }
    var autoCropEnabled by remember { mutableStateOf(false) } // Safe OFF by default; never chops off text accidentally!
    var showCropControls by remember { mutableStateOf(false) }
    var cropTop by remember { mutableFloatStateOf(0f) }
    var cropBottom by remember { mutableFloatStateOf(1f) }
    var cropLeft by remember { mutableFloatStateOf(0f) }
    var cropRight by remember { mutableFloatStateOf(1f) }

    var makeSearchableOcr by remember { mutableStateOf(true) }
    var ocrLang by remember { mutableStateOf(OcrLanguageMode.ARABIC_AND_ENGLISH) }

    var showInAppCamera by remember { mutableStateOf(false) }
    var batchScanMode by remember { mutableStateOf(false) } // False = Single Mode (instant review), True = Batch Multi-Page
    var flashOn by remember { mutableStateOf(false) }
    var isCapturingPhoto by remember { mutableStateOf(false) }
    var shutterFlash by remember { mutableStateOf(false) }
    var cameraErrorMsg by remember { mutableStateOf<String?>(null) }
    var scanCameraRef by remember { mutableStateOf<Camera?>(null) }
    var scanImageCaptureRef by remember { mutableStateOf<ImageCapture?>(null) }
    var lastCapturedThumb by remember { mutableStateOf<Bitmap?>(null) }
    val scanCameraExecutor = remember { Executors.newSingleThreadExecutor() }

    var isProcessing by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }
    var progressRatio by remember { mutableFloatStateOf(0f) }
    var translatedScanResult by remember { mutableStateOf("") }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }

    DisposableEffect(Unit) {
        onDispose { scanCameraExecutor.shutdown() }
    }

    BackHandler(enabled = showInAppCamera) {
        showInAppCamera = false
    }

    fun refreshPreview(item: ScannedPageItem?) {
        scope.launch {
            val bmp = withContext(Dispatchers.IO) {
                item?.let { CamScannerEngine.decodeScaledBitmap(it.enhancedImagePath, maxDimension = 1200) }
            }
            previewBitmap = bmp
        }
    }

    fun ingestRawImageFile(rawFile: File, closeCameraAfter: Boolean) {
        scope.launch {
            isProcessing = true
            statusText = if (s.isArabic) "جاري تحسين المسح الضوئي (CamScanner)..." else "Enhancing scanned page (CamScanner)..."
            runCatching {
                val outEnhanced = File(context.cacheDir, "camscan_enh_${System.currentTimeMillis()}_${scannedPages.size}.jpg")
                CamScannerEngine.processScanPage(
                    rawFilePath = rawFile.absolutePath,
                    outputFile = outEnhanced,
                    filter = activeFilter,
                    autoCrop = autoCropEnabled,
                    rotationDegrees = 0
                )
                val newItem = ScannedPageItem(
                    rawImagePath = rawFile.absolutePath,
                    enhancedImagePath = outEnhanced.absolutePath,
                    filter = activeFilter,
                    rotationDegrees = 0,
                    autoCropped = autoCropEnabled
                )
                val thumb = withContext(Dispatchers.IO) {
                    CamScannerEngine.decodeScaledBitmap(outEnhanced.absolutePath, maxDimension = 260)
                }
                lastCapturedThumb = thumb
                scannedPages.add(newItem)
                selectedIndex = scannedPages.lastIndex
                refreshPreview(newItem)
                if (closeCameraAfter) {
                    showInAppCamera = false
                }
            }.onFailure { err ->
                Toast.makeText(context, err.message ?: "Scan processing failed", Toast.LENGTH_LONG).show()
            }
            isProcessing = false
            statusText = ""
        }
    }

    fun reprocessSelectedPage(
        newFilter: ScanFilter,
        newAutoCrop: Boolean,
        newRotation: Int,
        cLeft: Float = cropLeft,
        cTop: Float = cropTop,
        cRight: Float = cropRight,
        cBottom: Float = cropBottom
    ) {
        val idx = selectedIndex
        val current = scannedPages.getOrNull(idx) ?: return
        scope.launch {
            isProcessing = true
            statusText = if (s.isArabic) "تطبيق التحسينات..." else "Applying CamScanner enhancements..."
            runCatching {
                val outEnhanced = File(context.cacheDir, "camscan_enh_${System.currentTimeMillis()}_$idx.jpg")
                CamScannerEngine.processScanPage(
                    rawFilePath = current.rawImagePath,
                    outputFile = outEnhanced,
                    filter = newFilter,
                    autoCrop = newAutoCrop,
                    rotationDegrees = newRotation,
                    cropLeft = cLeft,
                    cropTop = cTop,
                    cropRight = cRight,
                    cropBottom = cBottom
                )
                val updated = current.copy(
                    enhancedImagePath = outEnhanced.absolutePath,
                    filter = newFilter,
                    autoCropped = newAutoCrop,
                    rotationDegrees = newRotation,
                    cropLeft = cLeft,
                    cropTop = cTop,
                    cropRight = cRight,
                    cropBottom = cBottom
                )
                scannedPages[idx] = updated
                refreshPreview(updated)
            }.onFailure { err ->
                Toast.makeText(context, err.message ?: "Filter error", Toast.LENGTH_SHORT).show()
            }
            isProcessing = false
            statusText = ""
        }
    }

    val cameraPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            cameraErrorMsg = null
            showInAppCamera = true
        } else {
            Toast.makeText(
                context,
                if (s.isArabic) "يرجى السماح بإذن الكاميرا أو اختيار صور من المعرض" else "Camera permission denied — allow Camera in Settings or pick photos from Gallery.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val galleryMultiLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            scope.launch {
                isProcessing = true
                for ((i, uri) in uris.withIndex()) {
                    statusText = if (s.isArabic) "تحسين الصفحة ${i + 1} / ${uris.size}..." else "Enhancing page ${i + 1} / ${uris.size}..."
                    val copied = withContext(Dispatchers.IO) { FileHelper.copyAnyFileFromUriToCache(context, uri) } ?: continue
                    runCatching {
                        val outEnhanced = File(context.cacheDir, "camscan_enh_${System.currentTimeMillis()}_$i.jpg")
                        CamScannerEngine.processScanPage(
                            rawFilePath = copied.absolutePath,
                            outputFile = outEnhanced,
                            filter = activeFilter,
                            autoCrop = autoCropEnabled,
                            rotationDegrees = 0
                        )
                        scannedPages.add(
                            ScannedPageItem(
                                rawImagePath = copied.absolutePath,
                                enhancedImagePath = outEnhanced.absolutePath,
                                filter = activeFilter,
                                autoCropped = autoCropEnabled
                            )
                        )
                    }
                }
                if (scannedPages.isNotEmpty()) {
                    selectedIndex = scannedPages.lastIndex
                    refreshPreview(scannedPages[selectedIndex])
                }
                isProcessing = false
                statusText = ""
            }
        }
    }

    LaunchedEffect(selectedIndex, scannedPages.size) {
        val item = scannedPages.getOrNull(selectedIndex)
        if (item != null) {
            activeFilter = item.filter
            autoCropEnabled = item.autoCropped
            cropLeft = item.cropLeft
            cropTop = item.cropTop
            cropRight = item.cropRight
            cropBottom = item.cropBottom
            refreshPreview(item)
        } else {
            previewBitmap = null
        }
    }

    // FULL-SCREEN BUILT-IN CAMERAX DOCUMENT SCANNER (Tap-to-Focus + Fast 1600x1200 Capture + Single/Batch Mode)
    if (showInAppCamera) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val pv = PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    }
                    // Tap-to-Focus support on the live viewfinder
                    pv.setOnTouchListener { view, event ->
                        if (event.action == MotionEvent.ACTION_UP) {
                            val point = pv.meteringPointFactory.createPoint(event.x, event.y)
                            val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                                .setAutoCancelDuration(3, TimeUnit.SECONDS)
                                .build()
                            scanCameraRef?.cameraControl?.startFocusAndMetering(action)
                            view.performClick()
                        }
                        true
                    }

                    val providerFuture = ProcessCameraProvider.getInstance(ctx)
                    providerFuture.addListener({
                        runCatching {
                            val provider = providerFuture.get()
                            val resSel = fastDocumentResolutionSelector()
                            val preview = Preview.Builder()
                                .setResolutionSelector(resSel)
                                .build()
                                .also { it.surfaceProvider = pv.surfaceProvider }

                            val capture = ImageCapture.Builder()
                                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                .setResolutionSelector(resSel)
                                .setJpegQuality(90)
                                .build()
                            scanImageCaptureRef = capture

                            provider.unbindAll()
                            val selector = if (provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                                CameraSelector.DEFAULT_BACK_CAMERA
                            } else {
                                CameraSelector.DEFAULT_FRONT_CAMERA
                            }
                            scanCameraRef = provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
                            cameraErrorMsg = null
                        }.onFailure { err ->
                            cameraErrorMsg = err.localizedMessage ?: "Unable to start camera"
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    pv
                }
            )

            // Brief white shutter flash animation on capture
            if (shutterFlash) {
                Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.55f)))
            }

            // Crisp Green Document Guide Frame
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.88f)
                    .height(430.dp)
                    .border(2.5.dp, Color(0xFF22C55E), RoundedCornerShape(14.dp))
            ) {
                Text(
                    text = if (s.isArabic) "اضغط على الشاشة للتركيز ثم التقط المستند" else "Tap screen to focus, then tap Capture",
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 10.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            // Top Camera HUD (Close, Single/Batch Toggle, Flash)
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.62f))
                    .padding(horizontal = 10.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { showInAppCamera = false }) {
                    Icon(Icons.Default.Close, contentDescription = "Close Camera", tint = Color.White)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(
                        selected = !batchScanMode,
                        onClick = { batchScanMode = false },
                        label = { Text(if (s.isArabic) "صفحة واحدة" else "Single", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = batchScanMode,
                        onClick = { batchScanMode = true },
                        label = { Text(if (s.isArabic) "دفعة (${scannedPages.size})" else "Batch (${scannedPages.size})", fontSize = 11.sp) }
                    )
                }
                IconButton(onClick = {
                    flashOn = !flashOn
                    scanCameraRef?.cameraControl?.enableTorch(flashOn)
                }) {
                    Icon(
                        if (flashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = "Flash",
                        tint = if (flashOn) Color(0xFFFDE047) else Color.White
                    )
                }
            }

            cameraErrorMsg?.let { err ->
                Surface(
                    color = BrandCrimson,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.align(Alignment.Center).padding(24.dp)
                ) {
                    Text(err, color = Color.White, modifier = Modifier.padding(14.dp), fontSize = 13.sp)
                }
            }

            // Bottom Shutter, Last-Shot Thumbnail & Done Bar
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.78f))
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Live thumbnail of last captured page (tap to review) OR Gallery button
                if (lastCapturedThumb != null && scannedPages.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(2.dp, Color(0xFF22C55E), RoundedCornerShape(10.dp))
                            .clickable { showInAppCamera = false }
                    ) {
                        Image(
                            bitmap = lastCapturedThumb!!.asImageBitmap(),
                            contentDescription = "Last Scan",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Surface(
                            color = BrandBlue,
                            shape = CircleShape,
                            modifier = Modifier.align(Alignment.TopEnd).padding(2.dp)
                        ) {
                            Text("${scannedPages.size}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                        }
                    }
                } else {
                    OutlinedButton(onClick = {
                        showInAppCamera = false
                        galleryMultiLauncher.launch(arrayOf("image/*"))
                    }) {
                        Icon(Icons.Default.PhotoLibrary, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (s.isArabic) "المعرض" else "Gallery", color = Color.White, fontSize = 12.sp)
                    }
                }

                // Big Green Capture Shutter Button
                Button(
                    enabled = !isCapturingPhoto,
                    onClick = {
                        val cap = scanImageCaptureRef
                        if (cap == null) {
                            Toast.makeText(context, if (s.isArabic) "الكاميرا قيد التجهيز..." else "Camera initializing — wait 1s", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isCapturingPhoto = true
                        scope.launch {
                            shutterFlash = true
                            delay(140L)
                            shutterFlash = false
                        }
                        val outFile = File(context.cacheDir, "camscan_raw_${System.currentTimeMillis()}.jpg")
                        val opts = ImageCapture.OutputFileOptions.Builder(outFile).build()
                        cap.takePicture(opts, scanCameraExecutor, object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                scope.launch(Dispatchers.Main) {
                                    isCapturingPhoto = false
                                    if (outFile.exists() && outFile.length() > 0L) {
                                        ingestRawImageFile(outFile, closeCameraAfter = !batchScanMode)
                                    } else {
                                        Toast.makeText(context, "Captured image was empty — please retry", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }

                            override fun onError(exception: ImageCaptureException) {
                                scope.launch(Dispatchers.Main) {
                                    isCapturingPhoto = false
                                    Toast.makeText(context, "Capture failed: ${exception.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        })
                    },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald),
                    modifier = Modifier.size(74.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    if (isCapturingPhoto) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                    } else {
                        Icon(Icons.Default.CameraAlt, contentDescription = "Capture", tint = Color.White, modifier = Modifier.size(34.dp))
                    }
                }

                Button(
                    enabled = scannedPages.isNotEmpty(),
                    onClick = { showInAppCamera = false },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
                ) {
                    Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (s.isArabic) "تم (${scannedPages.size})" else "Done (${scannedPages.size})", fontSize = 12.sp)
                }
            }
        }
        return
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
                    Text(if (s.isArabic) "ماسح CamScanner الاحترافي" else "CamScanner Pro (Offline)")
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
            // Capture Controls Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = BrandNavy),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = if (s.isArabic) "ماسح CamScanner الاحترافي (كاميرا مدمجة سريعة)" else "CamScanner Pro — Fast Built-In Scanner",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.5.sp
                        )
                        Text(
                            text = if (s.isArabic) "تركيز باللمس • إزالة الظلال بسلاسة • قص يدوي أو تلقائي • فلاتر سحرية • PDF قابل للبحث OCR" else "Tap-to-Focus • Smooth Shadow Removal • Manual/Auto Crop • Magic Color • Searchable OCR PDF",
                            color = Color(0xFFBAE6FD),
                            fontSize = 12.sp
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                                    if (hasPerm) {
                                        cameraErrorMsg = null
                                        showInAppCamera = true
                                    } else {
                                        cameraPermLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald)
                            ) {
                                Icon(Icons.Default.AddAPhoto, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (s.isArabic) "تصوير مستند" else "Scan Camera")
                            }
                            OutlinedButton(
                                onClick = { galleryMultiLauncher.launch(arrayOf("image/*")) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.PhotoLibrary, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (s.isArabic) "من المعرض" else "From Gallery", color = Color.White)
                            }
                        }
                    }
                }
            }

            if (isProcessing) {
                item {
                    Card(shape = RoundedCornerShape(14.dp)) {
                        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(statusText.ifBlank { "Processing..." }, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            if (progressRatio > 0f) {
                                LinearProgressIndicator(progress = { progressRatio }, modifier = Modifier.fillMaxWidth())
                            } else {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }

            // Active Scanned Page Review, Filters & Manual Crop
            if (scannedPages.isNotEmpty()) {
                val activeItem = scannedPages.getOrNull(selectedIndex)
                item {
                    Card(shape = RoundedCornerShape(16.dp)) {
                        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (s.isArabic) "الصفحة ${selectedIndex + 1} من ${scannedPages.size}" else "Page ${selectedIndex + 1} of ${scannedPages.size}",
                                    fontWeight = FontWeight.Bold
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    IconButton(onClick = { showCropControls = !showCropControls }) {
                                        Icon(Icons.Default.Crop, contentDescription = "Crop", tint = if (showCropControls) BrandBlue else MaterialTheme.colorScheme.onSurface)
                                    }
                                    IconButton(onClick = {
                                        val nextRot = ((activeItem?.rotationDegrees ?: 0) + 90) % 360
                                        reprocessSelectedPage(activeFilter, autoCropEnabled, nextRot)
                                    }) {
                                        Icon(Icons.Default.RotateRight, contentDescription = "Rotate 90")
                                    }
                                    IconButton(onClick = {
                                        if (selectedIndex in scannedPages.indices) {
                                            scannedPages.removeAt(selectedIndex)
                                            selectedIndex = (selectedIndex - 1).coerceAtLeast(0)
                                        }
                                    }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete Page", tint = BrandCrimson)
                                    }
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(360.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF0F172A)),
                                contentAlignment = Alignment.Center
                            ) {
                                previewBitmap?.let { bmp ->
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = "Scanned Page Preview",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } ?: CircularProgressIndicator(color = Color.White)
                            }

                            // 5 Smooth CamScanner Pro Filters
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                itemsIndexed(ScanFilter.entries) { _, filter ->
                                    FilterChip(
                                        selected = activeFilter == filter,
                                        onClick = {
                                            activeFilter = filter
                                            reprocessSelectedPage(filter, autoCropEnabled, activeItem?.rotationDegrees ?: 0)
                                        },
                                        label = { Text(if (s.isArabic) filter.labelAr else filter.labelEn, fontSize = 11.5.sp) }
                                    )
                                }
                            }

                            // Precision Manual Crop Sliders + Safe Auto-Trim
                            if (showCropControls) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Text(if (s.isArabic) "قص حواف المستند بدقة" else "Precision Margin Crop", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            TextButton(onClick = {
                                                cropTop = 0f; cropBottom = 1f; cropLeft = 0f; cropRight = 1f; autoCropEnabled = false
                                                reprocessSelectedPage(activeFilter, false, activeItem?.rotationDegrees ?: 0, 0f, 0f, 1f, 1f)
                                            }) { Text(if (s.isArabic) "إعادة ضبط" else "Reset", fontSize = 11.5.sp) }
                                        }
                                        Text("${if (s.isArabic) "قص من الأعلى" else "Trim Top"}: ${(cropTop * 100).toInt()}%", fontSize = 11.5.sp)
                                        Slider(value = cropTop, onValueChange = { cropTop = it }, valueRange = 0f..0.40f)
                                        Text("${if (s.isArabic) "قص من الأسفل" else "Trim Bottom"}: ${((1f - cropBottom) * 100).toInt()}%", fontSize = 11.5.sp)
                                        Slider(value = 1f - cropBottom, onValueChange = { cropBottom = 1f - it }, valueRange = 0f..0.40f)
                                        Text("${if (s.isArabic) "قص من اليسار" else "Trim Left"}: ${(cropLeft * 100).toInt()}%", fontSize = 11.5.sp)
                                        Slider(value = cropLeft, onValueChange = { cropLeft = it }, valueRange = 0f..0.40f)
                                        Text("${if (s.isArabic) "قص من اليمين" else "Trim Right"}: ${((1f - cropRight) * 100).toInt()}%", fontSize = 11.5.sp)
                                        Slider(value = 1f - cropRight, onValueChange = { cropRight = 1f - it }, valueRange = 0f..0.40f)
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(
                                                onClick = {
                                                    autoCropEnabled = false
                                                    reprocessSelectedPage(activeFilter, false, activeItem?.rotationDegrees ?: 0, cropLeft, cropTop, cropRight, cropBottom)
                                                },
                                                modifier = Modifier.weight(1f)
                                            ) { Text(if (s.isArabic) "تطبيق القص" else "Apply Crop", fontSize = 12.sp) }
                                            OutlinedButton(
                                                onClick = {
                                                    autoCropEnabled = true
                                                    reprocessSelectedPage(activeFilter, true, activeItem?.rotationDegrees ?: 0)
                                                },
                                                modifier = Modifier.weight(1f)
                                            ) { Text(if (s.isArabic) "قص تلقائي" else "Auto-Trim", fontSize = 12.sp) }
                                        }
                                    }
                                }
                            }

                            // Multi-page switcher strip + Add Page button
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                itemsIndexed(scannedPages) { idx, _ ->
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (idx == selectedIndex) BrandBlue else MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.clickable { selectedIndex = idx }
                                    ) {
                                        Text(
                                            text = if (s.isArabic) "صفحة ${idx + 1}" else "Page ${idx + 1}",
                                            color = if (idx == selectedIndex) Color.White else MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                                item {
                                    OutlinedButton(
                                        onClick = {
                                            val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                                            if (hasPerm) showInAppCamera = true
                                            else cameraPermLauncher.launch(Manifest.permission.CAMERA)
                                        },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(if (s.isArabic) "+ إضافة صفحة" else "+ Add Page", fontSize = 11.5.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                // Export to PDF + OCR & Instant Translate
                item {
                    Card(shape = RoundedCornerShape(16.dp)) {
                        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(if (s.isArabic) "تفعيل OCR عربي + إنجليزي (PDF قابل للبحث)" else "Embed Offline AR+EN OCR Searchable Layer", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text(if (s.isArabic) "يستخدم نماذج Tesseract المدمجة بدون إنترنت" else "Uses bundled Tesseract 5.5 offline models", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(checked = makeSearchableOcr, onCheckedChange = { makeSearchableOcr = it })
                            }

                            if (makeSearchableOcr) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OcrLanguageMode.entries.forEach { m ->
                                        FilterChip(
                                            selected = ocrLang == m,
                                            onClick = { ocrLang = m },
                                            label = { Text(if (s.isArabic) m.labelAr else m.labelEn, fontSize = 11.sp) }
                                        )
                                    }
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    enabled = !isProcessing,
                                    onClick = {
                                        scope.launch {
                                            isProcessing = true
                                            progressRatio = 0.05f
                                            statusText = if (s.isArabic) "جاري إنشاء ملف PDF الممسوح..." else "Building multi-page CamScanner PDF..."
                                            runCatching {
                                                val outPdf = FileHelper.uniqueFile(
                                                    FileHelper.pdfsDir(context),
                                                    "CamScan_${System.currentTimeMillis() / 1000}.pdf"
                                                )
                                                val (savedPdf, _) = CamScannerEngine.exportPagesToPdf(
                                                    pages = scannedPages.toList(),
                                                    outputPdf = outPdf,
                                                    runOfflineOcr = makeSearchableOcr,
                                                    ocrEngine = app.ocrEngine,
                                                    ocrLang = ocrLang,
                                                    onProgress = { cur, tot ->
                                                        progressRatio = cur.toFloat() / tot.coerceAtLeast(1)
                                                        statusText = if (s.isArabic) "معالجة الصفحة $cur من $tot..." else "Processing page $cur of $tot..."
                                                    }
                                                )
                                                val info = app.pdfEngine.inspectDocument(savedPdf)
                                                app.repository.upsertDocument(
                                                    PdfDocumentItem(
                                                        name = savedPdf.name,
                                                        path = savedPdf.absolutePath,
                                                        sizeBytes = info.sizeBytes,
                                                        pageCount = info.pageCount
                                                    )
                                                )
                                                Toast.makeText(context, "Saved ${savedPdf.name}", Toast.LENGTH_SHORT).show()
                                                onOpenPdf(savedPdf.absolutePath)
                                            }.onFailure { err ->
                                                Toast.makeText(context, err.message ?: "Export failed", Toast.LENGTH_LONG).show()
                                            }
                                            isProcessing = false
                                            progressRatio = 0f
                                            statusText = ""
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.PictureAsPdf, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (s.isArabic) "حفظ كـ PDF" else "Save PDF")
                                }

                                OutlinedButton(
                                    enabled = !isProcessing,
                                    onClick = {
                                        val active = scannedPages.getOrNull(selectedIndex) ?: return@OutlinedButton
                                        scope.launch {
                                            isProcessing = true
                                            statusText = if (s.isArabic) "قراءة النص وترجمته أوفلاين..." else "Running OCR & Offline Translation..."
                                            runCatching {
                                                val bmp = withContext(Dispatchers.IO) {
                                                    CamScannerEngine.decodeScaledBitmap(active.enhancedImagePath, 1600)
                                                }
                                                if (bmp != null) {
                                                    val ocrText = app.ocrEngine.ocrBitmap(bmp, ocrLang)
                                                    bmp.recycle()
                                                    if (ocrText.isBlank()) {
                                                        translatedScanResult = if (s.isArabic) "لم يتم اكتشاف نص واضح في هذه الصفحة." else "No clear text detected on this scanned page."
                                                    } else {
                                                        val res = app.translationManager.translateSmartOffline(
                                                            text = ocrText,
                                                            direction = TranslationDirection.AUTO,
                                                            sourceLabel = "CamScanner Page ${selectedIndex + 1}",
                                                            saveToHistory = true
                                                        )
                                                        translatedScanResult = res.translatedText
                                                    }
                                                }
                                            }.onFailure { err ->
                                                Toast.makeText(context, err.message ?: "Translate error", Toast.LENGTH_LONG).show()
                                            }
                                            isProcessing = false
                                            statusText = ""
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Translate, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (s.isArabic) "ترجمة الصفحة" else "Translate Page")
                                }
                            }

                            if (translatedScanResult.isNotBlank()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(if (s.isArabic) "الترجمة المحفوظة محلياً:" else "Offline Translation (Saved Locally):", fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = BrandTeal)
                                        Text(translatedScanResult, fontSize = 13.sp, lineHeight = 19.sp)
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

// =============================================================================
// SCREEN 2: LIVE INSTANT CAMERA TRANSLATOR (OFFLINE OCR + TRIPLE-TIER AR<->EN)
// =============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveCameraTranslateScreen(
    app: PdfProApp,
    onBack: () -> Unit,
    onOpenPdf: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val s = LocalStrings.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var packStatus by remember { mutableStateOf(OfflinePackStatus(false, 0, 0)) }
    var isDownloadingModels by remember { mutableStateOf(false) }
    var downloadStatus by remember { mutableStateOf("") }
    var cameraError by remember { mutableStateOf<String?>(null) }

    var direction by remember { mutableStateOf(TranslationDirection.AUTO) }
    var liveAutoMode by remember { mutableStateOf(true) }
    var flashEnabled by remember { mutableStateOf(false) }

    var isScanningNow by remember { mutableStateOf(false) }
    var detectedOriginal by remember { mutableStateOf("") }
    var translatedResult by remember { mutableStateOf("") }
    var engineBadge by remember { mutableStateOf("") }

    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var imageCaptureRef by remember { mutableStateOf<ImageCapture?>(null) }

    val analyzerBusy = remember { AtomicBoolean(false) }
    val lastAnalyzeTimeMs = remember { AtomicLong(0L) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) tts = engine
        }
        onDispose {
            runCatching { engine?.stop(); engine?.shutdown() }
            cameraExecutor.shutdown()
        }
    }

    LaunchedEffect(Unit) {
        packStatus = app.translationManager.getOfflinePackStatus()
    }

    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun runOcrAndTranslateBitmap(bmp: Bitmap, saveHistory: Boolean, sourceTag: String) {
        scope.launch {
            isScanningNow = true
            runCatching {
                val ocrMode = when (direction) {
                    TranslationDirection.EN_TO_AR -> OcrLanguageMode.ENGLISH_ONLY
                    TranslationDirection.AR_TO_EN -> OcrLanguageMode.ARABIC_ONLY
                    TranslationDirection.AUTO -> OcrLanguageMode.ARABIC_AND_ENGLISH
                }
                val text = app.ocrEngine.ocrBitmap(bmp, ocrMode)
                if (text.length >= 2) {
                    detectedOriginal = text
                    val tr = app.translationManager.translateSmartOffline(
                        text = text,
                        direction = direction,
                        sourceLabel = sourceTag,
                        saveToHistory = saveHistory
                    )
                    translatedResult = tr.translatedText
                    engineBadge = when {
                        tr.fromLocalCache -> if (s.isArabic) "✓ من الذاكرة المحلية (فوري)" else "✓ Local Cache (Instant)"
                        tr.usedNeuralOfflineModel -> if (s.isArabic) "✓ النموذج العصبي الكامل (أوفلاين)" else "✓ Neural AR+EN Pack (Offline)"
                        tr.usedOnlineSavedLocally -> if (s.isArabic) "✓ ترجمة جملة كاملة (محفوظة محلياً للأوفلاين)" else "✓ Full Sentence (Saved Locally)"
                        tr.usedFullArabEyesDict -> if (s.isArabic) "✓ قاموس ArabEyes (87,423 كلمة أوفلاين)" else "✓ ArabEyes 87k Dictionary (Offline)"
                        else -> if (s.isArabic) "قاموس أوفلاين" else "Offline Dictionary"
                    }
                } else if (saveHistory) {
                    Toast.makeText(context, if (s.isArabic) "لم يتم اكتشاف نص واضح — قرّب الكاميرا وأعد المحاولة" else "No clear text found — move closer & retry", Toast.LENGTH_SHORT).show()
                }
            }.onFailure { err ->
                Toast.makeText(context, err.message ?: "Camera OCR error", Toast.LENGTH_SHORT).show()
            }
            if (!bmp.isRecycled) bmp.recycle()
            isScanningNow = false
            analyzerBusy.set(false)
        }
    }

    val pickGalleryPhoto = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val f = withContext(Dispatchers.IO) { FileHelper.copyAnyFileFromUriToCache(context, uri) }
                if (f != null) {
                    val bmp = withContext(Dispatchers.IO) { CamScannerEngine.decodeScaledBitmap(f.absolutePath, 1500) }
                    if (bmp != null) {
                        runOcrAndTranslateBitmap(bmp, saveHistory = true, sourceTag = "Camera Gallery Photo")
                    }
                }
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
                    Text(if (s.isArabic) "الترجمة الفورية بالكاميرا (أوفلاين)" else "Live Camera Translate (Offline)", maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                actions = {
                    IconButton(onClick = {
                        flashEnabled = !flashEnabled
                        boundCamera?.cameraControl?.enableTorch(flashEnabled)
                    }) {
                        Icon(if (flashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff, contentDescription = "Flash")
                    }
                    IconButton(onClick = { pickGalleryPhoto.launch(arrayOf("image/*")) }) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = "Gallery")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black)
        ) {
            // Top Status Strip: Bundled 87k Dictionary + ML Kit Neural Status + Direction Selector
            Surface(color = BrandNavy, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isDownloadingModels) {
                            Text(
                                text = downloadStatus.ifBlank { if (s.isArabic) "جاري التحضير..." else "Preparing packs..." },
                                color = Color(0xFFFDE047),
                                fontSize = 11.5.sp,
                                modifier = Modifier.weight(1f)
                            )
                        } else if (packStatus.neuralModelReady) {
                            Text(
                                text = if (s.isArabic) "✓ عصبي + ${packStatus.arabEyesWordCount} كلمة جاهزة أوفلاين" else "✓ Neural + ${packStatus.arabEyesWordCount} words ready offline",
                                color = Color(0xFF86EFAC),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp,
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (packStatus.arabEyesWordCount >= 1000) {
                                    Text(
                                        text = "✓ ${packStatus.arabEyesWordCount} ${if (s.isArabic) "كلمة" else "words"}",
                                        color = Color(0xFF86EFAC),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            isDownloadingModels = true
                                            val rep = app.translationManager.downloadCompleteOfflinePacks(wifiOnly = false) { msg ->
                                                downloadStatus = msg
                                            }
                                            packStatus = app.translationManager.getOfflinePackStatus()
                                            isDownloadingModels = false
                                            Toast.makeText(
                                                context,
                                                if (rep.neuralReady) (if (s.isArabic) "✓ تم تنزيل القاموس العصبي الكامل!" else "✓ Neural AR↔EN Pack Ready!")
                                                else "${rep.dictMessage}\n${rep.neuralMessage}",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Default.CloudDownload, null, tint = Color(0xFF38BDF8), modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (s.isArabic) "+ عصبي" else "+ Neural", color = Color.White, fontSize = 11.sp)
                                }
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(if (s.isArabic) "فوري" else "Live", color = Color.White, fontSize = 11.5.sp)
                            Switch(checked = liveAutoMode, onCheckedChange = { liveAutoMode = it })
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TranslationDirection.entries.forEach { d ->
                            FilterChip(
                                selected = direction == d,
                                onClick = { direction = d },
                                label = { Text(if (s.isArabic) d.labelAr else d.labelEn, fontSize = 10.5.sp) }
                            )
                        }
                    }
                }
            }

            // CameraX Live Viewfinder + Focus Target Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (hasCameraPermission) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            val previewView = PreviewView(ctx).apply {
                                scaleType = PreviewView.ScaleType.FILL_CENTER
                                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            }
                            previewView.setOnTouchListener { v, ev ->
                                if (ev.action == MotionEvent.ACTION_UP) {
                                    val pt = previewView.meteringPointFactory.createPoint(ev.x, ev.y)
                                    boundCamera?.cameraControl?.startFocusAndMetering(
                                        FocusMeteringAction.Builder(pt).setAutoCancelDuration(3, TimeUnit.SECONDS).build()
                                    )
                                    v.performClick()
                                }
                                true
                            }
                            val providerFuture = ProcessCameraProvider.getInstance(ctx)
                            providerFuture.addListener({
                                runCatching {
                                    val provider = providerFuture.get()
                                    val resSel = fastDocumentResolutionSelector()
                                    val selector = if (provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                                        CameraSelector.DEFAULT_BACK_CAMERA
                                    } else {
                                        CameraSelector.DEFAULT_FRONT_CAMERA
                                    }
                                    val preview = Preview.Builder().setResolutionSelector(resSel).build().also {
                                        it.surfaceProvider = previewView.surfaceProvider
                                    }
                                    val capture = ImageCapture.Builder()
                                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                        .setResolutionSelector(resSel)
                                        .build()
                                    imageCaptureRef = capture

                                    val analysis = ImageAnalysis.Builder()
                                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                        .build()
                                    analysis.setAnalyzer(cameraExecutor) { proxy ->
                                        val now = System.currentTimeMillis()
                                        if (liveAutoMode && !analyzerBusy.get() && (now - lastAnalyzeTimeMs.get() >= 2200L)) {
                                            if (analyzerBusy.compareAndSet(false, true)) {
                                                lastAnalyzeTimeMs.set(now)
                                                val bmp = imageProxyToCroppedCenterBitmap(proxy)
                                                proxy.close()
                                                if (bmp != null) {
                                                    runOcrAndTranslateBitmap(bmp, saveHistory = false, sourceTag = "Live Camera")
                                                } else {
                                                    analyzerBusy.set(false)
                                                }
                                                return@setAnalyzer
                                            }
                                        }
                                        proxy.close()
                                    }

                                    provider.unbindAll()
                                    boundCamera = try {
                                        provider.bindToLifecycle(lifecycleOwner, selector, preview, capture, analysis)
                                    } catch (_: Exception) {
                                        provider.unbindAll()
                                        liveAutoMode = false
                                        provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
                                    }
                                    cameraError = null
                                }.onFailure { err ->
                                    cameraError = err.localizedMessage ?: "Camera unavailable"
                                }
                            }, ContextCompat.getMainExecutor(ctx))
                            previewView
                        }
                    )

                    cameraError?.let { err ->
                        Surface(
                            color = BrandCrimson,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.align(Alignment.TopCenter).padding(12.dp)
                        ) {
                            Text(err, color = Color.White, modifier = Modifier.padding(10.dp), fontSize = 12.sp)
                        }
                    }

                    // Center Focus Frame Overlay
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth(0.86f)
                            .height(155.dp)
                            .border(2.dp, Color(0xFF38BDF8), RoundedCornerShape(14.dp))
                    ) {
                        Text(
                            text = if (s.isArabic) "وجّه الكاميرا على النص للترجمة الفورية أوفلاين" else "Point camera at English or Arabic text",
                            color = Color.White,
                            fontSize = 11.5.sp,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 6.dp)
                                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }

                    // Snap & Translate Shutter Button
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 14.dp)
                    ) {
                        Button(
                            enabled = !isScanningNow,
                            onClick = {
                                val cap = imageCaptureRef
                                if (cap == null) {
                                    Toast.makeText(context, "Camera initializing — please retry", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                isScanningNow = true
                                val outFile = File(context.cacheDir, "cam_tr_${System.currentTimeMillis()}.jpg")
                                val opts = ImageCapture.OutputFileOptions.Builder(outFile).build()
                                cap.takePicture(opts, cameraExecutor, object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                        val bmp = CamScannerEngine.decodeScaledBitmap(outFile.absolutePath, 1500)
                                        if (bmp != null) {
                                            runOcrAndTranslateBitmap(bmp, saveHistory = true, sourceTag = "Camera Snap")
                                        } else {
                                            scope.launch(Dispatchers.Main) {
                                                isScanningNow = false
                                                Toast.makeText(context, "Failed to decode captured photo", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        scope.launch(Dispatchers.Main) {
                                            isScanningNow = false
                                            analyzerBusy.set(false)
                                            Toast.makeText(context, "Capture failed: ${exception.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                })
                            },
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.CameraAlt, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (s.isArabic) "التقاط وترجمة فورية" else "Snap & Translate", fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (s.isArabic) "مطلوب إذن الكاميرا للترجمة الفورية، أو يمكنك اختيار صورة من المعرض." else "Camera permission is needed for live translation, or pick any photo from Gallery.",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = { permLauncher.launch(Manifest.permission.CAMERA) }) {
                                Text(if (s.isArabic) "تفعيل الكاميرا" else "Grant Camera")
                            }
                            OutlinedButton(onClick = { pickGalleryPhoto.launch(arrayOf("image/*")) }) {
                                Text(if (s.isArabic) "اختيار صورة" else "Pick Photo", color = Color.White)
                            }
                        }
                    }
                }
            }

            // Bottom AR Translucent Translation Output Card
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isScanningNow) {
                                if (s.isArabic) "جاري القراءة والترجمة..." else "Reading & Translating..."
                            } else {
                                engineBadge.ifBlank { if (s.isArabic) "نتيجة الترجمة الفورية (أوفلاين)" else "Instant Offline Translation" }
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = BrandPurple
                        )
                        Row {
                            IconButton(
                                enabled = translatedResult.isNotBlank(),
                                onClick = {
                                    val locale = if (ConverterCodec.containsArabic(translatedResult)) Locale("ar") else Locale.US
                                    tts?.language = locale
                                    tts?.speak(translatedResult.take(600), TextToSpeech.QUEUE_FLUSH, null, "cam_tts")
                                }
                            ) { Icon(Icons.Default.VolumeUp, contentDescription = "Speak", modifier = Modifier.size(18.dp)) }

                            IconButton(
                                enabled = translatedResult.isNotBlank(),
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("Camera Translation", translatedResult))
                                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                                }
                            ) { Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(18.dp)) }

                            IconButton(
                                enabled = translatedResult.isNotBlank(),
                                onClick = {
                                    scope.launch {
                                        app.translationManager.translateSmartOffline(
                                            text = detectedOriginal.ifBlank { translatedResult },
                                            direction = direction,
                                            sourceLabel = "Saved Camera Translation",
                                            saveToHistory = true
                                        )
                                        Toast.makeText(context, if (s.isArabic) "تم حفظ الترجمة محلياً" else "Saved translation locally!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            ) { Icon(Icons.Default.Save, contentDescription = "Save Locally", modifier = Modifier.size(18.dp)) }

                            IconButton(
                                enabled = translatedResult.isNotBlank(),
                                onClick = {
                                    scope.launch {
                                        runCatching {
                                            val out = FileHelper.uniqueFile(FileHelper.pdfsDir(context), "Camera_Translation_${System.currentTimeMillis() / 1000}.pdf")
                                            val body = "Original:\n$detectedOriginal\n\nTranslation:\n$translatedResult"
                                            app.pdfEngine.createNewPdf(out, "Camera Translation", body, 1, PageTemplate.BLANK)
                                            val info = app.pdfEngine.inspectDocument(out)
                                            app.repository.upsertDocument(PdfDocumentItem(name = out.name, path = out.absolutePath, sizeBytes = info.sizeBytes, pageCount = info.pageCount))
                                            onOpenPdf(out.absolutePath)
                                        }
                                    }
                                }
                            ) { Icon(Icons.Default.PictureAsPdf, contentDescription = "Export PDF", modifier = Modifier.size(18.dp)) }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (translatedResult.isBlank()) {
                            Text(
                                text = if (s.isArabic) "وجّه الكاميرا إلى أي كتاب أو لافتة أو مستند، أو اضغط 'التقاط وترجمة فورية'." else "Point your camera at any English or Arabic book, sign, or document — or tap 'Snap & Translate'.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(translatedResult, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp)
                            if (detectedOriginal.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "${if (s.isArabic) "الأصل:" else "Original:"} ${detectedOriginal.take(280)}",
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun imageProxyToCroppedCenterBitmap(proxy: ImageProxy): Bitmap? {
    return runCatching {
        val yBuffer = proxy.planes[0].buffer
        val uBuffer = proxy.planes[1].buffer
        val vBuffer = proxy.planes[2].buffer
        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuv = YuvImage(nv21, ImageFormat.NV21, proxy.width, proxy.height, null)
        val out = ByteArrayOutputStream()
        yuv.compressToJpeg(Rect(0, 0, proxy.width, proxy.height), 85, out)
        val raw = BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size()) ?: return null

        val rot = proxy.imageInfo.rotationDegrees
        val oriented = if (rot != 0) {
            val m = Matrix().apply { postRotate(rot.toFloat()) }
            Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true).also { raw.recycle() }
        } else raw

        val cx = (oriented.width * 0.07f).toInt().coerceAtLeast(0)
        val cy = (oriented.height * 0.29f).toInt().coerceAtLeast(0)
        val cw = (oriented.width * 0.86f).toInt().coerceAtMost(oriented.width - cx)
        val ch = (oriented.height * 0.42f).toInt().coerceAtMost(oriented.height - cy)
        Bitmap.createBitmap(oriented, cx, cy, cw, ch).also {
            if (it !== oriented) oriented.recycle()
        }
    }.getOrNull()
}
