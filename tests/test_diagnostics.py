"""Statistics and warning tests (tasks 4.1–4.2)."""

import math

from app.parsing.model import Move, MoveKind, ParseResult, Position
from app.parsing.parser import parse
from app.preview.diagnostics import compute_statistics, compute_warnings
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


def _move(kind, start, end, line, feed=None):
    return Move(kind=kind, start=Position(*start), end=Position(*end), line=line, feed=feed)


SYNTHETIC = ParseResult(
    moves=(
        _move(MoveKind.RAPID, (0, 0, 0), (10, 0, 0), 1),
        _move(MoveKind.CUT, (10, 0, -1), (10, 20, -1), 2, feed=100.0),
        _move(MoveKind.CUT, (10, 20, -1), (30, 20, -1), 3, feed=200.0),
        _move(MoveKind.CUT, (30, 20, -1), (30, 25, -1), 4, feed=None),
        _move(MoveKind.RAPID, (30, 25, 0), (0, 0, 0), 5),
    )
)


def test_statistics_match_hand_computed_values():
    stats = compute_statistics(SYNTHETIC, PROFILE)
    assert stats.cut_bbox == (10.0, 0.0, 30.0, 25.0)
    assert stats.cut_distance == 45.0
    assert math.isclose(stats.rapid_distance, 10.0 + math.sqrt(1525))
    expected_time = 20 / 100 + 20 / 200 + 5 / 800 + (10.0 + math.sqrt(1525)) / 1000
    assert math.isclose(stats.estimated_time_min, expected_time)


def test_missing_feed_moves_counted_at_max_cut():
    stats = compute_statistics(SYNTHETIC, PROFILE)
    expected_time = 20 / 100 + 20 / 200 + 5 / 800 + (10.0 + math.sqrt(1525)) / 1000
    assert math.isclose(stats.estimated_time_min, expected_time)  # 5 mm at 800, not skipped
    warnings = compute_warnings(SYNTHETIC, PROFILE)
    missing = [w for w in warnings if w.kind == "missing-feed"]
    assert len(missing) == 1 and missing[0].lines == (4,)


def test_empty_toolpath_statistics():
    stats = compute_statistics(ParseResult(), PROFILE)
    assert stats.cut_bbox is None
    assert stats.cut_distance == 0.0
    assert stats.rapid_distance == 0.0
    assert stats.estimated_time_min == 0.0


def test_out_of_bed_x_350_on_300_bed():
    result = parse("G1 X350 F600\n")
    warnings = compute_warnings(result, PROFILE)
    assert len(warnings) == 1
    warning = warnings[0]
    assert warning.kind == "out-of-bed"
    assert warning.lines == (1,)
    assert "300" in warning.message


def test_out_of_bed_covers_start_and_end_points():
    result = parse("G0 X10 Y10\nG1 X-5 F600\n")
    warnings = compute_warnings(result, PROFILE)
    assert warnings[0].kind == "out-of-bed"
    assert warnings[0].lines == (2,)


def test_excessive_feed_f2000_vs_800_cap():
    result = parse("F2000\nG1 X10\n")
    warnings = compute_warnings(result, PROFILE)
    assert len(warnings) == 1
    warning = warnings[0]
    assert warning.kind == "excessive-feed"
    assert warning.lines == (2,)
    assert "800" in warning.message


def test_feed_at_cap_is_not_flagged():
    result = parse("F800\nG1 X10\n")
    assert compute_warnings(result, PROFILE) == []
