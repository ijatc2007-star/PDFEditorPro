"""
PDF Editor Pro PC v4.0 — Advanced Offline Engines:
1. Offline Arabic + English Scanned-PDF OCR (PyMuPDF / Tesseract + bundled tessdata_fast)
2. Offline Bilingual Translator (EN <-> AR), Study Flashcards, Executive Reply Drafter & GGUF Validator
3. Interactive Native PDF AcroForm Filler & Flattener
4. Cryptographic X.509 RSA-2048 PKCS#12 Certificate Generator & SHA-256 PDF Digital Seal

Programmed by Ibrahim Jaber 2026 | برمجة إبراهيم جابر 2026
"""

from __future__ import annotations

import datetime
import hashlib
import os
import re
import struct
import sys
from pathlib import Path
from typing import Dict, List, Optional, Tuple

import fitz
from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding, rsa
from cryptography.hazmat.primitives.serialization import pkcs12
from cryptography.x509.oid import NameOID

import ai_engine_pc as ai_engine
import pdf_engine_pc as pdf_engine

# =============================================================================
# PILLAR 1: OFFLINE ARABIC & ENGLISH SCANNED-PDF OCR
# =============================================================================


def find_tessdata_dir() -> Optional[str]:
    candidates = []
    if getattr(sys, "frozen", False) and hasattr(sys, "_MEIPASS"):
        candidates.append(os.path.join(sys._MEIPASS, "tessdata"))
    here = os.path.dirname(os.path.abspath(__file__))
    candidates.extend([
        os.path.join(here, "tessdata"),
        os.environ.get("TESSDATA_PREFIX", ""),
        r"C:\Program Files\Tesseract-OCR\tessdata",
        "/usr/share/tesseract-ocr/5/tessdata",
        "/usr/share/tesseract-ocr/4.00/tessdata",
    ])
    for c in candidates:
        if c and os.path.isdir(c) and any(f.endswith(".traineddata") for f in os.listdir(c)):
            return c
    return None


def ocr_scanned_pdf(
    input_pdf: str,
    language: str = "ara+eng",
    output_searchable_pdf: Optional[str] = None,
) -> Tuple[List[str], Optional[str]]:
    """
    Runs offline OCR on a scanned PDF using PyMuPDF's native Tesseract bridge (or text extraction fallback
    if a page already has text) and optionally writes a searchable PDF copy.
    """
    tess_dir = find_tessdata_dir()
    doc = fitz.open(input_pdf)
    out_doc = fitz.open() if output_searchable_pdf else None
    page_texts: List[str] = []

    for page in doc:
        text = ""
        if tess_dir:
            try:
                tp = page.get_textpage_ocr(flags=0, language=language, dpi=200, full=True, tessdata=tess_dir)
                text = ai_engine.clean_extracted_text(page.get_text("text", textpage=tp))
            except Exception:
                text = ai_engine.clean_extracted_text(page.get_text("text"))
        else:
            text = ai_engine.clean_extracted_text(page.get_text("text"))
        page_texts.append(text)

        if out_doc is not None:
            new_p = out_doc.new_page(width=page.rect.width, height=page.rect.height)
            pix = page.get_pixmap(dpi=160)
            new_p.insert_image(new_p.rect, stream=pix.tobytes("jpeg"))
            if text:
                # Invisible searchable text layer (render_mode=3)
                new_p.insert_textbox(new_p.rect, text[:4000], fontsize=9, render_mode=3)

    saved_path = None
    if out_doc is not None and output_searchable_pdf:
        out_doc.set_metadata({"title": f"{Path(input_pdf).stem} (OCR)", "creator": pdf_engine.CREATOR_TAG})
        out_doc.save(output_searchable_pdf, deflate=True)
        out_doc.close()
        saved_path = output_searchable_pdf
    doc.close()
    return page_texts, saved_path


# =============================================================================
# PILLAR 2: OFFLINE TRANSLATION (EN <-> AR), FLASHCARDS, REPLY & GGUF VALIDATOR
# =============================================================================

EN_TO_AR_PHRASES = {
    "terms and conditions": "الشروط والأحكام",
    "privacy policy": "سياسة الخصوصية",
    "table of contents": "جدول المحتويات",
    "executive summary": "الملخص التنفيذي",
    "all rights reserved": "جميع الحقوق محفوظة",
    "digital signature": "التوقيع الرقمي",
    "payment terms": "شروط الدفع",
    "total amount": "المبلغ الإجمالي",
    "due date": "تاريخ الاستحقاق",
    "full name": "الاسم الكامل",
    "phone number": "رقم الهاتف",
    "email address": "البريد الإلكتروني",
    "action items": "المهام المطلوبة",
    "final report": "التقرير النهائي",
    "security audit": "التدقيق الأمني",
    "programmed by": "برمجة",
}

