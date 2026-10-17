package dev.radixhomework.gcodeide.model.parsing;

/** Parse diagnostic severity. ERROR means the line could not even be tokenized
 *  (drives the preview's stale indication); WARNING means well-formed words
 *  that cannot be interpreted; INFO means unsupported words or codes. */
public enum Severity {
    INFO,
    WARNING,
    ERROR
}
