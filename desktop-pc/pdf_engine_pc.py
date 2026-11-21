"""
PDF Pro Studio PC — 100% Offline Pro PDF Engine & Universal Format Converter.
Supports PDF <-> Word (.docx), Excel (.xlsx), CSV, PowerPoint (.pptx), Images, HTML, TXT,
plus Redaction, AES-256 Encryption, Compression, Grayscale, Watermarks, Bates Numbering,
Page Organizing, QR Stamps, Signatures, Compare Diff, and full Arabic RTL text support.

Programmed by Ibrahim Jaber 2026 | برمجة إبراهيم جابر 2026
"""

from __future__ import annotations

import csv
import html as html_mod
import io
import math
import os
import re
from pathlib import Path
from typing import Dict, List, Optional, Tuple

import fitz  # PyMuPDF
from PIL import Image, ImageDraw, ImageFont, ImageOps
import qrcode
from docx import Document as DocxDocument
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from openpyxl import Workbook, load_workbook
from pptx import Presentation
from pptx.util import Inches

try:
    import arabic_reshaper
    from bidi.algorithm import get_display
except Exception:
    arabic_reshaper = None
    get_display = None

CREATOR_TAG = "PDF Pro Studio — Programmed by Ibrahim Jaber 2026"


def contains_arabic(text: str) -> bool:
    return any("\u0600" <= ch <= "\u06ff" or "\u0750" <= ch <= "\u077f" for ch in text)


def shape_arabic(text: str) -> str:
    """Reshapes Arabic ligatures and applies BiDi ordering for crisp canvas/PDF drawing."""
    if not text or not contains_arabic(text):
        return text
    if arabic_reshaper is not None and get_display is not None:
        lines = []
        for line in text.splitlines():
            if contains_arabic(line):
                reshaped = arabic_reshaper.reshape(line)
                lines.append(get_display(reshaped))
            else:
                lines.append(line)
        return "\n".join(lines)
    return text


def parse_page_ranges(expression: str, total_pages: int) -> List[int]:
    """Parses 1-based range expression (e.g. '1-3, 5, 7-9') into 0-based distinct indices."""
    if total_pages <= 0:
        raise ValueError("Document has no pages.")
    expr = expression.strip()
    if not expr:
        raise ValueError("Enter a page range such as 1-3, 5.")
    seen = set()
    indices: List[int] = []
    for raw in expr.split(","):
        tok = raw.strip()
        if not tok:
            continue
        if "-" in tok:
            parts = tok.split("-")
            if len(parts) != 2:
                raise ValueError(f"Invalid range '{tok}'.")
            start, end = int(parts[0].strip()), int(parts[1].strip())
            if not (1 <= start <= total_pages and 1 <= end <= total_pages):
                raise ValueError(f"Range '{tok}' out of bounds (1..{total_pages}).")
            if start > end:
                raise ValueError(f"Start page must be <= end page in '{tok}'.")
            for p in range(start, end + 1):
                if p - 1 not in seen:
                    seen.add(p - 1)
                    indices.append(p - 1)
        else:
            p = int(tok)
            if not (1 <= p <= total_pages):
                raise ValueError(f"Page {p} out of bounds (1..{total_pages}).")
            if p - 1 not in seen:
                seen.add(p - 1)
                indices.append(p - 1)
    if not indices:
        raise ValueError("No valid pages found.")
    return indices


def format_size(num_bytes: int) -> str:
    if num_bytes < 1024:
        return f"{num_bytes} B"
    kb = num_bytes / 1024.0
    if kb < 1024:
        return f"{kb:.1f} KB"
    mb = kb / 1024.0
    if mb < 1024:
        return f"{mb:.2f} MB"
    return f"{mb / 1024.0:.2f} GB"


def _load_unicode_font(size: int = 24) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
    """Finds a Unicode/Arabic-capable TrueType font on Windows or Linux."""
    candidates = [
        r"C:\Windows\Fonts\arial.ttf",
        r"C:\Windows\Fonts\tahoma.ttf",
        r"C:\Windows\Fonts\segoeui.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/usr/share/fonts/truetype/freefont/FreeSans.ttf",
        "/usr/share/fonts/truetype/noto/NotoSansArabic-Regular.ttf",
    ]
    for path in candidates:
        if os.path.exists(path):
            try:
                return ImageFont.truetype(path, size=size)
            except Exception:
                pass
    return ImageFont.load_default()


def _render_unicode_label_png(
    text: str,
    font_size: int = 32,
    color_rgb: Tuple[int, int, int] = (15, 23, 42),
    bg_rgba: Optional[Tuple[int, int, int, int]] = None,
    border_rgb: Optional[Tuple[int, int, int]] = None,
    padding: int = 16,
    rotate_deg: float = 0.0,
) -> bytes:
    """Renders English or Arabic Unicode text onto a crisp transparent PNG for PDF embedding."""
    shaped = shape_arabic(text)
    font = _load_unicode_font(font_size)
    dummy = Image.new("RGBA", (10, 10), (0, 0, 0, 0))
    d = ImageDraw.Draw(dummy)
    bbox = d.multiline_textbbox((0, 0), shaped, font=font, spacing=8)
    tw = max(40, int(bbox[2] - bbox[0]))
    th = max(28, int(bbox[3] - bbox[1]))

    img = Image.new("RGBA", (tw + padding * 2, th + padding * 2), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    if bg_rgba is not None:
        draw.rounded_rectangle(
            [2, 2, img.width - 3, img.height - 3],
            radius=12,
            fill=bg_rgba,
            outline=(*border_rgb, 255) if border_rgb else None,
            width=4 if border_rgb else 0,
        )
    draw.multiline_text((padding - bbox[0], padding - bbox[1]), shaped, font=font, fill=(*color_rgb, 255), spacing=8)
    if rotate_deg != 0.0:
        img = img.rotate(rotate_deg, expand=True, resample=Image.BICUBIC)
    buf = io.BytesIO()
    img.save(buf, format="PNG")
    return buf.getvalue()


def _hex_to_rgb01(hex_color: str) -> Tuple[float, float, float]:
    h = hex_color.lstrip("#")
    if len(h) != 6:
        return (0.86, 0.15, 0.15)
    return (int(h[0:2], 16) / 255.0, int(h[2:4], 16) / 255.0, int(h[4:6], 16) / 255.0)


def _hex_to_rgb255(hex_color: str) -> Tuple[int, int, int]:
    h = hex_color.lstrip("#")
    if len(h) != 6:
        return (220, 38, 38)
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16))


