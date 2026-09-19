"""Toolpath model shared by preview, statistics, and future generators.

Canonical units: millimeters, absolute bed coordinates, Y-up (see design D3/D4).
Arcs are pre-flattened into linear moves but tagged `from_arc`.
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum


class MoveKind(Enum):
    RAPID = "rapid"
    CUT = "cut"


class Severity(Enum):
    INFO = "info"
    WARNING = "warning"
    ERROR = "error"


class Spindle(Enum):
    OFF = "off"
    CW = "cw"  # M3
    CCW = "ccw"  # M4


@dataclass(frozen=True)
class Position:
    x: float = 0.0
    y: float = 0.0
    z: float = 0.0


@dataclass(frozen=True)
class Move:
    """One straight toolpath segment attributed to its source line."""

    kind: MoveKind
    start: Position
    end: Position
    line: int
    feed: float | None = None  # mm/min; None for rapids and cuts before any F
    from_arc: bool = False
    spindle: Spindle = Spindle.OFF


@dataclass(frozen=True)
class Diagnostic:
    line: int  # 1-based source line
    severity: Severity
    message: str


@dataclass(frozen=True)
class ParseResult:
    moves: tuple[Move, ...] = ()
    diagnostics: tuple[Diagnostic, ...] = ()
