package dev.radixhomework.gcodeide.util;

import java.nio.file.Path;

/** Per-user configuration directories, resolved per OS (no JavaFX). */
public final class ConfigPaths {

    public static final String APP_DIR_NAME = "gcode-ide";

    private ConfigPaths() {
    }

    /** Base config dir: {@code %APPDATA%\gcode-ide} (win),
     *  {@code ~/Library/Application Support/gcode-ide} (mac),
     *  {@code ~/.config/gcode-ide} (linux). */
    public static Path configDir() {
        return baseConfigRoot().resolve(APP_DIR_NAME);
    }

    public static Path profilesDir() {
        return configDir().resolve("profiles");
    }

    public static Path configFile() {
        return configDir().resolve("config.yaml");
    }

    private static Path baseConfigRoot() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String home = System.getProperty("user.home", ".");
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData != null && !appData.isBlank()) {
                return Path.of(appData);
            }
            return Path.of(home, "AppData", "Roaming");
        }
        if (os.contains("mac")) {
            return Path.of(home, "Library", "Application Support");
        }
        return Path.of(home, ".config");
    }
}
