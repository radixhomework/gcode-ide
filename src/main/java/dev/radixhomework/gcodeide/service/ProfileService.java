package dev.radixhomework.gcodeide.service;

import dev.radixhomework.gcodeide.model.profiles.MachineProfile;
import dev.radixhomework.gcodeide.model.profiles.ProfileException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

/**
 * Loads built-in (classpath) and user-supplied (directory) machine profiles,
 * tracks the active profile, and persists the app config (active profile).
 */
@Slf4j
public class ProfileService {

    private static final List<String> BUILTIN_RESOURCES = List.of("assets/profiles/example_mill.yaml");

    private final Path userProfilesDir;
    private final Path configFile;
    private final List<MachineProfile> profiles = new ArrayList<>();
    private final List<Runnable> listeners = new ArrayList<>();
    private MachineProfile active;

    public ProfileService(Path userProfilesDir, Path configFile) {
        this.userProfilesDir = userProfilesDir;
        this.configFile = configFile;
        reload();
        restoreActiveFromConfig();
    }

    /** Loads built-in resources, then any user-supplied YAML files. */
    public final void reload() {
        profiles.clear();
        for (String resource : BUILTIN_RESOURCES) {
            try (var in = getClass().getClassLoader().getResourceAsStream(resource)) {
                if (in == null) {
                    log.warn("built-in profile resource missing: {}", resource);
                    continue;
                }
                profiles.add(MachineProfile.fromYaml(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)));
            } catch (IOException | ProfileException e) {
                log.warn("built-in profile '{}' skipped: {}", resource, e.getMessage());
            }
        }
        if (userProfilesDir != null && Files.isDirectory(userProfilesDir)) {
            try (var stream = Files.list(userProfilesDir)) {
                stream.filter(p -> {
                            String n = p.getFileName().toString().toLowerCase();
                            return n.endsWith(".yaml") || n.endsWith(".yml");
                        })
                        .sorted()
                        .forEach(this::tryLoadUserFile);
            } catch (IOException e) {
                log.warn("cannot list user profiles in {}: {}", userProfilesDir, e.getMessage());
            }
        }
        if (active != null) {
            active = byName(active.name()).orElse(profiles.isEmpty() ? null : profiles.get(0));
        }
    }

    private void tryLoadUserFile(Path path) {
        try {
            profiles.add(MachineProfile.fromYaml(Files.readString(path)));
        } catch (IOException | ProfileException e) {
            log.warn("user profile '{}' skipped: {}", path.getFileName(), e.getMessage());
        }
    }

    public List<MachineProfile> profiles() {
        return List.copyOf(profiles);
    }

    public Optional<MachineProfile> byName(String name) {
        return profiles.stream().filter(p -> p.name().equals(name)).findFirst();
    }

    public MachineProfile active() {
        return active;
    }

    public void select(String name) {
        MachineProfile found = byName(name)
                .orElseThrow(() -> new IllegalArgumentException("unknown profile: " + name));
        active = found;
        saveConfig();
        listeners.forEach(Runnable::run);
    }

    /** Applies a mutation to the config map and persists it (e.g. window geometry). */
    public synchronized void updateConfig(java.util.function.Consumer<Map<String, Object>> mutation) {
        Map<String, Object> data = loadConfig();
        mutation.accept(data);
        writeConfig(data);
    }

    public void addActiveProfileListener(Runnable listener) {
        listeners.add(listener);
    }

    private void restoreActiveFromConfig() {
        Map<String, Object> config = loadConfig();
        Object preferred = config.get("active_profile");
        if (preferred instanceof String name && byName(name).isPresent()) {
            active = byName(name).get();
        } else if (!profiles.isEmpty()) {
            active = profiles.get(0);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> loadConfig() {
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

    void saveConfig() {
        if (configFile == null) {
            return;
        }
        Map<String, Object> data = loadConfig();
        data.put("active_profile", active == null ? null : active.name());
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