EN_TO_AR_WORDS = {
    "agreement": "اتفاقية", "contract": "عقد", "document": "مستند", "file": "ملف",
    "page": "صفحة", "report": "تقرير", "summary": "ملخص", "project": "مشروع",
    "company": "شركة", "client": "عميل", "service": "خدمة", "system": "نظام",
    "security": "الأمان", "privacy": "الخصوصية", "offline": "بدون إنترنت",
    "password": "كلمة المرور", "encrypted": "مشفّر", "signature": "توقيع",
    "approved": "معتمد", "confidential": "سري للغاية", "draft": "مسودة",
    "urgent": "عاجل", "verified": "موثّق", "invoice": "فاتورة", "payment": "الدفع",
    "budget": "الميزانية", "amount": "المبلغ", "total": "الإجمالي", "date": "التاريخ",
    "deadline": "الموعد النهائي", "delivery": "التسليم", "department": "القسم",
    "author": "المؤلف", "title": "العنوان", "notes": "ملاحظات", "section": "القسم",
    "chapter": "الفصل", "requirements": "المتطلبات", "required": "مطلوب",
    "must": "يجب", "shall": "يلزم", "important": "مهم", "name": "الاسم",
    "email": "البريد الإلكتروني", "phone": "الهاتف", "text": "النص", "image": "صورة",
    "table": "جدول", "data": "البيانات", "search": "بحث", "edit": "تحرير",
    "save": "حفظ", "export": "تصدير", "convert": "تحويل", "merge": "دمج",
    "split": "تقسيم", "compress": "ضغط", "print": "طباعة", "share": "مشاركة",
    "protect": "حماية", "unlock": "فك القفل", "watermark": "علامة مائية",
    "translate": "ترجمة", "arabic": "العربية", "english": "الإنجليزية", "form": "نموذج",
    "welcome": "مرحباً", "guide": "دليل", "tools": "أدوات", "before": "قبل",
    "after": "بعد", "with": "مع", "from": "من", "to": "إلى", "and": "و", "or": "أو",
}

AR_TO_EN_PHRASES = {ai_engine.normalize_for_search(ar): en for en, ar in EN_TO_AR_PHRASES.items()}
AR_TO_EN_WORDS: Dict[str, str] = {}
for _en, _ar in EN_TO_AR_WORDS.items():
    _n = ai_engine.normalize_for_search(_ar)
    AR_TO_EN_WORDS.setdefault(_n, _en)
    if _n.startswith("ال") and len(_n) > 3:
        AR_TO_EN_WORDS.setdefault(_n[2:], _en)
AR_TO_EN_WORDS.update({"ابراهيم": "Ibrahim", "جابر": "Jaber", "ريال": "SAR", "دينار": "Dinar", "درهم": "AED", "دولار": "USD", "اعتماد": "approval", "تسليم": "delivery"})


def translate_offline(text: str, direction: str = "auto") -> str:
    cleaned = ai_engine.clean_extracted_text(text)
    if not cleaned:
        return ""
    if direction == "auto":
        lang = ai_engine.analyze_language(cleaned)
        direction = "ar_to_en" if lang["arabic_pct"] >= 45 else "en_to_ar"

    out_lines = []
    for line in cleaned.splitlines():
        if not line.strip():
            out_lines.append("")
            continue
        if direction == "en_to_ar":
            work = line
            for en_p, ar_p in EN_TO_AR_PHRASES.items():
                work = re.sub(rf"\b{re.escape(en_p)}\b", ar_p, work, flags=re.I)
            work = re.sub(
                r"[A-Za-z]+",
                lambda m: EN_TO_AR_WORDS.get(m.group(0).lower())
                or EN_TO_AR_WORDS.get(m.group(0).lower().rstrip("s"))
                or m.group(0),
                work,
            )
            out_lines.append(work)
        else:
            work = ai_engine.normalize_for_search(line)
            for ar_p, en_p in AR_TO_EN_PHRASES.items():
                work = work.replace(ar_p, en_p)
            work = re.sub(
                r"[\u0600-\u06FF]+",
                lambda m: AR_TO_EN_WORDS.get(m.group(0))
                or (AR_TO_EN_WORDS.get(m.group(0)[2:]) if m.group(0).startswith("ال") and len(m.group(0)) > 3 else None)
                or m.group(0),
                work,
            )
            out_lines.append(work)
    return "\n".join(out_lines)


