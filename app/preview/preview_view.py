"""2D top-down toolpath preview: QGraphicsView with millimeter scene units.

Renders the bed from the active machine profile and one line item per move
(rapids dashed gray, cuts depth-shaded, out-of-bed portions red), keeps the
last good scene when the parse fails (stale indication), and syncs selection
with the editor in both directions (design D5/D6).
"""

from __future__ import annotations

from PySide6.QtCore import QPointF, QRectF, Qt, Signal
from PySide6.QtGui import QBrush, QColor, QPainter, QPainterPath, QPainterPathStroker, QPen
from PySide6.QtWidgets import (
    QGraphicsLineItem,
    QGraphicsScene,
    QGraphicsView,
    QLabel,
)

from ..parsing.model import MoveKind, ParseResult, Severity
from ..profiles.profile import MachineProfile
from .diagnostics import move_out_of_bed

_RAPID_COLOR = QColor("#808080")
_OUT_OF_BED_COLOR = QColor("#D62728")
_SHALLOW_HUE = 210  # blue at Z0
_DEEP_HUE = 0  # red at the deepest cut
_HIGHLIGHT_COLOR = QColor(255, 140, 0, 170)
_HIT_TOLERANCE_MM = 2.0  # click band around a segment, independent of pen width


class _MoveItem(QGraphicsLineItem):
    """Line item with a generous hit area so thin segments stay clickable."""

    def boundingRect(self) -> QRectF:
        # the scene index culls by boundingRect before consulting shape(),
        # so it must be enlarged alongside the hit band
        line = self.line()
        rect = QRectF(line.p1(), line.p2()).normalized()
        return rect.adjusted(
            -_HIT_TOLERANCE_MM, -_HIT_TOLERANCE_MM, _HIT_TOLERANCE_MM, _HIT_TOLERANCE_MM
        )

    def shape(self) -> QPainterPath:
        stroker = QPainterPathStroker()
        stroker.setWidth(_HIT_TOLERANCE_MM)
        path = QPainterPath()
        path.moveTo(self.line().p1())
        path.lineTo(self.line().p2())
        return stroker.createStroke(path)


