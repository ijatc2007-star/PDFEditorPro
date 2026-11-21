"""
PDF Pro Studio PC — Complete 100% Offline Windows Desktop PDF Viewer, Pro Editor & Universal Converter.
Programmed by Ibrahim Jaber 2026 | برمجة إبراهيم جابر 2026
"""

from __future__ import annotations

import os
import tempfile
import tkinter as tk
from pathlib import Path
from tkinter import colorchooser, filedialog, messagebox, simpledialog
from typing import Dict, List, Optional, Tuple

import customtkinter as ctk
import fitz
from PIL import Image, ImageDraw, ImageOps, ImageTk

import ai_engine_pc as ai_engine
import pdf_engine_pc as engine
from strings_pc import APP_NAME, APP_VERSION, CREDIT_AR, CREDIT_EN, STRINGS

ctk.set_appearance_mode("Dark")
ctk.set_default_color_theme("blue")


class SignaturePadWindow(ctk.CTkToplevel):
    """Interactive handwritten Signature Pad saving transparent PNG signatures offline."""

    def __init__(self, master, on_saved):
        super().__init__(master)
        self.title("Draw Handwritten Signature — Ibrahim Jaber 2026")
        self.geometry("520x320")
        self.resizable(False, False)
        self.on_saved = on_saved
        self.strokes: List[List[Tuple[float, float]]] = []
        self.current: List[Tuple[float, float]] = []
        self.ink_color = "#0F172A"

        ctk.CTkLabel(self, text="Draw your signature below:", font=ctk.CTkFont(size=14, weight="bold")).pack(pady=(10, 4))
        self.canvas = tk.Canvas(self, bg="white", width=480, height=200, highlightthickness=1, highlightbackground="#94A3B8")
        self.canvas.pack(padx=16, pady=6)
        self.canvas.bind("<ButtonPress-1>", self._start)
        self.canvas.bind("<B1-Motion>", self._drag)
        self.canvas.bind("<ButtonRelease-1>", self._end)

        btn_row = ctk.CTkFrame(self, fg_color="transparent")
        btn_row.pack(fill="x", padx=16, pady=8)
        ctk.CTkButton(btn_row, text="Clear", width=90, fg_color="#64748B", command=self._clear).pack(side="left", padx=4)
        ctk.CTkButton(btn_row, text="Ink Color", width=90, command=self._pick_color).pack(side="left", padx=4)
        ctk.CTkButton(btn_row, text="Save & Place Signature", fg_color="#059669", command=self._save).pack(side="right", padx=4)

    def _start(self, e):
        self.current = [(e.x, e.y)]

    def _drag(self, e):
        if self.current:
            x0, y0 = self.current[-1]
            self.canvas.create_line(x0, y0, e.x, e.y, fill=self.ink_color, width=3, capstyle=tk.ROUND, smooth=True)
        self.current.append((e.x, e.y))

    def _end(self, _e):
        if len(self.current) >= 2:
            self.strokes.append(self.current)
        self.current = []

    def _clear(self):
        self.canvas.delete("all")
        self.strokes.clear()

    def _pick_color(self):
        res = colorchooser.askcolor(color=self.ink_color)
        if res and res[1]:
            self.ink_color = res[1]

    def _save(self):
        if not self.strokes:
            messagebox.showwarning("Signature", "Please draw your signature first.")
            return
        img = Image.new("RGBA", (960, 400), (0, 0, 0, 0))
        draw = ImageDraw.Draw(img)
        for pts in self.strokes:
            scaled = [(x * 2, y * 2) for x, y in pts]
            draw.line(scaled, fill=self.ink_color, width=7, joint="curve")
        out_path = os.path.join(tempfile.gettempdir(), f"pdfpro_sig_{os.getpid()}_{len(self.strokes)}.png")
        img.save(out_path, "PNG")
        self.on_saved(out_path)
        self.destroy()


