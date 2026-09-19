"""Editor tests: word classification (5.2, pure) and widget behavior (5.1, 5.3)."""

from PySide6.QtGui import QTextCursor

from app.editor.gcode_editor import (
    AXIS_PARAM,
    COMMENT,
    FALLBACK,
    G_WORD,
    LINE_NUMBER,
    GCodeEditor,
    classify_line,
)

# --- task 5.2: pure word-classification helper ------------------------------


def test_classify_representative_line():
    spans = classify_line("N10 G1 X10 Y20 F600 ; first pass")
    assert (0, 3, LINE_NUMBER) in spans
    assert (4, 6, G_WORD) in spans
    assert (7, 10, AXIS_PARAM) in spans
    assert (11, 14, AXIS_PARAM) in spans
    assert (15, 19, AXIS_PARAM) in spans
    assert (20, 32, COMMENT) in spans


def test_classify_unknown_and_unused_words_fall_back():
    spans = classify_line("G43 H1 M8")
    assert spans == [(0, 3, FALLBACK), (4, 6, FALLBACK), (7, 9, FALLBACK)]


def test_classify_parenthesized_comment():
    spans = classify_line("(setup) G1 X5")
    assert (0, 7, COMMENT) in spans
    assert (8, 10, G_WORD) in spans
    assert (11, 13, AXIS_PARAM) in spans


def test_classify_supported_m_codes():
    line = "M3 S10000 M5"
    spans = classify_line(line)
    categories = {line[span[0] : span[1]]: span[2] for span in spans}
    assert categories["M3"] == "m_word"
    assert categories["M5"] == "m_word"
    assert categories["S10000"] == AXIS_PARAM


# --- task 5.1: gutter + caret signals ----------------------------------------


def test_gutter_renders_and_caret_signal_fires(qtbot):
    editor = GCodeEditor()
    qtbot.addWidget(editor)
    editor.setPlainText("\n".join(f"G1 X{i}" for i in range(100)))
    editor.resize(400, 300)
    editor.show()
    qtbot.waitExposed(editor)

    assert editor.gutter_width() > 0
    assert editor._gutter.geometry().height() == editor.contentsRect().height()
    assert editor._gutter.geometry().width() == editor.gutter_width()
    pixmap = editor.grab()  # forces a render of editor + gutter
    assert not pixmap.isNull()

    with qtbot.waitSignal(editor.caretMoved) as blocker:
        editor.moveCursor(QTextCursor.MoveOperation.Down)
    assert blocker.args == [2, 1]

    with qtbot.waitSignal(editor.currentLineChanged) as blocker:
        editor.moveCursor(QTextCursor.MoveOperation.Down)
    assert blocker.args == [3]


# --- task 5.3: gotoLine -------------------------------------------------------


def test_goto_line_selects_and_is_visible(qtbot):
    editor = GCodeEditor()
    qtbot.addWidget(editor)
    lines = [f"G1 X{i} Y0" for i in range(50)]
    editor.setPlainText("\n".join(lines))
    editor.resize(400, 200)
    editor.show()
    qtbot.waitExposed(editor)

    with qtbot.waitSignal(editor.currentLineChanged) as blocker:
        editor.gotoLine(42)
    assert blocker.args == [42]

    cursor = editor.textCursor()
    assert cursor.blockNumber() + 1 == 42
    assert cursor.selectedText() == "G1 X41 Y0"

    rect = editor.cursorRect()
    viewport = editor.viewport().rect()
    assert viewport.top() <= rect.top() and rect.bottom() <= viewport.bottom()


def test_goto_line_out_of_range_is_ignored(qtbot):
    editor = GCodeEditor()
    qtbot.addWidget(editor)
    editor.setPlainText("G1 X10\nG1 X20\n")
    editor.gotoLine(99)  # must not raise or move the caret off the document
    assert editor.textCursor().blockNumber() + 1 in (1, 2)
