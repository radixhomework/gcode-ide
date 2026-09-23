package dev.radixhomework.gcodeide.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.radixhomework.gcodeide.model.profiles.MachineProfile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProfileServiceTest {

    @Test
    void builtinPresetLoadsWithAllDocumentedFields() {
        ProfileService service = new ProfileService(null, null);
        MachineProfile preset = service.byName("Example Mill").orElseThrow();
        assertEquals("Example Mill", preset.name());
        assertEquals(300.0, preset.bedX());
        assertEquals(180.0, preset.bedY());
        assertEquals(45.0, preset.bedZ());
        assertEquals(800.0, preset.maxCut());
        assertEquals(1000.0, preset.maxRapid());
        assertEquals(5.0, preset.safeZ());
        assertNotNull(service.active()); // first profile active by default
    }

    @Test
    void validUserProfileAppearsAlongsideBuiltins(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("custom.yaml"), """
                name: Custom Mill
                bed: { x: 200.0, y: 200.0, z: 50.0 }
                feeds: { max_cut: 500.0, max_rapid: 900.0 }
                safe_z: 2.5
                """);
        ProfileService service = new ProfileService(dir, null);
        List<String> names = service.profiles().stream().map(MachineProfile::name).toList();
        assertTrue(names.contains("Example Mill"));
        assertTrue(names.contains("Custom Mill"));
    }

    @Test
    void malformedProfileSkippedButOthersLoad(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("broken.yaml"), "name: Broken\nbed: { x: 0, y: 100, z: 50 }\n");
        Files.writeString(dir.resolve("good.yaml"), """
                name: Good Mill
                bed: { x: 100.0, y: 100.0, z: 30.0 }
                feeds: { max_cut: 400.0, max_rapid: 600.0 }
                safe_z: 1.0
                """);
        ProfileService service = new ProfileService(dir, null);
        List<String> names = service.profiles().stream().map(MachineProfile::name).toList();
        assertTrue(names.contains("Good Mill"));
        assertTrue(!names.contains("Broken"));
    }

    @Test
    void nonYamlFilesIgnored(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("notes.txt"), "not a profile");
        ProfileService service = new ProfileService(dir, null);
        assertEquals(List.of("Example Mill"),
                service.profiles().stream().map(MachineProfile::name).toList());
    }

    @Test
    void selectedProfilePersistsAcrossReload(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("wide.yaml"), """
                name: Wide Mill
                bed: { x: 400.0, y: 200.0, z: 45.0 }
                feeds: { max_cut: 800.0, max_rapid: 1000.0 }
                safe_z: 5.0
                """);
        Path config = dir.resolve("config.yaml");
        ProfileService first = new ProfileService(dir, config);
        first.select("Wide Mill");
        assertEquals("Wide Mill", first.active().name());

        ProfileService second = new ProfileService(dir, config);
        assertEquals("Wide Mill", second.active().name());
    }
}
