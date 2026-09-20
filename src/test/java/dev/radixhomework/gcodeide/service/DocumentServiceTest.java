package dev.radixhomework.gcodeide.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.radixhomework.gcodeide.service.DocumentService.SaveDecision;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DocumentServiceTest {

    private static SaveDecision decision(String answer) {
        return SaveDecision.valueOf(answer);
    }

    @Test
    void newDocumentWithoutPromptWhenUnmodified() {
        DocumentService service = new DocumentService(
                name -> {
                    throw new AssertionError("no prompt expected");
                },
                suggested -> Optional.empty());
        assertTrue(service.newDocument(() -> ""));
        assertEquals("untitled.nc", service.displayName());
        assertFalse(service.isModified());
    }

    @Test
    void saveClearsModifiedAndWritesFile(@TempDir Path dir) {
        Path target = dir.resolve("part.nc");
        DocumentService service = new DocumentService(name -> SaveDecision.CANCEL,
                suggested -> Optional.of(target));
        service.markModified();
        assertTrue(service.save("G1 X10 F600\n"));
        assertEquals("G1 X10 F600\n", read(target));
        assertFalse(service.isModified());
        assertEquals("part.nc", service.displayName());
    }

    @Test
    void saveAsSwitchesPathForLaterSaves(@TempDir Path dir) {
        Path first = dir.resolve("first.nc");
        Path second = dir.resolve("second.nc");
        List<Path> choosers = new ArrayList<>();
        DocumentService service = new DocumentService(name -> SaveDecision.CANCEL,
                suggested -> Optional.of(choosers.isEmpty() ? first : choosers.remove(0)));
        service.save("G1 X10\n");

        choosers.add(second);
        service.markModified();
        assertTrue(service.saveAs("G1 X10\nG1 X20\n", second));
        assertEquals(second, service.path());

        service.markModified();
        assertTrue(service.save("G1 X10\nG1 X20\nG1 X30\n")); // no chooser: writes to second
        assertEquals(3, read(second).split("G1", -1).length - 1);
        assertEquals(1, read(first).split("G1", -1).length - 1);
    }

    @Test
    void cancelPromptKeepsContentAndModified(@TempDir Path dir) {
        DocumentService service = new DocumentService(name -> SaveDecision.CANCEL,
                suggested -> Optional.of(dir.resolve("x.nc")));
        service.markModified();
        assertFalse(service.newDocument(() -> "G1 X10 F600\n"));
        assertTrue(service.isModified());
    }

    @Test
    void discardPromptProceeds(@TempDir Path dir) {
        DocumentService service = new DocumentService(name -> SaveDecision.DISCARD,
                suggested -> Optional.empty());
        service.markModified();
        assertTrue(service.newDocument(() -> "G1 X10 F600\n"));
        assertFalse(service.isModified());
        assertEquals("untitled.nc", service.displayName());
    }

    @Test
    void openLoadsContentAndName(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("part.nc");
        Files.writeString(file, "G0 X5\nG1 X10 F600\n");
        DocumentService service = new DocumentService(name -> SaveDecision.CANCEL,
                suggested -> Optional.empty());
        Optional<String> content = service.open(file, () -> "");
        assertTrue(content.isPresent());
        assertEquals("G0 X5\nG1 X10 F600\n", content.get());
        assertEquals("part.nc", service.displayName());
        assertFalse(service.isModified());
    }

    @Test
    void openCancelledKeepsCurrentDocument(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("part.nc");
        Files.writeString(file, "G0 X5\n");
        DocumentService service = new DocumentService(name -> SaveDecision.CANCEL,
                suggested -> Optional.empty());
        service.markModified();
        assertTrue(service.open(file, () -> "current").isEmpty());
        assertTrue(service.isModified());
    }

    @Test
    void promptDecisionNamesDocument() {
        List<String> prompted = new ArrayList<>();
        DocumentService service = new DocumentService(name -> {
            prompted.add(name);
            return decision("CANCEL");
        }, suggested -> Optional.empty());
        service.markModified();
        service.newDocument(() -> "");
        assertEquals(List.of("untitled.nc"), prompted);
    }

    private static String read(Path path) throws IllegalStateException {
        try {
            return Files.readString(path);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
