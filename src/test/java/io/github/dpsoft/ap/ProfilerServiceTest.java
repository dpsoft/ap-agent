package io.github.dpsoft.ap;

import io.github.dpsoft.ap.testing.FixtureApp;
import one.profiler.AsyncProfilerLoader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static io.github.dpsoft.ap.testing.Profiles.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** The library entry point: the same behaviour as the agent endpoints, without HTTP. */
class ProfilerServiceTest {
    private static ProfilerService profiler;
    private static FixtureApp.Workload workload;

    @BeforeAll
    static void start() throws Exception {
        assumeTrue(AsyncProfilerLoader.isSupported());
        profiler = ProfilerService.create();
        workload = FixtureApp.start();
    }

    @AfterAll
    static void stop() throws Exception {
        if (workload != null) workload.close();
    }

    @Test
    void profilesAndConverts() throws Exception {
        try (var profile = profiler.profile("/profiler/profile", Map.of("output", "flame", "duration", "1"))) {
            assertEquals("text/html; charset=utf-8", profile.contentType());
            assertTrue(profile.contentEncoding().isEmpty());
            final var out = new ByteArrayOutputStream();
            profile.writeTo(out);
            assertEquals(profile.size(), out.size());
            assertTrue(flameRootTotal(out.toString(StandardCharsets.UTF_8)) > 0);
        }
    }

    @Test
    void goPathsSelectTheEventForPprof() throws Exception {
        // without go mode the default output is JFR; an explicit pprof output follows the Go path naming
        try (var profile = profiler.profile("/debug/pprof/allocs", Map.of("seconds", "1", "output", "pprof"))) {
            final var out = new ByteArrayOutputStream();
            profile.writeTo(out);
            assertEquals(java.util.List.of("allocations"), pprof(out.toByteArray()).sampleTypes);
        }
    }

    @Test
    void closeDeletesTheTemporaryFile() throws Exception {
        final var profile = profiler.profile("/profiler/profile", Map.of("duration", "1"));
        final var size = profile.size();
        assertTrue(size > 0);
        profile.close();
        assertThrows(java.io.UncheckedIOException.class, profile::size);
    }

    @Test
    void invalidParametersAreIllegalArguments() {
        assertThrows(IllegalArgumentException.class, () -> profiler.profile("/profiler/profile", Map.of("duration", "abc")));
        assertThrows(IllegalArgumentException.class, () -> profiler.profile("/profiler/profile", Map.of("tag", "[")));
    }

    @Test
    void profilerFailuresAreIOExceptions() {
        final var failure = assertThrows(IOException.class,
                () -> profiler.profile("/profiler/profile", Map.of("event", "cpu", "interval", "0", "duration", "1")));
        assertTrue(failure.getMessage().startsWith("Profiling failed"), failure.getMessage());
    }

    @Test
    void sessionsAreExclusiveAcrossServiceInstances() throws Exception {
        final var other = ProfilerService.create();
        final var pool = Executors.newSingleThreadExecutor();
        try {
            final var first = pool.submit(() -> {
                try (var profile = profiler.profile("/profiler/profile", Map.of("duration", "2"))) {
                    return profile.size();
                }
            });
            Thread.sleep(500);
            assertThrows(ProfilerBusyException.class, () -> other.profile("/profiler/profile", Map.of("duration", "1")));
            assertTrue(first.get(30, TimeUnit.SECONDS) > 0);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentSessionsFailFast() throws Exception {
        final var pool = Executors.newSingleThreadExecutor();
        final var started = new CountDownLatch(1);
        try {
            final var first = pool.submit(() -> {
                started.countDown();
                try (var profile = profiler.profile("/profiler/profile", Map.of("duration", "3"))) {
                    return profile.size();
                }
            });
            started.await();
            Thread.sleep(500); // let the first session start recording
            final long start = System.nanoTime();
            assertThrows(ProfilerBusyException.class, () -> profiler.profile("/profiler/profile", Map.of("duration", "1")));
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 1_000, "must not wait for the running session");
            assertTrue(first.get(30, TimeUnit.SECONDS) > 0, "the running session is not affected");
        } finally {
            pool.shutdownNow();
        }
        // and the profiler is usable again afterwards
        try (var profile = profiler.profile("/profiler/profile", Map.of("duration", "1"))) {
            assertTrue(profile.size() > 0);
        }
    }
}
