package io.github.dpsoft.ap.it;

import io.github.dpsoft.ap.testing.Pprof;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.github.dpsoft.ap.testing.Profiles.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end tests of the packaged (shaded) agent attached to a separate JVM with -javaagent,
 * in the spirit of the OpenTelemetry Java agent smoke tests.
 */
class AgentSmokeIT {

    @Test
    void startsWithBannerAndServesProfiles() throws Exception {
        try (var agent = AgentProcess.start()) {
            final var log = agent.output();
            assertTrue(log.contains("Async Profiler Agent"), log);
            assertTrue(log.contains(AgentProcess.STARTUP_COMPLETED), log);
            assertTrue(log.contains("127.0.0.1:" + agent.port + "[/profiler/profile]"), log);

            final var flame = agent.get("/profiler/profile?duration=1&output=flame");
            assertEquals(200, flame.statusCode());
            assertTrue(flameRootTotal(text(flame.body())) > 0);

            final var jfr = agent.get("/profiler/profile?duration=1");
            assertEquals(200, jfr.statusCode());
            final var raw = gunzip(jfr.body());
            assertTrue(jfrEventCounts(raw).getOrDefault("jdk.ExecutionSample", 0L) > 0);
            assertEquals("10000000", jfrSettings(raw).get("interval"), "default interval from reference.conf");

            assertEquals(404, agent.get("/debug/pprof/profile").statusCode());
        }
    }

    @Test
    void spanFiltersWorkThroughTheShadedAgent() throws Exception {
        try (var agent = AgentProcess.start()) {
            final var raw = gunzip(agent.get("/profiler/profile?duration=1").body());
            assertFalse(spanTags(raw).isEmpty(), "the application's one.profiler.Span must bind to the agent's profiler");
            final var tag = java.net.URLEncoder.encode("fixture:kind=orders,.*", java.nio.charset.StandardCharsets.UTF_8);
            final var flame = agent.get("/profiler/profile?duration=1&output=flame&tag=" + tag);
            assertTrue(flameRootTotal(text(flame.body())) > 0);
        }
    }

    @Test
    void bannerCanBeDisabled() throws Exception {
        try (var agent = AgentProcess.start("ap-agent.show-banner=false")) {
            assertFalse(agent.output().contains("Async Profiler Agent"), agent.output());
        }
    }

    @Test
    void goModeServesPprofEndpoints() throws Exception {
        try (var agent = AgentProcess.start("ap-agent.handler.go-mode=true")) {
            assertPprof(agent, "/debug/pprof/profile?seconds=1", "cpu");
            assertPprof(agent, "/debug/pprof/allocs?seconds=1", "allocations");
            assertPprof(agent, "/debug/pprof/block?seconds=1", "locks");
            // the standard endpoint stays available in go mode and defaults to pprof
            assertPprof(agent, "/profiler/profile?duration=1", "cpu");
        }
    }

    private static void assertPprof(AgentProcess agent, String pathAndQuery, String expectedType) throws Exception {
        final var response = agent.get(pathAndQuery);
        assertEquals(200, response.statusCode(), pathAndQuery);
        assertTrue(isGzip(response.body()), pathAndQuery);
        final Pprof profile = pprof(response.body());
        assertEquals(List.of(expectedType), profile.sampleTypes, pathAndQuery + " " + profile);
        assertTrue(profile.samples > 0, pathAndQuery + " " + profile);
        assertTrue(profile.hasFunction("FixtureApp"), pathAndQuery + " " + profile);
    }

    @Test
    void profilerIntervalCanBeConfigured() throws Exception {
        try (var agent = AgentProcess.start("ap-agent.profiler.interval=1000000")) {
            final var jfr = agent.get("/profiler/profile?duration=1");
            assertEquals(200, jfr.statusCode());
            final var raw = gunzip(jfr.body());
            assertEquals("1000000", jfrSettings(raw).get("interval"));
            assertTrue(jfrEventCounts(raw).getOrDefault("jdk.ExecutionSample", 0L) > 0);
        }
    }

    @Test
    void intervalQueryParameterOverridesConfiguration() throws Exception {
        try (var agent = AgentProcess.start()) {
            final var raw = gunzip(agent.get("/profiler/profile?duration=1&interval=5000000").body());
            assertEquals("5000000", jfrSettings(raw).get("interval"));
        }
    }
}
