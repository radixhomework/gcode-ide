"""G-code editor widget: line-number gutter, syntax coloring, goto-line API.

The word classification powering the highlighter is a pure function
(:func:`classify_line`) so it can be unit-tested without rendered colors
(design D9).
"""

from __future__ import annotations

import re

from PySide6.QtCore import QRect, QSize, Qt, Signal
from PySide6.QtGui import (
    QColor,
    QFontDatabase,
    QSyntaxHighlighter,
    QTextCharFormat,
    QTextCursor,
)
from PySide6.QtWidgets import QPlainTextEdit, QTextEdit, QWidget

# Categories (coloring mirrors what the parser actually interprets; words the
# parser recognizes but does not use, and unknown words, fall back).
COMMENT = "comment"
G_WORD = "g_word"
M_WORD = "m_word"
AXIS_PARAM = "axis_param"
LINE_NUMBER = "line_number"
FALLBACK = "fallback"

SUPPORTED_G_CODES = {0, 1, 2, 3, 17, 20, 21, 90, 91}
SUPPORTED_M_CODES = {3, 4, 5}
AXIS_PARAM_LETTERS = set("XYZIJKRFST")

_WORD_RE = re.compile(r"([A-Za-z])\s*([+-]?(?:\d+\.?\d*|\.\d+))")
_PAREN_COMMENT_RE = re.compile(r"\([^)]*\)")


def classify_line(text: str) -> list[tuple[int, int, str]]:
    """Classify a source line into styled spans.

    Returns ``(start, end, category)`` half-open spans for everything that is
    not plain text: comments, G/M words, axis/parameter words, N line numbers,
    and a fallback style for recognized-but-unused or unknown words.
    """
    spans: list[tuple[int, int, str]] = []

    comment_start = text.find(";")
    code_end = len(text) if comment_start < 0 else comment_start
    code = text[:code_end]

    for match in _PAREN_COMMENT_RE.finditer(code):
        spans.append((match.start(), match.end(), COMMENT))
    # blank out comments with same-width spaces so later word offsets stay true
    commented = _PAREN_COMMENT_RE.sub(lambda m: " " * (m.end() - m.start()), code)

    for match in _WORD_RE.finditer(commented):
        letter = match.group(1).upper()
        value = float(match.group(2))
        if letter == "G":
            category = G_WORD if _as_code(value) in SUPPORTED_G_CODES else FALLBACK
        elif letter == "M":
            category = M_WORD if _as_code(value) in SUPPORTED_M_CODES else FALLBACK
        elif letter == "N":
            category = LINE_NUMBER
        elif letter in AXIS_PARAM_LETTERS:
            category = AXIS_PARAM
        else:
            category = FALLBACK
        spans.append((match.start(0), match.end(0), category))

    if comment_start >= 0:
        spans.append((comment_start, len(text), COMMENT))
    return spans


def _as_code(value: float) -> int:
    return int(value) if value == int(value) else -1


_CATEGORY_COLORS = {
    COMMENT: QColor("#008000"),
    G_WORD: QColor("#0000C0"),
    M_WORD: QColor("#C00000"),
    AXIS_PARAM: QColor("#B05000"),
    LINE_NUMBER: QColor("#606060"),
    FALLBACK: QColor("#909090"),
}


class GCodeHighlighter(QSyntaxHighlighter):
    """Applies :func:`classify_line` spans to each block."""

    def highlightBlock(self, text: str) -> None:
        for start, end, category in classify_line(text):
            fmt = QTextCharFormat()
            fmt.setForeground(_CATEGORY_COLORS[category])
            if category == FALLBACK:
                fmt.setFontItalic(True)
            self.setFormat(start, end - start, fmt)


class _LineNumberArea(QWidget):
    def __init__(self, editor: GCodeEditor) -> None:
        super().__init__(editor)
        self._editor = editor

    def sizeHint(self) -> QSize:  # pragma: no cover - trivial
        return QSize(self._editor.gutter_width(), 0)

    def paintEvent(self, event) -> None:
        self._editor.paint_gutter(event)


