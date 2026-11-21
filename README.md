# PDF Pro Studio — Complete Offline Android PDF Viewer & Editor

**Programmed by Ibrahim Jaber 2026**  
`© 2026 Ibrahim Jaber. All Rights Reserved.`

A native, 100% **offline** Android PDF Viewer & Pro Editor built with **Kotlin**, **Jetpack Compose (Material 3)**, **Android `PdfRenderer`**, and **Apache PDFBox-Android**.  
No `INTERNET` permission is declared — every document stays private on your device.

---

## Complete Feature Set (All 100% Offline)

### 1. Pro PDF Viewer & Reading Experience
- **Crisp Native Rendering:** Hardware-accelerated `PdfRenderer` with LRU bitmap caching.
- **Pinch-to-Zoom & Pan:** Smooth 1×–5× zoom and pan gestures.
- **Reading Modes:** Day, warm Sepia, and Night (inverted color matrix) modes.
- **Offline Keyword Search:** Search text across every page and jump directly to hits.
- **Bookmarks & Last-Page Memory:** Bookmark pages and automatically resume where you left off.
- **Offline Read Aloud (TTS):** Listen to any PDF page using Android Text-to-Speech.
- **Direct Open & Share:** Open PDFs directly from File Managers, WhatsApp, or Email (`ACTION_VIEW` / `ACTION_SEND`), print via Android Print Framework, or export copies via Storage Access Framework.

### 2. Interactive Pro PDF Editor (Permanently Embedded into PDF)
- **Freehand Pen & Semi-Transparent Highlighter** with custom color swatches and stroke widths.
- **Vector Shapes:** Rectangles, Ovals/Circles, and directional Arrows.
- **Text Boxes (English & Arabic Unicode support):** Custom font size, color, and bold styling.
- **Pro Stamps:** `APPROVED`, `CONFIDENTIAL`, `DRAFT`, `URGENT`, `VERIFIED`, and `IBRAHIM JABER 2026`.
- **Sticky Notes** and **Handwritten Signature Pad** (draw once, save reusable transparent PNG signatures, stamp anywhere).
- **Undo / Redo** stack + **Save Over Original** or **Save Copy**.

### 3. 12 Offline Pro PDF Tools
1. **Merge PDFs** — Combine multiple PDFs in custom order.
2. **Split PDF** — Extract custom page ranges (e.g., `1-3, 5, 8-10`) or split every page into separate PDFs.
3. **Visual Page Organizer** — Thumbnail grid to reorder, rotate 90°, duplicate, delete, or insert blank A4 pages.
4. **Images to PDF** — Convert multiple gallery photos (with EXIF auto-rotation) into clean A4 PDFs.
5. **PDF to Images** — Export PDF pages as high-resolution PNG or JPEG images.
6. **Compress PDF** — Light, Balanced, or Maximum offline compression with before/after size savings.
7. **Protect PDF** — Lock PDFs with AES-128 password encryption and printing/copying permissions.
8. **Unlock PDF** — Remove password protection from encrypted PDFs.
9. **Watermark PDF** — Diagonal or tiled custom watermarks (English & Arabic) with adjustable opacity.
10. **Add Page Numbers** — Stamp formatted page numbers (`1, 2, 3`, `Page 1 of 10`, `- 1 -`).
11. **Extract Text** — Extract full selectable PDF text, copy to clipboard, or save `.txt`.
12. **Metadata Editor & Create New PDF** — Edit PDF Title/Author/Subject/Keywords or create Blank, Lined, Grid, and Text PDFs from scratch.

---

## Building the APK

### Automated Build (GitHub Actions)
Every push to `main` automatically runs `gradle testDebugUnitTest` and `gradle assembleDebug` and uploads `PDFProStudio-IbrahimJaber2026-Debug-APK` in the **Actions** tab.

### Local Build (Android Studio)
1. Open this folder in **Android Studio** (JDK 17, Android SDK 35).
2. Run `./gradlew assembleDebug` (or click **Run**).
