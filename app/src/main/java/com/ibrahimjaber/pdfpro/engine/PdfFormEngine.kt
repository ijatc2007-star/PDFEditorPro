package com.ibrahimjaber.pdfpro.engine

import android.content.Context
import com.ibrahimjaber.pdfpro.data.AppBranding
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDComboBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDListBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDRadioButton
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

enum class FormFieldKind { TEXT, CHECKBOX, DROPDOWN, RADIO }

data class PdfFormFieldItem(
    val name: String,
    val label: String,
    val kind: FormFieldKind,
    val value: String,
    val checked: Boolean = false,
    val options: List<String> = emptyList(),
    val readOnly: Boolean = false
)

/**
 * 100% Offline Interactive PDF AcroForm Engine (Read, Fill, Flatten & Create Sample Interactive Forms).
 * Programmed by Ibrahim Jaber 2026 | برمجة إبراهيم جابر 2026
 */
class PdfFormEngine(private val context: Context) {

    suspend fun readFormFields(pdfFile: File, password: String? = null): List<PdfFormFieldItem> =
        withContext(Dispatchers.IO) {
            val result = mutableListOf<PdfFormFieldItem>()
            PDDocument.load(pdfFile, password ?: "").use { doc ->
                val acroForm = doc.documentCatalog.acroForm ?: return@withContext emptyList()
                for (field in acroForm.fieldTree) {
                    val fqName = field.fullyQualifiedName ?: continue
                    val label = field.alternateFieldName?.takeIf { it.isNotBlank() } ?: fqName
                    when (field) {
                        is PDCheckBox -> {
                            result.add(
                                PdfFormFieldItem(
                                    name = fqName,
                                    label = label,
                                    kind = FormFieldKind.CHECKBOX,
                                    value = if (field.isChecked) "Yes" else "Off",
                                    checked = field.isChecked,
                                    readOnly = field.isReadOnly
                                )
                            )
                        }
                        is PDComboBox -> {
                            result.add(
                                PdfFormFieldItem(
                                    name = fqName,
                                    label = label,
                                    kind = FormFieldKind.DROPDOWN,
                                    value = field.valueAsString ?: "",
                                    options = field.options ?: emptyList(),
                                    readOnly = field.isReadOnly
                                )
                            )
                        }
                        is PDListBox -> {
                            result.add(
                                PdfFormFieldItem(
                                    name = fqName,
                                    label = label,
                                    kind = FormFieldKind.DROPDOWN,
                                    value = field.valueAsString ?: "",
                                    options = field.options ?: emptyList(),
                                    readOnly = field.isReadOnly
                                )
                            )
                        }
                        is PDRadioButton -> {
                            result.add(
                                PdfFormFieldItem(
                                    name = fqName,
                                    label = label,
                                    kind = FormFieldKind.RADIO,
                                    value = field.valueAsString ?: "",
                                    options = field.onValues?.toList() ?: emptyList(),
                                    readOnly = field.isReadOnly
                                )
                            )
                        }
                        is PDTextField -> {
                            result.add(
                                PdfFormFieldItem(
                                    name = fqName,
                                    label = label,
                                    kind = FormFieldKind.TEXT,
                                    value = field.valueAsString ?: "",
                                    readOnly = field.isReadOnly
                                )
                            )
                        }
                    }
                }
            }
            result
        }

