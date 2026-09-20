package dev.radixhomework.gcodeide.model.parsing;

/** A parse diagnostic citing the 1-based source line. */
public record Diagnostic(int line, Severity severity, String message) {
}
