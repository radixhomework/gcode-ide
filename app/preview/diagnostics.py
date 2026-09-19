"""Toolpath statistics and safety warnings (pure Python, no Qt).

Computed on demand from ``(ParseResult, MachineProfile)`` — never stored in
the toolpath model (design D4).
"""

from __future__ import annotations

import math
from dataclasses import dataclass

from ..parsing.model import Move, MoveKind, ParseResult
from ..profiles.profile import MachineProfile


@dataclass(frozen=True)
class ToolpathStats:
    """Aggregate figures for the status bar (mm, mm/min, minutes)."""

    cut_bbox: tuple[float, float, float, float] | None  # min_x, min_y, max_x, max_y
    cut_distance: float
    rapid_distance: float
    estimated_time_min: float


@dataclass(frozen=True)
class ToolpathWarning:
    """A safety diagnostic aggregating the offending source lines."""

    kind: str  # "out-of-bed" | "excessive-feed" | "missing-feed"
    message: str
    lines: tuple[int, ...]


def move_length(move: Move) -> float:
    return math.dist(
        (move.start.x, move.start.y, move.start.z),
        (move.end.x, move.end.y, move.end.z),
    )


def move_out_of_bed(move: Move, profile: MachineProfile) -> bool:
    """True if either endpoint leaves the profile's travel envelope."""
    for p in (move.start, move.end):
        if not 0 <= p.x <= profile.bed_x:
            return True
        if not 0 <= p.y <= profile.bed_y:
            return True
        if not -profile.bed_z <= p.z <= profile.bed_z:
            return True
    return False


def compute_statistics(result: ParseResult, profile: MachineProfile) -> ToolpathStats:
    """Bounding box, distances, and estimated run time (design D7).

    Cut moves without an active feed are counted at ``max_cut`` (and warned
    about by :func:`compute_warnings`); rapids run at ``max_rapid``.
    """
    cut_bbox = None
    min_x = min_y = math.inf
    max_x = max_y = -math.inf
    cut_distance = 0.0
    rapid_distance = 0.0
    time_min = 0.0

    for move in result.moves:
        length = move_length(move)
        if move.kind is MoveKind.CUT:
            cut_distance += length
            min_x = min(min_x, move.start.x, move.end.x)
            min_y = min(min_y, move.start.y, move.end.y)
            max_x = max(max_x, move.start.x, move.end.x)
            max_y = max(max_y, move.start.y, move.end.y)
            feed = move.feed if move.feed else profile.max_cut
            time_min += length / feed
        else:
            rapid_distance += length
            time_min += length / profile.max_rapid

    if math.isfinite(min_x):
        cut_bbox = (min_x, min_y, max_x, max_y)
    return ToolpathStats(
        cut_bbox=cut_bbox,
        cut_distance=cut_distance,
        rapid_distance=rapid_distance,
        estimated_time_min=time_min,
    )


def compute_warnings(result: ParseResult, profile: MachineProfile) -> list[ToolpathWarning]:
    """Out-of-bed, excessive-feed, and missing-feed warnings with source lines."""
    out_of_bed_lines: list[int] = []
    over_feed_lines: list[int] = []
    missing_feed_lines: list[int] = []

    for move in result.moves:
        if move_out_of_bed(move, profile) and move.line not in out_of_bed_lines:
            out_of_bed_lines.append(move.line)
        if move.kind is MoveKind.CUT:
            if move.feed is None:
                if move.line not in missing_feed_lines:
                    missing_feed_lines.append(move.line)
            elif move.feed > profile.max_cut and move.line not in over_feed_lines:
                over_feed_lines.append(move.line)

    warnings: list[ToolpathWarning] = []
    if out_of_bed_lines:
        warnings.append(
            ToolpathWarning(
                kind="out-of-bed",
                message=(
                    f"Moves outside the {profile.bed_x:g}x{profile.bed_y:g} mm bed "
                    f"on lines {', '.join(map(str, out_of_bed_lines))}"
                ),
                lines=tuple(out_of_bed_lines),
            )
        )
    if over_feed_lines:
        warnings.append(
            ToolpathWarning(
                kind="excessive-feed",
                message=(
                    f"Feed above the {profile.max_cut:g} mm/min cap "
                    f"on lines {', '.join(map(str, over_feed_lines))}"
                ),
                lines=tuple(over_feed_lines),
            )
        )
    if missing_feed_lines:
        warnings.append(
            ToolpathWarning(
                kind="missing-feed",
                message=(
                    f"Cut moves without a feed (estimated at {profile.max_cut:g} mm/min) "
                    f"on lines {', '.join(map(str, missing_feed_lines))}"
                ),
                lines=tuple(missing_feed_lines),
            )
        )
    return warnings
