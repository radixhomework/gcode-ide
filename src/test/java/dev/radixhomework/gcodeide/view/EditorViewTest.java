package dev.radixhomework.gcodeide.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;

class EditorViewTest extends ApplicationTest {

    private GCodeEditorView editor;

    @Override
    public void start(Stage stage) {
        editor = new GCodeEditorView();
        stage.setScene(new javafx.scene.Scene(editor.node(), 400, 250));
        stage.show();
    }

    @Test
    void gutterAttachedAndCaretSignalFires() {
        interact(() -> {
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < 100; i++) {
                text.append("G1 X").append(i).append('\n');
            }
            editor.setText(text.toString());
        });
        assertNotNull(editor.node().getParagraphGraphicFactory(), "line-number gutter attached");

        List<int[]> caretEvents = new ArrayList<>();
        editor.addCaretListener((line, column) -> caretEvents.add(new int[] {line, column}));
        editor.addCurrentLineListener(line -> {
        });
        interact(() -> editor.node().moveTo(1, 0)); // second paragraph
        assertTrue(!caretEvents.isEmpty());
        int[] last = caretEvents.get(caretEvents.size() - 1);
        assertEquals(2, last[0]);
        assertEquals(1, last[1]);
    }

    @Test
    void gotoLineSelectsAndIsVisible() {
        interact(() -> {
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < 2000; i++) {
                text.append("G1 X").append(i).append('\n');
            }
            editor.setText(text.toString());
        });
        List<Integer> lineEvents = new ArrayList<>();
        editor.addCurrentLineListener(lineEvents::add);
        interact(() -> editor.gotoLine(1500));
        assertEquals(1500, lineEvents.get(lineEvents.size() - 1));
        interact(() -> assertEquals("G1 X1499", editor.node().getSelectedText()));
        interact(() -> assertTrue(
                editor.node().getVisibleParagraphs().stream()
                        .anyMatch(p -> p.getText().equals("G1 X1499")),
                "line 1500 scrolled into view"));
    }

    @Test
    void gotoLineOutOfRangeIgnored() {
        interact(() -> editor.setText("G1 X10\nG1 X20\n"));
        int before = editor.node().getCurrentParagraph();
        interact(() -> editor.gotoLine(99)); // must not throw or move the caret
        assertEquals(before, editor.node().getCurrentParagraph());
    }

    // --- autocompletion ------------------------------------------------------------

    @Test
    void completionPopupShowsAndInserts() {
        interact(() -> editor.setText(""));
        interact(() -> editor.node().insertText(0, "G"));
        assertTrue(editor.isCompletionPopupShowing(), "popup visible while typing a word");
        assertTrue(editor.currentSuggestions().stream().anyMatch(s -> s.startsWith("G1 —")),
                "suggestions carry documentation");
        String g1 = editor.currentSuggestions().stream()
                .filter(s -> s.startsWith("G1"))
                .findFirst()
                .orElseThrow();
        interact(() -> editor.acceptSuggestion(g1));
        assertTrue(editor.getText().startsWith("G1"), "accepted code inserted");
        assertTrue(!editor.isCompletionPopupShowing(), "popup hidden after acceptance");
    }

    @Test
    void typingThroughDismissesWithoutDisturbingText() {
        interact(() -> editor.setText(""));
        interact(() -> editor.node().insertText(0, "G"));
        assertTrue(editor.isCompletionPopupShowing());
        interact(() -> editor.node().insertText(editor.node().getCaretPosition(), "q"));
        assertTrue(!editor.isCompletionPopupShowing(), "no match: popup hidden");
        assertTrue(editor.getText().startsWith("Gq"), "typed text untouched");
    }

    @Test
    void noPopupOutsideAWord() {
        interact(() -> editor.setText("G1 X10\n"));
        interact(() -> editor.node().moveTo(0, 6)); // after the digits: digits break the prefix
        interact(() -> editor.node().insertText(editor.node().getCaretPosition(), "5"));
        assertTrue(!editor.isCompletionPopupShowing());
    }

    // --- documentation popup (hover / Ctrl+Q) ---------------------------------------

    @Test
    void wordAtCoversBothSidesOfTheCaret() {
        interact(() -> editor.setText("G2 X10\n"));
        interact(() -> org.junit.jupiter.api.Assertions.assertEquals("G2",
                GCodeEditorView.wordAt("G2 X10", 0)));
        org.junit.jupiter.api.Assertions.assertEquals("G2", GCodeEditorView.wordAt("G2 X10", 1));
        org.junit.jupiter.api.Assertions.assertEquals("G2", GCodeEditorView.wordAt("G2 X10", 2));
        org.junit.jupiter.api.Assertions.assertEquals("X10",
                GCodeEditorView.wordAt("G2 X10", 4));
        org.junit.jupiter.api.Assertions.assertEquals("G2",
                GCodeEditorView.wordAt("G2 X10", 2), "after the word's digits still resolves");
        org.junit.jupiter.api.Assertions.assertEquals("",
                GCodeEditorView.wordAt("G2  X10", 3), "double space resolves to nothing");
        org.junit.jupiter.api.Assertions.assertNull(GCodeEditorView.documentationFor("Qw"));
        org.junit.jupiter.api.Assertions.assertTrue(
                GCodeEditorView.documentationFor("g2").startsWith("G2 — "));
        org.junit.jupiter.api.Assertions.assertTrue(
                GCodeEditorView.documentationFor("X10").startsWith("X — "),
                "axis words document after digit stripping");
    }

    @Test
    void ctrlQShowsDocumentationAtCaret() {
        interact(() -> editor.setText("G3 X10\n"));
        interact(() -> editor.node().moveTo(0, 1)); // caret on "3" of G3
        interact(() -> javafx.event.Event.fireEvent(editor.node(),
                new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED,
                        "", "", javafx.scene.input.KeyCode.Q, false, true, false, false)));
        assertTrue(editor.isDocPopupShowing(), "doc popup visible on Ctrl+Q");
        assertTrue(editor.docPopupText().startsWith("G3 — "), editor.docPopupText());
    }

    @Test
    void ctrlQOnUnknownWordShowsNothing() {
        interact(() -> editor.setText("Qw 10\n"));
        interact(() -> editor.node().moveTo(0, 1));
        interact(() -> javafx.event.Event.fireEvent(editor.node(),
                new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED,
                        "", "", javafx.scene.input.KeyCode.Q, false, true, false, false)));
        assertTrue(!editor.isDocPopupShowing());
    }

    // --- professional styling -------------------------------------------------------

    @Test
    void editorRendersInMonospaceFont() {
        assertTrue(GCodeEditorView.MONO_CANDIDATES.contains(GCodeEditorView.monoFamily())
                        || GCodeEditorView.monoFamily().equals("Monospaced"),
                "chosen family is a monospace candidate or the fallback");
        interact(() -> assertTrue(editor.node().getStyle()
                        .contains("-fx-font-family"),
                "area style carries the monospace family"));
    }

    @Test
    void suggestionRowsSplitIntoCodeAndDescription() {
        String[] parts = GCodeEditorView.splitSuggestion("G1 — linear feed move");
        assertEquals("G1", parts[0]);
        assertEquals("linear feed move", parts[1]);
    }

    @Test
    void hoverShowsDocumentationAfterDelay() throws Exception {
        editor.setHoverDocDelayMillis(60);
        interact(() -> editor.setText("G2 X10\n"));
        var bounds = new java.util.concurrent.atomic.AtomicReference<javafx.geometry.Bounds>();
        interact(() -> {
            editor.node().moveTo(0, 1);
            // getCaretBounds() is in screen coordinates; the event needs node-local
            bounds.set(editor.node().screenToLocal(editor.node().getCaretBounds().orElseThrow()));
        });
        interact(() -> javafx.event.Event.fireEvent(editor.node(),
                new javafx.scene.input.MouseEvent(
                        javafx.scene.input.MouseEvent.MOUSE_MOVED,
                        bounds.get().getMinX() + 2, bounds.get().getMinY() + 2,
                        0, 0, javafx.scene.input.MouseButton.NONE, 0,
                        false, false, false, false, false, false, false, false, false, false,
                        null)));
        for (int i = 0; i < 40 && !editor.isDocPopupShowing(); i++) {
            Thread.sleep(25);
        }
        assertTrue(editor.isDocPopupShowing(), "doc popup appears after the hover delay");
        assertTrue(editor.docPopupText().startsWith("G2 — "));
    }
}
