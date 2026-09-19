"""Main window integration tests (tasks 7.1–7.4)."""

import pytest
from PySide6.QtWidgets import QFileDialog, QMessageBox

from app.config import load_config
from app.main_window import MainWindow
from app.profiles.profile import MachineProfile

PROFILE = MachineProfile(
    name="Test Mill",
    bed_x=300.0,
    bed_y=180.0,
    bed_z=45.0,
    max_cut=800.0,
    max_rapid=1000.0,
    safe_z=5.0,
)

WIDER_PROFILE = MachineProfile(
    name="Wide Mill",
    bed_x=400.0,
    bed_y=200.0,
    bed_z=45.0,
    max_cut=800.0,
    max_rapid=1000.0,
    safe_z=5.0,
)


@pytest.fixture
def window(qtbot, tmp_path):
    win = MainWindow(profiles=[PROFILE, WIDER_PROFILE], config_path=tmp_path / "config.yaml")
    qtbot.addWidget(win)
    return win


@pytest.fixture(autouse=True)
def _no_real_prompts(monkeypatch):
    """Never let a real modal dialog appear and block the run.

    pytest-qt closes tracked widgets after each test, which reaches
    closeEvent/_maybe_save for any document left modified.
    """
    monkeypatch.setattr(
        QMessageBox,
        "question",
        staticmethod(lambda *a, **k: QMessageBox.StandardButton.Discard),
    )


@pytest.fixture
def save_dialog(tmp_path, monkeypatch):
    calls = []
    targets = []

    def fake_save(parent, caption, dir_, filter_):
        calls.append(dir_)
        path = targets.pop(0) if targets else str(tmp_path / "saved.nc")
        return (path, "")

    monkeypatch.setattr(QFileDialog, "getSaveFileName", staticmethod(fake_save))
    return targets


# --- task 7.1: document lifecycle ---------------------------------------------


def test_new_document_unmodified_no_prompt(qtbot, window, monkeypatch):
    monkeypatch.setattr(
        QMessageBox, "question", staticmethod(lambda *a, **k: pytest.fail("no prompt expected"))
    )
    window.new_document()
    assert window.editor.toPlainText() == ""
    assert window.document.path is None
    assert not window.document.modified
    assert "untitled" in window.windowTitle()


def test_save_clears_modified_and_writes_file(qtbot, window, save_dialog):
    window.editor.setPlainText("G1 X10 F600\n")
    assert window.document.modified
    assert window.save_file()
    assert window.document.path.name == "saved.nc"
    assert window.document.path.read_text(encoding="utf-8") == "G1 X10 F600\n"
    assert not window.document.modified
    assert "saved.nc" in window.windowTitle() and "*" not in window.windowTitle()


def test_save_as_switches_path_for_later_saves(qtbot, window, save_dialog, tmp_path):
    save_dialog.append(str(tmp_path / "first.nc"))
    window.editor.setPlainText("G1 X10\n")
    window.save_file()
    first_path = window.document.path

    save_dialog.append(str(tmp_path / "second.nc"))
    window.editor.setPlainText("G1 X10\nG1 X20\n")
    window.save_file_as()
    assert window.document.path != first_path

    window.editor.setPlainText("G1 X10\nG1 X20\nG1 X30\n")
    window.save_file()  # no dialog: writes to the save-as path
    assert window.document.path.read_text(encoding="utf-8").count("G1") == 3
    assert first_path.read_text(encoding="utf-8").count("G1") != 3


def test_cancel_prompt_keeps_content(qtbot, window, monkeypatch):
    window.editor.setPlainText("G1 X10 F600\n")
    monkeypatch.setattr(
        QMessageBox, "question", staticmethod(lambda *a, **k: QMessageBox.StandardButton.Cancel)
    )
    window.new_document()
    assert window.editor.toPlainText() == "G1 X10 F600\n"
    assert window.document.modified


def test_open_file_shows_content_and_title(qtbot, window, tmp_path):
    path = tmp_path / "part.nc"
    path.write_text("G0 X5\nG1 X10 F600\n", encoding="utf-8")
    assert window.open_file(path)
    assert window.editor.toPlainText() == "G0 X5\nG1 X10 F600\n"
    assert window.document.path == path
    assert "part.nc" in window.windowTitle()


def test_close_cancelled_leaves_window_open(qtbot, window, monkeypatch):
    window.show()
    qtbot.waitExposed(window)
    window.editor.setPlainText("G1 X10\n")
    monkeypatch.setattr(
        QMessageBox, "question", staticmethod(lambda *a, **k: QMessageBox.StandardButton.Cancel)
    )
    window.close()
    assert window.isVisible()


# --- task 7.2: debounced live re-parse ------------------------------------------


def test_debounce_updates_preview_and_status(qtbot, window):
    window.editor.setPlainText("G0 X10\n")
    qtbot.waitUntil(lambda: len(window.preview.move_items()) == 1)
    assert "cut 0.0 mm" in window._stats_label.text()

    window.editor.setPlainText("G0 X10\nG1 X20 F600\n")
    qtbot.waitUntil(lambda: len(window.preview.move_items()) == 2)


def test_syntax_error_sets_stale_indicator(qtbot, window):
    window.editor.setPlainText("G0 X10\nG1 X20 F600\n")
    qtbot.waitUntil(lambda: len(window.preview.move_items()) == 2)

    window.editor.setPlainText("G0 X10\nG1 X\n")
    qtbot.waitUntil(window.preview.is_stale)
    assert len(window.preview.move_items()) == 2  # last good scene kept


# --- task 7.3: status bar & profile selector --------------------------------------


def test_statistics_rendered_for_known_program(qtbot, window):
    window.editor.setPlainText("G1 X10 F600\n")
    qtbot.waitUntil(lambda: "bbox" in window._stats_label.text())
    stats = window._stats_label.text()
    assert "bbox 0.0..10.0 x 0.0..0.0 mm" in stats
    assert "cut 10.0 mm" in stats
    assert "rapid 0.0 mm" in stats
    assert "est. 0.0 min" in stats  # 10 mm at 600 mm/min = 0.0167 min


def test_caret_position_shown_in_status(qtbot, window):
    window.editor.setPlainText("G1 X10\nG1 X20\n")
    window.editor.gotoLine(2)
    # gotoLine selects the line, so the caret reports the selection end
    assert window._pos_label.text().startswith("2:")


def test_profile_switch_recomputes_warnings_and_bed(qtbot, window):
    window.editor.setPlainText("G1 X350 F600\n")
    qtbot.waitUntil(lambda: len(window.preview.move_items()) == 1)
    assert "1 warning(s)" in window._warnings_label.text()
    assert "300" in window._warnings_label.toolTip()

    window.select_profile("Wide Mill")
    assert window._warnings_label.text() == ""
    assert window.preview._bed_item.rect().width() == 400.0


# --- task 7.4: app config ----------------------------------------------------------

def test_selected_profile_active_after_config_reload(qtbot, tmp_path):
    config_path = tmp_path / "config.yaml"
    first = MainWindow(profiles=[PROFILE, WIDER_PROFILE], config_path=config_path)
    first.select_profile("Wide Mill")
    first.close()

    second = MainWindow(profiles=[PROFILE, WIDER_PROFILE], config_path=config_path)
    assert second._profile_combo.currentText() == "Wide Mill"
    assert second.preview._bed_item.rect().width() == 400.0


def test_close_saves_geometry_and_profile(qtbot, window, tmp_path):
    window.select_profile("Wide Mill")
    window.close()
    config = load_config(tmp_path / "config.yaml")
    assert config["active_profile"] == "Wide Mill"
    assert config["window_geometry"]
