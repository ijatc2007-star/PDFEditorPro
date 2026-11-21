import os
import sys
from pathlib import Path
import pytest

sys.path.insert(0, str(Path(__file__).resolve().parent))
import pdf_engine_pc as engine


def test_branding_and_arabic_detection():
    assert "Ibrahim Jaber 2026" in engine.CREATOR_TAG
    assert engine.contains_arabic("برمجة إبراهيم جابر 2026")
    assert not engine.contains_arabic("Programmed by Ibrahim Jaber 2026")


def test_parse_page_ranges():
    assert engine.parse_page_ranges("1-3, 5, 7-8", 10) == [0, 1, 2, 4, 6, 7]
    with pytest.raises(ValueError):
        engine.parse_page_ranges("1-12", 5)


def test_full_offline_pipeline_and_conversions(tmp_path: Path):
    sample_pdf = str(tmp_path / "sample.pdf")
    engine.create_sample_welcome_pdf(sample_pdf)
    assert os.path.exists(sample_pdf)

    # PDF -> Word, Excel, CSV, PowerPoint, HTML, TXT, PNG
    docx_out = str(tmp_path / "out.docx")
    xlsx_out = str(tmp_path / "out.xlsx")
    csv_out = str(tmp_path / "out.csv")
    pptx_out = str(tmp_path / "out.pptx")
    html_out = str(tmp_path / "out.html")
    txt_out = str(tmp_path / "out.txt")

    engine.pdf_to_docx(sample_pdf, docx_out)
    engine.pdf_to_xlsx(sample_pdf, xlsx_out)
    engine.pdf_to_csv(sample_pdf, csv_out)
    engine.pdf_to_pptx(sample_pdf, pptx_out)
    engine.pdf_to_html(sample_pdf, html_out)
    engine.pdf_to_txt(sample_pdf, txt_out)
    imgs = engine.pdf_to_images(sample_pdf, str(tmp_path / "imgs"), fmt="png", dpi=96)

    for p in (docx_out, xlsx_out, csv_out, pptx_out, html_out, txt_out):
        assert os.path.getsize(p) > 0
    assert len(imgs) == 2

    # Round-trip TO PDF: Word, Excel, CSV, PPTX, Images, HTML
    for label, fn, src in [
        ("from_docx.pdf", engine.docx_to_pdf, docx_out),
        ("from_xlsx.pdf", engine.xlsx_to_pdf, xlsx_out),
        ("from_csv.pdf", engine.csv_to_pdf, csv_out),
        ("from_pptx.pdf", engine.pptx_to_pdf, pptx_out),
        ("from_html.pdf", engine.text_or_html_to_pdf, html_out),
    ]:
        out_p = str(tmp_path / label)
        fn(src, out_p)
        assert os.path.getsize(out_p) > 0

    from_imgs_pdf = str(tmp_path / "from_imgs.pdf")
    engine.images_to_pdf(imgs, from_imgs_pdf)
    assert os.path.getsize(from_imgs_pdf) > 0

    # Direct CSV <-> Excel
    csv_back = str(tmp_path / "back.csv")
    xlsx_back = str(tmp_path / "back.xlsx")
    engine.xlsx_to_csv(xlsx_out, csv_back)
    engine.csv_to_xlsx(csv_back, xlsx_back)
    assert os.path.getsize(xlsx_back) > 0

    # Pro tools: Merge, Split, Compress, Grayscale, Protect/Unlock, Watermark, Bates, Redact, Compare
    merged = str(tmp_path / "merged.pdf")
    engine.merge_pdfs([sample_pdf, from_imgs_pdf], merged)
    assert os.path.getsize(merged) > 0

    splits = engine.split_pdf(merged, str(tmp_path / "splits"), range_expr="1-2")
    assert len(splits) == 1

    o, n, _ = engine.compress_pdf(sample_pdf, str(tmp_path / "comp.pdf"), "medium")
    assert o > 0 and n > 0

    o2, n2, _ = engine.convert_to_grayscale_pdf(sample_pdf, str(tmp_path / "gray.pdf"))
    assert o2 > 0 and n2 > 0

    locked = str(tmp_path / "locked.pdf")
    unlocked = str(tmp_path / "unlocked.pdf")
    engine.protect_pdf(sample_pdf, locked, "secret123")
    engine.unlock_pdf(locked, unlocked, "secret123")
    assert os.path.getsize(unlocked) > 0

    wm = str(tmp_path / "wm.pdf")
    engine.add_watermark(sample_pdf, wm, "سري للغاية — إبراهيم جابر 2026")
    assert os.path.getsize(wm) > 0

    bates = str(tmp_path / "bates.pdf")
    engine.add_headers_footers_bates(sample_pdf, bates, header_center="CONFIDENTIAL", bates_prefix="IJ-")
    assert os.path.getsize(bates) > 0

    redacted = str(tmp_path / "redacted.pdf")
    qr_png = engine.generate_qr_png("Ibrahim Jaber 2026", str(tmp_path / "qr.png"))
    engine.apply_annotations_to_pdf(
        sample_pdf,
        redacted,
        [
            {"type": "redact", "page": 0, "x1": 0.1, "y1": 0.1, "x2": 0.4, "y2": 0.2},
            {"type": "text", "page": 0, "x": 0.2, "y": 0.3, "text": "برمجة إبراهيم جابر 2026", "color": "#DC2626"},
            {"type": "qr_code", "page": 0, "x": 0.8, "y": 0.8, "image_path": qr_png},
        ],
    )
    assert os.path.getsize(redacted) > 0

    diff = engine.compare_pdfs(sample_pdf, redacted)
    assert "Comparison Report" in diff


