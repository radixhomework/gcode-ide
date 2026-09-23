package dev.radixhomework.gcodeide.view;

import dev.radixhomework.gcodeide.view.syntax.GCodeWords;
import dev.radixhomework.gcodeide.view.syntax.WordClassifier;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.stage.Popup;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;

/**
 * G-code editor view: RichTextFX {@link CodeArea} with a line-number gutter,
 * syntax coloring driven by {@link WordClassifier}, current-line highlight,
 * caret reporting, a goto-line API, and autocompletion with command
 * documentation driven by {@link GCodeWords} (a small built-in popup:
 * richtextfx 0.11.x does not bundle its autocompletion package).
 */
public class GCodeEditorView {

    private final CodeArea codeArea = new CodeArea();
    private final List<BiConsumer<Integer, Integer>> caretListeners = new ArrayList<>();
    private final List<IntConsumer> currentLineListeners = new ArrayList<>();
    private final Popup completionPopup = new Popup();
    private final ListView<String> suggestionList = new ListView<>();
    private boolean suppressNextCompletion;
    private final Popup docPopup = new Popup();
    private final javafx.scene.control.Label docLabel = new javafx.scene.control.Label();
    private final javafx.animation.PauseTransition hoverTimer =
            new javafx.animation.PauseTransition(javafx.util.Duration.millis(HOVER_DOC_DELAY_MS));

    /** Hover delay before the documentation popup appears. */
    public static final double HOVER_DOC_DELAY_MS = 2000;

