package net.player005.vanillablocking;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateCheckerTest {

    @Test
    void comparesVersionsNumerically() {
        assertTrue(UpdateChecker.isNewer("1.5.1", "1.5.0"));
        assertTrue(UpdateChecker.isNewer("1.10.0", "1.9.9"));
        assertTrue(UpdateChecker.isNewer("2.0", "1.99.99"));
        assertFalse(UpdateChecker.isNewer("1.5.0", "1.5.0"));
        assertFalse(UpdateChecker.isNewer("1.4.9", "1.5.0"));
    }

    @Test
    void ignoresSuffixesAndMissingParts() {
        assertFalse(UpdateChecker.isNewer("1.5.0-SNAPSHOT", "1.5.0"));
        assertTrue(UpdateChecker.isNewer("1.5.1-rc1", "1.5.0"));
        assertFalse(UpdateChecker.isNewer("1.5", "1.5.0"));
        assertTrue(UpdateChecker.isNewer("1.5.0.1", "1.5.0"));
    }

    @Test
    void readsTheFirstVersionNumberFromModrinthJson() {
        String body = "[{\"name\":\"1.6.0\",\"version_number\":\"1.6.0\"},{\"version_number\":\"1.5.0\"}]";
        assertEquals("1.6.0", UpdateChecker.firstVersionNumber(body));
        assertNull(UpdateChecker.firstVersionNumber("[]"));
    }
}
