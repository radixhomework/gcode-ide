"""Modal interpreter and arc tests (tasks 3.2–3.4)."""

import math

from app.parsing.model import MoveKind, Severity, Spindle
from app.parsing.parser import parse


def _endpoints(result):
    return [(m.end.x, m.end.y, m.end.z) for m in result.moves]


# --- task 3.2: modal interpreter -------------------------------------------


def test_incremental_inches_end_at_25_4():
    result = parse("G20 G91 G1 X1 Y0\nG1 Y1\n")
    cuts = [m for m in result.moves if m.kind is MoveKind.CUT]
    assert len(cuts) == 2
    assert (round(cuts[0].end.x, 6), round(cuts[0].end.y, 6)) == (25.4, 0.0)
    assert (round(cuts[1].end.x, 6), round(cuts[1].end.y, 6)) == (25.4, 25.4)


def test_feed_is_modal():
    result = parse("F600\nG1 X10\nG1 X20\nF900\nG1 X30\n")
    feeds = [m.feed for m in result.moves]
    assert feeds == [600.0, 600.0, 900.0]


def test_feed_converted_from_inches():
    result = parse("G20\nG1 X1 F10\n")
    move = result.moves[0]
    assert abs(move.feed - 254.0) < 1e-9


def test_rapid_moves_carry_no_feed():
    result = parse("F600\nG0 X10\nG1 X20\n")
    assert result.moves[0].kind is MoveKind.RAPID and result.moves[0].feed is None
    assert result.moves[1].kind is MoveKind.CUT and result.moves[1].feed == 600.0


def test_modal_motion_continuation():
    result = parse("G1 X10 Y0 F500\nX20\nX30\n")
    assert all(m.kind is MoveKind.CUT for m in result.moves)
    assert _endpoints(result) == [(10, 0, 0), (20, 0, 0), (30, 0, 0)]


def test_absolute_and_incremental_modes():
    result = parse("G90 G1 X10 Y10\nG91 G1 X5 Y-5\n")
    assert _endpoints(result) == [(10, 10, 0), (15, 5, 0)]


def test_spindle_state_tracked_and_associated():
    result = parse("M3 S10000\nG1 X10 F600\nM5\nG1 X20\n")
    assert [m.spindle for m in result.moves] == [Spindle.CW, Spindle.OFF]


def test_line_attribution():
    text = "\n\nG0 X10\n\nG1 X20 F600\n"
    result = parse(text)
    assert [m.line for m in result.moves] == [3, 5]


def test_line_number_words_ignored():
    result = parse("N10 G1 X10 F600\nN20 G1 X20\n")
    assert len(result.moves) == 2
    assert result.diagnostics == ()


def test_zero_move_start_position():
    result = parse("G1 X10\n")
    start = result.moves[0].start
    assert (start.x, start.y, start.z) == (0.0, 0.0, 0.0)


# --- task 3.3: arcs ----------------------------------------------------------


def test_quarter_circle_ij_form_flattening_tolerance():
    result = parse("G21 G90\nG0 X0 Y0\nG2 X10 Y10 I10 F600\n", arc_tolerance_mm=0.01)
    arc_moves = [m for m in result.moves if m.from_arc]
    assert arc_moves, "expected flattened arc segments"
    assert all(m.kind is MoveKind.CUT for m in arc_moves)
    # chain endpoints match the arc endpoints exactly
    assert (round(arc_moves[0].start.x, 9), round(arc_moves[0].start.y, 9)) == (0.0, 0.0)
    last = arc_moves[-1].end
    assert (round(last.x, 9), round(last.y, 9)) == (10.0, 10.0)
    # center is (10, 0): start angle pi, end angle pi/2, clockwise quarter
    center = (10.0, 0.0)
    radius = 10.0
    for m in arc_moves:
        mid = (
            (m.start.x + m.end.x) / 2,
            (m.start.y + m.end.y) / 2,
        )
        # chord midpoint deviation from the true arc must stay within tolerance
        dist = math.hypot(mid[0] - center[0], mid[1] - center[1])
        assert abs(dist - radius) <= 0.01 + 1e-9
    # all arc moves attributed to the arc line (line 3)
    assert {m.line for m in arc_moves} == {3}


def test_r_form_minor_arc_quarter():
    result = parse("G3 X10 Y10 R10 F600\n")
    arc_moves = [m for m in result.moves if m.from_arc]
    assert arc_moves
    last = arc_moves[-1].end
    assert (round(last.x, 9), round(last.y, 9)) == (10.0, 10.0)
    # swept angle: sum of segment angle steps around the solved center
    center = _solved_center(arc_moves)
    total = 0.0
    for m in arc_moves:
        a0 = math.atan2(m.start.y - center[1], m.start.x - center[0])
        a1 = math.atan2(m.end.y - center[1], m.end.x - center[0])
        step = (a1 - a0) % (2 * math.pi)  # G3 = CCW
        total += step
    assert abs(total - math.pi / 2) < 1e-6


