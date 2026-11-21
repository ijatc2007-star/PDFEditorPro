"""
PDF Editor Pro PC — 100% Offline Bilingual AI Engine (Arabic & English).
Provides on-device Extractive TF-IDF Summarization, BM25 Ask-Your-PDF Q&A,
Smart Entity & Action Extraction, PII Privacy Redaction Audit, Smart Outline,
Arabic NFKC/Diacritic Normalization, and Offline Windows SAPI5 Voice Reading.

Programmed by Ibrahim Jaber 2026 | برمجة إبراهيم جابر 2026
"""

from __future__ import annotations

import math
import re
import subprocess
import sys
import unicodedata
from typing import Dict, List, Tuple

import fitz

EN_STOP = {
    "the", "and", "for", "that", "this", "with", "from", "are", "was", "were", "have", "has",
    "had", "not", "but", "you", "your", "all", "can", "will", "into", "about", "more", "than",
    "also", "they", "their", "them", "been", "being", "which", "when", "where", "what", "who",
    "how", "its", "our", "out", "use", "using", "used", "any", "each", "other", "such", "only",
    "may", "should", "would", "could", "there", "here", "then", "these", "those", "page",
}

AR_STOP = {
    "من", "في", "علي", "الي", "عن", "مع", "هذا", "هذه", "ذلك", "تلك", "التي", "الذي",
    "الذين", "كان", "كانت", "يكون", "تكون", "ان", "لا", "لم", "لن", "ما", "هو", "هي",
    "هم", "نحن", "انت", "او", "ثم", "بعد", "قبل", "بين", "عند", "حتي", "كل", "بعض",
    "غير", "كما", "وقد", "فقد", "بها", "فيها", "عليه", "عليها", "منها", "منه", "لها", "له",
}

DIACRITICS_RE = re.compile(r"[\u064B-\u065F\u0670\u0640]")
EMAIL_RE = re.compile(r"[a-zA-Z0-9._%+\-]+@[a-zA-Z0-9.\-]+\.[a-zA-Z]{2,}")
URL_RE = re.compile(r"https?://[^\s<>\"']+")
PHONE_RE = re.compile(r"(?:\+?\d{1,3}[\s\-]?)?(?:\(?\d{2,4}\)?[\s\-]?){2,4}\d{3,4}")
DATE_RE = re.compile(r"\b(?:\d{4}[-/]\d{1,2}[-/]\d{1,2}|\d{1,2}[-/]\d{1,2}[-/]\d{2,4})\b")
AMOUNT_RE = re.compile(r"(?:\$|€|£|USD|EUR|SAR|AED|JOD|EGP)\s?\d[\d,.]*|\d[\d,.]*\s?(?:USD|EUR|SAR|AED|JOD|EGP|ريال|دينار|درهم|دولار|جنيه)", re.I)
IBAN_RE = re.compile(r"\b[A-Z]{2}\d{2}[A-Z0-9]{11,26}\b")
CARD_RE = re.compile(r"\b(?:\d{4}[\s\-]?){3}\d{4}\b")
IP_RE = re.compile(r"\b(?:\d{1,3}\.){3}\d{1,3}\b")
ACTION_HINTS = ("must", "shall", "required", "deadline", "verify", "submit", "approve", "يجب", "يلزم", "مطلوب", "الموعد", "تقديم", "اعتماد")


def clean_extracted_text(raw: str) -> str:
    if not raw:
        return ""
    text = unicodedata.normalize("NFKC", raw)
    text = text.replace("\u200f", "").replace("\u200e", "").replace("\u00a0", " ")
    text = re.sub(r"[ \t]{2,}", " ", text)
    text = re.sub(r"\n{3,}", "\n\n", text)
    return text.strip()


def normalize_for_search(text: str) -> str:
    cleaned = DIACRITICS_RE.sub("", clean_extracted_text(text))
    trans = str.maketrans({"أ": "ا", "إ": "ا", "آ": "ا", "ٱ": "ا", "ة": "ه", "ى": "ي", "ؤ": "و", "ئ": "ي"})
    return cleaned.translate(trans).lower()