    suspend fun saveFilledForm(
        inputPdf: File,
        outputPdf: File,
        values: Map<String, String>,
        checkboxStates: Map<String, Boolean>,
        flattenForm: Boolean = false,
        password: String? = null
    ): File = withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, "form_save_${System.currentTimeMillis()}.pdf")
        PDDocument.load(inputPdf, password ?: "").use { doc ->
            doc.isAllSecurityToBeRemoved = true
            val acroForm = doc.documentCatalog.acroForm
            if (acroForm != null) {
                acroForm.needAppearances = true
                for (field in acroForm.fieldTree) {
                    val name = field.fullyQualifiedName ?: continue
                    if (field.isReadOnly) continue
                    when (field) {
                        is PDCheckBox -> {
                            val checked = checkboxStates[name] ?: continue
                            if (checked) field.check() else field.unCheck()
                        }
                        is PDComboBox -> {
                            val v = values[name]
                            if (!v.isNullOrBlank()) runCatching { field.setValue(v) }
                        }
                        is PDListBox -> {
                            val v = values[name]
                            if (!v.isNullOrBlank()) runCatching { field.setValue(v) }
                        }
                        is PDRadioButton -> {
                            val v = values[name]
                            if (!v.isNullOrBlank()) runCatching { field.setValue(v) }
                        }
                        is PDTextField -> {
                            val v = values[name]
                            if (v != null) {
                                // Ensure WinAnsi safe characters for standard PDF form appearances
                                val safeVal = v.filter { ch -> ch.code in 32..255 || ch == '\n' }
                                runCatching { field.setValue(safeVal.ifEmpty { v }) }
                            }
                        }
                    }
                }
                if (flattenForm) {
                    runCatching { acroForm.flatten() }
                }
            }
            doc.documentInformation.creator = AppBranding.PDF_CREATOR_TAG
            doc.save(tempFile)
        }
        outputPdf.parentFile?.mkdirs()
        tempFile.copyTo(outputPdf, overwrite = true)
        tempFile.delete()
        outputPdf
    }

    /**
     * Generates a real, native interactive AcroForm PDF so users can immediately test
     * filling Text Fields, Dropdowns, and Checkboxes + Flattening offline.
     */
    suspend fun createSampleInteractiveFormPdf(outputPdf: File): File = withContext(Dispatchers.IO) {
        outputPdf.parentFile?.mkdirs()
        PDDocument().use { doc ->
            val page = PDPage(PDRectangle.A4)
            doc.addPage(page)

            // Draw clean visual header & labels
            PDPageContentStream(doc, page).use { cs ->
                cs.setNonStrokingColor(15, 23, 42)
                cs.addRect(0f, 752f, 595f, 90f)
                cs.fill()

                cs.beginText()
                cs.setFont(PDType1Font.HELVETICA_BOLD, 20f)
                cs.setNonStrokingColor(255, 255, 255)
                cs.newLineAtOffset(42f, 798f)
                cs.showText("Interactive Approval Form (AcroForm)")
                cs.endText()

                cs.beginText()
                cs.setFont(PDType1Font.HELVETICA, 10f)
                cs.setNonStrokingColor(203, 213, 225)
                cs.newLineAtOffset(42f, 774f)
                cs.showText("PDF Editor Pro v4.0  |  Programmed by Ibrahim Jaber 2026")
                cs.endText()

                val labels = listOf(
                    "Full Name:" to 702f,
                    "Department / Organization:" to 642f,
                    "Email Address:" to 582f,
                    "Priority Level:" to 522f,
                    "Notes / Comments:" to 462f,
                    "Approved for Release:" to 356f,
                    "Confidential Handling:" to 316f
                )
                cs.setNonStrokingColor(30, 41, 59)
                for ((lbl, y) in labels) {
                    cs.beginText()
                    cs.setFont(PDType1Font.HELVETICA_BOLD, 12f)
                    cs.newLineAtOffset(42f, y)
                    cs.showText(lbl)
                    cs.endText()
                }
            }

            val acroForm = PDAcroForm(doc)
            val resources = PDResources()
            resources.put(COSName.getPDFName("Helv"), PDType1Font.HELVETICA)
            acroForm.defaultResources = resources
            acroForm.defaultAppearance = "/Helv 11 Tf 0 g"
            doc.documentCatalog.acroForm = acroForm

            fun addTextField(name: String, alt: String, yBottom: Float, height: Float = 26f, defaultVal: String = "") {
                val tf = PDTextField(acroForm).apply {
                    partialName = name
                    alternateFieldName = alt
                    defaultAppearance = "/Helv 11 Tf 0 g"
                }
                val widget = PDAnnotationWidget().apply {
                    rectangle = PDRectangle(220f, yBottom, 330f, height)
                    this.page = page
                    isPrinted = true
                }
                tf.widgets = listOf(widget)
                page.annotations.add(widget)
                acroForm.fields.add(tf)
                if (defaultVal.isNotEmpty()) tf.value = defaultVal
            }

            addTextField("full_name", "Full Name", 694f, defaultVal = "Ibrahim Jaber")
            addTextField("department", "Department / Organization", 634f, defaultVal = "Engineering")
            addTextField("email", "Email Address", 574f, defaultVal = "contact@example.com")

            // Dropdown ComboBox
            val combo = PDComboBox(acroForm).apply {
                partialName = "priority"
                alternateFieldName = "Priority Level"
                defaultAppearance = "/Helv 11 Tf 0 g"
                setOptions(listOf("Normal", "High", "Urgent", "Confidential"))
            }
            val comboWidget = PDAnnotationWidget().apply {
                rectangle = PDRectangle(220f, 514f, 330f, 26f)
                this.page = page
                isPrinted = true
            }
            combo.widgets = listOf(comboWidget)
            page.annotations.add(comboWidget)
            acroForm.fields.add(combo)
            combo.setValue("High")

            addTextField("notes", "Notes / Comments", 412f, height = 66f, defaultVal = "Verified offline with PDF Editor Pro v4.0")

            fun addCheckBox(name: String, alt: String, yBottom: Float, checked: Boolean) {
                val cb = PDCheckBox(acroForm).apply {
                    partialName = name
                    alternateFieldName = alt
                }
                val w = PDAnnotationWidget().apply {
                    rectangle = PDRectangle(220f, yBottom, 20f, 20f)
                    this.page = page
                    isPrinted = true
                }
                val apDict = COSDictionary()
                apDict.setItem(COSName.ON, COSName.YES)
                cb.widgets = listOf(w)
                page.annotations.add(w)
                acroForm.fields.add(cb)
                if (checked) runCatching { cb.check() }
            }

            addCheckBox("approved", "Approved for Release", 350f, true)
            addCheckBox("confidential", "Confidential Handling", 310f, false)

            doc.documentInformation.apply {
                title = "Sample Interactive AcroForm — Ibrahim Jaber 2026"
                author = AppBranding.DEVELOPER_NAME
                creator = AppBranding.PDF_CREATOR_TAG
            }
            doc.save(outputPdf)
        }
        outputPdf
    }
}
