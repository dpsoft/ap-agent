package io.github.dpsoft.ap.util;

import io.github.dpsoft.ap.config.AgentConfiguration;
import one.profiler.AsyncProfiler;
import one.profiler.AsyncProfilerLoader;
import org.tinylog.Logger;

import java.util.function.BiConsumer;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

public final class Runner {
    public static void runWith(BiConsumer<AsyncProfiler, AgentConfiguration> consumer) {
        if (!AsyncProfilerLoader.isSupported()) Logger.error("Async Profiler is not supported on this platform.");

        try {
            Runnable timed = timedRunnable(() -> {
                try {
                    consumer.accept(AsyncProfilerLoader.load(), AgentConfiguration.instance());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            timed.run();
        } catch (Exception e) {
            Logger.error(e, () -> "Error trying to load Async Profiler.");
        }
    }

    private static Runnable timedRunnable(final Runnable thunk) {
        return () -> {
            final var startMillis = System.nanoTime();
            try { thunk.run(); }
            finally {
                final var timeSpent = MILLISECONDS.convert((System.nanoTime() - startMillis), NANOSECONDS);
                Logger.info(() -> "Startup completed in " + timeSpent + " ms");
            }
        };
    }
}