    public GCodeEditorView() {
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
        codeArea.getStylesheets().add(
                getClass().getResource("/editor.css").toExternalForm());
        codeArea.plainTextChanges().subscribe(change -> {
            restyleAffected(change.getPosition(), change.getInsertionEnd());
            updateCompletionPopup();
        });
        codeArea.caretPositionProperty().addListener((obs, oldPos, newPos) -> fireCaretMoved());

        suggestionList.setPrefSize(320, 170);
        suggestionList.setOnMouseClicked(event -> {
            String chosen = suggestionList.getSelectionModel().getSelectedItem();
            if (chosen != null) {
                acceptSuggestion(chosen);
            }
        });
        completionPopup.getContent().add(suggestionList);
        completionPopup.setAutoHide(true);
        codeArea.addEventFilter(KeyEvent.KEY_PRESSED, this::onKeyPressed);

        docLabel.setStyle("-fx-background-color: #FFFFE0; -fx-border-color: #808080;"
                + " -fx-padding: 4 8 4 8;");
        docPopup.getContent().add(docLabel);
        docPopup.setAutoHide(true);
        hoverTimer.setOnFinished(event -> showDocumentationForHover(hoverX, hoverY));
        codeArea.addEventFilter(MouseEvent.MOUSE_MOVED, event -> {
            hideDocumentation();
            hoverX = event.getX();
            hoverY = event.getY();
            hoverTimer.playFromStart();
        });
        codeArea.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.isControlDown() && event.getCode() == KeyCode.Q) {
                showDocumentationForCaret();
                event.consume();
            }
        });
    }

    private double hoverX;
    private double hoverY;

    private void onKeyPressed(KeyEvent event) {
        if (!completionPopup.isShowing()) {
            return;
        }
        KeyCode code = event.getCode();
        if (code == KeyCode.ESCAPE) {
            completionPopup.hide();
            event.consume();
        } else if (code == KeyCode.ENTER) {
            String chosen = suggestionList.getSelectionModel().getSelectedItem();
            if (chosen != null) {
                acceptSuggestion(chosen);
            }
            event.consume();
        } else if (code == KeyCode.DOWN || code == KeyCode.UP) {
            suggestionList.requestFocus(); // let the list handle the navigation
        }
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

    // -- autocompletion ------------------------------------------------------------

    private void updateCompletionPopup() {
        if (suppressNextCompletion) {
            suppressNextCompletion = false;
            completionPopup.hide();
            return;
        }
        String prefix = wordPrefixBeforeCaret();
        List<String> suggestions = GCodeWords.suggestionsFor(prefix).stream()
                .map(GCodeWords.Entry::display)
                .toList();
        if (prefix.isEmpty() || suggestions.isEmpty()) {
            completionPopup.hide();
            return;
        }
        suggestionList.getItems().setAll(suggestions);
        suggestionList.getSelectionModel().selectFirst();
        codeArea.getCaretBounds().ifPresentOrElse(
                bounds -> completionPopup.show(codeArea, bounds.getMinX(), bounds.getMaxY()),
                () -> completionPopup.show(codeArea, 0, 0));
    }

    /** The (letters-only) word being typed immediately before the caret. */
    private String wordPrefixBeforeCaret() {
        String paragraph = codeArea.getParagraph(codeArea.getCurrentParagraph()).getText();
        int column = codeArea.getCaretColumn();
        int start = column;
        while (start > 0 && Character.isLetter(paragraph.charAt(start - 1))) {
            start--;
        }
        return paragraph.substring(start, column);
    }

    /** Replaces the typed prefix with the chosen suggestion's code. */
    public void acceptSuggestion(String suggestion) {
        String code = GCodeWords.codeOf(suggestion);
        String prefix = wordPrefixBeforeCaret();
        if (prefix.isEmpty()) {
            return;
        }
        suppressNextCompletion = true;
        int position = codeArea.getCaretPosition();
        codeArea.replaceText(position - prefix.length(), position, code);
        codeArea.moveTo(position - prefix.length() + code.length());
        completionPopup.hide();
    }

    public boolean isCompletionPopupShowing() {
        return completionPopup.isShowing();
    }

    public List<String> currentSuggestions() {
        return List.copyOf(suggestionList.getItems());
    }

    // -- documentation popup (hover / Ctrl+Q) ---------------------------------------

    /** The word (letter(s) plus trailing digits) at position {@code column}, or "". */
    static String wordAt(String paragraph, int column) {
        int n = paragraph == null ? 0 : paragraph.length();
        for (int idx : new int[] {column, column - 1}) {
            int i = idx;
            while (i >= 0 && i < n && Character.isDigit(paragraph.charAt(i))) {
                i--; // digits never anchor a word: keep looking left for the letter
            }
            if (i >= 0 && i < n && Character.isLetter(paragraph.charAt(i))) {
                int start = i;
                while (start > 0 && Character.isLetter(paragraph.charAt(start - 1))) {
                    start--;
                }
                int end = i + 1;
                while (end < n && Character.isLetterOrDigit(paragraph.charAt(end))) {
                    end++;
                }
                return paragraph.substring(start, end);
            }
        }
        return "";
    }

    /** The word at the caret, or the nearest word left then right in the paragraph. */
    String wordAtOrNearCaret() {
        String paragraph = codeArea.getParagraph(codeArea.getCurrentParagraph()).getText();
        int column = codeArea.getCaretColumn();
        String word = wordAt(paragraph, column);
        if (!word.isEmpty()) {
            return word;
        }
        for (int d = 1; d <= paragraph.length(); d++) {
            word = wordAt(paragraph, column - d);
            if (!word.isEmpty()) {
                return word;
            }
            word = wordAt(paragraph, column + d);
            if (!word.isEmpty()) {
                return word;
            }
        }
        return "";
    }

    /** The documentation line for a word, or null when unknown. Trailing
     *  digits are stripped so axis words document ("X10" → "X"). */
    static String documentationFor(String word) {
        String probe = word == null ? "" : word.strip();
        while (!probe.isEmpty()) {
            final String candidate = probe;
            String doc = GCodeWords.suggestionsFor(candidate).stream()
                    .filter(e -> e.code().equalsIgnoreCase(candidate))
                    .findFirst()
                    .map(GCodeWords.Entry::display)
                    .orElse(null);
            if (doc != null) {
                return doc;
            }
            probe = probe.substring(0, probe.length() - 1);
        }
        return null;
    }

    private void showDocumentationForHover(double x, double y) {
        var hit = codeArea.hit(x, y);
        if (hit.getCharacterIndex().isEmpty()) {
            return;
        }
        var position = codeArea.offsetToPosition(hit.getCharacterIndex().getAsInt(),
                org.fxmisc.richtext.model.TwoDimensional.Bias.Backward);
        String doc = documentationFor(
                wordAt(codeArea.getParagraph(position.getMajor()).getText(), position.getMinor()));
        if (doc == null) {
            return;
        }
        docLabel.setText(doc);
        var screen = codeArea.localToScreen(x, y + 16); // popup.show takes screen coords
        if (screen != null) {
            docPopup.show(codeArea, screen.getX(), screen.getY());
        }
    }

    void showDocumentationForCaret() {
        String doc = documentationFor(wordAtOrNearCaret());
        if (doc == null) {
            return;
        }
        var bounds = codeArea.getCaretBounds();
        docLabel.setText(doc);
        if (bounds.isPresent()) {
            docPopup.show(codeArea, bounds.get().getMinX(), bounds.get().getMaxY());
        } else {
            docPopup.show(codeArea, 10, 10);
        }
    }

    void hideDocumentation() {
        docPopup.hide();
    }

    public boolean isDocPopupShowing() {
        return docPopup.isShowing();
    }

    public String docPopupText() {
        return docLabel.getText();
    }

    /** Test hook: shorten the hover delay. */
    public void setHoverDocDelayMillis(double millis) {
        hoverTimer.setDuration(new javafx.util.Duration(millis));
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
