package dev.radixhomework.gcodeide.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        assertNotNull(editor.codeArea().getParagraphGraphicFactory(), "line-number gutter attached");

        List<int[]> caretEvents = new ArrayList<>();
        editor.addCaretListener((line, column) -> caretEvents.add(new int[] {line, column}));
        editor.addCurrentLineListener(line -> {
        });
        interact(() -> editor.codeArea().moveTo(1, 0)); // second paragraph
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
        interact(() -> assertEquals("G1 X1499", editor.codeArea().getSelectedText()));
        interact(() -> assertTrue(
                editor.codeArea().getVisibleParagraphs().stream()
                        .anyMatch(p -> p.getText().equals("G1 X1499")),
                "line 1500 scrolled into view"));
    }

    @Test
    void gotoLineOutOfRangeIgnored() {
        interact(() -> editor.setText("G1 X10\nG1 X20\n"));
        int before = editor.codeArea().getCurrentParagraph();
        interact(() -> editor.gotoLine(99)); // must not throw or move the caret
        assertEquals(before, editor.codeArea().getCurrentParagraph());
    }

    // --- autocompletion ------------------------------------------------------------

    @Test
    void completionPopupShowsAndInserts() {
        interact(() -> editor.setText(""));
        interact(() -> editor.codeArea().insertText(0, "G"));
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
        interact(() -> editor.codeArea().insertText(0, "G"));
        assertTrue(editor.isCompletionPopupShowing());
        interact(() -> editor.codeArea().insertText(editor.codeArea().getCaretPosition(), "q"));
        assertTrue(!editor.isCompletionPopupShowing(), "no match: popup hidden");
        assertTrue(editor.getText().startsWith("Gq"), "typed text untouched");
    }

    @Test
    void noPopupOutsideAWord() {
        interact(() -> editor.setText("G1 X10\n"));
        interact(() -> editor.codeArea().moveTo(0, 6)); // after the digits: digits break the prefix
        interact(() -> editor.codeArea().insertText(editor.codeArea().getCaretPosition(), "5"));
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
        interact(() -> editor.codeArea().moveTo(0, 1)); // caret on "3" of G3
        interact(() -> javafx.event.Event.fireEvent(editor.codeArea(),
                new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED,
                        "", "", javafx.scene.input.KeyCode.Q, false, true, false, false)));
        assertTrue(editor.isDocPopupShowing(), "doc popup visible on Ctrl+Q");
        assertTrue(editor.docPopupText().startsWith("G3 — "), editor.docPopupText());
    }

    @Test
    void ctrlQOnUnknownWordShowsNothing() {
        interact(() -> editor.setText("Qw 10\n"));
        interact(() -> editor.codeArea().moveTo(0, 1));
        interact(() -> javafx.event.Event.fireEvent(editor.codeArea(),
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
        interact(() -> assertTrue(editor.codeArea().getStyle()
                        .contains("-fx-font-family"),
                "area style carries the monospace family"));
    }

    @Test
    void suggestionRowsSplitIntoCodeAndDescription() {
        String[] parts = GCodeEditorView.splitSuggestion("G1 — linear feed move");
        assertEquals("G1", parts[0]);
        assertEquals("linear feed move", parts[1]);
    }

    // --- scroll bars (task 1.1) ------------------------------------------------------

    @Test
    void scrollBarsAppearForOverflowingContent() {
        interact(() -> {
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < 300; i++) {
                text.append("G1 X").append(i).append('\n');
            }
            text.append("G1 X1000 Y"); // long line for the horizontal bar
            for (int i = 0; i < 300; i++) {
                text.append("0");
            }
            editor.setText(text.toString());
        });
        interact(() -> {
            editor.node().applyCss();
            editor.node().layout();
        });
        var bars = editor.node().lookupAll(".scroll-bar");
        assertTrue(bars.size() >= 2, "vertical + horizontal scroll bars present");
        assertNotNull(editor.node().lookup(".virtual-flow"), "virtual flow present");
    }

    // --- sticky documentation popup (task 1.2) ----------------------------------------

    @Test
    void docPopupStaysWhileCursorStaysOnKeyword() throws Exception {
        editor.setHoverDocDelayMillis(60);
        interact(() -> editor.setText("G2 X10\nG3 X10\n"));
        var bounds = new java.util.concurrent.atomic.AtomicReference<javafx.geometry.Bounds>();
        interact(() -> {
            editor.codeArea().moveTo(0, 1);
            bounds.set(editor.codeArea().screenToLocal(
                    editor.codeArea().getCaretBounds().orElseThrow()));
        });
        double x = bounds.get().getMinX() + 2;
        double y = bounds.get().getMinY() + 2;
        interact(() -> javafx.event.Event.fireEvent(editor.codeArea(),
                moveEvent(x, y)));
        waitUntilShowing();

        // movement within the same keyword keeps the popup open
        interact(() -> javafx.event.Event.fireEvent(editor.codeArea(),
                moveEvent(x + 3, y)));
        assertTrue(editor.isDocPopupShowing(), "movement within keyword keeps popup");

        // movement onto empty space hides it
        interact(() -> javafx.event.Event.fireEvent(editor.codeArea(),
                moveEvent(x + 200, y)));
        assertTrue(!editor.isDocPopupShowing(), "leaving the keyword hides popup");
    }

    @Test
    void docPopupHidesOnKeyPress() throws Exception {
        editor.setHoverDocDelayMillis(60);
        interact(() -> editor.setText("G2 X10\n"));
        var bounds = new java.util.concurrent.atomic.AtomicReference<javafx.geometry.Bounds>();
        interact(() -> {
            editor.codeArea().moveTo(0, 1);
            bounds.set(editor.codeArea().screenToLocal(
                    editor.codeArea().getCaretBounds().orElseThrow()));
        });
        interact(() -> javafx.event.Event.fireEvent(editor.codeArea(),
                moveEvent(bounds.get().getMinX() + 2, bounds.get().getMinY() + 2)));
        waitUntilShowing();

        interact(() -> javafx.event.Event.fireEvent(editor.codeArea(),
                new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED,
                        "", "", javafx.scene.input.KeyCode.ESCAPE,
                        false, false, false, false)));
        assertTrue(!editor.isDocPopupShowing(), "any key press hides the popup");
    }

    private static javafx.scene.input.MouseEvent moveEvent(double x, double y) {
        return new javafx.scene.input.MouseEvent(
                javafx.scene.input.MouseEvent.MOUSE_MOVED, x, y, 0, 0,
                javafx.scene.input.MouseButton.NONE, 0,
                false, false, false, false, false, false, false, false, false, false, null);
    }

    private void waitUntilShowing() throws InterruptedException {
        for (int i = 0; i < 40 && !editor.isDocPopupShowing(); i++) {
            Thread.sleep(25);
        }
        assertTrue(editor.isDocPopupShowing(), "doc popup appears");
    }

    // --- Tab-validated completion (task 1.3) -------------------------------------------

    @Test
    void tabAcceptsSelectedSuggestionWithoutInsertingTab() {
        interact(() -> {
            editor.setText("");
            editor.codeArea().insertText(0, "G");
            assertTrue(editor.isCompletionPopupShowing());
            // same interact: models real typing, where the popup is open when
            // the user presses Tab
            javafx.event.Event.fireEvent(editor.codeArea(),
                    new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED,
                            "", "", javafx.scene.input.KeyCode.TAB,
                            false, false, false, false));
        });
        assertEquals("G0", editor.getText()); // first suggestion accepted
        assertFalse(editor.getText().contains("	"), "no tab character inserted");
        assertTrue(!editor.isCompletionPopupShowing(), "popup closed after acceptance");
    }

    @Test
    void hoverShowsDocumentationAfterDelay() throws Exception {
        editor.setHoverDocDelayMillis(60);
        interact(() -> editor.setText("G2 X10\n"));
        var bounds = new java.util.concurrent.atomic.AtomicReference<javafx.geometry.Bounds>();
        interact(() -> {
            editor.codeArea().moveTo(0, 1);
            // getCaretBounds() is in screen coordinates; the event needs node-local
            bounds.set(editor.codeArea().screenToLocal(editor.codeArea().getCaretBounds().orElseThrow()));
        });
        interact(() -> javafx.event.Event.fireEvent(editor.codeArea(),
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
