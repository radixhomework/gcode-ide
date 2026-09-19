"""Main window: editor + preview splitter, document actions, live re-parse,
status bar with toolpath statistics/warnings, profile selector, app config.
"""

from __future__ import annotations

import base64
from pathlib import Path

from PySide6.QtCore import QTimer
from PySide6.QtGui import QAction, QKeySequence
from PySide6.QtWidgets import (
    QComboBox,
    QFileDialog,
    QLabel,
    QMainWindow,
    QMessageBox,
    QSplitter,
    QStatusBar,
)

from .config import load_config, save_config
from .document import GCODE_NAME_FILTER, Document, read_document, write_document
from .editor.gcode_editor import GCodeEditor
from .parsing.model import Severity
from .parsing.parser import parse
from .preview.diagnostics import compute_statistics, compute_warnings
from .preview.preview_view import PreviewView
from .profiles.loader import load_profiles
from .profiles.profile import MachineProfile
from .profiles.qt_paths import app_config_file, user_profiles_dir
from .resources import PROFILES_DIR

DEBOUNCE_MS = 300


class MainWindow(QMainWindow):
    def __init__(
        self,
        profiles: list[MachineProfile] | None = None,
        config_path: Path | str | None = None,
        parent=None,
    ) -> None:
        super().__init__(parent)
        self.setWindowTitle("G-Code IDE")

        if profiles is None:
            profiles = load_profiles(PROFILES_DIR, user_profiles_dir()).profiles
        self._profiles = profiles or []
        self._config_path = Path(config_path) if config_path is not None else app_config_file()
        self._config = load_config(self._config_path)

        self.document = Document()
        self._loading = False
        self._last_good_result = None

        self._build_ui()
        self._restore_active_profile()
        self._update_title()
        self._refresh_status()

    # -- UI construction ------------------------------------------------------

    def _build_ui(self) -> None:
        self.editor = GCodeEditor()
        self.preview = PreviewView()

        splitter = QSplitter()
        splitter.addWidget(self.editor)
        splitter.addWidget(self.preview)
        splitter.setSizes([500, 500])
        self.setCentralWidget(splitter)

        self._new_action = self._action("&New", QKeySequence.StandardKey.New, self.new_document)
        self._open_action = self._action("&Open...", QKeySequence.StandardKey.Open, self.open_dialog)
        self._save_action = self._action("&Save", QKeySequence.StandardKey.Save, self.save_file)
        self._save_as_action = self._action(
            "Save &As...", QKeySequence.StandardKey.SaveAs, self.save_file_as
        )
        file_menu = self.menuBar().addMenu("&File")
        file_menu.addAction(self._new_action)
        file_menu.addAction(self._open_action)
        file_menu.addSeparator()
        file_menu.addAction(self._save_action)
        file_menu.addAction(self._save_as_action)

        status: QStatusBar = self.statusBar()
        self._pos_label = QLabel("1:1")
        self._stats_label = QLabel("")
        self._warnings_label = QLabel("")
        self._warnings_label.setToolTip("")
        self._profile_combo = QComboBox()
        self._profile_combo.addItems([p.name for p in self._profiles])
        status.addWidget(self._pos_label)
        status.addWidget(self._stats_label)
        status.addPermanentWidget(self._warnings_label)
        status.addPermanentWidget(QLabel("Machine:"))
        status.addPermanentWidget(self._profile_combo)

        self._debounce = QTimer(self)
        self._debounce.setSingleShot(True)
        self._debounce.setInterval(DEBOUNCE_MS)
        self._debounce.timeout.connect(self._reparse)

        self.editor.textChanged.connect(self._on_text_changed)
        self.editor.caretMoved.connect(self._on_caret_moved)
        self.editor.currentLineChanged.connect(self.preview.set_current_line)
        self.preview.lineSelected.connect(self.editor.gotoLine)

        geometry = self._config.get("window_geometry")
        if geometry:
            self.restoreGeometry(base64.b64decode(geometry))

    def _action(self, text: str, shortcut, slot) -> QAction:
        action = QAction(text, self)
        action.setShortcut(QKeySequence(shortcut))
        action.triggered.connect(slot)
        return action

    # -- profile handling --------------------------------------------------------

    def _active_profile(self) -> MachineProfile | None:
        name = self._profile_combo.currentText() if hasattr(self, "_profile_combo") else None
        return self._profile_by_name(name)

    def _profile_by_name(self, name: str | None) -> MachineProfile | None:
        for profile in self._profiles:
            if profile.name == name:
                return profile
        return None

    def _restore_active_profile(self) -> None:
        preferred = self._config.get("active_profile")
        if preferred and self._profile_by_name(preferred):
            self._profile_combo.setCurrentText(preferred)
        elif self._profiles:
            self._profile_combo.setCurrentIndex(0)
        self._apply_active_profile()
        # connect only after the initial selection: populating the combo would
        # otherwise look like a user profile switch and rewrite the config
        self._profile_combo.currentTextChanged.connect(self._on_profile_selected)

    def select_profile(self, name: str) -> None:
        self._profile_combo.setCurrentText(name)

    def _on_profile_selected(self, name: str) -> None:
        self._apply_active_profile()
        self._config["active_profile"] = name
        save_config(self._config_path, self._config)

    def _apply_active_profile(self) -> None:
        profile = self._active_profile()
        if profile is not None:
            self.preview.set_profile(profile)
        self._refresh_status()

    # -- document actions ----------------------------------------------------------

    def new_document(self) -> None:
        if not self._maybe_save():
            return
        self._load_text("", path=None)

    def open_dialog(self) -> None:
        path, _ = QFileDialog.getOpenFileName(self, "Open G-code file", "", GCODE_NAME_FILTER)
        if path:
            self.open_file(path)

    def open_file(self, path: Path | str) -> bool:
        if not self._maybe_save():
            return False
        try:
            text = read_document(path)
        except OSError as exc:
            QMessageBox.warning(self, "Open failed", f"Could not open {path}:\n{exc}")
            return False
        self._load_text(text, path=Path(path))
        return True

    def save_file(self) -> bool:
        if self.document.path is None:
            return self.save_file_as()
        write_document(self.document.path, self.editor.toPlainText())
        self.document.modified = False
        self._update_title()
        return True

    def save_file_as(self) -> bool:
        suggested = str(self.document.path or "untitled.nc")
        path, _ = QFileDialog.getSaveFileName(self, "Save G-code as", suggested, GCODE_NAME_FILTER)
        if not path:
            return False
        self.document.path = Path(path)
        return self.save_file()

    def _maybe_save(self) -> bool:
        """True when it is safe to drop the current content (saved or discarded)."""
        if not self.document.modified:
            return True
        answer = QMessageBox.question(
            self,
            "Unsaved changes",
            f"The document {self.document.display_name} has unsaved changes.",
            QMessageBox.StandardButton.Save
            | QMessageBox.StandardButton.Discard
            | QMessageBox.StandardButton.Cancel,
        )
        if answer == QMessageBox.StandardButton.Save:
            return self.save_file()
        return answer == QMessageBox.StandardButton.Discard

    def _load_text(self, text: str, path: Path | None) -> None:
        self._loading = True
        try:
            self.editor.setPlainText(text)
        finally:
            self._loading = False
        self.document = Document(path=path, modified=False)
        self._update_title()
        self._reparse()

    # -- live edit → parse → preview loop ------------------------------------------

    def _on_text_changed(self) -> None:
        if not self._loading:
            self.document.modified = True
            self._update_title()
        self._debounce.start()

    def _reparse(self) -> None:
        result = parse(self.editor.toPlainText())
        if not any(d.severity is Severity.ERROR for d in result.diagnostics):
            self._last_good_result = result
        self.preview.set_toolpath(result)
        self._refresh_status()

    # -- status bar -------------------------------------------------------------------

    def _on_caret_moved(self, line: int, column: int) -> None:
        self._pos_label.setText(f"{line}:{column}")

    def _refresh_status(self) -> None:
        result = self._last_good_result
        profile = self._active_profile()
        if result is None or profile is None:
            self._stats_label.setText("")
            self._warnings_label.setText("")
            return
        stats = compute_statistics(result, profile)
        warnings = compute_warnings(result, profile)
        if stats.cut_bbox is not None:
            min_x, min_y, max_x, max_y = stats.cut_bbox
            bbox_text = f"bbox {min_x:.1f}..{max_x:.1f} x {min_y:.1f}..{max_y:.1f} mm"
        else:
            bbox_text = "bbox -"
        self._stats_label.setText(
            f"{bbox_text} | cut {stats.cut_distance:.1f} mm | rapid {stats.rapid_distance:.1f} mm"
            f" | est. {stats.estimated_time_min:.1f} min"
        )
        if warnings:
            self._warnings_label.setText(f"{len(warnings)} warning(s)")
            self._warnings_label.setToolTip("\n".join(w.message for w in warnings))
        else:
            self._warnings_label.setText("")
            self._warnings_label.setToolTip("")

    # -- misc -------------------------------------------------------------------------

    def _update_title(self) -> None:
        marker = "*" if self.document.modified else ""
        self.setWindowTitle(f"{self.document.display_name}{marker} - G-Code IDE")

    def closeEvent(self, event) -> None:
        if not self._maybe_save():
            event.ignore()
            return
        self._config["window_geometry"] = base64.b64encode(bytes(self.saveGeometry())).decode("ascii")
        self._config["active_profile"] = self._profile_combo.currentText()
        save_config(self._config_path, self._config)
        event.accept()
