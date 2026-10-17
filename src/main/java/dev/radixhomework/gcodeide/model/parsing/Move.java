package dev.radixhomework.gcodeide.model.parsing;

/** One straight toolpath segment attributed to its 1-based source line.
 *  Arcs arrive pre-flattened but tagged {@code fromArc}.
 *
 * @param feed mm/min; {@code null} for rapids and cuts commanded before any F
 */
public record Move(
        MoveKind kind,
        Position start,
        Position end,
        int line,
        Double feed,
        boolean fromArc,
        Spindle spindle) {
}
