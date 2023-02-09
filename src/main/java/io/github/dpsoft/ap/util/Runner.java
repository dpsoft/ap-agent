package io.github.dpsoft.ap.util;

import io.github.dpsoft.ap.config.AgentConfiguration;
import io.vavr.control.Try;
import one.profiler.AsyncProfiler;
import one.profiler.AsyncProfilerLoader;
import org.tinylog.Logger;

import java.util.function.BiConsumer;

public final class Runner {
    public static void runWith(BiConsumer<AsyncProfiler, AgentConfiguration> consumer) {
        if (!AsyncProfilerLoader.isSupported()) Logger.error("Async Profiler is not supported on this platform.");

        Try.run(() -> consumer.accept(AsyncProfilerLoader.load(), AgentConfiguration.instance()))
           .onFailure(cause -> Logger.error(cause, () -> "Error trying to load Async Profiler."));
    }
}
