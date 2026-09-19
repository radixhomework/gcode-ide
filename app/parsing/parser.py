"""Two-stage GRBL-dialect G-code parser (pure Python, no Qt).

Stage 1 tokenizes each line into words (letter + number, case-insensitive),
stripping `;` and parenthesized comments. Stage 2 runs a modal state machine
(position, motion mode G0-G3, G90/G91, G20/G21, F, spindle M3/M4/M5) and
emits a flat list of line-attributed moves, arcs flattened into sub-segments.

Severity policy (drives the preview's stale indication, design D6):

- ``ERROR``   — the line could not even be tokenized (e.g. a word without a
  number); typical mid-typing state. The line is skipped.
- ``WARNING`` — well-formed words that cannot be interpreted (e.g. an arc
  with neither I/J nor R, or an R too small for the endpoints). The line is
  skipped.
- ``INFO``    — unsupported words/codes (e.g. ``G43``, ``M8``, ``T1``);
  parsing continues with best-effort state.
"""

from __future__ import annotations

import math
import re
from dataclasses import dataclass

from .model import Diagnostic, Move, MoveKind, ParseResult, Position, Severity, Spindle

DEFAULT_ARC_TOLERANCE_MM = 0.01
_INCH_TO_MM = 25.4
_TWO_PI = 2.0 * math.pi
_EPS = 1e-9

_WORD_RE = re.compile(r"([A-Za-z])\s*([+-]?(?:\d+\.?\d*|\.\d+))")
_PAREN_COMMENT_RE = re.compile(r"\([^)]*\)")

_SUPPORTED_LINEAR_MODES = (0, 1, 2, 3)


class TokenizeError(ValueError):
    """A line is not syntactically valid G-code words."""


@dataclass(frozen=True)
class Word:
    letter: str  # normalized to uppercase
    value: float
    raw: str


def strip_comments(line: str) -> str:
    """Remove parenthesized comments and the trailing `;` comment."""
    return _PAREN_COMMENT_RE.sub(" ", line).split(";", 1)[0]


def tokenize(line: str) -> list[Word]:
    """Tokenize one line of G-code into words; raises TokenizeError on junk text."""
    stripped = strip_comments(line)
    words: list[Word] = []
    pos = 0
    for match in _WORD_RE.finditer(stripped):
        if stripped[pos : match.start()].strip():
            raise TokenizeError(f"unrecognized text near {stripped[match.start() - 1: match.start() + 8]!r}")
        words.append(Word(match.group(1).upper(), float(match.group(2)), match.group(0)))
        pos = match.end()
    tail = stripped[pos:].strip()
    if tail:
        raise TokenizeError(f"unrecognized text {tail!r}")
    return words


def _fmt_code(letter: str, value: float) -> str:
    return f"{letter}{value:g}"