def analyze_language(text: str) -> Dict:
    ar = sum(1 for c in text if "\u0600" <= c <= "\u06ff" or "\u0750" <= c <= "\u077f")
    en = sum(1 for c in text if ("A" <= c <= "Z") or ("a" <= c <= "z"))
    tot = max(1, ar + en)
    ar_pct = int(ar * 100 / tot)
    en_pct = 100 - ar_pct
    if ar_pct >= 65:
        primary = "Arabic (العربية)"
    elif en_pct >= 65 or (ar == 0 and en == 0):
        primary = "English"
    else:
        primary = "Arabic & English (ثنائي اللغة)"
    words = len([w for w in text.split() if w.strip()])
    mins = max(1, math.ceil(words / 190.0)) if words > 0 else 0
    return {"primary": primary, "arabic_pct": ar_pct, "english_pct": en_pct, "words": words, "read_minutes": mins}


def split_sentences(text: str) -> List[str]:
    cleaned = clean_extracted_text(text)
    if not cleaned:
        return []
    parts = re.split(r"(?<=[.!?؟\n])\s+", cleaned)
    return [re.sub(r"\s+", " ", p).strip() for p in parts if len(p.strip()) >= 12]


def tokenize(text: str) -> List[str]:
    norm = normalize_for_search(text)
    tokens = re.findall(r"[^\W_]+", norm)
    return [t for t in tokens if len(t) >= 2 and t not in EN_STOP and t not in AR_STOP]


def extract_pages_text(pdf_path: str, password: str | None = None) -> List[str]:
    doc = fitz.open(pdf_path)
    if doc.is_encrypted and password:
        doc.authenticate(password)
    pages = [clean_extracted_text(p.get_text("text")) for p in doc]
    doc.close()
    return pages


def _mask(val: str) -> str:
    s = val.strip()
    if len(s) <= 6:
        return "***"
    return f"{s[:3]}***{s[-3:]}"


def analyze_document(pages: List[str], num_sentences: int = 5) -> Dict:
    full = "\n\n".join(pages)
    lang = analyze_language(full)

    sentences: List[Tuple[int, int, str, List[str]]] = []
    idx = 0
    for p_idx, p_txt in enumerate(pages):
        for s in split_sentences(p_txt):
            toks = tokenize(s)
            if len(toks) >= 3:
                sentences.append((idx, p_idx, s, toks))
                idx += 1

    df: Dict[str, int] = {}
    tf: Dict[str, int] = {}
    for _, _, _, toks in sentences:
        for t in set(toks):
            df[t] = df.get(t, 0) + 1
        for t in toks:
            tf[t] = tf.get(t, 0) + 1

    n_docs = max(1, len(sentences))
    idf = {t: math.log((n_docs + 1.0) / (c + 1.0)) + 1.0 for t, c in df.items()}
    keywords = [t for t, _ in sorted(tf.items(), key=lambda kv: kv[1] * idf.get(kv[0], 1.0), reverse=True)[:10]]

    scored = []
    for s_idx, p_idx, txt, toks in sentences:
        score = sum(tf.get(t, 1) * idf.get(t, 1.0) for t in toks) / (len(toks) + 2.0)
        if s_idx == 0:
            score *= 1.35
        elif s_idx <= 3:
            score *= 1.18
        if 35 <= len(txt) <= 260:
            score *= 1.12
        scored.append((score, s_idx, txt))

    top = sorted(scored, key=lambda x: x[0], reverse=True)[:num_sentences]
    summary = [txt for _, _, txt in sorted(top, key=lambda x: x[1])]

    dates = list(dict.fromkeys(DATE_RE.findall(full)))[:12]
    amounts = list(dict.fromkeys(AMOUNT_RE.findall(full)))[:12]
    emails = list(dict.fromkeys(EMAIL_RE.findall(full)))[:12]
    urls = list(dict.fromkeys(URL_RE.findall(full)))[:12]
    phones = list(dict.fromkeys([p.strip() for p in PHONE_RE.findall(full) if 8 <= sum(c.isdigit() for c in p) <= 15]))[:12]
    actions = [f"P.{p + 1}: {txt}" for _, p, txt, _ in sentences if any(h in normalize_for_search(txt) for h in ACTION_HINTS)][:8]

    pii = []
    for p_idx, p_txt in enumerate(pages):
        for m in EMAIL_RE.findall(p_txt)[:4]:
            pii.append((f"Page {p_idx + 1}", "Email", _mask(m)))
        for m in IBAN_RE.findall(p_txt)[:3]:
            pii.append((f"Page {p_idx + 1}", "IBAN", _mask(m)))
        for m in CARD_RE.findall(p_txt)[:3]:
            pii.append((f"Page {p_idx + 1}", "Card", _mask(m)))
        for m in IP_RE.findall(p_txt)[:3]:
            pii.append((f"Page {p_idx + 1}", "IP", m))

    headings = []
    h_re = re.compile(r"^(?:\d+(?:\.\d+)*[\.)]?\s+|(?:Chapter|Section|Part|الفصل|الباب|القسم|البند)\s+).+", re.I)
    seen_h = set()
    for p_idx, p_txt in enumerate(pages):
        lines = [ln.strip() for ln in p_txt.splitlines() if 5 <= len(ln.strip()) <= 85]
        for li, ln in enumerate(lines):
            if h_re.match(ln) or (li == 0 and not ln.endswith(".") and len(ln.split()) <= 10):
                if ln.lower() not in seen_h:
                    seen_h.add(ln.lower())
                    headings.append((p_idx + 1, ln))

    return {
        "language": lang,
        "summary": summary,
        "keywords": keywords,
        "dates": dates,
        "amounts": amounts,
        "emails": emails,
        "phones": phones,
        "urls": urls,
        "actions": actions,
        "pii": pii[:25],
        "headings": headings[:25],
    }


