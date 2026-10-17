package dev.radixhomework.gcodeide.model.preview;

/** Aggregate figures for the status bar (mm, mm/min, minutes). */
public record ToolpathStats(
        BBox cutBBox, double cutDistance, double rapidDistance, double estimatedTimeMin) {
}
