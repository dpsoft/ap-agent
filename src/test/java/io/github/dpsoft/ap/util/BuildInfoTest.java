package io.github.dpsoft.ap.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BuildInfoTest {

    @Test
    void versionsAreFilteredFromThePom() {
        assertNotNull(BuildInfo.version());
        assertNotNull(BuildInfo.apLoaderVersion());
        assertFalse(BuildInfo.version().contains("${"), BuildInfo.version());
        assertFalse(BuildInfo.apLoaderVersion().contains("${"), BuildInfo.apLoaderVersion());
        assertTrue(BuildInfo.apLoaderVersion().matches("\\d+\\.\\d+(\\.\\d+)?-\\d+"), BuildInfo.apLoaderVersion());
    }
}
