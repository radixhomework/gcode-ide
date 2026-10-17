package dev.radixhomework.gcodeide.model.svg;

/** Import parameters collected by the wizard. Pure model. */
public record SvgImportOptions(
        double widthMm,
        double placeX,
        double placeY,
        double depthPerPass,
        double finalDepth,
        double cutFeed,
        double safeZ,
        int spindleRpm,
        CutSide cutSide,
        double cutterDiameterMm) {

    public SvgImportOptions {
        if (cutSide == null) {
            cutSide = CutSide.ON_LINE;
        }
    }
}
