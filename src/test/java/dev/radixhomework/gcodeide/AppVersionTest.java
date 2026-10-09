package dev.radixhomework.gcodeide;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AppVersionTest {

    @Test
    void versionIsDefined() {
        assertTrue(App.VERSION != null && !App.VERSION.isBlank());
    }

    @Test
    void versionArgIsHandledWithoutStartingFx() {
        assertTrue(App.handleVersionArg(new String[] {"--version"}));
        assertFalse(App.handleVersionArg(new String[] {}));
        assertFalse(App.handleVersionArg(new String[] {"other.nc"}));
    }
}