def test_r_form_negative_r_selects_major_arc():
    result = parse("G3 X10 Y10 R-10 F600\n")
    arc_moves = [m for m in result.moves if m.from_arc]
    center = _solved_center(arc_moves)
    total = 0.0
    for m in arc_moves:
        a0 = math.atan2(m.start.y - center[1], m.start.x - center[0])
        a1 = math.atan2(m.end.y - center[1], m.end.x - center[0])
        total += (a1 - a0) % (2 * math.pi)
    assert abs(total - 3 * math.pi / 2) < 1e-6


def _solved_center(arc_moves):
    # circumcenter of three points on the arc
    pts = [arc_moves[0].start, arc_moves[len(arc_moves) // 2].end, arc_moves[-1].end]
    (x1, y1), (x2, y2), (x3, y3) = [(p.x, p.y) for p in pts]
    d = 2 * (x1 * (y2 - y3) + x2 * (y3 - y1) + x3 * (y1 - y2))
    ux = (
        (x1**2 + y1**2) * (y2 - y3) + (x2**2 + y2**2) * (y3 - y1) + (x3**2 + y3**2) * (y1 - y2)
    ) / d
    uy = (
        (x1**2 + y1**2) * (x3 - x2) + (x2**2 + y2**2) * (x1 - x3) + (x3**2 + y3**2) * (x2 - x1)
    ) / d
    return (ux, uy)


def test_full_circle_ij_form():
    result = parse("G2 I-5 F600\n")
    arc_moves = [m for m in result.moves if m.from_arc]
    assert len(arc_moves) >= 8  # a full circle needs many sub-segments
    last = arc_moves[-1].end
    assert (round(last.x, 9), round(last.y, 9)) == (0.0, 0.0)
    total_length = sum(math.hypot(m.end.x - m.start.x, m.end.y - m.start.y) for m in arc_moves)
    assert abs(total_length - 2 * math.pi * 5) < 0.5  # chorded circumference, close to 31.4


def test_arc_missing_center_and_radius_diagnosed():
    result = parse("G0 X10 Y10\nG2 X20 Y20\nG1 X30 Y30 F600\n")
    warnings = [d for d in result.diagnostics if d.severity is Severity.WARNING]
    assert len(warnings) == 1
    assert warnings[0].line == 2
    assert "I/J" in warnings[0].message or "R" in warnings[0].message
    # surrounding moves still present
    assert _endpoints(result) == [(10, 10, 0), (30, 30, 0)]


def test_arc_radius_too_small_diagnosed():
    result = parse("G2 X20 Y0 R5\n")
    assert result.moves == ()
    warnings = [d for d in result.diagnostics if d.severity is Severity.WARNING]
    assert len(warnings) == 1
    assert warnings[0].line == 1


# --- task 3.4: tolerance & golden fixture -----------------------------------


def test_unsupported_words_are_info_and_parsing_continues():
    result = parse("G43 H1\nM8\nT1\nG1 X10 F600\n")
    moves = result.moves
    assert len(moves) == 1 and moves[0].end.x == 10
    infos = [d for d in result.diagnostics if d.severity is Severity.INFO]
    assert {d.line for d in infos} == {1, 2, 3}
    assert any("G43" in d.message for d in infos)


def test_malformed_number_is_error_and_line_skipped():
    result = parse("G1 X10 F600\nG1 X\nG1 Y10\n")
    errors = [d for d in result.diagnostics if d.severity is Severity.ERROR]
    assert len(errors) == 1 and errors[0].line == 2
    assert _endpoints(result) == [(10, 0, 0), (10, 10, 0)]


def test_golden_program_end_to_end():
    text = """
; Golden fixture: linear, arcs, comments, units/mode switches, unsupported code
G21 G90 G17
G0 X10 Y10 Z5        (rapid approach)
G1 Z-1 F600          ; plunge
G1 X20 Y10
G3 X30 Y20 R10       ; quarter circle, R form
G2 X40 Y10 I-10 J0   ; quarter circle, I/J form (cw)
G91                  ; incremental
G1 X5 Y5
G20                  ; inches from here
G1 X1
G21 G90
M8                   ; unsupported coolant code, ignored
G1 X50 Y10
"""
    result = parse(text)
    cuts = [m for m in result.moves if m.kind is MoveKind.CUT]
    rapids = [m for m in result.moves if m.kind is MoveKind.RAPID]
    assert len(rapids) == 1 and (rapids[0].end.x, rapids[0].end.y, rapids[0].end.z) == (10, 10, 5)
    # arc segments tagged
    assert any(m.from_arc for m in cuts)
    # incremental X5 Y5 after absolute (40,10): ends at (45,15)
    non_arc = [m for m in cuts if not m.from_arc]
    endpoints = [(round(m.end.x, 6), round(m.end.y, 6)) for m in non_arc]
    assert (20.0, 10.0) in endpoints
    assert (45.0, 15.0) in endpoints
    # then G20 incremental X1 => +25.4 mm => (70.4, 15)
    assert (70.4, 15.0) in endpoints
    assert (50.0, 10.0) in endpoints
    # M8 recorded as INFO, nothing worse
    assert [d.severity for d in result.diagnostics] == [Severity.INFO]
    assert result.diagnostics[0].line == 14
    # every move carries its source line (first motion is on line 4)
    assert all(m.line >= 4 for m in result.moves)
