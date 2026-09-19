"""End-to-end verification against assets/examples/demo.nc (task 8.1)."""

from pathlib import Path

import pytest

from app.main_window import MainWindow
from app.profiles.profile import MachineProfile
from app.resources import EXAMPLES_DIR

PROFILE = MachineProfile(
    name="Test Mill",
    bed_x=300.0,
    bed_y=180.0,
    bed_z=45.0,
    max_cut=800.0,
    max_rapid=1000.0,
    safe_z=5.0,
)


@pytest.fixture
def window(qtbot, tmp_path):
    win = MainWindow(profiles=[PROFILE], config_path=tmp_path / "config.yaml")
    qtbot.addWidget(win)
    return win


def test_demo_program_end_to_end(qtbot, window):
    demo = Path(EXAMPLES_DIR) / "demo.nc"
    assert window.open_file(demo)
    assert not window.preview.is_stale()

    # toolpath rendered: 4 linear cuts + 2 flattened arcs + 3 rapids
    assert len(window.preview.move_items()) > 50

    # statistics: cuts = 6 + 20 + 5 + 24.2 mm plus two quarter arcs of r=20
    # rapids = 15 + 299.2 + sqrt(122636); time = cut/600 + rapid/1000
    stats = window._stats_label.text()
    assert "bbox 10.0..75.0 x 10.0..30.0 mm" in stats
    assert "cut 118.0 mm" in stats
    assert "rapid 664.4 mm" in stats
    assert "est. 0.9 min" in stats

    # warnings: the deliberate out-of-bed rapid (lines 16-17), no feed issues
    assert window._warnings_label.text() == "1 warning(s)"
    tooltip = window._warnings_label.toolTip()
    assert "300x180" in tooltip
    assert "16" in tooltip

    # bidirectional sync still live: the arc on line 8 highlights from the editor
    window.editor.gotoLine(8)
    assert len(window.preview._highlight_items) > 1  # flattened arc = many segments