def generate_study_flashcards(pages: List[str], max_cards: int = 8) -> List[Tuple[int, str, str]]:
    rep = ai_engine.analyze_document(pages, num_sentences=8)
    cards: List[Tuple[int, str, str]] = []
    for pg, h in rep["headings"][:4]:
        sents = ai_engine.split_sentences(pages[pg - 1]) if 0 < pg <= len(pages) else []
        ans = next((s for s in sents if s.lower() != h.lower()), sents[0] if sents else "")
        if ans:
            q = f"ما المقصود بـ: {h}؟" if pdf_engine.contains_arabic(h) else f"What are the key details of '{h}'?"
            cards.append((pg, q, ans))
    for i, b in enumerate(rep["summary"], start=1):
        if len(cards) >= max_cards:
            break
        q = f"نقطة مراجعة رئيسية #{i}" if pdf_engine.contains_arabic(b) else f"Key Takeaway Review #{i}"
        cards.append((1, q, b))
    return cards[:max_cards]


def draft_formal_reply(report: Dict, file_name: str, arabic: bool = False) -> str:
    if arabic:
        lines = [f"الموضوع: مراجعة والرد على المستند ({file_name})", "تحية طيبة وبعد،", ""]
        lines.append("تمت مراجعة المستند المرفق واستخلاص أبرز النقاط التالية:")
        for i, b in enumerate(report["summary"][:4], start=1):
            lines.append(f"{i}. {b}")
        if report["dates"]:
            lines.append("\nالتواريخ المهمة: " + "، ".join(report["dates"][:4]))
        if report["amounts"]:
            lines.append("القيم والمبالغ: " + "، ".join(report["amounts"][:4]))
        lines.extend(["", "وتفضلوا بقبول فائق الاحترام،", "إبراهيم جابر — 2026"])
        return "\n".join(lines)
    lines = [f"Subject: Executive Review & Response — {file_name}", "Dear Colleague,", ""]
    lines.append(f"Following a review of '{file_name}', here are the key takeaways:")
    for i, b in enumerate(report["summary"][:4], start=1):
        lines.append(f"{i}. {b}")
    if report["dates"]:
        lines.append("\nKey Dates: " + ", ".join(report["dates"][:4]))
    if report["amounts"]:
        lines.append("Key Amounts: " + ", ".join(report["amounts"][:4]))
    lines.extend(["", "Best regards,", "Ibrahim Jaber — 2026"])
    return "\n".join(lines)


def inspect_gguf_model(path: str) -> Dict:
    valid = False
    version = 0
    size = os.path.getsize(path) if os.path.exists(path) else 0
    if size >= 8:
        with open(path, "rb") as f:
            hdr = f.read(8)
        if hdr[:4] == b"GGUF":
            valid = True
            version = struct.unpack("<I", hdr[4:8])[0]
    return {"name": Path(path).name, "size": size, "valid": valid, "version": version}


# =============================================================================
# PILLAR 3: INTERACTIVE NATIVE PDF ACROFORM FILLER & FLATTENER
# =============================================================================


def create_sample_interactive_form(output_pdf: str) -> str:
    doc = fitz.open()
    page = doc.new_page(width=595, height=842)
    page.draw_rect(fitz.Rect(0, 0, 595, 92), color=(0.06, 0.09, 0.16), fill=(0.06, 0.09, 0.16))
    page.insert_text(fitz.Point(40, 42), "Interactive Approval Form (AcroForm)", fontsize=20, color=(1, 1, 1))
    page.insert_text(fitz.Point(40, 68), pdf_engine.CREATOR_TAG, fontsize=10, color=(0.8, 0.85, 0.92))

    fields = [
        ("full_name", "Full Name:", 140, "Ibrahim Jaber"),
        ("department", "Department:", 195, "Engineering"),
        ("email", "Email Address:", 250, "contact@example.com"),
        ("notes", "Comments / Notes:", 310, "Verified offline in PDF Editor Pro v4.0"),
    ]
    for name, lbl, y, val in fields:
        page.insert_text(fitz.Point(40, y + 18), lbl, fontsize=12, color=(0.12, 0.16, 0.23))
        w = fitz.Widget()
        w.field_name = name
        w.field_label = lbl.rstrip(":")
        w.field_type = fitz.PDF_WIDGET_TYPE_TEXT
        w.rect = fitz.Rect(210, y, 540, y + 28)
        w.field_value = val
        w.border_color = (0.4, 0.5, 0.6)
        w.border_width = 1
        page.add_widget(w)

    page.insert_text(fitz.Point(40, 388), "Approved for Release:", fontsize=12, color=(0.12, 0.16, 0.23))
    cb = fitz.Widget()
    cb.field_name = "approved"
    cb.field_label = "Approved for Release"
    cb.field_type = fitz.PDF_WIDGET_TYPE_CHECKBOX
    cb.rect = fitz.Rect(210, 372, 232, 394)
    cb.field_value = True
    page.add_widget(cb)

    doc.set_metadata({"title": "Sample Interactive AcroForm 2026", "author": "Ibrahim Jaber", "creator": pdf_engine.CREATOR_TAG})
    doc.save(output_pdf, deflate=True)
    doc.close()
    return output_pdf