def ask_question(pages: List[str], question: str) -> List[Tuple[int, int, str]]:
    """Offline BM25 semantic Q&A returning list of (1-based page, relevance %, passage)."""
    q_toks = tokenize(question)
    if not q_toks:
        return []
    passages = []
    for p_idx, p_txt in enumerate(pages):
        for s in split_sentences(p_txt):
            toks = tokenize(s)
            if toks:
                passages.append((p_idx, s, toks))
    if not passages:
        return []

    avg_len = max(1.0, sum(len(t) for _, _, t in passages) / len(passages))
    n = float(len(passages))
    df_map = {qt: sum(1 for _, _, toks in passages if any(qt == t or (len(qt) >= 4 and (qt in t or t in qt)) for t in toks)) for qt in q_toks}

    scored = []
    for p_idx, s, toks in passages:
        dl = float(len(toks))
        sc = 0.0
        for qt in q_toks:
            tf = float(sum(1 for t in toks if qt == t or (len(qt) >= 4 and (qt in t or t in qt))))
            if tf > 0:
                df = float(df_map[qt])
                idf = math.log((n - df + 0.5) / (df + 0.5) + 1.0)
                sc += idf * ((tf * 2.5) / (tf + 1.5 * (0.25 + 0.75 * (dl / avg_len))))
        if sc > 0:
            scored.append((sc, p_idx + 1, s))

    scored.sort(key=lambda x: x[0], reverse=True)
    if not scored:
        return []
    top_sc = max(0.001, scored[0][0])
    return [(pg, max(25, min(100, int(sc / top_sc * 100))), txt) for sc, pg, txt in scored[:5]]


def speak_text_offline_windows(text: str) -> bool:
    """Uses Windows built-in offline SAPI5 speech synthesizer (no internet, no extra binary)."""
    cleaned = clean_extracted_text(text)[:1800]
    if not cleaned or sys.platform != "win32":
        return False
    safe = cleaned.replace("'", "''").replace("\n", " ")
    ps_cmd = (
        "Add-Type -AssemblyName System.Speech; "
        "$s = New-Object System.Speech.Synthesis.SpeechSynthesizer; "
        f"$s.Speak('{safe}');"
    )
    subprocess.Popen(["powershell", "-NoProfile", "-WindowStyle", "Hidden", "-Command", ps_cmd])
    return True
