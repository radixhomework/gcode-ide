package dev.radixhomework.gcodeide.service;

import dev.radixhomework.gcodeide.model.parsing.GCodeParser;
import dev.radixhomework.gcodeide.model.parsing.ParseResult;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import lombok.extern.slf4j.Slf4j;

/**
 * Synchronous parse with a last-good policy: results containing ERROR
 * diagnostics never replace the last good toolpath (the preview keeps it and
 * shows the stale indication). The 300 ms debounce lives in the controller
 * (JavaFX {@code PauseTransition}) so this service stays JavaFX-free.
 */
@Slf4j
public class ParseService {

    private final List<BiConsumer<ParseResult, Boolean>> listeners = new ArrayList<>();
    private ParseResult lastGood;
    private boolean wasStale;

    /** Parses text and notifies listeners with (freshResult, stale). */
    public void parse(String text) {
        ParseResult result = GCodeParser.parse(text);
        boolean stale = result.hasErrors();
        if (!stale) {
            lastGood = result;
        }
        if (stale != wasStale) {
            wasStale = stale;
            if (stale) {
                log.info("parse left the document unparseable; keeping last good toolpath ({} moves)",
                        lastGood == null ? 0 : lastGood.moves().size());
            }
        }
        listeners.forEach(l -> l.accept(result, stale));
    }

    public ParseResult lastGood() {
        return lastGood;
    }

    public void addParseListener(BiConsumer<ParseResult, Boolean> listener) {
        listeners.add(listener);
    }
}
