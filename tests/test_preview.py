"""Preview widget tests (tasks 6.1–6.4)."""

import pytest
from PySide6.QtCore import QPointF, QRectF, Qt

from app.parsing.parser import parse
from app.preview.preview_view import PreviewView
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


def _make_view(qtbot, profile=PROFILE):
    view = PreviewView()
    qtbot.addWidget(view)
    view.set_profile(profile)
    return view


# --- task 6.1: bed follows the profile ---------------------------------------


def test_bed_rect_resizes_when_profile_changes(qtbot):
    view = _make_view(qtbot)
    assert view._bed_item.rect() == QRectF(0, 0, 300, 180)
    assert view.sceneRect() == QRectF(0, 0, 300, 180)
    view.set_profile(WIDER_PROFILE)
    assert view._bed_item.rect() == QRectF(0, 0, 400, 200)
    assert view.sceneRect() == QRectF(0, 0, 400, 200)


def test_y_axis_points_up(qtbot):
    view = _make_view(qtbot)
    assert view.transform().m22() < 0  # Y flipped: scene +Y renders upward


# --- task 6.2: toolpath rendering --------------------------------------------


def test_one_item_per_move_with_dash_and_attribution(qtbot):
    view = _make_view(qtbot)
    result = parse("G0 X10\nG1 X20 F600\nG1 X30 Z-2\n")
    view.set_toolpath(result)
    items = view.move_items()
    assert len(items) == 3
    assert [item.data(0) for item in items] == [1, 2, 3]
    assert items[0].pen().style() == Qt.PenStyle.DashLine  # rapid
    assert items[1].pen().style() == Qt.PenStyle.SolidLine
    assert items[2].pen().style() == Qt.PenStyle.SolidLine


def test_depth_shading_distinguishes_depths(qtbot):
    view = _make_view(qtbot)
    result = parse("G1 X10 Z-1 F600\nG1 X20 Z-4\n")
    view.set_toolpath(result)
    items = view.move_items()
    assert items[0].pen().color() != items[1].pen().color()


def test_out_of_bed_move_marked_red_and_recolored_on_profile_switch(qtbot):
    view = _make_view(qtbot)
    result = parse("G1 X350 F600\n")
    view.set_toolpath(result)
    pen = view.move_items()[0].pen()
    assert pen.color().name().upper() == "#D62728"

    view.set_profile(WIDER_PROFILE)  # 400 mm bed: the move fits now
    pen = view.move_items()[0].pen()
    assert pen.color().name().upper() != "#D62728"


# --- task 6.3: bidirectional sync ---------------------------------------------


def test_click_emits_line_selected(qtbot):
    view = _make_view(qtbot)
    view.resize(500, 350)
    view.show()
    qtbot.waitExposed(view)
    result = parse("G0 X10\nG1 X20 F600\n")
    view.set_toolpath(result)

    scene_point = QPointF(15, 0)  # midpoint of the line-2 cut segment
    global_pos = view.mapToGlobal(view.mapFromScene(scene_point))
    vp_pos = view.viewport().mapFromGlobal(global_pos)
    with qtbot.waitSignal(view.lineSelected) as blocker:
        qtbot.mouseClick(view.viewport(), Qt.MouseButton.LeftButton, pos=vp_pos)
    assert blocker.args == [2]


def test_set_current_line_highlights_that_lines_segments(qtbot):
    view = _make_view(qtbot)
    result = parse("G0 X10\nG1 X20 F600\n")
    view.set_toolpath(result)
    view.set_current_line(2)
    highlights = view._highlight_items
    assert len(highlights) == 1
    assert highlights[0].line() == view.move_items()[1].line()
    assert highlights[0].line() != view.move_items()[0].line()


def test_set_current_line_without_moves_is_noop(qtbot):
    view = _make_view(qtbot)
    view.set_current_line(5)
    assert view._highlight_items == []


# --- task 6.4: stale handling ---------------------------------------------------


def test_error_parse_keeps_last_good_scene_and_sets_stale(qtbot):
    view = _make_view(qtbot)
    good = parse("G0 X10\nG1 X20 F600\n")
    view.set_toolpath(good)
    assert not view.is_stale()
    count = len(view.move_items())

    bad = parse("G1 X\n")  # syntax error
    view.set_toolpath(bad)
    assert view.is_stale()
    assert len(view.move_items()) == count  # scene retained

    view.set_toolpath(good)
    assert not view.is_stale()


def test_warning_only_parse_is_not_stale(qtbot):
    view = _make_view(qtbot)
    result = parse("G2 X20 Y20\n")  # bad arc data: WARNING, not ERROR
    view.set_toolpath(result)
    assert not view.is_stale()


def test_stale_signal_fires(qtbot):
    view = _make_view(qtbot)
    view.set_toolpath(parse("G1 X10 F600\n"))
    with qtbot.waitSignal(view.staleChanged) as blocker:
        view.set_toolpath(parse("G1 X\n"))
    assert blocker.args == [True]


@pytest.mark.parametrize("text,count", [("G0 X10 Y10\nG1 X20 Y20 F600\n", 2), ("", 0)])
def test_item_count_matches_move_count(qtbot, text, count):
    view = _make_view(qtbot)
    view.set_toolpath(parse(text))
    assert len(view.move_items()) == count
