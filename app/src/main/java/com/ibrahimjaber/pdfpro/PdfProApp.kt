package com.ibrahimjaber.pdfpro

import android.app.Application
import com.ibrahimjaber.pdfpro.data.PdfRepository
import com.ibrahimjaber.pdfpro.engine.PdfEngine
import com.ibrahimjaber.pdfpro.engine.PdfRenderManager
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

/**
 * Application entry point for PDF Pro Studio.
 * Programmed by Ibrahim Jaber 2026.
 */
class PdfProApp : Application() {

    lateinit var repository: PdfRepository
        private set

    lateinit var pdfEngine: PdfEngine
        private set

    lateinit var renderManager: PdfRenderManager
        private set

    override fun onCreate() {
        super.onCreate()
        // Initialize Apache PDFBox-Android font/encoding resources offline
        PDFBoxResourceLoader.init(applicationContext)
        repository = PdfRepository(applicationContext)
        pdfEngine = PdfEngine(applicationContext)
        renderManager = PdfRenderManager(applicationContext)
    }
}