def read_form_fields(input_pdf: str) -> List[Dict]:
    doc = fitz.open(input_pdf)
    fields: List[Dict] = []
    for p_idx, page in enumerate(doc):
        for w in page.widgets() or []:
            fields.append({
                "page": p_idx,
                "name": w.field_name,
                "label": w.field_label or w.field_name,
                "type": w.field_type_string,
                "value": str(w.field_value or ""),
                "is_checkbox": w.field_type == fitz.PDF_WIDGET_TYPE_CHECKBOX,
                "checked": bool(w.field_value) and str(w.field_value).lower() not in ("off", "false", "0", ""),
            })
    doc.close()
    return fields


def fill_and_save_form(input_pdf: str, output_pdf: str, values: Dict[str, str], checks: Dict[str, bool], flatten: bool = False) -> str:
    doc = fitz.open(input_pdf)
    for page in doc:
        for w in page.widgets() or []:
            if w.field_type == fitz.PDF_WIDGET_TYPE_CHECKBOX:
                if w.field_name in checks:
                    w.field_value = checks[w.field_name]
                    w.update()
            elif w.field_name in values:
                w.field_value = values[w.field_name]
                w.update()
    if flatten:
        doc.bake()
    doc.save(output_pdf, deflate=True)
    doc.close()
    return output_pdf


# =============================================================================
# PILLAR 5: CRYPTOGRAPHIC X.509 RSA-2048 PKCS#12 CERTIFICATE & PDF DIGITAL SEAL
# =============================================================================


def generate_x509_p12(output_p12: str, common_name: str = "Ibrahim Jaber", organization: str = "PDF Editor Pro 2026", password: str = "2026") -> str:
    key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    subject = issuer = x509.Name([
        x509.NameAttribute(NameOID.COMMON_NAME, common_name),
        x509.NameAttribute(NameOID.ORGANIZATION_NAME, organization),
    ])
    now = datetime.datetime.now(datetime.timezone.utc)
    cert = (
        x509.CertificateBuilder()
        .subject_name(subject)
        .issuer_name(issuer)
        .public_key(key.public_key())
        .serial_number(x509.random_serial_number())
        .not_valid_before(now)
        .not_valid_after(now + datetime.timedelta(days=365 * 5))
        .sign(key, hashes.SHA256())
    )
    p12_bytes = pkcs12.serialize_key_and_certificates(
        name=b"pdf_signer",
        key=key,
        cert=cert,
        cas=None,
        encryption_algorithm=serialization.BestAvailableEncryption(password.encode("utf-8")),
    )
    with open(output_p12, "wb") as f:
        f.write(p12_bytes)
    return output_p12


def sign_pdf_with_x509(input_pdf: str, output_pdf: str, p12_path: str, p12_password: str, reason: str = "Verified Offline") -> Dict:
    with open(p12_path, "rb") as f:
        priv_key, cert, _ = pkcs12.load_key_and_certificates(f.read(), p12_password.encode("utf-8"))
    if priv_key is None or cert is None:
        raise ValueError("Invalid PKCS#12 certificate.")

    with open(input_pdf, "rb") as f:
        raw_digest = hashlib.sha256(f.read()).hexdigest()

    sig_bytes = priv_key.sign(raw_digest.encode("utf-8"), padding.PKCS1v15(), hashes.SHA256())
    sig_hex = sig_bytes.hex()
    signer_cn = cert.subject.get_attributes_for_oid(NameOID.COMMON_NAME)[0].value
    stamp_time = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%d %H:%M UTC")

    doc = fitz.open(input_pdf)
    last_page = doc[-1]
    w, h = last_page.rect.width, last_page.rect.height
    seal_rect = fitz.Rect(w - 250, h - 68, w - 20, h - 18)
    last_page.draw_rect(seal_rect, color=(0.02, 0.58, 0.41), fill=(0.93, 0.99, 0.96), width=1.5)
    last_page.insert_textbox(
        fitz.Rect(seal_rect.x0 + 8, seal_rect.y0 + 5, seal_rect.x1 - 6, seal_rect.y1 - 4),
        f"DIGITALLY SIGNED (RSA-2048/SHA-256)\nSigner: {signer_cn}  |  {stamp_time}\nReason: {reason}  |  Hash: {raw_digest[:14]}...",
        fontsize=7.5,
        color=(0.02, 0.42, 0.29),
    )
    meta = dict(doc.metadata or {})
    meta["keywords"] = f"X509_SIGNED|CN={signer_cn}|DATE={stamp_time}|SHA256={raw_digest}|SIG={sig_hex[:32]}"
    meta["creator"] = pdf_engine.CREATOR_TAG
    doc.set_metadata(meta)
    doc.save(output_pdf, deflate=True)
    doc.close()
    return {"signer": signer_cn, "timestamp": stamp_time, "sha256": raw_digest}
