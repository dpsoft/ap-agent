package io.github.dpsoft.ap.it;

import org.junit.jupiter.api.Test;

import static io.github.dpsoft.ap.testing.Profiles.*;
import static org.junit.jupiter.api.Assertions.*;

/** End-to-end tests of the packaged (shaded) agent attached to a separate JVM with -javaagent. */
class AgentSmokeIT {

    @Test
    void startsWithBannerAndServesProfiles() throws Exception {
        try (var agent = AgentProcess.start()) {
            final var log = agent.output();
            assertTrue(log.contains("Async Profiler Agent"), log);
            assertTrue(log.contains("Startup completed in"), log);
            assertTrue(log.contains("127.0.0.1:" + agent.port + "[/profiler/profile]"), log);

            final var flame = agent.get("/profiler/profile?duration=1&output=flame");
            assertEquals(200, flame.statusCode());
            assertTrue(flameRootTotal(text(flame.body())) > 0);

            final var jfr = agent.get("/profiler/profile?duration=1");
            assertEquals(200, jfr.statusCode());
            assertTrue(jfrEventCounts(gunzip(jfr.body())).getOrDefault("jdk.ExecutionSample", 0L) > 0);

            assertEquals(404, agent.get("/debug/pprof/profile").statusCode());
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
            final var cpu = agent.get("/debug/pprof/profile?seconds=1");
            assertEquals(200, cpu.statusCode());
            final var cpuRaw = gunzip(cpu.body());
            assertTrue(pprofHasString(cpuRaw, "cpu"));
            assertTrue(pprofHasString(cpuRaw, "FixtureApp"));

            final var allocs = agent.get("/debug/pprof/allocs?seconds=1");
            assertEquals(200, allocs.statusCode());
            assertTrue(isGzip(allocs.body()));

            final var block = agent.get("/debug/pprof/block?seconds=1");
            assertEquals(200, block.statusCode());
            assertTrue(isGzip(block.body()));

            // the standard endpoint stays available in go mode and defaults to pprof
            final var standard = agent.get("/profiler/profile?duration=1");
            assertEquals(200, standard.statusCode());
            assertTrue(pprofHasString(gunzip(standard.body()), "cpu"));
        }
    }

    @Test
    void profilerIntervalCanBeConfigured() throws Exception {
        try (var agent = AgentProcess.start("ap-agent.profiler.interval=1000000")) {
            final var jfr = agent.get("/profiler/profile?duration=1");
            assertEquals(200, jfr.statusCode());
            assertTrue(jfrEventCounts(gunzip(jfr.body())).getOrDefault("jdk.ExecutionSample", 0L) > 0);
        }
    }
}