# =============================================================================
# PRO EDITOR ANNOTATION ENGINE (Permanent PDF Embedding + True Redaction!)
# =============================================================================


def apply_annotations_to_pdf(
    input_pdf: str,
    output_pdf: str,
    annotations: List[Dict],
    password: Optional[str] = None,
) -> str:
    """
    Permanently embeds all Pro Editor annotations into a PDF:
    - pen, highlighter, underline, strike, rectangle, oval, arrow
    - redact (TRUE PyMuPDF content redaction removing underlying text!)
    - whiteout (opaque white eraser patch)
    - text (English & Arabic RTL), stamp, sticky_note, image, qr_code, signature
    """
    doc = fitz.open(input_pdf)
    if doc.is_encrypted and password:
        doc.authenticate(password)

    by_page: Dict[int, List[Dict]] = {}
    for ann in annotations:
        by_page.setdefault(int(ann["page"]), []).append(ann)

    for page_idx, items in by_page.items():
        if not (0 <= page_idx < len(doc)):
            continue
        page = doc[page_idx]
        rect = page.rect
        w, h = rect.width, rect.height
        has_redactions = False

        # First pass: apply true PDF redactions if present
        for ann in items:
            if ann["type"] == "redact":
                x1, y1 = ann["x1"] * w, ann["y1"] * h
                x2, y2 = ann["x2"] * w, ann["y2"] * h
                r = fitz.Rect(min(x1, x2), min(y1, y2), max(x1, x2), max(y1, y2))
                page.add_redact_annot(r, fill=(0, 0, 0))
                has_redactions = True

        if has_redactions:
            page.apply_redactions()

        # Second pass: draw all other annotations
        for ann in items:
            kind = ann["type"]
            if kind == "redact":
                continue

            rgb = _hex_to_rgb01(ann.get("color", "#DC2626"))
            rgb255 = _hex_to_rgb255(ann.get("color", "#DC2626"))

            if kind in ("pen", "highlighter"):
                pts = ann.get("points", [])
                if len(pts) < 2:
                    continue
                shape = page.new_shape()
                fitz_pts = [fitz.Point(px * w, py * h) for px, py in pts]
                shape.draw_polyline(fitz_pts)
                stroke_w = max(1.5, float(ann.get("width_norm", 0.004)) * w)
                shape.finish(
                    color=rgb,
                    width=stroke_w,
                    stroke_opacity=0.35 if kind == "highlighter" else 1.0,
                    lineCap=1,
                    lineJoin=1,
                )
                shape.commit()

            elif kind == "whiteout":
                x1, y1 = ann["x1"] * w, ann["y1"] * h
                x2, y2 = ann["x2"] * w, ann["y2"] * h
                r = fitz.Rect(min(x1, x2), min(y1, y2), max(x1, x2), max(y1, y2))
                page.draw_rect(r, color=(1, 1, 1), fill=(1, 1, 1))

            elif kind in ("rectangle", "oval", "arrow", "underline", "strike"):
                x1, y1 = ann["x1"] * w, ann["y1"] * h
                x2, y2 = ann["x2"] * w, ann["y2"] * h
                stroke_w = max(1.5, float(ann.get("width_norm", 0.004)) * w)
                r = fitz.Rect(min(x1, x2), min(y1, y2), max(x1, x2), max(y1, y2))
                if kind == "rectangle":
                    page.draw_rect(r, color=rgb, width=stroke_w)
                elif kind == "oval":
                    page.draw_oval(r, color=rgb, width=stroke_w)
                elif kind in ("underline", "strike"):
                    col = (0.86, 0.15, 0.15) if kind == "strike" else rgb
                    page.draw_line(fitz.Point(x1, y1), fitz.Point(x2, y1), color=col, width=stroke_w)
                elif kind == "arrow":
                    page.draw_line(fitz.Point(x1, y1), fitz.Point(x2, y2), color=rgb, width=stroke_w)
                    angle = math.atan2(y2 - y1, x2 - x1)
                    head = max(8.0, min(18.0, w * 0.025))
                    for delta in (-0.48, 0.48):
                        ax = x2 - head * math.cos(angle + delta)
                        ay = y2 - head * math.sin(angle + delta)
                        page.draw_line(fitz.Point(x2, y2), fitz.Point(ax, ay), color=rgb, width=stroke_w)

            elif kind == "text":
                txt = ann.get("text", "")
                if not txt.strip():
                    continue
                size_pt = int(ann.get("font_size", 16))
                png = _render_unicode_label_png(txt, font_size=size_pt * 2, color_rgb=rgb255, padding=6)
                pil = Image.open(io.BytesIO(png))
                dw, dh = min(w * 0.75, pil.width / 2.0), min(h * 0.5, pil.height / 2.0)
                px = min(max(0.0, ann["x"] * w), max(0.0, w - dw))
                py = min(max(0.0, ann["y"] * h), max(0.0, h - dh))
                page.insert_image(fitz.Rect(px, py, px + dw, py + dh), stream=png)

            elif kind == "stamp":
                label = ann.get("label", "APPROVED")
                png = _render_unicode_label_png(
                    label,
                    font_size=34,
                    color_rgb=rgb255,
                    bg_rgba=(*rgb255, 28),
                    border_rgb=rgb255,
                    padding=18,
                )
                pil = Image.open(io.BytesIO(png))
                dw = min(190.0, max(110.0, w * 0.28))
                dh = dw * (pil.height / max(1, pil.width))
                px = min(max(0.0, ann["x"] * w - dw / 2), w - dw)
                py = min(max(0.0, ann["y"] * h - dh / 2), h - dh)
                page.insert_image(fitz.Rect(px, py, px + dw, py + dh), stream=png)

            elif kind == "sticky_note":
                txt = ann.get("text", "Note")
                png = _render_unicode_label_png(
                    txt,
                    font_size=24,
                    color_rgb=(30, 41, 59),
                    bg_rgba=(254, 240, 138, 245),
                    border_rgb=(217, 119, 6),
                    padding=14,
                )
                pil = Image.open(io.BytesIO(png))
                dw = min(w * 0.42, pil.width / 1.8)
                dh = dw * (pil.height / max(1, pil.width))
                px = min(max(0.0, ann["x"] * w), max(0.0, w - dw))
                py = min(max(0.0, ann["y"] * h), max(0.0, h - dh))
                page.insert_image(fitz.Rect(px, py, px + dw, py + dh), stream=png)

            elif kind in ("image", "signature", "qr_code"):
                img_path = ann.get("image_path", "")
                if img_path and os.path.exists(img_path):
                    dw = float(ann.get("width_norm", 0.24)) * w
                    dh = float(ann.get("height_norm", 0.14)) * h
                    px = min(max(0.0, ann["x"] * w - dw / 2), max(0.0, w - dw))
                    py = min(max(0.0, ann["y"] * h - dh / 2), max(0.0, h - dh))
                    page.insert_image(fitz.Rect(px, py, px + dw, py + dh), filename=img_path)

    meta = doc.metadata or {}
    meta["creator"] = CREATOR_TAG
    doc.set_metadata(meta)

    same_file = os.path.abspath(input_pdf) == os.path.abspath(output_pdf)
    if same_file:
        tmp = output_pdf + ".tmp_save.pdf"
        doc.save(tmp, garbage=3, deflate=True)
        doc.close()
        os.replace(tmp, output_pdf)
    else:
        doc.save(output_pdf, garbage=3, deflate=True)
        doc.close()
    return output_pdf


