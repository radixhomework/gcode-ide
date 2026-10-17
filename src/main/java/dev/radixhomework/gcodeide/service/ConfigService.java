package dev.radixhomework.gcodeide.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

/**
 * App configuration (window geometry, last used directory, theme) as a small
 * YAML file. Owns nothing else: profiles are not part of the product
 * (change remove-machine-profiles); unknown keys in an existing config file
 * (e.g. a stale {@code active_profile}) are left in place, harmlessly ignored.
 */
@Slf4j
public class ConfigService {

    private final Path configFile;

    public ConfigService(Path configFile) {
        this.configFile = configFile;
    }

    @SuppressWarnings("unchecked")
    public synchronized Map<String, Object> loadConfig() {
        if (configFile == null || !Files.isRegularFile(configFile)) {
            return new LinkedHashMap<>();
        }
        try {
            Object loaded = new Yaml().load(Files.readString(configFile));
            return loaded instanceof Map ? (Map<String, Object>) loaded : new LinkedHashMap<>();
        } catch (IOException | RuntimeException e) {
            log.warn("config '{}' unreadable, starting fresh: {}", configFile, e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    /** Applies a mutation to the config map and persists it. */
    public synchronized void updateConfig(Consumer<Map<String, Object>> mutation) {
        Map<String, Object> data = loadConfig();
        mutation.accept(data);
        writeConfig(data);
    }

    private void writeConfig(Map<String, Object> data) {
        if (configFile == null) {
            return;
        }
        try {
            Files.createDirectories(configFile.getParent());
            Files.writeString(configFile, new Yaml().dump(data));
        } catch (IOException e) {
            log.warn("config '{}' not saved: {}", configFile, e.getMessage());
            throw new UncheckedIOException(e);
        }
    }
}