class GCodeEditor(QPlainTextEdit):
    """Plain-text editor tuned for G-code: gutter, coloring, caret signals."""

    caretMoved = Signal(int, int)  # 1-based line, 1-based column
    currentLineChanged = Signal(int)  # 1-based line

    def __init__(self, parent: QWidget | None = None) -> None:
        super().__init__(parent)
        self.setFont(QFontDatabase.systemFont(QFontDatabase.SystemFont.FixedFont))
        self.setLineWrapMode(QPlainTextEdit.LineWrapMode.NoWrap)

        self._gutter = _LineNumberArea(self)
        self._highlighter = GCodeHighlighter(self.document())

        self.blockCountChanged.connect(self._update_gutter_width)
        self.updateRequest.connect(self._scroll_gutter)
        self.cursorPositionChanged.connect(self._on_cursor_moved)

        self._update_gutter_width()
        self._highlight_current_line()

    # -- gutter ---------------------------------------------------------------

    def gutter_width(self) -> int:
        digits = max(2, len(str(max(1, self.blockCount()))))
        return 12 + self.fontMetrics().horizontalAdvance("9") * digits

    def _update_gutter_width(self) -> None:
        self.setViewportMargins(self.gutter_width(), 0, 0, 0)

    def _scroll_gutter(self, rect: QRect, dy: int) -> None:
        if dy:
            self._gutter.scroll(0, dy)
        else:
            self._gutter.update(0, rect.y(), self._gutter.width(), rect.height())

    def resizeEvent(self, event) -> None:
        super().resizeEvent(event)
        content = self.contentsRect()
        self._gutter.setGeometry(
            QRect(content.left(), content.top(), self.gutter_width(), content.height())
        )

    def paint_gutter(self, event) -> None:
        from PySide6.QtGui import QPainter, QPen

        painter = QPainter(self._gutter)
        painter.fillRect(event.rect(), QColor("#F0F0F0"))
        block = self.firstVisibleBlock()
        block_number = block.blockNumber()
        top = round(self.blockBoundingGeometry(block).translated(self.contentOffset()).top())
        bottom = top + round(self.blockBoundingRect(block).height())
        current = self.textCursor().blockNumber()
        bold = self.font()
        bold.setBold(True)
        while block.isValid() and top <= event.rect().bottom():
            if block.isVisible() and bottom >= event.rect().top():
                painter.setPen(QPen(QColor("#303030") if block_number == current else QColor("#808080")))
                painter.setFont(bold if block_number == current else self.font())
                painter.drawText(
                    0,
                    top,
                    self._gutter.width() - 6,
                    self.fontMetrics().height(),
                    Qt.AlignmentFlag.AlignRight,
                    str(block_number + 1),
                )
            block = block.next()
            top = bottom
            bottom = top + round(self.blockBoundingRect(block).height())
            block_number += 1
        painter.end()

    # -- caret tracking ---------------------------------------------------------

    def _on_cursor_moved(self) -> None:
        cursor = self.textCursor()
        line = cursor.blockNumber() + 1
        self.caretMoved.emit(line, cursor.columnNumber() + 1)
        self.currentLineChanged.emit(line)
        self._highlight_current_line()
        self._gutter.update()

    def _highlight_current_line(self) -> None:
        selection = QTextEdit.ExtraSelection()
        selection.format.setBackground(QColor(0, 120, 215, 30))
        selection.format.setProperty(QTextCharFormat.Property.FullWidthSelection, True)
        selection.cursor = self.textCursor()
        selection.cursor.clearSelection()
        self.setExtraSelections([selection])

    # -- public API ---------------------------------------------------------------

    def gotoLine(self, line: int) -> None:
        """Select line ``line`` (1-based), scroll it into view, and signal the change."""
        block = self.document().findBlockByNumber(line - 1)
        if not block.isValid():
            return
        cursor = QTextCursor(block)
        cursor.movePosition(QTextCursor.MoveOperation.EndOfBlock, QTextCursor.MoveMode.KeepAnchor)
        self.setTextCursor(cursor)
        self.ensureCursorVisible()