def test_offline_ai_arabic_and_english_engine():
    import ai_engine_pc as ai

    norm = ai.normalize_for_search("إبْرَاهِيمُ جَابِر — مَدرَسَةٌ")
    assert "ابراهيم" in norm and "مدرسه" in norm

    pages = [
        "1. Contract Summary\nThis contract must be approved before 2026-12-31 for $30,000 USD. Email test@company.com.",
        "الفصل الثاني: التسليم والدفع\nيجب تسليم العمل النهائي قبل 2026-12-31 بقيمة 50000 ريال سعودي. برمجة إبراهيم جابر 2026.",
    ]
    rep = ai.analyze_document(pages, num_sentences=3)
    assert len(rep["summary"]) >= 1
    assert "2026-12-31" in rep["dates"]
    assert len(rep["amounts"]) >= 1
    assert any(cat == "Email" for _, cat, _ in rep["pii"])
    assert len(rep["headings"]) >= 1

    hits_en = ai.ask_question(pages, "What is the contract deadline and amount?")
    assert len(hits_en) >= 1 and hits_en[0][0] == 1

    hits_ar = ai.ask_question(pages, "ما هي قيمة التسليم والدفع؟")
    assert len(hits_ar) >= 1 and hits_ar[0][0] == 2


def test_v4_flagship_engines(tmp_path: Path):
    import v4_engines_pc as v4

    # 1. Offline EN <-> AR Translation + Flashcards + Reply + GGUF validator
    ar_txt = v4.translate_offline("Executive Summary: This contract must be approved before deadline.", "en_to_ar")
    assert "الملخص التنفيذي" in ar_txt and "يجب" in ar_txt
    en_txt = v4.translate_offline("الملخص التنفيذي: يجب اعتماد التقرير النهائي", "ar_to_en")
    assert "executive summary" in en_txt.lower()

    cards = v4.generate_study_flashcards(["1. Security\nAll documents must be encrypted before 2026-12-31."])
    assert len(cards) >= 1

    gguf = tmp_path / "demo.gguf"
    gguf.write_bytes(b"GGUF\x03\x00\x00\x00\x00")
    info = v4.inspect_gguf_model(str(gguf))
    assert info["valid"] is True and info["version"] == 3

    # 2. Interactive AcroForm create, read, fill & flatten
    form_pdf = str(tmp_path / "sample_form.pdf")
    filled_pdf = str(tmp_path / "filled_form.pdf")
    v4.create_sample_interactive_form(form_pdf)
    fields = v4.read_form_fields(form_pdf)
    assert len(fields) >= 4
    v4.fill_and_save_form(form_pdf, filled_pdf, {"full_name": "Ibrahim Jaber 2026"}, {"approved": True}, flatten=True)
    assert os.path.getsize(filled_pdf) > 0

    # 3. Cryptographic X.509 PKCS#12 Cert Generation & PDF Signing
    p12 = str(tmp_path / "ibrahim.p12")
    signed_pdf = str(tmp_path / "signed.pdf")
    v4.generate_x509_p12(p12, "Ibrahim Jaber", "PDF Editor Pro 2026", "2026")
    sig_info = v4.sign_pdf_with_x509(form_pdf, signed_pdf, p12, "2026")
    assert sig_info["signer"] == "Ibrahim Jaber" and os.path.getsize(signed_pdf) > 0

    # 4. Offline OCR pipeline (with graceful fallback when tessdata is absent in test env)
    texts, out_ocr = v4.ocr_scanned_pdf(form_pdf, language="eng", output_searchable_pdf=str(tmp_path / "ocr.pdf"))
    assert len(texts) == 1 and out_ocr and os.path.getsize(out_ocr) > 0


