package dev.radixhomework.gcodeide.model.profiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MachineProfileTest {

    private static Map<String, Object> valid() {
        Map<String, Object> bed = new HashMap<>();
        bed.put("x", 300.0);
        bed.put("y", 180.0);
        bed.put("z", 45.0);
        Map<String, Object> feeds = new HashMap<>();
        feeds.put("max_cut", 800.0);
        feeds.put("max_rapid", 1000.0);
        Map<String, Object> data = new HashMap<>();
        data.put("name", "Test Mill");
        data.put("bed", bed);
        data.put("feeds", feeds);
        data.put("safe_z", 5.0);
        return data;
    }

    @Test
    void validProfileLoads() {
        MachineProfile p = MachineProfile.fromMap(valid());
        assertEquals("Test Mill", p.name());
        assertEquals(300.0, p.bedX());
        assertEquals(180.0, p.bedY());
        assertEquals(45.0, p.bedZ());
        assertEquals(800.0, p.maxCut());
        assertEquals(1000.0, p.maxRapid());
        assertEquals(5.0, p.safeZ());
    }

    @Test
    void invalidFieldsRejectedWithNames() {
        assertRejected(set("bed.x", 0), "bed.x");
        assertRejected(set("bed.y", -10), "bed.y");
        assertRejected(set("bed.z", "wide"), "bed.z");
        assertRejected(set("feeds.max_cut", 0), "feeds.max_cut");
        assertRejected(set("feeds.max_rapid", -1), "feeds.max_rapid");
    }

    @Test
    void missingFieldRejectedWithName() {
        Map<String, Object> data = valid();
        ((Map<?, ?>) data.get("feeds")).remove("max_rapid");
        ProfileException e = assertThrows(ProfileException.class,
                () -> MachineProfile.fromMap(data));
        assertTrue(e.getMessage().contains("feeds.max_rapid"));
    }

    @Test
    void yamlRoundTrip() {
        MachineProfile original = MachineProfile.fromMap(valid());
        MachineProfile restored = MachineProfile.fromYaml(original.toYaml());
        assertEquals(original, restored);
    }

    private static Map<String, Object> set(String dotted, Object value) {
        Map<String, Object> data = valid();
        String[] parts = dotted.split("\\.");
        @SuppressWarnings("unchecked")
        Map<String, Object> section = (Map<String, Object>) data.get(parts[0]);
        section.put(parts[1], value);
        return data;
    }

    private void assertRejected(Map<String, Object> data, String field) {
        ProfileException e = assertThrows(ProfileException.class,
                () -> MachineProfile.fromMap(data));
        assertTrue(e.getMessage().contains(field), "reason should name " + field);
    }
}
