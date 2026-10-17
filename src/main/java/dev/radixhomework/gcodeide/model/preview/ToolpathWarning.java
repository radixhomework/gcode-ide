package dev.radixhomework.gcodeide.model.preview;

import java.util.List;

/** A safety diagnostic aggregating the offending source lines. */
public record ToolpathWarning(String kind, String message, List<Integer> lines) {

    /** Warning kinds emitted by {@link Diagnostics#computeWarnings}. */
    public static final String OUT_OF_BED = "out-of-bed";
    public static final String EXCESSIVE_FEED = "excessive-feed";
    public static final String MISSING_FEED = "missing-feed";
}
