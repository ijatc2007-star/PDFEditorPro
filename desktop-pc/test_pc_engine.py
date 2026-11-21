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