class PdfProStudioApp(ctk.CTk):
    def __init__(self):
        super().__init__()
        self.lang = "en"
        self.theme_dark = True
        self.title(f"{APP_NAME} v{APP_VERSION} — {CREDIT_EN}")
        self.geometry("1260x820")
        self.minsize(1020, 680)

        # Viewer & Editor state
        self.doc: Optional[fitz.Document] = None
        self.doc_path: Optional[str] = None
        self.doc_password: Optional[str] = None
        self.current_page: int = 0
        self.zoom: float = 1.25
        self.reading_mode: str = "day"  # day, sepia, night
        self.active_tool: str = "pan"
        self.active_color: str = "#DC2626"
        self.active_stamp: str = "APPROVED"
        self.selected_image_path: Optional[str] = None
        self.selected_sig_path: Optional[str] = None

        self.annotations: List[Dict] = []
        self.redo_stack: List[Dict] = []
        self._drag_points: List[Tuple[float, float]] = []
        self._drag_start: Optional[Tuple[float, float]] = None
        self._temp_canvas_ids: List[int] = []
        self._page_photo: Optional[ImageTk.PhotoImage] = None
        self._rendered_w: int = 1
        self._rendered_h: int = 1
        self._offset_x: int = 0
        self._offset_y: int = 0

        self._build_layout()
        self._show_tab("viewer")
        self._load_welcome_pdf()

    def t(self, key: str) -> str:
        return STRINGS[self.lang][key]

    def _build_layout(self):
        self.grid_columnconfigure(1, weight=1)
        self.grid_rowconfigure(0, weight=1)

        # Left Navigation Sidebar
        self.sidebar = ctk.CTkFrame(self, width=230, corner_radius=0)
        self.sidebar.grid(row=0, column=0, sticky="nsew")
        self.sidebar.grid_rowconfigure(8, weight=1)

        self.brand_lbl = ctk.CTkLabel(
            self.sidebar,
            text=APP_NAME,
            font=ctk.CTkFont(size=20, weight="bold"),
        )
        self.brand_lbl.grid(row=0, column=0, padx=16, pady=(18, 2), sticky="w")

        self.credit_lbl = ctk.CTkLabel(
            self.sidebar,
            text=f"{CREDIT_EN}\n{CREDIT_AR}",
            font=ctk.CTkFont(size=11),
            text_color="#38BDF8",
            justify="left",
        )
        self.credit_lbl.grid(row=1, column=0, padx=16, pady=(0, 14), sticky="w")

        self.btn_nav_viewer = ctk.CTkButton(self.sidebar, text=self.t("nav_viewer"), anchor="w", command=lambda: self._show_tab("viewer"))
        self.btn_nav_viewer.grid(row=2, column=0, padx=14, pady=5, sticky="ew")

        self.btn_nav_converter = ctk.CTkButton(self.sidebar, text=self.t("nav_converter"), anchor="w", command=lambda: self._show_tab("converter"))
        self.btn_nav_converter.grid(row=3, column=0, padx=14, pady=5, sticky="ew")

        self.btn_nav_ai = ctk.CTkButton(self.sidebar, text=self.t("nav_ai"), anchor="w", fg_color="#7C3AED", command=lambda: self._show_tab("ai"))
        self.btn_nav_ai.grid(row=4, column=0, padx=14, pady=5, sticky="ew")

        self.btn_nav_tools = ctk.CTkButton(self.sidebar, text=self.t("nav_tools"), anchor="w", command=lambda: self._show_tab("tools"))
        self.btn_nav_tools.grid(row=5, column=0, padx=14, pady=5, sticky="ew")

        self.btn_nav_about = ctk.CTkButton(self.sidebar, text=self.t("nav_about"), anchor="w", command=lambda: self._show_tab("about"))
        self.btn_nav_about.grid(row=6, column=0, padx=14, pady=5, sticky="ew")

        # Language + Theme Controls at bottom of sidebar
        ctrl_frame = ctk.CTkFrame(self.sidebar, fg_color="transparent")
        ctrl_frame.grid(row=9, column=0, padx=14, pady=12, sticky="ew")
        self.btn_lang = ctk.CTkButton(ctrl_frame, text=self.t("lang_switch"), width=92, fg_color="#DC2626", command=self._toggle_lang)
        self.btn_lang.pack(side="left", padx=(0, 6))
        self.btn_theme = ctk.CTkButton(ctrl_frame, text=self.t("theme_switch"), width=92, fg_color="#475569", command=self._toggle_theme)
        self.btn_theme.pack(side="right")

        ctk.CTkLabel(
            self.sidebar,
            text=f"v{APP_VERSION} • 100% Offline",
            font=ctk.CTkFont(size=11),
            text_color="#94A3B8",
        ).grid(row=10, column=0, padx=16, pady=(0, 12), sticky="w")

        # Main Content Container
        self.main_area = ctk.CTkFrame(self, corner_radius=0, fg_color="transparent")
        self.main_area.grid(row=0, column=1, sticky="nsew")
        self.main_area.grid_rowconfigure(0, weight=1)
        self.main_area.grid_columnconfigure(0, weight=1)

        # Status Bar
        self.status_var = tk.StringVar(value=self.t("ready"))
        self.status_bar = ctk.CTkLabel(self, textvariable=self.status_var, anchor="w", font=ctk.CTkFont(size=11.5), padx=14, height=26)
        self.status_bar.grid(row=1, column=0, columnspan=2, sticky="ew")

        # Build the 5 tab frames
        self.tabs: Dict[str, ctk.CTkFrame] = {}
        self._build_viewer_tab()
        self._build_converter_tab()
        self._build_ai_tab()
        self._build_tools_tab()
        self._build_about_tab()

    def _toggle_lang(self):
        self.lang = "ar" if self.lang == "en" else "en"
        self.title(f"{self.t('title')} — {self.t('credit')}")
        self.btn_nav_viewer.configure(text=self.t("nav_viewer"))
        self.btn_nav_converter.configure(text=self.t("nav_converter"))
        self.btn_nav_ai.configure(text=self.t("nav_ai"))
        self.btn_nav_tools.configure(text=self.t("nav_tools"))
        self.btn_nav_about.configure(text=self.t("nav_about"))
        self.btn_lang.configure(text=self.t("lang_switch"))
        self.btn_theme.configure(text=self.t("theme_switch"))
        self.status_var.set(self.t("ready"))

    def _toggle_theme(self):
        self.theme_dark = not self.theme_dark
        ctk.set_appearance_mode("Dark" if self.theme_dark else "Light")

    def _show_tab(self, name: str):
        for k, frame in self.tabs.items():
            if k == name:
                frame.grid(row=0, column=0, sticky="nsew")
            else:
                frame.grid_forget()

    # =========================================================================
    # TAB 1: VIEWER & PRO PDF EDITOR
    # =========================================================================

    def _build_viewer_tab(self):
        frame = ctk.CTkFrame(self.main_area, corner_radius=0)
        self.tabs["viewer"] = frame
        frame.grid_rowconfigure(2, weight=1)
        frame.grid_columnconfigure(0, weight=1)

        # Top File & Reading Bar
        top_bar = ctk.CTkFrame(frame, height=48)
        top_bar.grid(row=0, column=0, sticky="ew", padx=10, pady=(8, 4))

        ctk.CTkButton(top_bar, text="Open PDF", width=86, command=self._open_pdf_dialog).pack(side="left", padx=3, pady=6)
        ctk.CTkButton(top_bar, text="Save", width=66, fg_color="#059669", command=lambda: self._save_pdf(False)).pack(side="left", padx=3)
        ctk.CTkButton(top_bar, text="Save As", width=72, fg_color="#0284C7", command=lambda: self._save_pdf(True)).pack(side="left", padx=3)
        ctk.CTkButton(top_bar, text="Search", width=68, fg_color="#475569", command=self._search_in_pdf).pack(side="left", padx=3)
        ctk.CTkButton(top_bar, text="Read Aloud", width=88, fg_color="#0D9488", command=self._read_page_aloud).pack(side="left", padx=3)
        ctk.CTkButton(top_bar, text="AI Studio", width=80, fg_color="#7C3AED", command=self._open_ai_for_current).pack(side="left", padx=3)

        ctk.CTkButton(top_bar, text="◀", width=36, command=self._prev_page).pack(side="left", padx=(14, 2))
        self.page_lbl = ctk.CTkLabel(top_bar, text="Page 0 / 0", width=92)
        self.page_lbl.pack(side="left", padx=2)
        ctk.CTkButton(top_bar, text="▶", width=36, command=self._next_page).pack(side="left", padx=(2, 10))

        ctk.CTkButton(top_bar, text="Zoom -", width=66, command=lambda: self._set_zoom(self.zoom - 0.2)).pack(side="left", padx=2)
        ctk.CTkButton(top_bar, text="Zoom +", width=66, command=lambda: self._set_zoom(self.zoom + 0.2)).pack(side="left", padx=2)
        ctk.CTkButton(top_bar, text="Mode", width=66, fg_color="#7C3AED", command=self._cycle_reading_mode).pack(side="left", padx=6)

        ctk.CTkButton(top_bar, text="Undo", width=60, fg_color="#64748B", command=self._undo).pack(side="right", padx=4)
        ctk.CTkButton(top_bar, text="Redo", width=60, fg_color="#64748B", command=self._redo).pack(side="right", padx=2)

        # Second Bar: Pro Editor Tools
        edit_bar = ctk.CTkScrollableFrame(frame, height=44, orientation="horizontal")
        edit_bar.grid(row=1, column=0, sticky="ew", padx=10, pady=(0, 4))

        tools = [
            ("pan", "Pan"),
            ("pen", "Pen"),
            ("highlighter", "Highlight"),
            ("underline", "Underline"),
            ("strike", "Strike"),
            ("text", "Text (EN/AR)"),
            ("redact", "Redact"),
            ("whiteout", "Whiteout"),
            ("rectangle", "Box"),
            ("oval", "Circle"),
            ("arrow", "Arrow"),
            ("stamp", "Stamp"),
            ("sticky_note", "Note"),
            ("image", "Image"),
            ("qr_code", "QR Stamp"),
            ("signature", "Sign"),
        ]
        self.tool_buttons: Dict[str, ctk.CTkButton] = {}
        for tid, label in tools:
            btn = ctk.CTkButton(
                edit_bar,
                text=label,
                width=78,
                height=30,
                fg_color="#0284C7" if tid == "pan" else "#334155",
                command=lambda t=tid: self._select_tool(t),
            )
            btn.pack(side="left", padx=3, pady=3)
            self.tool_buttons[tid] = btn

        self.btn_color = ctk.CTkButton(edit_bar, text="Color", width=64, height=30, fg_color=self.active_color, command=self._pick_editor_color)
        self.btn_color.pack(side="left", padx=6)

        # Scrollable PDF Canvas
        canvas_container = ctk.CTkFrame(frame)
        canvas_container.grid(row=2, column=0, sticky="nsew", padx=10, pady=(0, 8))
        canvas_container.grid_rowconfigure(0, weight=1)
        canvas_container.grid_columnconfigure(0, weight=1)

        self.pdf_canvas = tk.Canvas(canvas_container, bg="#1E293B", highlightthickness=0)
        v_scroll = tk.Scrollbar(canvas_container, orient="vertical", command=self.pdf_canvas.yview)
        h_scroll = tk.Scrollbar(canvas_container, orient="horizontal", command=self.pdf_canvas.xview)
        self.pdf_canvas.configure(yscrollcommand=v_scroll.set, xscrollcommand=h_scroll.set)

        self.pdf_canvas.grid(row=0, column=0, sticky="nsew")
        v_scroll.grid(row=0, column=1, sticky="ns")
        h_scroll.grid(row=1, column=0, sticky="ew")

        self.pdf_canvas.bind("<ButtonPress-1>", self._on_canvas_down)
        self.pdf_canvas.bind("<B1-Motion>", self._on_canvas_drag)
        self.pdf_canvas.bind("<ButtonRelease-1>", self._on_canvas_up)

    def _load_welcome_pdf(self):
        welcome_path = os.path.join(tempfile.gettempdir(), "PDF_Pro_Studio_Welcome_2026.pdf")
        try:
            if not os.path.exists(welcome_path):
                engine.create_sample_welcome_pdf(welcome_path)
            self._open_pdf_path(welcome_path)
        except Exception:
            pass

    def _open_pdf_dialog(self):
        path = filedialog.askopenfilename(filetypes=[("PDF Files", "*.pdf")])
        if path:
            self._open_pdf_path(path)

    def _open_pdf_path(self, path: str):
        try:
            doc = fitz.open(path)
            pwd = None
            if doc.is_encrypted:
                pwd = simpledialog.askstring("Password Protected PDF", "Enter PDF password:", show="*")
                if not pwd or not doc.authenticate(pwd):
                    doc.close()
                    messagebox.showerror("Unlock Failed", "Incorrect password.")
                    return
            if self.doc is not None:
                self.doc.close()
            self.doc = doc
            self.doc_path = path
            self.doc_password = pwd
            self.current_page = 0
            self.annotations.clear()
            self.redo_stack.clear()
            self._render_current_page()
            self.status_var.set(f"Opened: {Path(path).name} ({len(doc)} pages) | {CREDIT_EN}")
        except Exception as exc:
            messagebox.showerror("Open Error", str(exc))

    def _render_current_page(self):
        if self.doc is None or len(self.doc) == 0:
            return
        page = self.doc[self.current_page]
        mat = fitz.Matrix(self.zoom, self.zoom)
        pix = page.get_pixmap(matrix=mat)
        img = Image.frombytes("RGB", [pix.width, pix.height], pix.samples)

        if self.reading_mode == "night":
            img = ImageOps.invert(img)
        elif self.reading_mode == "sepia":
            gray = ImageOps.grayscale(img)
            img = ImageOps.colorize(gray, black="#2E2216", white="#F5ECD9")

        self._rendered_w, self._rendered_h = img.width, img.height
        self._page_photo = ImageTk.PhotoImage(img)
        self.pdf_canvas.delete("all")
        self._offset_x, self._offset_y = 24, 20
        self.pdf_canvas.create_image(self._offset_x, self._offset_y, anchor="nw", image=self._page_photo)
        self.pdf_canvas.configure(scrollregion=(0, 0, img.width + 48, img.height + 40))

        self._draw_pending_annotations_overlay()
        unsaved = f" • {len(self.annotations)} unsaved" if self.annotations else ""
        self.page_lbl.configure(text=f"Page {self.current_page + 1} / {len(self.doc)}{unsaved}")

    def _draw_pending_annotations_overlay(self):
        w, h = self._rendered_w, self._rendered_h
        ox, oy = self._offset_x, self._offset_y
        for ann in self.annotations:
            if ann["page"] != self.current_page:
                continue
            kind = ann["type"]
            col = ann.get("color", "#DC2626")
            if kind in ("pen", "highlighter"):
                pts = ann.get("points", [])
                flat = []
                for px, py in pts:
                    flat.extend([ox + px * w, oy + py * h])
                if len(flat) >= 4:
                    self.pdf_canvas.create_line(
                        *flat,
                        fill=col,
                        width=10 if kind == "highlighter" else 3,
                        stipple="gray50" if kind == "highlighter" else "",
                        capstyle=tk.ROUND,
                    )
            elif kind == "redact":
                self.pdf_canvas.create_rectangle(ox + ann["x1"] * w, oy + ann["y1"] * h, ox + ann["x2"] * w, oy + ann["y2"] * h, fill="black", outline="red")
            elif kind == "whiteout":
                self.pdf_canvas.create_rectangle(ox + ann["x1"] * w, oy + ann["y1"] * h, ox + ann["x2"] * w, oy + ann["y2"] * h, fill="white", outline="#94A3B8")
            elif kind == "rectangle":
                self.pdf_canvas.create_rectangle(ox + ann["x1"] * w, oy + ann["y1"] * h, ox + ann["x2"] * w, oy + ann["y2"] * h, outline=col, width=2.5)
            elif kind == "oval":
                self.pdf_canvas.create_oval(ox + ann["x1"] * w, oy + ann["y1"] * h, ox + ann["x2"] * w, oy + ann["y2"] * h, outline=col, width=2.5)
            elif kind in ("underline", "strike"):
                c = "#DC2626" if kind == "strike" else col
                self.pdf_canvas.create_line(ox + ann["x1"] * w, oy + ann["y1"] * h, ox + ann["x2"] * w, oy + ann["y1"] * h, fill=c, width=2.5)
            elif kind == "arrow":
                self.pdf_canvas.create_line(ox + ann["x1"] * w, oy + ann["y1"] * h, ox + ann["x2"] * w, oy + ann["y2"] * h, fill=col, width=2.5, arrow=tk.LAST)
            elif kind == "text":
                self.pdf_canvas.create_text(ox + ann["x"] * w, oy + ann["y"] * h, anchor="nw", text=engine.shape_arabic(ann["text"]), fill=col, font=("Arial", 12, "bold"))
            elif kind == "stamp":
                cx, cy = ox + ann["x"] * w, oy + ann["y"] * h
                self.pdf_canvas.create_rectangle(cx - 70, cy - 18, cx + 70, cy + 18, outline=col, width=2.5)
                self.pdf_canvas.create_text(cx, cy, text=engine.shape_arabic(ann["label"]), fill=col, font=("Arial", 10, "bold"))
            elif kind == "sticky_note":
                nx, ny = ox + ann["x"] * w, oy + ann["y"] * h
                self.pdf_canvas.create_rectangle(nx, ny, nx + 140, ny + 48, fill="#FEF08A", outline="#D97706")
                self.pdf_canvas.create_text(nx + 6, ny + 6, anchor="nw", text=ann["text"][:32], fill="#1E293B", font=("Arial", 9))
            elif kind in ("image", "signature", "qr_code"):
                cx, cy = ox + ann["x"] * w, oy + ann["y"] * h
                self.pdf_canvas.create_rectangle(cx - 55, cy - 28, cx + 55, cy + 28, outline="#0284C7", dash=(4, 2))
                self.pdf_canvas.create_text(cx, cy, text=f"[{kind.upper()}]", fill="#0284C7", font=("Arial", 9, "bold"))

    def _select_tool(self, tool_id: str):
        self.active_tool = tool_id
        for tid, btn in self.tool_buttons.items():
            btn.configure(fg_color="#DC2626" if tid == tool_id else "#334155")
        if tool_id == "highlighter":
            self.active_color = "#FACC15"
            self.btn_color.configure(fg_color=self.active_color)
        elif tool_id == "stamp":
            choice = simpledialog.askstring(
                "Stamp Text",
                "Enter stamp text (e.g. APPROVED, CONFIDENTIAL, معتمد, إبراهيم جابر 2026):",
                initialvalue=self.active_stamp,
            )
            if choice:
                self.active_stamp = choice
        elif tool_id == "image":
            img_path = filedialog.askopenfilename(filetypes=[("Images", "*.png *.jpg *.jpeg *.webp *.bmp")])
            if img_path:
                self.selected_image_path = img_path
                self.status_var.set("Click on the PDF page to place your image.")
        elif tool_id == "signature":
            SignaturePadWindow(self, on_saved=self._on_signature_created)

    def _on_signature_created(self, path: str):
        self.selected_sig_path = path
        self.active_tool = "signature"
        self.status_var.set("Signature ready! Click anywhere on the PDF page to place it.")

    def _pick_editor_color(self):
        res = colorchooser.askcolor(color=self.active_color)
        if res and res[1]:
            self.active_color = res[1]
            self.btn_color.configure(fg_color=self.active_color)

    def _canvas_to_norm(self, event) -> Tuple[float, float]:
        cx = self.pdf_canvas.canvasx(event.x) - self._offset_x
        cy = self.pdf_canvas.canvasy(event.y) - self._offset_y
        nx = max(0.0, min(1.0, cx / max(1, self._rendered_w)))
        ny = max(0.0, min(1.0, cy / max(1, self._rendered_h)))
        return nx, ny

    def _on_canvas_down(self, event):
        if self.doc is None or self.active_tool == "pan":
            return
        nx, ny = self._canvas_to_norm(event)
        if self.active_tool in ("pen", "highlighter"):
            self._drag_points = [(nx, ny)]
        elif self.active_tool in ("redact", "whiteout", "rectangle", "oval", "arrow", "underline", "strike"):
            self._drag_start = (nx, ny)
        elif self.active_tool == "text":
            txt = simpledialog.askstring("Insert Text (English / Arabic)", "Enter text to place on PDF:")
            if txt:
                self.annotations.append({"type": "text", "page": self.current_page, "x": nx, "y": ny, "text": txt, "color": self.active_color, "font_size": 16})
                self.redo_stack.clear()
                self._render_current_page()
        elif self.active_tool == "sticky_note":
            txt = simpledialog.askstring("Sticky Note", "Enter comment note:")
            if txt:
                self.annotations.append({"type": "sticky_note", "page": self.current_page, "x": nx, "y": ny, "text": txt})
                self.redo_stack.clear()
                self._render_current_page()
        elif self.active_tool == "stamp":
            self.annotations.append({"type": "stamp", "page": self.current_page, "x": nx, "y": ny, "label": self.active_stamp, "color": self.active_color})
            self.redo_stack.clear()
            self._render_current_page()
        elif self.active_tool == "image" and self.selected_image_path:
            self.annotations.append({"type": "image", "page": self.current_page, "x": nx, "y": ny, "image_path": self.selected_image_path, "width_norm": 0.28, "height_norm": 0.20})
            self.redo_stack.clear()
            self._render_current_page()
        elif self.active_tool == "signature" and self.selected_sig_path:
            self.annotations.append({"type": "signature", "page": self.current_page, "x": nx, "y": ny, "image_path": self.selected_sig_path, "width_norm": 0.26, "height_norm": 0.11})
            self.redo_stack.clear()
            self._render_current_page()
        elif self.active_tool == "qr_code":
            qr_data = simpledialog.askstring("Offline QR Code Stamp", "Enter URL, Text, or Document ID:", initialvalue=engine.CREATOR_TAG)
            if qr_data:
                qr_png = os.path.join(tempfile.gettempdir(), f"pdfpro_qr_{len(self.annotations)}.png")
                engine.generate_qr_png(qr_data, qr_png)
                self.annotations.append({"type": "qr_code", "page": self.current_page, "x": nx, "y": ny, "image_path": qr_png, "width_norm": 0.18, "height_norm": 0.13})
                self.redo_stack.clear()
                self._render_current_page()

    def _on_canvas_drag(self, event):
        if self.doc is None:
            return
        nx, ny = self._canvas_to_norm(event)
        if self.active_tool in ("pen", "highlighter") and self._drag_points:
            px, py = self._drag_points[-1]
            self._drag_points.append((nx, ny))
            w, h = self._rendered_w, self._rendered_h
            ox, oy = self._offset_x, self._offset_y
            cid = self.pdf_canvas.create_line(ox + px * w, oy + py * h, ox + nx * w, oy + ny * h, fill=self.active_color, width=10 if self.active_tool == "highlighter" else 3)
            self._temp_canvas_ids.append(cid)

    def _on_canvas_up(self, event):
        if self.doc is None:
            return
        nx, ny = self._canvas_to_norm(event)
        for cid in self._temp_canvas_ids:
            self.pdf_canvas.delete(cid)
        self._temp_canvas_ids.clear()

        if self.active_tool in ("pen", "highlighter") and len(self._drag_points) >= 2:
            self.annotations.append(
                {
                    "type": self.active_tool,
                    "page": self.current_page,
                    "points": list(self._drag_points),
                    "color": self.active_color,
                    "width_norm": 0.022 if self.active_tool == "highlighter" else 0.004,
                }
            )
            self.redo_stack.clear()
            self._drag_points.clear()
            self._render_current_page()
        elif self._drag_start is not None:
            x1, y1 = self._drag_start
            self.annotations.append(
                {
                    "type": self.active_tool,
                    "page": self.current_page,
                    "x1": x1,
                    "y1": y1,
                    "x2": nx,
                    "y2": ny,
                    "color": self.active_color,
                    "width_norm": 0.004,
                }
            )
            self.redo_stack.clear()
            self._drag_start = None
            self._render_current_page()

    def _undo(self):
        if self.annotations:
            self.redo_stack.append(self.annotations.pop())
            self._render_current_page()

    def _redo(self):
        if self.redo_stack:
            self.annotations.append(self.redo_stack.pop())
            self._render_current_page()

    def _save_pdf(self, save_as: bool):
        if not self.doc_path:
            return
        target = self.doc_path
        if save_as:
            chosen = filedialog.asksaveasfilename(defaultextension=".pdf", filetypes=[("PDF Files", "*.pdf")])
            if not chosen:
                return
            target = chosen
        try:
            saved_page = self.current_page
            if self.doc is not None:
                self.doc.close()
                self.doc = None
            engine.apply_annotations_to_pdf(self.doc_path, target, self.annotations, self.doc_password)
            self.annotations.clear()
            self.redo_stack.clear()
            self._open_pdf_path(target)
            if self.doc and saved_page < len(self.doc):
                self.current_page = saved_page
                self._render_current_page()
            messagebox.showinfo("Saved", f"Saved PDF successfully:\n{target}")
        except Exception as exc:
            messagebox.showerror("Save Error", str(exc))

    def _prev_page(self):
        if self.doc and self.current_page > 0:
            self.current_page -= 1
            self._render_current_page()

    def _next_page(self):
        if self.doc and self.current_page < len(self.doc) - 1:
            self.current_page += 1
            self._render_current_page()

    def _set_zoom(self, val: float):
        self.zoom = max(0.5, min(3.5, val))
        self._render_current_page()

    def _cycle_reading_mode(self):
        order = ["day", "sepia", "night"]
        self.reading_mode = order[(order.index(self.reading_mode) + 1) % len(order)]
        self._render_current_page()

    def _search_in_pdf(self):
        if self.doc is None:
            return
        q = simpledialog.askstring("Offline Search (EN / Arabic)", "Enter keyword to find (Arabic diacritics tolerant):")
        if not q:
            return
        needle = ai_engine.normalize_for_search(q)
        hits = []
        for idx, page in enumerate(self.doc):
            if needle in ai_engine.normalize_for_search(page.get_text("text")):
                hits.append(idx + 1)
        if hits:
            self.current_page = hits[0] - 1
            self._render_current_page()
            messagebox.showinfo("Search Results", f"Found '{q}' on pages: {', '.join(map(str, hits))}\nJumped to page {hits[0]}.")
        else:
            messagebox.showinfo("Search Results", f"No matches found for '{q}'.")

    def _read_page_aloud(self):
        if self.doc is None:
            return
        text = ai_engine.clean_extracted_text(self.doc[self.current_page].get_text("text"))
        if not text:
            messagebox.showinfo("Read Aloud", "No selectable text found on this page.")
            return
        ok = ai_engine.speak_text_offline_windows(text)
        if ok:
            self.status_var.set(f"Reading Page {self.current_page + 1} aloud offline (Windows Speech)...")
        else:
            messagebox.showinfo("Read Aloud", "Offline speech synthesis is active on Windows 10/11 PCs.")

    def _open_ai_for_current(self):
        self._show_tab("ai")
        if self.doc_path:
            self._run_ai_analysis(self.doc_path)

    # =========================================================================
    # TAB 2B: OFFLINE AI PDF STUDIO (ARABIC & ENGLISH)
    # =========================================================================

    def _build_ai_tab(self):
        frame = ctk.CTkScrollableFrame(self.main_area, corner_radius=0)
        self.tabs["ai"] = frame
        self._ai_pages: List[str] = []
        self._ai_pdf_path: Optional[str] = None
        self._ai_last_report: Optional[Dict] = None

        ctk.CTkLabel(frame, text="Offline AI PDF Studio (Arabic & English)", font=ctk.CTkFont(size=22, weight="bold")).pack(anchor="w", padx=20, pady=(18, 2))
        ctk.CTkLabel(frame, text=f"AI Summarizer • Ask Your PDF • PII Privacy Audit • Smart Outline • 100% Offline • {CREDIT_EN}", text_color="#38BDF8").pack(anchor="w", padx=20, pady=(0, 12))

        ctrl = ctk.CTkFrame(frame)
        ctrl.pack(fill="x", padx=20, pady=6)
        ctk.CTkButton(ctrl, text="Analyze Currently Open PDF", fg_color="#7C3AED", command=lambda: self._run_ai_analysis(self.doc_path)).pack(side="left", padx=8, pady=10)
        ctk.CTkButton(ctrl, text="Pick Another PDF...", command=self._pick_pdf_for_ai).pack(side="left", padx=6, pady=10)
        ctk.CTkButton(ctrl, text="Export AI Summary PDF", fg_color="#059669", command=self._export_ai_summary_pdf).pack(side="right", padx=8, pady=10)

        # Ask Your PDF Bar
        qa_bar = ctk.CTkFrame(frame)
        qa_bar.pack(fill="x", padx=20, pady=6)
        self.ai_question_entry = ctk.CTkEntry(qa_bar, placeholder_text="Ask anything about this PDF in English or Arabic (اسأل أي سؤال عن الملف)...")
        self.ai_question_entry.pack(side="left", fill="x", expand=True, padx=8, pady=10)
        ctk.CTkButton(qa_bar, text="Ask AI", width=96, fg_color="#0284C7", command=self._ask_ai_question).pack(side="right", padx=8, pady=10)

        self.ai_output_box = ctk.CTkTextbox(frame, height=480, font=ctk.CTkFont(size=13))
        self.ai_output_box.pack(fill="both", expand=True, padx=20, pady=(6, 16))
        self.ai_output_box.insert("1.0", "Click 'Analyze Currently Open PDF' to run 100% offline AI Summarization, Arabic/English Analytics, Entity & Action Extraction, PII Redaction Audit, and Smart Outline.\n")

    def _pick_pdf_for_ai(self):
        p = filedialog.askopenfilename(filetypes=[("PDF Files", "*.pdf")])
        if p:
            self._run_ai_analysis(p)

    def _run_ai_analysis(self, pdf_path: Optional[str]):
        if not pdf_path or not os.path.exists(pdf_path):
            messagebox.showinfo("AI Studio", "Please open or select a PDF file first.")
            return
        try:
            pages = ai_engine.extract_pages_text(pdf_path, self.doc_password if pdf_path == self.doc_path else None)
            rep = ai_engine.analyze_document(pages, num_sentences=5)
            self._ai_pages = pages
            self._ai_pdf_path = pdf_path
            self._ai_last_report = rep

            lang = rep["language"]
            lines = [
                f"=== AI ANALYSIS: {Path(pdf_path).name} ===",
                f"Language: {lang['primary']} (AR {lang['arabic_pct']}% / EN {lang['english_pct']}%) | Words: {lang['words']} | ~{lang['read_minutes']} min read",
                "",
                "--- AI EXECUTIVE SUMMARY ---",
            ]
            for i, b in enumerate(rep["summary"], start=1):
                lines.append(f"{i}. {b}")
            if rep["keywords"]:
                lines.append("\nTop Topics: " + ", ".join(rep["keywords"]))
            if rep["pii"]:
                lines.append(f"\n--- PII PRIVACY & REDACTION AUDIT ({len(rep['pii'])} found) ---")
                for pg, cat, val in rep["pii"]:
                    lines.append(f"  [!] {pg} — {cat}: {val}")
            else:
                lines.append("\n--- PII PRIVACY AUDIT: CLEAN (0 exposed emails/IBANs/cards/IPs) ---")
            if rep["actions"]:
                lines.append("\n--- ACTION ITEMS & OBLIGATIONS ---")
                lines.extend(f"  • {a}" for a in rep["actions"])
            if rep["amounts"] or rep["dates"]:
                lines.append("\n--- DATES & AMOUNTS ---")
                if rep["dates"]:
                    lines.append("  Dates: " + ", ".join(rep["dates"]))
                if rep["amounts"]:
                    lines.append("  Amounts: " + ", ".join(rep["amounts"]))
            if rep["headings"]:
                lines.append("\n--- AI SMART OUTLINE ---")
                for pg, h in rep["headings"]:
                    lines.append(f"  Page {pg}: {h}")

            self.ai_output_box.delete("1.0", "end")
            self.ai_output_box.insert("1.0", "\n".join(lines))
            self.status_var.set(f"AI Analysis complete for {Path(pdf_path).name} — 100% Offline")
        except Exception as exc:
            messagebox.showerror("AI Studio Error", str(exc))

    def _ask_ai_question(self):
        q = self.ai_question_entry.get().strip()
        if not q:
            return
        if not self._ai_pages and self.doc_path:
            self._run_ai_analysis(self.doc_path)
        if not self._ai_pages:
            messagebox.showinfo("Ask AI", "Select or open a PDF first.")
            return
        hits = ai_engine.ask_question(self._ai_pages, q)
        out_lines = [f"\n\n=== Q: {q} ==="]
        if not hits:
            out_lines.append("No direct matching passage found. Try different keywords.")
        else:
            for pg, rel, txt in hits:
                out_lines.append(f"  [Page {pg} • {rel}% match] {txt}")
        self.ai_output_box.insert("1.0", "\n".join(out_lines) + "\n\n")
        self.ai_question_entry.delete(0, "end")

    def _export_ai_summary_pdf(self):
        if not self._ai_last_report or not self._ai_pdf_path:
            messagebox.showinfo("Export Summary", "Run AI Analysis on a PDF first.")
            return
        out = filedialog.asksaveasfilename(defaultextension=".pdf", initialfile=f"{Path(self._ai_pdf_path).stem}_AI_Summary.pdf")
        if not out:
            return
        text = self.ai_output_box.get("1.0", "end").strip()
        engine._render_lines_to_pdf(f"AI Summary — {Path(self._ai_pdf_path).stem}", text.splitlines(), out)
        self._open_pdf_path(out)
        self._show_tab("viewer")
        messagebox.showinfo("Saved", f"Saved AI Summary PDF:\n{out}")

    # =========================================================================
    # TAB 2: UNIVERSAL OFFLINE FORMAT CONVERTER
    # =========================================================================

    def _build_converter_tab(self):
        frame = ctk.CTkScrollableFrame(self.main_area, corner_radius=0)
        self.tabs["converter"] = frame

        ctk.CTkLabel(frame, text="Universal Offline Format Converter", font=ctk.CTkFont(size=22, weight="bold")).pack(anchor="w", padx=20, pady=(18, 2))
        ctk.CTkLabel(frame, text="Convert PDF ↔ Word (.docx), Excel (.xlsx), CSV, PowerPoint (.pptx), Images, HTML & TXT • 100% Offline • Ibrahim Jaber 2026", text_color="#94A3B8").pack(anchor="w", padx=20, pady=(0, 14))

        # Section A: From PDF
        sec_a = ctk.CTkFrame(frame)
        sec_a.pack(fill="x", padx=20, pady=8)
        ctk.CTkLabel(sec_a, text="1. Convert FROM PDF (من PDF إلى الصيغ الأخرى)", font=ctk.CTkFont(size=15, weight="bold")).pack(anchor="w", padx=14, pady=(12, 8))
        row_a = ctk.CTkFrame(sec_a, fg_color="transparent")
        row_a.pack(fill="x", padx=12, pady=(0, 12))
        from_actions = [
            ("PDF → Word (.docx)", lambda: self._run_from_pdf("docx")),
            ("PDF → Excel (.xlsx)", lambda: self._run_from_pdf("xlsx")),
            ("PDF → CSV (Arabic UTF-8)", lambda: self._run_from_pdf("csv")),
            ("PDF → PowerPoint (.pptx)", lambda: self._run_from_pdf("pptx")),
            ("PDF → PNG Images", lambda: self._run_from_pdf("png")),
            ("PDF → JPEG Images", lambda: self._run_from_pdf("jpg")),
            ("PDF → HTML", lambda: self._run_from_pdf("html")),
            ("PDF → TXT", lambda: self._run_from_pdf("txt")),
        ]
        for idx, (lbl, cmd) in enumerate(from_actions):
            ctk.CTkButton(row_a, text=lbl, height=38, command=cmd).grid(row=idx // 4, column=idx % 4, padx=6, pady=6, sticky="ew")
        for c in range(4):
            row_a.grid_columnconfigure(c, weight=1)

        # Section B: To PDF
        sec_b = ctk.CTkFrame(frame)
        sec_b.pack(fill="x", padx=20, pady=8)
        ctk.CTkLabel(sec_b, text="2. Convert TO PDF (تحويل الملفات والصور إلى PDF)", font=ctk.CTkFont(size=15, weight="bold")).pack(anchor="w", padx=14, pady=(12, 8))
        row_b = ctk.CTkFrame(sec_b, fg_color="transparent")
        row_b.pack(fill="x", padx=12, pady=(0, 12))
        to_actions = [
            ("Word (.docx) → PDF", lambda: self._run_to_pdf("docx")),
            ("Excel (.xlsx) → PDF", lambda: self._run_to_pdf("xlsx")),
            ("CSV (.csv) → PDF", lambda: self._run_to_pdf("csv")),
            ("PowerPoint (.pptx) → PDF", lambda: self._run_to_pdf("pptx")),
            ("Images → PDF", self._run_images_to_pdf),
            ("HTML / MD / TXT → PDF", lambda: self._run_to_pdf("text")),
        ]
        for idx, (lbl, cmd) in enumerate(to_actions):
            ctk.CTkButton(row_b, text=lbl, height=38, fg_color="#059669", command=cmd).grid(row=idx // 3, column=idx % 3, padx=6, pady=6, sticky="ew")
        for c in range(3):
            row_b.grid_columnconfigure(c, weight=1)

        # Section C: Office Cross-Conversion
        sec_c = ctk.CTkFrame(frame)
        sec_c.pack(fill="x", padx=20, pady=8)
        ctk.CTkLabel(sec_c, text="3. Direct Office Conversions (تحويل مباشر بين CSV و Excel)", font=ctk.CTkFont(size=15, weight="bold")).pack(anchor="w", padx=14, pady=(12, 8))
        row_c = ctk.CTkFrame(sec_c, fg_color="transparent")
        row_c.pack(fill="x", padx=12, pady=(0, 12))
        ctk.CTkButton(row_c, text="CSV → Excel (.xlsx)", height=38, fg_color="#7C3AED", command=self._run_csv_to_xlsx).pack(side="left", expand=True, fill="x", padx=6)
        ctk.CTkButton(row_c, text="Excel (.xlsx) → CSV (Arabic UTF-8)", height=38, fg_color="#7C3AED", command=self._run_xlsx_to_csv).pack(side="left", expand=True, fill="x", padx=6)

    def _run_from_pdf(self, target: str):
        src = filedialog.askopenfilename(title="Select Source PDF", filetypes=[("PDF Files", "*.pdf")])
        if not src:
            return
        try:
            if target in ("png", "jpg"):
                out_dir = filedialog.askdirectory(title="Select Output Folder for Images")
                if not out_dir:
                    return
                files = engine.pdf_to_images(src, out_dir, fmt=target)
                messagebox.showinfo("Converted", f"Exported {len(files)} {target.upper()} image(s) to:\n{out_dir}")
                return
            out = filedialog.asksaveasfilename(defaultextension=f".{target}", initialfile=f"{Path(src).stem}.{target}")
            if not out:
                return
            fn = {
                "docx": engine.pdf_to_docx,
                "xlsx": engine.pdf_to_xlsx,
                "csv": engine.pdf_to_csv,
                "pptx": engine.pdf_to_pptx,
                "html": engine.pdf_to_html,
                "txt": engine.pdf_to_txt,
            }[target]
            fn(src, out)
            messagebox.showinfo("Converted", f"Converted successfully:\n{out}")
        except Exception as exc:
            messagebox.showerror("Conversion Error", str(exc))

    def _run_to_pdf(self, kind: str):
        ext_map = {
            "docx": [("Word Documents", "*.docx")],
            "xlsx": [("Excel Workbooks", "*.xlsx")],
            "csv": [("CSV Files", "*.csv")],
            "pptx": [("PowerPoint", "*.pptx")],
            "text": [("Text / HTML / Markdown", "*.txt *.md *.html *.htm")],
        }
        src = filedialog.askopenfilename(title="Select Input File", filetypes=ext_map[kind])
        if not src:
            return
        out = filedialog.asksaveasfilename(defaultextension=".pdf", initialfile=f"{Path(src).stem}.pdf", filetypes=[("PDF", "*.pdf")])
        if not out:
            return
        try:
            fn = {
                "docx": engine.docx_to_pdf,
                "xlsx": engine.xlsx_to_pdf,
                "csv": engine.csv_to_pdf,
                "pptx": engine.pptx_to_pdf,
                "text": engine.text_or_html_to_pdf,
            }[kind]
            fn(src, out)
            self._open_pdf_path(out)
            self._show_tab("viewer")
            messagebox.showinfo("Converted to PDF", f"Created PDF and opened in Viewer:\n{out}")
        except Exception as exc:
            messagebox.showerror("Conversion Error", str(exc))

    def _run_images_to_pdf(self):
        imgs = filedialog.askopenfilenames(title="Select Images", filetypes=[("Images", "*.png *.jpg *.jpeg *.webp *.bmp")])
        if not imgs:
            return
        out = filedialog.asksaveasfilename(defaultextension=".pdf", initialfile="Images_Merged_2026.pdf", filetypes=[("PDF", "*.pdf")])
        if not out:
            return
        try:
            engine.images_to_pdf(list(imgs), out)
            self._open_pdf_path(out)
            self._show_tab("viewer")
            messagebox.showinfo("Converted", f"Combined {len(imgs)} image(s) into PDF:\n{out}")
        except Exception as exc:
            messagebox.showerror("Error", str(exc))

    def _run_csv_to_xlsx(self):
        src = filedialog.askopenfilename(filetypes=[("CSV Files", "*.csv")])
        if not src:
            return
        out = filedialog.asksaveasfilename(defaultextension=".xlsx", initialfile=f"{Path(src).stem}.xlsx")
        if not out:
            return
        try:
            engine.csv_to_xlsx(src, out)
            messagebox.showinfo("Converted", f"Saved Excel workbook:\n{out}")
        except Exception as exc:
            messagebox.showerror("Error", str(exc))

    def _run_xlsx_to_csv(self):
        src = filedialog.askopenfilename(filetypes=[("Excel Workbooks", "*.xlsx")])
        if not src:
            return
        out = filedialog.asksaveasfilename(defaultextension=".csv", initialfile=f"{Path(src).stem}.csv")
        if not out:
            return
        try:
            engine.xlsx_to_csv(src, out)
            messagebox.showinfo("Converted", f"Saved UTF-8 BOM CSV:\n{out}")
        except Exception as exc:
            messagebox.showerror("Error", str(exc))

    # =========================================================================
    # TAB 3: 16 PRO OFFLINE PDF TOOLS
    # =========================================================================

    def _build_tools_tab(self):
        frame = ctk.CTkScrollableFrame(self.main_area, corner_radius=0)
        self.tabs["tools"] = frame

        ctk.CTkLabel(frame, text="16 Commercial-Grade Offline Pro PDF Tools", font=ctk.CTkFont(size=22, weight="bold")).pack(anchor="w", padx=20, pady=(18, 2))
        ctk.CTkLabel(frame, text=f"{CREDIT_EN}  |  {CREDIT_AR}", text_color="#38BDF8").pack(anchor="w", padx=20, pady=(0, 14))

        grid = ctk.CTkFrame(frame, fg_color="transparent")
        grid.pack(fill="both", expand=True, padx=16, pady=6)
        for c in range(3):
            grid.grid_columnconfigure(c, weight=1)

        tools = [
            ("Merge PDFs", "Combine multiple PDFs", "#DC2626", self._tool_merge),
            ("Split PDF", "Extract ranges or single pages", "#0284C7", self._tool_split),
            ("Rotate / Reorder Pages", "Rotate 90° or reorder pages", "#7C3AED", self._tool_rotate),
            ("Compress PDF", "Reduce file size offline", "#D97706", self._tool_compress),
            ("Grayscale / B&W PDF", "Ink-saving grayscale PDF", "#475569", self._tool_grayscale),
            ("Protect PDF (AES-256)", "Encrypt with password", "#DC2626", self._tool_protect),
            ("Unlock PDF", "Remove PDF password lock", "#059669", self._tool_unlock),
            ("Add Watermark", "Diagonal/Tiled EN or Arabic", "#0284C7", self._tool_watermark),
            ("Add Page Numbers", "Stamp Page X of Y", "#7C3AED", self._tool_page_numbers),
            ("Headers & Bates IDs", "Legal Bates numbering", "#0D9488", self._tool_bates),
            ("Compare Two PDFs", "Page-by-page diff report", "#DC2626", self._tool_compare),
            ("Edit PDF Metadata", "Title, Author, Keywords", "#D97706", self._tool_metadata),
            ("Create New PDF", "Create from custom text", "#059669", self._tool_create_new),
            ("Open Format Converter", "Word / Excel / CSV / PPTX", "#0284C7", lambda: self._show_tab("converter")),
            ("Open Sample Guide", "Interactive test PDF", "#0D9488", self._load_welcome_pdf),
        ]

        for idx, (title, sub, color, cmd) in enumerate(tools):
            card = ctk.CTkFrame(grid)
            card.grid(row=idx // 3, column=idx % 3, padx=8, pady=8, sticky="nsew")
            ctk.CTkLabel(card, text=title, font=ctk.CTkFont(size=15, weight="bold")).pack(anchor="w", padx=14, pady=(12, 2))
            ctk.CTkLabel(card, text=sub, font=ctk.CTkFont(size=12), text_color="#94A3B8").pack(anchor="w", padx=14, pady=(0, 10))
            ctk.CTkButton(card, text="Launch", fg_color=color, command=cmd).pack(fill="x", padx=14, pady=(0, 12))

    def _tool_merge(self):
        files = filedialog.askopenfilenames(title="Select 2+ PDFs to Merge", filetypes=[("PDF", "*.pdf")])
        if len(files) < 2:
            return
        out = filedialog.asksaveasfilename(defaultextension=".pdf", initialfile="Merged_PDF_2026.pdf")
        if not out:
            return
        try:
            engine.merge_pdfs(list(files), out)
            self._open_pdf_path(out)
            self._show_tab("viewer")
        except Exception as exc:
            messagebox.showerror("Merge Error", str(exc))

    def _tool_split(self):
        src = filedialog.askopenfilename(filetypes=[("PDF", "*.pdf")])
        if not src:
            return
        rng = simpledialog.askstring("Split PDF", "Enter page range (e.g. 1-3, 5) or leave blank to split every page:", initialvalue="1-2")
        out_dir = filedialog.askdirectory(title="Select Output Folder")
        if not out_dir:
            return
        try:
            outs = engine.split_pdf(src, out_dir, range_expr=rng or "", split_every=not bool((rng or "").strip()))
            messagebox.showinfo("Split Complete", f"Created {len(outs)} PDF file(s) in:\n{out_dir}")
        except Exception as exc:
            messagebox.showerror("Split Error", str(exc))

    def _tool_rotate(self):
        src = filedialog.askopenfilename(filetypes=[("PDF", "*.pdf")])
        if not src:
            return
        deg = simpledialog.askinteger("Rotate All Pages", "Enter clockwise rotation degrees (90, 180, 270):", initialvalue=90)
        if not deg:
            return
        out = filedialog.asksaveasfilename(defaultextension=".pdf", initialfile=f"{Path(src).stem}_rotated.pdf")
        if not out:
            return
        try:
            d = fitz.open(src)
            ops = [(i, deg) for i in range(len(d))]
            d.close()
            engine.organize_pages(src, out, ops)
            self._open_pdf_path(out)
            self._show_tab("viewer")
        except Exception as exc:
            messagebox.showerror("Rotate Error", str(exc))

    def _tool_compress(self):
        src = filedialog.askopenfilename(filetypes=[("PDF", "*.pdf")])
        if not src:
            return
        lvl = simpledialog.askstring("Compress PDF", "Compression level (low / medium / high):", initialvalue="medium")
        if not lvl:
            return
        out = filedialog.asksaveasfilename(defaultextension=".pdf", initialfile=f"{Path(src).stem}_compressed.pdf")
        if not out:
            return
        try:
            o, n, pct = engine.compress_pdf(src, out, lvl)
            messagebox.showinfo("Compressed", f"Original: {engine.format_size(o)}\nCompressed: {engine.format_size(n)}\nSaved: {pct}%")
            self._open_pdf_path(out)
            self._show_tab("viewer")
        except Exception as exc:
            messagebox.showerror("Compress Error", str(exc))

    def _tool_grayscale(self):
        src = filedialog.askopenfilename(filetypes=[("PDF", "*.pdf")])
        if not src:
            return
        out = filedialog.asksaveasfilename(defaultextension=".pdf", initialfile=f"{Path(src).stem}_grayscale.pdf")
        if not out:
            return
        try:
            o, n, pct = engine.convert_to_grayscale_pdf(src, out)
            messagebox.showinfo("Grayscale Complete", f"Original: {engine.format_size(o)} → Grayscale: {engine.format_size(n)} ({pct}% smaller)")
            self._open_pdf_path(out)
            self._show_tab("viewer")
        except Exception as exc:
            messagebox.showerror("Grayscale Error", str(exc))

    def _tool_protect(self):
        src = filedialog.askopenfilename(filetypes=[("PDF", "*.pdf")])
        if not src:
            return
        pwd = simpledialog.askstring("Protect PDF (AES-256)", "Enter password to lock PDF:", show="*")
        if not pwd:
            return
        out = filedialog.asksaveasfilename(defaultextension=".pdf", initialfile=f"{Path(src).stem}_locked.pdf")
        if not out:
            return
        try:
            engine.protect_pdf(src, out, pwd)
            messagebox.showinfo("Protected", f"Encrypted with AES-256:\n{out}")
        except Exception as exc:
            messagebox.showerror("Protect Error", str(exc))

    def _tool_unlock(self):
        src = filedialog.askopenfilename(filetypes=[("PDF", "*.pdf")])
        if not src:
            return
        pwd = simpledialog.askstring("Unlock PDF", "Enter current PDF password:", show="*")
        if pwd is None:
            return
        out = filedialog.asksaveasfilename(defaultextension=".pdf", initialfile=f"{Path(src).stem}_unlocked.pdf")
        if not out:
            return
        try:
            engine.unlock_pdf(src, out, pwd)
            self._open_pdf_path(out)
            self._show_tab("viewer")
            messagebox.showinfo("Unlocked", f"Saved unlocked PDF:\n{out}")
        except Exception as exc:
            messagebox.showerror("Unlock Error", str(exc))

    def _tool_watermark(self):
        src = filedialog.askopenfilename(filetypes=[("PDF", "*.pdf")])
        if not src:
            return
        txt = simpledialog.askstring("Watermark Text", "Enter watermark (English or Arabic):", initialvalue="CONFIDENTIAL — IBRAHIM JABER 2026")
        if not txt:
            return
        out = filedialog.asksaveasfilename(defaultextension=".pdf", initialfile=f"{Path(src).stem}_watermarked.pdf")
        if not out:
            return
        try:
            engine.add_watermark(src, out, txt)
            self._open_pdf_path(out)
            self._show_tab("viewer")
        except Exception as exc:
            messagebox.showerror("Watermark Error", str(exc))

    def _tool_page_numbers(self):
        src = filedialog.askopenfilename(filetypes=[("PDF", "*.pdf")])
        if not src:
            return
        out = filedialog.asksaveasfilename(defaultextension=".pdf", initialfile=f"{Path(src).stem}_numbered.pdf")
        if not out:
            return
        try:
            engine.add_page_numbers(src, out)
            self._open_pdf_path(out)
            self._show_tab("viewer")
        except Exception as exc:
            messagebox.showerror("Page Numbers Error", str(exc))

    def _tool_bates(self):
        src = filedialog.askopenfilename(filetypes=[("PDF", "*.pdf")])
        if not src:
            return
        prefix = simpledialog.askstring("Bates Prefix", "Enter Bates ID prefix (e.g. DOC- / CASE-):", initialvalue="DOC-")
        if prefix is None:
            return
        out = filedialog.asksaveasfilename(defaultextension=".pdf", initialfile=f"{Path(src).stem}_bates.pdf")
        if not out:
            return
        try:
            engine.add_headers_footers_bates(src, out, header_center="CONFIDENTIAL", bates_prefix=prefix)
            self._open_pdf_path(out)
            self._show_tab("viewer")
        except Exception as exc:
            messagebox.showerror("Bates Error", str(exc))

    def _tool_compare(self):
        f1 = filedialog.askopenfilename(title="Select Document A", filetypes=[("PDF", "*.pdf")])
        if not f1:
            return
        f2 = filedialog.askopenfilename(title="Select Document B", filetypes=[("PDF", "*.pdf")])
        if not f2:
            return
        try:
            report = engine.compare_pdfs(f1, f2)
            win = ctk.CTkToplevel(self)
            win.title("PDF Diff Comparison Report")
            win.geometry("680x480")
            box = ctk.CTkTextbox(win)
            box.pack(fill="both", expand=True, padx=14, pady=14)
            box.insert("1.0", report)
        except Exception as exc:
            messagebox.showerror("Compare Error", str(exc))

    def _tool_metadata(self):
        src = filedialog.askopenfilename(filetypes=[("PDF", "*.pdf")])
        if not src:
            return
        meta = engine.read_metadata(src)
        title = simpledialog.askstring("Edit Metadata — Title", "Title:", initialvalue=meta.get("title", ""))
        author = simpledialog.askstring("Edit Metadata — Author", "Author:", initialvalue=meta.get("author", "Ibrahim Jaber"))
        if title is None or author is None:
            return
        out = filedialog.asksaveasfilename(defaultextension=".pdf", initialfile=f"{Path(src).stem}_meta.pdf")
        if not out:
            return
        try:
            engine.update_metadata(src, out, {"title": title, "author": author})
            messagebox.showinfo("Updated", f"Saved updated metadata PDF:\n{out}")
        except Exception as exc:
            messagebox.showerror("Metadata Error", str(exc))

    def _tool_create_new(self):
        title = simpledialog.askstring("Create New PDF", "Document Title (English or Arabic):", initialvalue="New Document 2026")
        if title is None:
            return
        body = simpledialog.askstring("Create New PDF", "Document Body Text:", initialvalue=f"Created offline with {engine.CREATOR_TAG}")
        if body is None:
            return
        out = filedialog.asksaveasfilename(defaultextension=".pdf", initialfile="New_Document_2026.pdf")
        if not out:
            return
        try:
            engine._render_lines_to_pdf(title, body.splitlines(), out)
            self._open_pdf_path(out)
            self._show_tab("viewer")
        except Exception as exc:
            messagebox.showerror("Create Error", str(exc))

    # =========================================================================
    # TAB 4: SETTINGS & ABOUT (IBRAHIM JABER 2026)
    # =========================================================================

    def _build_about_tab(self):
        frame = ctk.CTkFrame(self.main_area, corner_radius=0)
        self.tabs["about"] = frame

        card = ctk.CTkFrame(frame, corner_radius=16)
        card.pack(fill="x", padx=28, pady=28)

        ctk.CTkLabel(card, text=f"{APP_NAME}  v{APP_VERSION}", font=ctk.CTkFont(size=24, weight="bold")).pack(anchor="w", padx=22, pady=(20, 4))
        ctk.CTkLabel(card, text=f"{CREDIT_EN}  |  {CREDIT_AR}", font=ctk.CTkFont(size=16, weight="bold"), text_color="#38BDF8").pack(anchor="w", padx=22, pady=(0, 6))
        ctk.CTkLabel(card, text="© 2026 Ibrahim Jaber. All Rights Reserved. | © 2026 إبراهيم جابر. جميع الحقوق محفوظة.", text_color="#94A3B8").pack(anchor="w", padx=22, pady=(0, 14))
        ctk.CTkLabel(
            card,
            text=(
                "• 100% Offline Standalone Windows PC (.exe) + Android (.apk) Suite\n"
                "• Universal Bidirectional Converter: PDF ↔ Word (.docx), Excel (.xlsx), CSV, PowerPoint (.pptx), Images, HTML & TXT\n"
                "• Full Arabic RTL & English Bilingual Interface\n"
                "• Pro Editor: True Redaction, Whiteout, Pen, Highlighter, Underline, Strike, Shapes, Stamps, QR Codes & Signature Pad\n"
                "• 16 Commercial-Grade Offline Tools: Merge, Split, Organize, Compress, Grayscale, AES-256 Lock/Unlock, Watermark, Bates & Compare Diff"
            ),
            justify="left",
            font=ctk.CTkFont(size=13),
        ).pack(anchor="w", padx=22, pady=(0, 22))


if __name__ == "__main__":
    app = PdfProStudioApp()
    app.mainloop()