class _Interpreter:
    """Modal state machine over tokenized lines (GRBL power-on defaults)."""

    def __init__(self, arc_tolerance_mm: float):
        self.tol = arc_tolerance_mm
        self.pos = Position()
        self.motion = 0  # G0
        self.absolute = True  # G90
        self.inches = False  # G21
        self.feed: float | None = None  # mm/min
        self.spindle = Spindle.OFF
        self.moves: list[Move] = []
        self.diagnostics: list[Diagnostic] = []

    # -- driver -----------------------------------------------------------

    def run(self, text: str) -> ParseResult:
        for lineno, raw in enumerate(text.splitlines(), start=1):
            try:
                words = tokenize(raw)
            except TokenizeError as exc:
                self._diag(lineno, Severity.ERROR, f"malformed line skipped: {exc}")
                continue
            if words:
                self._line(lineno, words)
        return ParseResult(tuple(self.moves), tuple(self.diagnostics))

    # -- per-line interpretation -------------------------------------------

    def _line(self, lineno: int, words: list[Word]) -> None:
        params: dict[str, float] = {}
        motion_word: int | None = None
        for word in words:
            letter, value = word.letter, word.value
            if letter == "G":
                code = int(value) if value == int(value) else value
                if code in _SUPPORTED_LINEAR_MODES:
                    motion_word = code
                elif code == 90:
                    self.absolute = True
                elif code == 91:
                    self.absolute = False
                elif code == 20:
                    self.inches = True
                elif code == 21:
                    self.inches = False
                elif code == 17:
                    pass  # XY plane selection: the plane the model works in
                else:
                    self._diag(lineno, Severity.INFO, f"unsupported code {_fmt_code('G', value)} ignored")
            elif letter == "M":
                if value == 3:
                    self.spindle = Spindle.CW
                elif value == 4:
                    self.spindle = Spindle.CCW
                elif value == 5:
                    self.spindle = Spindle.OFF
                else:
                    self._diag(lineno, Severity.INFO, f"unsupported code {_fmt_code('M', value)} ignored")
            elif letter == "N":
                pass  # line number, consumed
            elif letter in "XYZIJRFS":
                params[letter] = value
            else:
                self._diag(lineno, Severity.INFO, f"unsupported word {_fmt_code(letter, value)} ignored")

        if "F" in params:
            self.feed = self._mm(params["F"])
        if motion_word is not None:
            self.motion = motion_word

        has_axis = any(axis in params for axis in "XYZ")
        has_arc_data = any(key in params for key in ("I", "J", "R"))
        if self.motion in (0, 1):
            if has_axis:
                self._linear_move(lineno, params)
        elif has_axis or has_arc_data:
            self._arc_move(lineno, params)

    # -- motion execution ---------------------------------------------------

    def _linear_move(self, lineno: int, params: dict[str, float]) -> None:
        start = self.pos
        end = self._target(params)
        self.pos = end
        kind = MoveKind.RAPID if self.motion == 0 else MoveKind.CUT
        self.moves.append(
            Move(
                kind=kind,
                start=start,
                end=end,
                line=lineno,
                feed=self.feed if kind is MoveKind.CUT else None,
                from_arc=False,
                spindle=self.spindle,
            )
        )

    def _arc_move(self, lineno: int, params: dict[str, float]) -> None:
        start = self.pos
        end = self._target(params)
        clockwise = self.motion == 2

        if "R" in params:
            radius = self._mm(params["R"])
            center = self._center_from_radius(start, end, radius, clockwise)
            if center is None:
                self._diag(
                    lineno,
                    Severity.WARNING,
                    f"arc skipped: R {abs(radius):g} too small for the given endpoints",
                )
                return
        elif "I" in params or "J" in params:
            i = self._mm(params.get("I", 0.0))
            j = self._mm(params.get("J", 0.0))
            center = Position(start.x + i, start.y + j)
        else:
            self._diag(lineno, Severity.WARNING, "arc skipped: neither I/J offsets nor R radius given")
            return

        radius = math.hypot(start.x - center.x, start.y - center.y)
        if radius < _EPS:
            self._diag(lineno, Severity.WARNING, "arc skipped: zero radius")
            return

        start_angle = math.atan2(start.y - center.y, start.x - center.x)
        sweep = self._sweep(start, end, center, clockwise)
        full_circle = (
            "R" not in params
            and abs(end.x - start.x) < _EPS
            and abs(end.y - start.y) < _EPS
            and abs(end.z - start.z) < _EPS
        )
        if full_circle:
            sweep = -_TWO_PI if clockwise else _TWO_PI
        if abs(sweep) < _EPS and not full_circle:
            sweep = _TWO_PI if not clockwise else -_TWO_PI  # degenerate: force a path

        segments = self._segment_count(radius, abs(sweep))
        previous = start
        for i in range(1, segments + 1):
            if i == segments:
                point = end  # land exactly on the commanded endpoint
            else:
                angle = start_angle + sweep * i / segments
                point = Position(
                    center.x + radius * math.cos(angle),
                    center.y + radius * math.sin(angle),
                    start.z + (end.z - start.z) * i / segments,
                )
            self.moves.append(
                Move(
                    kind=MoveKind.CUT,
                    start=previous,
                    end=point,
                    line=lineno,
                    feed=self.feed,
                    from_arc=True,
                    spindle=self.spindle,
                )
            )
            previous = point
        self.pos = end

    def _target(self, params: dict[str, float]) -> Position:
        values = {axis: self._mm(params[axis]) for axis in "XYZ" if axis in params}
        if self.absolute:
            return Position(
                values.get("X", self.pos.x),
                values.get("Y", self.pos.y),
                values.get("Z", self.pos.z),
            )
        return Position(
            self.pos.x + values.get("X", 0.0),
            self.pos.y + values.get("Y", 0.0),
            self.pos.z + values.get("Z", 0.0),
        )

    # -- arc helpers ---------------------------------------------------------

    def _sweep(self, start: Position, end: Position, center: Position, clockwise: bool) -> float:
        a0 = math.atan2(start.y - center.y, start.x - center.x)
        a1 = math.atan2(end.y - center.y, end.x - center.x)
        sweep = (a0 - a1) % _TWO_PI if clockwise else (a1 - a0) % _TWO_PI
        return -sweep if clockwise else sweep

    def _center_from_radius(
        self, start: Position, end: Position, signed_radius: float, clockwise: bool
    ) -> Position | None:
        dx, dy = end.x - start.x, end.y - start.y
        chord = math.hypot(dx, dy)
        radius = abs(signed_radius)
        if chord < _EPS or radius < _EPS or chord > 2 * radius + _EPS:
            return None
        h = math.sqrt(max(0.0, radius * radius - (chord / 2) ** 2))
        mid_x, mid_y = (start.x + end.x) / 2, (start.y + end.y) / 2
        ux, uy = dx / chord, dy / chord
        # Two candidate centers; R > 0 selects the minor (<180°) arc,
        # R < 0 the major one (the sign convention selects the sweep, not the
        # direction — G2/G3 do that), so pick by swept angle.
        for sign in (1.0, -1.0):
            candidate = Position(mid_x - sign * h * uy, mid_y + sign * h * ux)
            swept = abs(self._sweep(start, end, candidate, clockwise))
            if signed_radius > 0 and swept <= math.pi + _EPS:
                return candidate
            if signed_radius < 0 and swept >= math.pi - _EPS:
                return candidate
        return None

    def _segment_count(self, radius: float, sweep: float) -> int:
        """Sub-segment count from the sagitta bound: chord deviation <= tolerance."""
        ratio = max(-1.0, min(1.0, 1.0 - self.tol / radius))
        max_step = 2.0 * math.acos(ratio)
        if max_step <= _EPS:
            return 1
        return max(1, math.ceil(sweep / max_step))

    # -- misc -----------------------------------------------------------------

    def _mm(self, value: float) -> float:
        return value * _INCH_TO_MM if self.inches else value

    def _diag(self, line: int, severity: Severity, message: str) -> None:
        self.diagnostics.append(Diagnostic(line=line, severity=severity, message=message))


def parse(text: str, arc_tolerance_mm: float = DEFAULT_ARC_TOLERANCE_MM) -> ParseResult:
    """Parse G-code text into a canonical toolpath model (mm, absolute, Y-up)."""
    return _Interpreter(arc_tolerance_mm).run(text)
