package dev.radixhomework.gcodeide.model.parsing;

import java.util.List;

/** Parse output: flat move list plus diagnostics. */
public record ParseResult(List<Move> moves, List<Diagnostic> diagnostics) {

    public ParseResult {
        moves = List.copyOf(moves);
        diagnostics = List.copyOf(diagnostics);
    }

    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(d -> d.severity() == Severity.ERROR);
    }
}
