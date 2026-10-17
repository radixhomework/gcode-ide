package dev.radixhomework.gcodeide.model.parsing;

/** A position in canonical bed coordinates: millimeters, absolute, Y-up. */
public record Position(double x, double y, double z) {

    public Position() {
        this(0.0, 0.0, 0.0);
    }
}
