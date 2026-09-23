package dev.radixhomework.gcodeide.model.profiles;

import java.util.LinkedHashMap;
import java.util.Map;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * A machine definition in millimeters and mm/min (pure model).
 *
 * <p>The YAML schema is an interchange contract shared with
 * grbl-machine-controller (see design D8): always millimeters, flat documented
 * field names, additive-only evolution:
 *
 * <pre>{@code
 * name: Example Mill
 * bed: { x: 300.0, y: 180.0, z: 45.0 }
 * feeds:
 *   max_cut: 800.0
 *   max_rapid: 1000.0
 * safe_z: 5.0
 * }</pre>
 */
public record MachineProfile(
        String name, double bedX, double bedY, double bedZ, double maxCut, double maxRapid,
        double safeZ) {

    /** Builds a profile from a YAML mapping, validating each field as it is read. */
    public static MachineProfile fromMap(Map<String, Object> data) {
        if (!(data instanceof Map)) {
            throw new ProfileException("profile must be a YAML mapping");
        }
        Object name = data.get("name");
        if (!(name instanceof String s) || s.isBlank()) {
            throw new ProfileException("name must be a non-empty string");
        }
        Object bed = data.get("bed");
        if (!(bed instanceof Map<?, ?> bedMap)) {
            throw new ProfileException("bed must be a mapping with x, y, z extents");
        }
        double bedX = positive(bedMap, "x", "bed");
        double bedY = positive(bedMap, "y", "bed");
        double bedZ = positive(bedMap, "z", "bed");

        Object feeds = data.get("feeds");
        if (!(feeds instanceof Map<?, ?> feedsMap)) {
            throw new ProfileException("feeds must be a mapping with max_cut, max_rapid");
        }
        double maxCut = positive(feedsMap, "max_cut", "feeds");
        double maxRapid = positive(feedsMap, "max_rapid", "feeds");

        double safeZ = number(data.get("safe_z"), "safe_z");
        if (safeZ < 0) {
            throw new ProfileException("safe_z must be non-negative, got " + safeZ);
        }
        return new MachineProfile(s, bedX, bedY, bedZ, maxCut, maxRapid, safeZ);
    }

    /** Serializes to the documented YAML mapping (round-trips through fromMap). */
    public Map<String, Object> toMap() {
        Map<String, Object> bed = new LinkedHashMap<>();
        bed.put("x", bedX);
        bed.put("y", bedY);
        bed.put("z", bedZ);
        Map<String, Object> feeds = new LinkedHashMap<>();
        feeds.put("max_cut", maxCut);
        feeds.put("max_rapid", maxRapid);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("name", name);
        out.put("bed", bed);
        out.put("feeds", feeds);
        out.put("safe_z", safeZ);
        return out;
    }

    /** Parses and validates a profile from YAML text. */
    public static MachineProfile fromYaml(String text) {
        Object loaded;
        try {
            loaded = new Yaml().load(text);
        } catch (YAMLException e) {
            throw new ProfileException("invalid YAML: " + e.getMessage());
        }
        if (!(loaded instanceof Map)) {
            throw new ProfileException("profile must be a YAML mapping");
        }
        return fromMap(castToStringKeys(loaded));
    }

    /** Serializes the profile as YAML text. */
    public String toYaml() {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        return new Yaml(options).dump(toMap());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castToStringKeys(Object loaded) {
        return (Map<String, Object>) loaded;
    }

    private static double positive(Map<?, ?> mapping, String key, String prefix) {
        if (!mapping.containsKey(key)) {
            throw new ProfileException("missing required field: " + prefix + "." + key);
        }
        double value = number(mapping.get(key), prefix + "." + key);
        if (value <= 0) {
            throw new ProfileException(prefix + "." + key + " must be positive, got " + value);
        }
        return value;
    }

    private static double number(Object value, String field) {
        if (value instanceof Integer i) {
            return i;
        }
        if (value instanceof Long l) {
            return l;
        }
        if (value instanceof Double d && Double.isFinite(d)) {
            return d;
        }
        throw new ProfileException(field + " must be a number, got " + String.valueOf(value));
    }
}