def test_v5_flagship_engines(tmp_path: Path):
    from PIL import Image, ImageDraw
    import v5_engines_pc as v5

    # 1. Local SQLite Dictionary + Persistent Translation Cache & Saved History
    db_file = str(tmp_path / "test_v5_trans.db")
    store = v5.LocalTranslationStore(db_path=db_file)
    assert store.get_dictionary_count() >= 250

    res1 = store.translate_smart(
        "Executive Summary: All engineering documents must pass security verification.",
        direction="en_to_ar",
        source_label="UnitTest.pdf",
        allow_online_sync=False,
    )
    assert "الملخص التنفيذي" in res1["translated"]
    assert res1["from_cache"] is False

    # Second identical lookup must hit local SQLite cache instantaneously
    res2 = store.translate_smart(
        "Executive Summary: All engineering documents must pass security verification.",
        direction="en_to_ar",
        source_label="UnitTest.pdf",
        allow_online_sync=False,
    )
    assert res2["from_cache"] is True
    assert res2["translated"] == res1["translated"]
    assert len(store.list_saved_history()) >= 1

    # 2. CamScanner Pro filters (Magic Color, Crisp B&W, Shadow Remove, Grayscale) & Batch PDF
    raw_img = tmp_path / "scan_page.jpg"
    img = Image.new("RGB", (600, 800), color=(238, 236, 230))
    draw = ImageDraw.Draw(img)
    draw.rectangle([20, 20, 580, 780], outline=(60, 60, 60), width=2)
    draw.text((60, 80), "PDF Editor Pro v5.0 CamScanner Test", fill=(20, 20, 20))
    img.save(raw_img, "JPEG")

    for mode in ("magic_color", "crisp_bw", "shadow_remove", "grayscale", "original"):
        out_f = str(tmp_path / f"enh_{mode}.jpg")
        v5.camscan_enhance_image(str(raw_img), out_f, filter_mode=mode, auto_crop=True)
        assert os.path.getsize(out_f) > 0

    cam_pdf = str(tmp_path / "camscan_out.pdf")
    saved_pdf, _ = v5.camscan_batch_to_pdf([str(raw_img)], cam_pdf, filter_mode="magic_color", run_ocr=False)
    assert os.path.getsize(saved_pdf) > 0

    # 3. Large-PDF Map-Reduce AI Engine & Inverted-Index BM25 Q&A (120-page book)
    big_pages = []
    for p in range(1, 121):
        if p == 1:
            big_pages.append("Chapter 1 Network Security Overview.\nAll firewalls must be audited before 2026-12-31.")
        elif p == 60:
            big_pages.append("Chapter 6 Annual Budget Allocation.\nTotal approved budget is $450,000 USD for offline deployment.")
        elif p == 115:
            big_pages.append("الفصل العاشر الخاتمة.\nيجب اعتماد التوقيع الرقمي والترجمة الفورية بالكاميرا لجميع الفرق.")
        else:
            big_pages.append(f"Section {p} Compliance Procedure.\nPage {p} covers enterprise archiving and offline privacy controls.")

    rep = v5.analyze_large_document_pc(big_pages, start_page_1based=1, total_doc_pages=120, num_sentences=8)
    assert rep["used_map_reduce"] is True
    assert rep["analyzed_pages"] == 120
    assert len(rep["chapter_summaries"]) >= 4
    assert any("450,000" in a for a in rep["amounts"])

    idx = v5.LargeDocIndexPC(big_pages, start_page_1based=1)
    hits_en = idx.ask("annual budget 450,000 USD")
    assert any(pg == 60 for pg, _, _ in hits_en)
    hits_ar = idx.ask("التوقيع الرقمي والترجمة الفورية")
    assert any(pg == 115 for pg, _, _ in hits_ar)