def generate_qr_png(content: str, output_png: str) -> str:
    qr = qrcode.QRCode(box_size=8, border=2)
    qr.add_data(content or CREATOR_TAG)
    qr.make(fit=True)
    img = qr.make_image(fill_color="black", back_color="white")
    img.save(output_png)
    return output_png


# =============================================================================
# UNIVERSAL OFFLINE FORMAT CONVERTER (PDF <-> WORD / EXCEL / CSV / PPTX / IMG / HTML / TXT)
# =============================================================================


def pdf_to_docx(input_pdf: str, output_docx: str, password: Optional[str] = None) -> str:
    doc = fitz.open(input_pdf)
    if doc.is_encrypted and password:
        doc.authenticate(password)
    word = DocxDocument()
    word.core_properties.author = "Ibrahim Jaber"
    word.core_properties.comments = CREATOR_TAG

    for idx, page in enumerate(doc):
        text = page.get_text("text")
        for line in text.splitlines():
            clean = line.strip()
            if not clean:
                continue
            p = word.add_paragraph(clean)
            if contains_arabic(clean):
                pPr = p._p.get_or_add_pPr()
                bidi = OxmlElement("w:bidi")
                pPr.append(bidi)
        if idx < len(doc) - 1:
            word.add_page_break()
    doc.close()
    word.save(output_docx)
    return output_docx


def pdf_to_xlsx(input_pdf: str, output_xlsx: str, password: Optional[str] = None) -> str:
    doc = fitz.open(input_pdf)
    if doc.is_encrypted and password:
        doc.authenticate(password)
    wb = Workbook()
    ws = wb.active
    ws.title = "PDF_Export"
    ws.append(["Page", "Column 1", "Column 2", "Column 3", "Column 4"])
    for idx, page in enumerate(doc):
        for line in page.get_text("text").splitlines():
            clean = line.strip()
            if not clean:
                continue
            cols = [c.strip() for c in re.split(r"\t+|\s{2,}", clean) if c.strip()]
            ws.append([f"Page {idx + 1}"] + cols)
    doc.close()
    wb.save(output_xlsx)
    return output_xlsx


