package dev.radixhomework.gcodeide.view;

import dev.radixhomework.gcodeide.view.syntax.WordClassifier;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;

/**
 * G-code editor view: RichTextFX {@link CodeArea} with a line-number gutter,
 * syntax coloring driven by {@link WordClassifier}, current-line highlight,
 * caret reporting, and a goto-line API.
 */
public class GCodeEditorView {

    private final CodeArea codeArea = new CodeArea();
    private final List<BiConsumer<Integer, Integer>> caretListeners = new ArrayList<>();
    private final List<IntConsumer> currentLineListeners = new ArrayList<>();

    public GCodeEditorView() {
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
        codeArea.getStylesheets().add(
                getClass().getResource("/editor.css").toExternalForm());
        codeArea.plainTextChanges().subscribe(change -> restyleAffected(change.getPosition(),
                change.getInsertionEnd()));
        codeArea.caretPositionProperty().addListener((obs, oldPos, newPos) -> fireCaretMoved());
    }

    /** The JavaFX node to place in a layout. */
    public CodeArea node() {
        return codeArea;
    }

    // -- content -----------------------------------------------------------------

    public void setText(String text) {
        codeArea.replaceText(text == null ? "" : text);
    }

    public String getText() {
        return codeArea.getText();
    }

    public void requestFocus() {
        codeArea.requestFocus();
    }

    // -- caret reporting -----------------------------------------------------------

    public void addCaretListener(BiConsumer<Integer, Integer> listener) {
        caretListeners.add(listener);
    }

    public void addCurrentLineListener(IntConsumer listener) {
        currentLineListeners.add(listener);
    }

    private void fireCaretMoved() {
        int line = codeArea.getCurrentParagraph() + 1;
        int column = codeArea.getCaretColumn() + 1;
        caretListeners.forEach(l -> l.accept(line, column));
        currentLineListeners.forEach(l -> l.accept(line));
    }

    /** Selects {@code line} (1-based), scrolls it into view, and signals the change. */
    public void gotoLine(int line) {
        int paragraph = line - 1;
        if (paragraph < 0 || paragraph >= codeArea.getParagraphs().size()) {
            return;
        }
        int length = codeArea.getParagraph(paragraph).length();
        codeArea.selectRange(paragraph, 0, paragraph, length);
        codeArea.showParagraphInViewport(paragraph);
        fireCaretMoved();
    }

    // -- syntax coloring --------------------------------------------------------------

    private void restyleAffected(int fromPosition, int toPosition) {
        int from = paragraphIndexAt(Math.min(fromPosition, toPosition));
        // a change that inserts/removes line separators can shift later paragraphs
        int to = paragraphIndexAt(Math.max(fromPosition, toPosition));
        for (int i = from; i <= to && i < codeArea.getParagraphs().size(); i++) {
            restyleParagraph(i);
        }
    }

    private void restyleParagraph(int index) {
        int start = codeArea.getAbsolutePosition(index, 0);
        int end = start + codeArea.getParagraph(index).length();
        codeArea.clearStyle(start, end);
        String text = codeArea.getParagraph(index).getText();
        for (WordClassifier.Span span : WordClassifier.classifyLine(text)) {
            codeArea.setStyleClass(start + span.start(), start + span.end(), span.category());
        }
    }

    private int paragraphIndexAt(int absolutePosition) {
        int cumulative = 0;
        for (int i = 0; i < codeArea.getParagraphs().size(); i++) {
            // +1 for the line separator counted in absolute positions
            int length = codeArea.getParagraph(i).length() + 1;
            if (absolutePosition <= cumulative + length - 1) {
                return i;
            }
            cumulative += length;
        }
        return Math.max(0, codeArea.getParagraphs().size() - 1);
    }
}
