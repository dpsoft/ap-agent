package io.github.dpsoft.ap.util;

import io.github.dpsoft.ap.config.AgentConfiguration;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class BannerTest {

    @Test
    void printsBannerWithVersions() {
        final var original = System.out;
        final var captured = new ByteArrayOutputStream();
        System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
        try {
            Banner.show(AgentConfiguration.instance());
        } finally {
            System.setOut(original);
        }
        final var output = captured.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("Async Profiler Agent"), output);
        assertTrue(output.contains("Powered by AP-Loader"), output);
        assertTrue(output.contains(BuildInfo.version()), output);
        assertTrue(output.contains(BuildInfo.apLoaderVersion()), output);
        assertFalse(output.contains("${"), output);
    }
}
