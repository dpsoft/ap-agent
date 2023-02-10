package io.github.dpsoft.ap.util;

import io.github.dpsoft.ap.config.AgentConfiguration;
import io.vavr.CheckedRunnable;
import io.vavr.control.Try;
import one.profiler.AsyncProfiler;
import one.profiler.AsyncProfilerLoader;
import org.tinylog.Logger;

import java.util.function.BiConsumer;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

public final class Runner {
    public static void runWith(BiConsumer<AsyncProfiler, AgentConfiguration> consumer) {
        if (!AsyncProfilerLoader.isSupported()) Logger.error("Async Profiler is not supported on this platform.");

        Try.run(timed(() -> consumer.accept(AsyncProfilerLoader.load(), AgentConfiguration.instance())))
           .onFailure(cause -> Logger.error(cause, () -> "Error trying to load Async Profiler."));
    }

    private static CheckedRunnable timed(final CheckedRunnable thunk) {
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
