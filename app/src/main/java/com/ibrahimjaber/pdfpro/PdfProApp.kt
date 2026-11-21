package com.ibrahimjaber.pdfpro

import android.app.Application
import com.ibrahimjaber.pdfpro.data.PdfRepository
import com.ibrahimjaber.pdfpro.engine.DocumentConverterEngine
import com.ibrahimjaber.pdfpro.engine.PdfEngine
import com.ibrahimjaber.pdfpro.engine.PdfRenderManager
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

/**
 * Application entry point for PDF Pro Studio v2.0.
 * Programmed by Ibrahim Jaber 2026 | برمجة إبراهيم جابر 2026
 */
class PdfProApp : Application() {

    lateinit var repository: PdfRepository
        private set

    lateinit var pdfEngine: PdfEngine
        private set

    lateinit var converterEngine: DocumentConverterEngine
        private set

    lateinit var renderManager: PdfRenderManager
        private set

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
        repository = PdfRepository(applicationContext)
        pdfEngine = PdfEngine(applicationContext)
        converterEngine = DocumentConverterEngine(applicationContext, pdfEngine)
        renderManager = PdfRenderManager(applicationContext)
    }
}
