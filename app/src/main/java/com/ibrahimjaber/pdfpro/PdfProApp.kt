package com.ibrahimjaber.pdfpro

import android.app.Application
import com.ibrahimjaber.pdfpro.data.PdfRepository
import com.ibrahimjaber.pdfpro.engine.DigitalSignatureEngine
import com.ibrahimjaber.pdfpro.engine.DocumentConverterEngine
import com.ibrahimjaber.pdfpro.engine.NeuralTranslationManager
import com.ibrahimjaber.pdfpro.engine.OfflineOcrEngine
import com.ibrahimjaber.pdfpro.engine.PdfEngine
import com.ibrahimjaber.pdfpro.engine.PdfFormEngine
import com.ibrahimjaber.pdfpro.engine.PdfRenderManager
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

/**
 * Application entry point for PDF Editor Pro v5.0.
 * Programmed by Ibrahim Jaber 2026 | برمجة إبراهيم جابر 2026
 */
class PdfProApp : Application() {

    lateinit var repository: PdfRepository
        private set

    lateinit var pdfEngine: PdfEngine
        private set

    lateinit var converterEngine: DocumentConverterEngine
        private set

    lateinit var ocrEngine: OfflineOcrEngine
        private set

    lateinit var translationManager: NeuralTranslationManager
        private set

    lateinit var formEngine: PdfFormEngine
        private set

    lateinit var digitalSignatureEngine: DigitalSignatureEngine
        private set

    lateinit var renderManager: PdfRenderManager
        private set

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
        repository = PdfRepository(applicationContext)
        pdfEngine = PdfEngine(applicationContext)
        converterEngine = DocumentConverterEngine(applicationContext, pdfEngine)
        ocrEngine = OfflineOcrEngine(applicationContext)
        translationManager = NeuralTranslationManager(applicationContext)
        formEngine = PdfFormEngine(applicationContext)
        digitalSignatureEngine = DigitalSignatureEngine(applicationContext)
        renderManager = PdfRenderManager(applicationContext)
    }
}
