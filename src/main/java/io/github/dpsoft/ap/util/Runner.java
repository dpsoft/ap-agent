package io.github.dpsoft.ap.util;

import io.github.dpsoft.ap.config.AgentConfiguration;
import one.profiler.AsyncProfiler;
import one.profiler.AsyncProfilerLoader;
import org.tinylog.Logger;

import java.util.function.BiConsumer;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

public final class Runner {
    /**
     * Loads Async Profiler and hands it to the consumer. Failures are logged and never propagated: a broken agent
     * must not prevent the application from starting (e.g. an unsupported platform or a missing native library).
     */
    public static void runWith(BiConsumer<AsyncProfiler, AgentConfiguration> consumer) {
        if (!AsyncProfilerLoader.isSupported()) Logger.error("Async Profiler is not supported on this platform.");

        final var start = System.nanoTime();
        try {
            consumer.accept(AsyncProfilerLoader.load(), AgentConfiguration.instance());
        } catch (Exception | LinkageError cause) {
            Logger.error(cause, () -> "Error trying to load Async Profiler.");
        } finally {
            final var timeSpent = MILLISECONDS.convert(System.nanoTime() - start, NANOSECONDS);
            Logger.info(() -> "Startup completed in " + timeSpent + " ms");
        }
    }

    private Runner() {}
}
