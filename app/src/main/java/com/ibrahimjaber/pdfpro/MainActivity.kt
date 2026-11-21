package com.ibrahimjaber.pdfpro

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import com.ibrahimjaber.pdfpro.data.PdfDocumentItem
import com.ibrahimjaber.pdfpro.engine.FileHelper
import com.ibrahimjaber.pdfpro.ui.navigation.PdfProNavHost
import com.ibrahimjaber.pdfpro.ui.theme.PdfProTheme
import kotlinx.coroutines.launch
import java.io.File

/**
 * Main Activity for PDF Pro Studio — 100% Offline PDF Viewer & Pro Editor.
 * Programmed by Ibrahim Jaber 2026.
 */
class MainActivity : ComponentActivity() {

    private val pendingOpenPath = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as PdfProApp
        ensureSampleDocumentExists(app)
        handleIncomingIntent(intent, app)

        setContent {
            val settings by app.repository.settings.collectAsState()
            PdfProTheme(themeMode = settings.themeMode) {
                PdfProNavHost(
                    app = app,
                    pendingOpenPath = pendingOpenPath.value,
                    onConsumedOpenPath = { pendingOpenPath.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent, application as PdfProApp)
    }

    private fun ensureSampleDocumentExists(app: PdfProApp) {
        lifecycleScope.launch {
            if (app.repository.documents.value.isEmpty()) {
                val sample = File(FileHelper.pdfsDir(this@MainActivity), "PDF_Pro_Studio_Welcome_2026.pdf")
                runCatching {
                    if (!sample.exists()) {
                        app.pdfEngine.createSampleWelcomePdf(sample)
                    }
                    val info = app.pdfEngine.inspectDocument(sample)
                    app.repository.upsertDocument(
                        PdfDocumentItem(
                            name = sample.name,
                            path = sample.absolutePath,
                            sizeBytes = info.sizeBytes,
                            pageCount = info.pageCount
                        )
                    )
                }
            }
        }
    }

    private fun handleIncomingIntent(intent: Intent?, app: PdfProApp) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_VIEW -> {
                intent.data?.let { uri -> importAndOpenPdf(uri, app) }
            }

            Intent.ACTION_SEND -> {
                val uri = getParcelableUri(intent, Intent.EXTRA_STREAM) ?: return
                val mime = intent.type.orEmpty()
                if (mime.contains("pdf", ignoreCase = true)) {
                    importAndOpenPdf(uri, app)
                } else if (mime.startsWith("image/", ignoreCase = true)) {
                    convertImagesAndOpen(listOf(uri), app)
                }
            }

            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = getParcelableUriList(intent, Intent.EXTRA_STREAM)
                if (uris.isNotEmpty()) {
                    convertImagesAndOpen(uris, app)
                }
            }
        }
    }

    private fun importAndOpenPdf(uri: Uri, app: PdfProApp) {
        lifecycleScope.launch {
            val imported = FileHelper.importPdfFromUri(this@MainActivity, uri)
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
                pendingOpenPath.value = imported.absolutePath
            } else {
                Toast.makeText(this@MainActivity, "Could not open PDF", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun convertImagesAndOpen(uris: List<Uri>, app: PdfProApp) {
        lifecycleScope.launch {
            val imgFiles = uris.mapIndexedNotNull { idx, uri ->
                FileHelper.copyImageFromUriToCache(this@MainActivity, uri, idx)
            }
            if (imgFiles.isEmpty()) return@launch
            runCatching {
                val outFile = FileHelper.uniqueFile(
                    FileHelper.pdfsDir(this@MainActivity),
                    "Images_Scan_${System.currentTimeMillis()}.pdf"
                )
                app.pdfEngine.imagesToPdf(imgFiles, outFile)
                val info = app.pdfEngine.inspectDocument(outFile)
                app.repository.upsertDocument(
                    PdfDocumentItem(
                        name = outFile.name,
                        path = outFile.absolutePath,
                        sizeBytes = info.sizeBytes,
                        pageCount = info.pageCount
                    )
                )
                pendingOpenPath.value = outFile.absolutePath
            }.onFailure {
                Toast.makeText(this@MainActivity, "Image conversion failed: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun getParcelableUri(intent: Intent, key: String): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(key, Uri::class.java)
        } else {
            intent.getParcelableExtra(key)
        }
    }

    @Suppress("DEPRECATION")
    private fun getParcelableUriList(intent: Intent, key: String): List<Uri> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(key, Uri::class.java) ?: emptyList()
        } else {
            intent.getParcelableArrayListExtra(key) ?: emptyList()
        }
    }
}
