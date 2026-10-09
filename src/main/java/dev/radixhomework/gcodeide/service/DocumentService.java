package dev.radixhomework.gcodeide.service;

import dev.radixhomework.gcodeide.service.DocumentService.SaveDecision;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

/**
 * Document state (path, modified flag) and UTF-8 file IO for one G-code
 * document. The save/discard/cancel decision and path choosers are injected
 * callbacks so tests never raise real modal dialogs; the controller backs
 * them with an Alert and FileChoosers.
 */
@Slf4j
public class DocumentService {

    public enum SaveDecision {
        SAVE, DISCARD, CANCEL
    }

    @FunctionalInterface
    public interface SavePrompt {
        SaveDecision prompt(String displayName);
    }

    @FunctionalInterface
    public interface PathChooser {
        Optional<Path> choose(String suggestedName);
    }

    private SavePrompt prompt;
    private final PathChooser saveAsChooser;
    private final List<Runnable> listeners = new ArrayList<>();
    private Path path;
    private boolean modified;

    public DocumentService(SavePrompt prompt, PathChooser saveAsChooser) {
        this.prompt = prompt;
        this.saveAsChooser = saveAsChooser;
    }

    /** Swaps the save/discard/cancel decision source (tests inject stubs, the app an Alert). */
    public void setPrompt(SavePrompt prompt) {
        this.prompt = prompt;
    }

    /** True when it is safe to drop the current content (saved or discarded). */
    public boolean maybeSave(Supplier<String> currentText) {
        if (!modified) {
            return true;
        }
        return switch (prompt.prompt(displayName())) {
            case SAVE -> save(currentText.get());
            case DISCARD -> true;
            case CANCEL -> false;
        };
    }

    /** Saves to the current path, choosing one first when untitled. */
    public boolean save(String currentText) {
        if (path == null) {
            Optional<Path> chosen = saveAsChooser.choose(displayName());
            if (chosen.isEmpty()) {
                return false;
            }
            path = chosen.get();
        }
        write(currentText);
        modified = false;
        fire();
        return true;
    }

    /** Saves under an explicit target path (save-as); subsequent saves go there. */
    public boolean saveAs(String currentText, Path target) {
        path = target;
        return save(currentText);
    }

    /** Replaces the document with a file's content; empty when cancelled. */
    public Optional<String> open(Path file, Supplier<String> currentText) {
        if (!maybeSave(currentText)) {
            return Optional.empty();
        }
        String content;
        try {
            content = Files.readString(file);
        } catch (IOException e) {
            log.warn("cannot open {}: {}", file, e.getMessage());
            throw new UncheckedIOException(e);
        }
        path = file;
        modified = false;
        fire();
        return Optional.of(content);
    }

    /** Clears to an untitled document; false when the user cancelled the prompt. */
    public boolean newDocument(Supplier<String> currentText) {
        if (!maybeSave(currentText)) {
            return false;
        }
        path = null;
        modified = false;
        fire();
        return true;
    }

    public void markModified() {
        if (!modified) {
            modified = true;
            fire();
        }
    }

    public boolean isModified() {
        return modified;
    }

    public Path path() {
        return path;
    }

    public String displayName() {
        return path == null ? "untitled.nc" : path.getFileName().toString();
    }

    public void addDocumentListener(Runnable listener) {
        listeners.add(listener);
    }

    private void write(String text) {
        try {
            Files.writeString(path, text);
        } catch (IOException e) {
            log.warn("cannot save {}: {}", path, e.getMessage());
            throw new UncheckedIOException(e);
        }
    }

    private void fire() {
        listeners.forEach(Runnable::run);
    }
}