class PreviewView(QGraphicsView):
    """Top-down toolpath preview against the machine bed (Y-up on screen)."""

    lineSelected = Signal(int)
    staleChanged = Signal(bool)

    def __init__(self, parent=None) -> None:
        super().__init__(parent)
        self.setRenderHint(QPainter.RenderHint.Antialiasing)
        self.setDragMode(QGraphicsView.DragMode.RubberBandDrag)
        self.setTransformationAnchor(QGraphicsView.ViewportAnchor.AnchorUnderMouse)
        self.scale(1.0, -1.0)  # flip: scene +Y (machine +Y) is up on screen

        self._scene = QGraphicsScene(self)
        self.setScene(self._scene)

        self._bed_item = self._scene.addRect(
            QRectF(0, 0, 0, 0), QPen(QColor("#404040"), 0), QBrush(QColor("#FAFAFA"))
        )
        self._move_items: list[QGraphicsLineItem] = []
        self._highlight_items: list[QGraphicsLineItem] = []
        self._profile: MachineProfile | None = None
        self._last_good: ParseResult | None = None
        self._current_line: int | None = None
        self._stale = False
        self._press_pos: QPointF | None = None
        self._press_item = None

        self._stale_badge = QLabel("  STALE — showing last good toolpath  ", self)
        self._stale_badge.setStyleSheet("background-color: #C00000; color: white; font-weight: bold;")
        self._stale_badge.hide()

    # -- profile & toolpath ------------------------------------------------------

    def set_profile(self, profile: MachineProfile) -> None:
        self._profile = profile
        self._bed_item.setRect(QRectF(0, 0, profile.bed_x, profile.bed_y))
        self._scene.setSceneRect(QRectF(0, 0, profile.bed_x, profile.bed_y))
        if self._last_good is not None:
            self._render(self._last_good)  # recolor out-of-bed portions
        self.fit_to_bed()

    def set_toolpath(self, result: ParseResult) -> None:
        """Show the parsed toolpath, or keep the last good scene when unparseable."""
        if any(d.severity is Severity.ERROR for d in result.diagnostics):
            self._set_stale(True)
            return
        self._last_good = result
        self._set_stale(False)
        self._render(result)

    def is_stale(self) -> bool:
        return self._stale

    def fit_to_bed(self) -> None:
        if self._profile is not None:
            rect = QRectF(0, 0, self._profile.bed_x, self._profile.bed_y).adjusted(-5, -5, 5, 5)
            self.fitInView(rect, Qt.AspectRatioMode.KeepAspectRatio)

    # -- rendering ------------------------------------------------------------------

    def _render(self, result: ParseResult) -> None:
        for item in self._move_items + self._highlight_items:
            self._scene.removeItem(item)
        self._move_items.clear()
        self._highlight_items.clear()

        cut_zs = [m.end.z for m in result.moves if m.kind is MoveKind.CUT]
        deepest = min(cut_zs) if cut_zs else -1.0

        for move in result.moves:
            out_of_bed = self._profile is not None and move_out_of_bed(move, self._profile)
            if out_of_bed:
                pen = QPen(_OUT_OF_BED_COLOR, 0)
            elif move.kind is MoveKind.RAPID:
                pen = QPen(_RAPID_COLOR, 0)
                pen.setStyle(Qt.PenStyle.DashLine)
            else:
                pen = QPen(self._depth_color(move.end.z, deepest), 0)
            pen.setCosmetic(True)
            item = _MoveItem()
            item.setLine(move.start.x, move.start.y, move.end.x, move.end.y)
            item.setPen(pen)
            item.setData(0, move.line)
            self._scene.addItem(item)
            self._move_items.append(item)
        self._apply_highlight()

    @staticmethod
    def _depth_color(z: float, deepest: float) -> QColor:
        if deepest >= 0:
            return QColor.fromHsv(_SHALLOW_HUE, 200, 180)
        t = max(0.0, min(1.0, z / deepest))
        hue = round(_SHALLOW_HUE + (_DEEP_HUE - _SHALLOW_HUE) * t)
        return QColor.fromHsv(hue, 200, 180)

    # -- editor<->preview sync ---------------------------------------------------

    def set_current_line(self, line: int) -> None:
        self._current_line = line
        self._apply_highlight()

    def _apply_highlight(self) -> None:
        for item in self._highlight_items:
            self._scene.removeItem(item)
        self._highlight_items.clear()
        if self._current_line is None:
            return
        pen = QPen(_HIGHLIGHT_COLOR, 2.5)
        pen.setCosmetic(True)
        for item in self._move_items:
            if item.data(0) == self._current_line:
                highlight = self._scene.addLine(item.line(), pen)
                highlight.setAcceptedMouseButtons(Qt.MouseButton.NoButton)
                highlight.setAcceptHoverEvents(False)
                self._highlight_items.append(highlight)

    def move_items(self) -> list[QGraphicsLineItem]:
        return list(self._move_items)

    # -- stale indication -----------------------------------------------------------

    def _set_stale(self, stale: bool) -> None:
        if stale != self._stale:
            self._stale = stale
            self.staleChanged.emit(stale)
        self._stale_badge.setVisible(stale)

    # -- interaction: click-to-select-line, wheel zoom -------------------------------

    def mousePressEvent(self, event) -> None:
        self._press_pos = event.position()
        self._press_item = self.itemAt(event.position().toPoint())
        super().mousePressEvent(event)

    def mouseReleaseEvent(self, event) -> None:
        released_on = self.itemAt(event.position().toPoint())
        moved_far = (
            self._press_pos is None
            or (event.position() - self._press_pos).manhattanLength() > 4
        )
        if (
            not moved_far
            and released_on is not None
            and released_on is self._press_item
            and isinstance(released_on.data(0), int)
        ):
            self.lineSelected.emit(released_on.data(0))
        super().mouseReleaseEvent(event)

    def wheelEvent(self, event) -> None:
        factor = 1.15 if event.angleDelta().y() > 0 else 1.0 / 1.15
        self.scale(factor, factor)
        event.accept()

    def resizeEvent(self, event) -> None:
        super().resizeEvent(event)
        self._stale_badge.move(10, 10)
        if self._profile is not None:
            self.fit_to_bed()