def pdf_to_csv(input_pdf: str, output_csv: str, password: Optional[str] = None) -> str:
    doc = fitz.open(input_pdf)
    if doc.is_encrypted and password:
        doc.authenticate(password)
    with open(output_csv, "w", encoding="utf-8-sig", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(["Page", "Column 1", "Column 2", "Column 3", "Column 4"])
        for idx, page in enumerate(doc):
            for line in page.get_text("text").splitlines():
                clean = line.strip()
                if not clean:
                    continue
                cols = [c.strip() for c in re.split(r"\t+|\s{2,}", clean) if c.strip()]
                writer.writerow([f"Page {idx + 1}"] + cols)
    doc.close()
    return output_csv


def pdf_to_pptx(input_pdf: str, output_pptx: str, password: Optional[str] = None) -> str:
    doc = fitz.open(input_pdf)
    if doc.is_encrypted and password:
        doc.authenticate(password)
    prs = Presentation()
    prs.slide_width = Inches(10)
    prs.slide_height = Inches(7.5)
    blank_layout = prs.slide_layouts[6]

    for page in doc:
        pix = page.get_pixmap(dpi=150)
        img_stream = io.BytesIO(pix.tobytes("png"))
        slide = prs.slides.add_slide(blank_layout)
        slide.shapes.add_picture(img_stream, Inches(0.25), Inches(0.25), width=Inches(9.5))
    doc.close()
    prs.save(output_pptx)
    return output_pptx


def pdf_to_images(
    input_pdf: str,
    output_dir: str,
    fmt: str = "png",
    dpi: int = 180,
    password: Optional[str] = None,
) -> List[str]:
    os.makedirs(output_dir, exist_ok=True)
    doc = fitz.open(input_pdf)
    if doc.is_encrypted and password:
        doc.authenticate(password)
    stem = Path(input_pdf).stem
    out_files: List[str] = []
    ext = "png" if fmt.lower() == "png" else "jpg"
    for idx, page in enumerate(doc):
        pix = page.get_pixmap(dpi=dpi)
        out_path = os.path.join(output_dir, f"{stem}_page_{idx + 1}.{ext}")
        if ext == "png":
            pix.save(out_path)
        else:
            img = Image.frombytes("RGB", [pix.width, pix.height], pix.samples)
            img.save(out_path, "JPEG", quality=92)
        out_files.append(out_path)
    doc.close()
    return out_files


def pdf_to_html(input_pdf: str, output_html: str, password: Optional[str] = None) -> str:
    doc = fitz.open(input_pdf)
    if doc.is_encrypted and password:
        doc.authenticate(password)
    parts = [
        "<!DOCTYPE html><html><head><meta charset='UTF-8'>",
        f"<title>{html_mod.escape(Path(input_pdf).stem)}</title>",
        "<style>body{font-family:system-ui,sans-serif;max-width:860px;margin:24px auto;background:#f8fafc;color:#1e293b}",
        ".pg{background:#fff;padding:24px;border-radius:10px;margin-bottom:18px;box-shadow:0 1px 4px rgba(0,0,0,.08)}</style></head><body>",
    ]
    for idx, page in enumerate(doc):
        txt = page.get_text("text")
        direction = "rtl" if contains_arabic(txt) else "auto"
        parts.append(f"<div class='pg' dir='{direction}'><h4>Page {idx + 1}</h4>")
        for line in txt.splitlines():
            if line.strip():
                parts.append(f"<p>{html_mod.escape(line.strip())}</p>")
        parts.append("</div>")
    parts.append(f"<footer style='text-align:center;color:#64748b'>{CREATOR_TAG}</footer></body></html>")
    doc.close()
    with open(output_html, "w", encoding="utf-8") as f:
        f.write("\n".join(parts))
    return output_html


def pdf_to_txt(input_pdf: str, output_txt: str, password: Optional[str] = None) -> str:
    doc = fitz.open(input_pdf)
    if doc.is_encrypted and password:
        doc.authenticate(password)
    full = "\n\n".join(page.get_text("text").strip() for page in doc)
    doc.close()
    with open(output_txt, "w", encoding="utf-8") as f:
        f.write(full)
    return output_txt


def _render_lines_to_pdf(title: str, lines: List[str], output_pdf: str) -> str:
    """Renders English & Arabic paragraphs onto crisp paginated A4 PDF pages."""
    page_w, page_h = 1240, 1754
    margin = 84
    title_font = _load_unicode_font(38)
    body_font = _load_unicode_font(26)
    footer_font = _load_unicode_font(18)

    out_doc = fitz.open()
    img = Image.new("RGB", (page_w, page_h), "white")
    draw = ImageDraw.Draw(img)
    y = margin
    page_num = 1

    def flush():
        nonlocal img, draw, y, page_num
        draw.text((margin, page_h - 46), f"Page {page_num}  -  {CREATOR_TAG}", font=footer_font, fill=(100, 116, 139))
        buf = io.BytesIO()
        img.save(buf, format="JPEG", quality=88)
        p = out_doc.new_page(width=595, height=842)
        p.insert_image(p.rect, stream=buf.getvalue())
        page_num += 1
        img = Image.new("RGB", (page_w, page_h), "white")
        draw = ImageDraw.Draw(img)
        y = margin

    if title:
        shaped_title = shape_arabic(title)
        draw.text((margin, y), shaped_title, font=title_font, fill=(15, 23, 42))
        y += 66

    max_chars = 72
    for raw_line in lines:
        text = raw_line.strip()
        if not text:
            y += 22
            continue
        # Wrap long lines cleanly
        chunks = [text[i : i + max_chars] for i in range(0, len(text), max_chars)]
        for chunk in chunks:
            if y > page_h - margin - 60:
                flush()
            shaped = shape_arabic(chunk)
            if contains_arabic(chunk):
                bbox = draw.textbbox((0, 0), shaped, font=body_font)
                tw = bbox[2] - bbox[0]
                draw.text((page_w - margin - tw, y), shaped, font=body_font, fill=(30, 41, 59))
            else:
                draw.text((margin, y), shaped, font=body_font, fill=(30, 41, 59))
            y += 38

    flush()
    out_doc.set_metadata({"title": title, "author": "Ibrahim Jaber", "creator": CREATOR_TAG})
    out_doc.save(output_pdf, deflate=True)
    out_doc.close()
    return output_pdf


def _render_table_to_pdf(title: str, rows: List[List[str]], output_pdf: str) -> str:
    if not rows:
        raise ValueError("No table rows found.")
    max_cols = max(1, min(10, max(len(r) for r in rows)))
    wide = max_cols > 5
    page_w, page_h = (1754, 1240) if wide else (1240, 1754)
    pdf_w, pdf_h = (842, 595) if wide else (595, 842)
    margin = 60
    col_w = (page_w - margin * 2) / max_cols
    row_h = 52

    title_font = _load_unicode_font(32)
    cell_font = _load_unicode_font(20)
    out_doc = fitz.open()
    img = Image.new("RGB", (page_w, page_h), "white")
    draw = ImageDraw.Draw(img)
    y = margin
    draw.text((margin, y), shape_arabic(title), font=title_font, fill=(15, 23, 42))
    y += 52

    def flush():
        nonlocal img, draw, y
        buf = io.BytesIO()
        img.save(buf, format="JPEG", quality=88)
        p = out_doc.new_page(width=pdf_w, height=pdf_h)
        p.insert_image(p.rect, stream=buf.getvalue())
        img = Image.new("RGB", (page_w, page_h), "white")
        draw = ImageDraw.Draw(img)
        y = margin

    for r_idx, row in enumerate(rows):
        if y + row_h > page_h - margin:
            flush()
        is_hdr = r_idx == 0
        bg = (15, 23, 42) if is_hdr else ((241, 245, 249) if r_idx % 2 == 0 else (255, 255, 255))
        fg = (255, 255, 255) if is_hdr else (30, 41, 59)
        draw.rectangle([margin, y, page_w - margin, y + row_h], fill=bg, outline=(203, 213, 225), width=2)
        for c in range(max_cols):
            x = margin + c * col_w
            draw.line([x, y, x, y + row_h], fill=(203, 213, 225), width=1)
            val = str(row[c] if c < len(row) and row[c] is not None else "")[:34]
            if val:
                draw.text((x + 8, y + 14), shape_arabic(val), font=cell_font, fill=fg)
        y += row_h

    flush()
    out_doc.set_metadata({"title": title, "author": "Ibrahim Jaber", "creator": CREATOR_TAG})
    out_doc.save(output_pdf, deflate=True)
    out_doc.close()
    return output_pdf


def docx_to_pdf(input_docx: str, output_pdf: str) -> str:
    word = DocxDocument(input_docx)
    lines = [p.text for p in word.paragraphs]
    for tbl in word.tables:
        for row in tbl.rows:
            lines.append("  |  ".join(cell.text.strip() for cell in row.cells))
    return _render_lines_to_pdf(Path(input_docx).stem, lines or [" "], output_pdf)


def xlsx_to_pdf(input_xlsx: str, output_pdf: str) -> str:
    wb = load_workbook(input_xlsx, data_only=True)
    ws = wb.active
    rows = [[("" if cell is None else str(cell)) for cell in row] for row in ws.iter_rows(values_only=True)]
    rows = [r for r in rows if any(c.strip() for c in r)]
    return _render_table_to_pdf(Path(input_xlsx).stem, rows, output_pdf)


def csv_to_pdf(input_csv: str, output_pdf: str) -> str:
    with open(input_csv, "r", encoding="utf-8-sig", newline="") as f:
        rows = [row for row in csv.reader(f) if any(c.strip() for c in row)]
    return _render_table_to_pdf(Path(input_csv).stem, rows, output_pdf)


def pptx_to_pdf(input_pptx: str, output_pdf: str) -> str:
    prs = Presentation(input_pptx)
    lines: List[str] = []
    for idx, slide in enumerate(prs.slides, start=1):
        lines.append(f"=== Slide {idx} ===")
        for shape in slide.shapes:
            if shape.has_text_frame:
                for p in shape.text_frame.paragraphs:
                    if p.text.strip():
                        lines.append(p.text.strip())
        lines.append("")
    return _render_lines_to_pdf(Path(input_pptx).stem, lines or [" "], output_pdf)


def text_or_html_to_pdf(input_file: str, output_pdf: str) -> str:
    with open(input_file, "r", encoding="utf-8-sig", errors="replace") as f:
        raw = f.read()
    if Path(input_file).suffix.lower() in (".html", ".htm"):
        raw = re.sub(r"(?is)<(script|style)[^>]*>.*?</(script|style)>", "", raw)
        raw = re.sub(r"(?i)<(br|p|div|li|tr|h[1-6])[^>]*>", "\n", raw)
        raw = html_mod.unescape(re.sub(r"<[^>]+>", " ", raw))
    return _render_lines_to_pdf(Path(input_file).stem, raw.splitlines(), output_pdf)


def images_to_pdf(image_paths: List[str], output_pdf: str) -> str:
    if not image_paths:
        raise ValueError("Select at least one image.")
    out_doc = fitz.open()
    for p_str in image_paths:
        img = ImageOps.exif_transpose(Image.open(p_str)).convert("RGB")
        landscape = img.width > img.height
        pw, ph = (842, 595) if landscape else (595, 842)
        page = out_doc.new_page(width=pw, height=ph)
        margin = 24
        avail_w, avail_h = pw - margin * 2, ph - margin * 2
        scale = min(avail_w / img.width, avail_h / img.height)
        dw, dh = img.width * scale, img.height * scale
        dx, dy = (pw - dw) / 2, (ph - dh) / 2
        buf = io.BytesIO()
        img.save(buf, format="JPEG", quality=90)
        page.insert_image(fitz.Rect(dx, dy, dx + dw, dy + dh), stream=buf.getvalue())
    out_doc.set_metadata({"creator": CREATOR_TAG, "author": "Ibrahim Jaber"})
    out_doc.save(output_pdf, deflate=True)
    out_doc.close()
    return output_pdf


def csv_to_xlsx(input_csv: str, output_xlsx: str) -> str:
    wb = Workbook()
    ws = wb.active
    ws.title = "Sheet1"
    with open(input_csv, "r", encoding="utf-8-sig", newline="") as f:
        for row in csv.reader(f):
            ws.append(row)
    wb.save(output_xlsx)
    return output_xlsx


def xlsx_to_csv(input_xlsx: str, output_csv: str) -> str:
    wb = load_workbook(input_xlsx, data_only=True)
    ws = wb.active
    with open(output_csv, "w", encoding="utf-8-sig", newline="") as f:
        writer = csv.writer(f)
        for row in ws.iter_rows(values_only=True):
            writer.writerow(["" if v is None else str(v) for v in row])
    return output_csv


# =============================================================================
# 16 OFFLINE PRO PDF TOOLS
# =============================================================================


def merge_pdfs(input_pdfs: List[str], output_pdf: str) -> str:
    if len(input_pdfs) < 2:
        raise ValueError("Select at least 2 PDF files to merge.")
    out = fitz.open()
    for path in input_pdfs:
        src = fitz.open(path)
        out.insert_pdf(src)
        src.close()
    out.set_metadata({"creator": CREATOR_TAG, "author": "Ibrahim Jaber"})
    out.save(output_pdf, garbage=3, deflate=True)
    out.close()
    return output_pdf


def split_pdf(input_pdf: str, output_dir: str, range_expr: str = "", split_every: bool = False) -> List[str]:
    os.makedirs(output_dir, exist_ok=True)
    src = fitz.open(input_pdf)
    stem = Path(input_pdf).stem
    outputs: List[str] = []
    if split_every:
        for i in range(len(src)):
            out = fitz.open()
            out.insert_pdf(src, from_page=i, to_page=i)
            out_path = os.path.join(output_dir, f"{stem}_page_{i + 1}.pdf")
            out.save(out_path, deflate=True)
            out.close()
            outputs.append(out_path)
    else:
        indices = parse_page_ranges(range_expr, len(src))
        out = fitz.open()
        for idx in indices:
            out.insert_pdf(src, from_page=idx, to_page=idx)
        out_path = os.path.join(output_dir, f"{stem}_split.pdf")
        out.save(out_path, deflate=True)
        out.close()
        outputs.append(out_path)
    src.close()
    return outputs


def organize_pages(input_pdf: str, output_pdf: str, ops: List[Tuple[Optional[int], int]]) -> str:
    """ops: list of (source_page_idx or None for blank A4, extra_rotation_deg)."""
    if not ops:
        raise ValueError("Must keep at least 1 page.")
    src = fitz.open(input_pdf)
    out = fitz.open()
    for src_idx, rot in ops:
        if src_idx is None:
            out.new_page(width=595, height=842)
        elif 0 <= src_idx < len(src):
            out.insert_pdf(src, from_page=src_idx, to_page=src_idx)
            if rot:
                p = out[-1]
                p.set_rotation((p.rotation + rot) % 360)
    out.set_metadata({"creator": CREATOR_TAG})
    out.save(output_pdf, garbage=3, deflate=True)
    out.close()
    src.close()
    return output_pdf


def compress_pdf(input_pdf: str, output_pdf: str, level: str = "medium") -> Tuple[int, int, int]:
    dpi_map = {"low": (150, 85), "medium": (120, 68), "high": (90, 48)}
    dpi, quality = dpi_map.get(level.lower(), (120, 68))
    orig_size = os.path.getsize(input_pdf)
    src = fitz.open(input_pdf)
    out = fitz.open()
    for page in src:
        pix = page.get_pixmap(dpi=dpi)
        img = Image.frombytes("RGB", [pix.width, pix.height], pix.samples)
        buf = io.BytesIO()
        img.save(buf, format="JPEG", quality=quality, optimize=True)
        p = out.new_page(width=page.rect.width, height=page.rect.height)
        p.insert_image(p.rect, stream=buf.getvalue())
    out.set_metadata({"creator": CREATOR_TAG})
    out.save(output_pdf, garbage=4, deflate=True)
    out.close()
    src.close()
    new_size = os.path.getsize(output_pdf)
    saved_pct = max(0, int((orig_size - new_size) / max(1, orig_size) * 100))
    return orig_size, new_size, saved_pct


def convert_to_grayscale_pdf(input_pdf: str, output_pdf: str, dpi: int = 140) -> Tuple[int, int, int]:
    orig_size = os.path.getsize(input_pdf)
    src = fitz.open(input_pdf)
    out = fitz.open()
    for page in src:
        pix = page.get_pixmap(dpi=dpi, colorspace=fitz.csGRAY)
        img = Image.frombytes("L", [pix.width, pix.height], pix.samples)
        buf = io.BytesIO()
        img.save(buf, format="JPEG", quality=78, optimize=True)
        p = out.new_page(width=page.rect.width, height=page.rect.height)
        p.insert_image(p.rect, stream=buf.getvalue())
    out.set_metadata({"creator": CREATOR_TAG})
    out.save(output_pdf, garbage=4, deflate=True)
    out.close()
    src.close()
    new_size = os.path.getsize(output_pdf)
    saved_pct = max(0, int((orig_size - new_size) / max(1, orig_size) * 100))
    return orig_size, new_size, saved_pct


def protect_pdf(input_pdf: str, output_pdf: str, password: str, allow_print: bool = True, allow_copy: bool = False) -> str:
    if not password.strip():
        raise ValueError("Password cannot be empty.")
    doc = fitz.open(input_pdf)
    perms = 0
    if allow_print:
        perms |= fitz.PDF_PERM_PRINT
    if allow_copy:
        perms |= fitz.PDF_PERM_COPY
    doc.save(
        output_pdf,
        encryption=fitz.PDF_ENCRYPT_AES_256,
        user_pw=password,
        owner_pw=password,
        permissions=perms,
    )
    doc.close()
    return output_pdf


def unlock_pdf(input_pdf: str, output_pdf: str, password: str) -> str:
    doc = fitz.open(input_pdf)
    if doc.is_encrypted:
        if not doc.authenticate(password):
            doc.close()
            raise ValueError("Incorrect PDF password.")
    doc.save(output_pdf, encryption=fitz.PDF_ENCRYPT_NONE)
    doc.close()
    return output_pdf


def add_watermark(
    input_pdf: str,
    output_pdf: str,
    text: str,
    opacity: float = 0.28,
    tiled: bool = False,
    color_hex: str = "#B91C1C",
) -> str:
    if not text.strip():
        raise ValueError("Watermark text cannot be empty.")
    rgb = _hex_to_rgb255(color_hex)
    alpha = int(max(0.08, min(0.9, opacity)) * 255)
    shaped = shape_arabic(text)
    font = _load_unicode_font(54)
    dummy = Image.new("RGBA", (10, 10), (0, 0, 0, 0))
    bbox = ImageDraw.Draw(dummy).textbbox((0, 0), shaped, font=font)
    tw, th = max(120, int(bbox[2] - bbox[0])), max(50, int(bbox[3] - bbox[1]))
    wm = Image.new("RGBA", (tw + 40, th + 40), (0, 0, 0, 0))
    ImageDraw.Draw(wm).text((20 - bbox[0], 20 - bbox[1]), shaped, font=font, fill=(*rgb, alpha))
    wm = wm.rotate(35, expand=True, resample=Image.BICUBIC)
    buf = io.BytesIO()
    wm.save(buf, format="PNG")
    wm_bytes = buf.getvalue()

    doc = fitz.open(input_pdf)
    for page in doc:
        w, h = page.rect.width, page.rect.height
        if not tiled:
            dw = w * 0.72
            dh = dw * (wm.height / max(1, wm.width))
            r = fitz.Rect((w - dw) / 2, (h - dh) / 2, (w + dw) / 2, (h + dh) / 2)
            page.insert_image(r, stream=wm_bytes)
        else:
            dw = w * 0.38
            dh = dw * (wm.height / max(1, wm.width))
            for row in range(3):
                for col in range(2):
                    x = w * (0.08 + col * 0.48)
                    y = h * (0.12 + row * 0.30)
                    page.insert_image(fitz.Rect(x, y, x + dw, y + dh), stream=wm_bytes)
    doc.save(output_pdf, deflate=True)
    doc.close()
    return output_pdf


def add_page_numbers(input_pdf: str, output_pdf: str, position: str = "bottom_center", fmt: str = "page_x_of_y") -> str:
    doc = fitz.open(input_pdf)
    total = len(doc)
    for idx, page in enumerate(doc):
        num = idx + 1
        if fmt == "number_only":
            label = str(num)
        elif fmt == "dash":
            label = f"- {num} -"
        else:
            label = f"Page {num} of {total}"
        w, h = page.rect.width, page.rect.height
        if position == "top_right":
            pt = fitz.Point(w - 110, 26)
        elif position == "bottom_right":
            pt = fitz.Point(w - 110, h - 20)
        else:
            pt = fitz.Point(w / 2 - 36, h - 20)
        page.insert_text(pt, label, fontsize=10.5, color=(0.28, 0.33, 0.41))
    doc.save(output_pdf, deflate=True)
    doc.close()
    return output_pdf


def add_headers_footers_bates(
    input_pdf: str,
    output_pdf: str,
    header_center: str = "",
    footer_left: str = CREATOR_TAG,
    bates_prefix: str = "DOC-",
    bates_start: int = 1,
) -> str:
    doc = fitz.open(input_pdf)
    for idx, page in enumerate(doc):
        w, h = page.rect.width, page.rect.height
        if header_center.strip():
            page.insert_text(fitz.Point(w * 0.35, 24), header_center.strip(), fontsize=9.5, color=(0.28, 0.33, 0.41))
        if footer_left.strip():
            page.insert_text(fitz.Point(28, h - 18), footer_left.strip(), fontsize=9, color=(0.28, 0.33, 0.41))
        if bates_prefix.strip():
            bates = f"{bates_prefix}{bates_start + idx:06d}"
            page.insert_text(fitz.Point(w - 125, h - 18), bates, fontsize=9.5, color=(0.72, 0.11, 0.11))
    doc.save(output_pdf, deflate=True)
    doc.close()
    return output_pdf


def compare_pdfs(file_a: str, file_b: str) -> str:
    doc_a = fitz.open(file_a)
    doc_b = fitz.open(file_b)
    pages_a = [p.get_text("text").strip() for p in doc_a]
    pages_b = [p.get_text("text").strip() for p in doc_b]
    doc_a.close()
    doc_b.close()

    max_p = max(len(pages_a), len(pages_b))
    lines = [
        f"PDF Comparison Report — {CREATOR_TAG}",
        f"Document A: {Path(file_a).name} ({len(pages_a)} pages)",
        f"Document B: {Path(file_b).name} ({len(pages_b)} pages)",
        "-" * 56,
    ]
    diff_count = 0
    for i in range(max_p):
        a = pages_a[i] if i < len(pages_a) else None
        b = pages_b[i] if i < len(pages_b) else None
        if a != b:
            diff_count += 1
            lines.append(f"\n[Page {i + 1} Differs]")
            if a is None:
                lines.append(f"  + Added page in Document B ({len(b or '')} chars)")
            elif b is None:
                lines.append(f"  - Removed page from Document A ({len(a)} chars)")
            else:
                set_a = {ln.strip() for ln in a.splitlines() if ln.strip()}
                set_b = {ln.strip() for ln in b.splitlines() if ln.strip()}
                for rem in list(set_a - set_b)[:5]:
                    lines.append(f"  - A: {rem[:90]}")
                for add in list(set_b - set_a)[:5]:
                    lines.append(f"  + B: {add[:90]}")
    if diff_count == 0:
        lines.append(f"\nAll {max_p} page(s) have identical text content.")
    return "\n".join(lines)


def read_metadata(input_pdf: str) -> Dict[str, str]:
    doc = fitz.open(input_pdf)
    meta = dict(doc.metadata or {})
    meta["pages"] = str(len(doc))
    doc.close()
    return meta


def update_metadata(input_pdf: str, output_pdf: str, meta: Dict[str, str]) -> str:
    doc = fitz.open(input_pdf)
    current = dict(doc.metadata or {})
    current.update(meta)
    current["creator"] = meta.get("creator") or CREATOR_TAG
    doc.set_metadata(current)
    doc.save(output_pdf, deflate=True)
    doc.close()
    return output_pdf


def create_sample_welcome_pdf(output_pdf: str) -> str:
    doc = fitz.open()
    pages_spec = [
        (
            "PDF Pro Studio PC — Welcome Guide",
            "100% Offline Viewer, Pro Editor & Universal Converter | Programmed by Ibrahim Jaber 2026",
            [
                "• Universal Offline Converter: PDF <-> Word (.docx), Excel (.xlsx), CSV, PowerPoint (.pptx), Images, HTML & TXT.",
                "• Pro PDF Editor: True Redaction, Whiteout, Pen, Highlighter, Underline, Strike, Text (Arabic & English), Shapes, Stamps, QR Codes & Signatures.",
                "• 16 Commercial-Grade Offline Tools: Merge, Split, Organize, Compress, Grayscale, AES-256 Lock/Unlock, Watermark, Bates Numbering & Compare Diff.",
                "• Full Bilingual Support: Switch between English and Arabic (العربية) instantly with zero internet required.",
            ],
        ),
        (
            "Interactive Pro Editor Playground",
            "Select any Pro Editor tool from the top bar and edit right on this page",
            [
                "1. Try the Redact tool to permanently black out confidential text.",
                "2. Use Pen, Highlighter, Underline, Strike, Box, Oval, or Arrow.",
                "3. Stamp APPROVED, CONFIDENTIAL, or Arabic stamps, or insert a QR Code.",
                "4. Draw your handwritten signature in the Signature Pad and click Save PDF!",
            ],
        ),
    ]
    for heading, subtitle, bullets in pages_spec:
        p = doc.new_page(width=595, height=842)
        p.draw_rect(fitz.Rect(0, 0, 595, 118), color=(0.06, 0.09, 0.16), fill=(0.06, 0.09, 0.16))
        p.draw_rect(fitz.Rect(0, 118, 595, 124), color=(0.86, 0.15, 0.15), fill=(0.86, 0.15, 0.15))
        p.insert_text(fitz.Point(40, 56), heading, fontsize=22, color=(1, 1, 1))
        p.insert_text(fitz.Point(40, 88), subtitle, fontsize=10, color=(0.8, 0.85, 0.92))
        y = 172
        for b in bullets:
            p.insert_textbox(fitz.Rect(42, y, 552, y + 60), b, fontsize=12, color=(0.12, 0.16, 0.23))
            y += 64
        p.insert_text(fitz.Point(40, 812), f"© 2026 Ibrahim Jaber. All Rights Reserved.  |  {CREATOR_TAG}", fontsize=9, color=(0.39, 0.45, 0.55))

    doc.set_metadata({"title": "PDF Pro Studio Welcome 2026", "author": "Ibrahim Jaber", "creator": CREATOR_TAG})
    doc.save(output_pdf, deflate=True)
    doc.close()
    return output_pdf
