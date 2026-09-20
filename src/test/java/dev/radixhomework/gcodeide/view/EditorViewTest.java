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
}
