package dev.radixhomework.gcodeide.model.preview;

/** Cutting bounding box in millimeters (min/max over cut move endpoints). */
public record BBox(double minX, double minY, double maxX, double maxY) {
}
