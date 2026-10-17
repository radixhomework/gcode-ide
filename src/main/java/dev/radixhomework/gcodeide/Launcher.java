package dev.radixhomework.gcodeide;

/**
 * Packaging entry point. The JVM intercepts main classes that extend
 * {@code javafx.application.Application} and defers to the JavaFX launcher
 * before {@code main()} runs — a plain launcher class keeps argument handling
 * (e.g. {@code --version}) in our control.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        App.main(args);
    }
}
