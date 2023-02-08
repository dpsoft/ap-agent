package io.github.dpsoft.ap.util;

import io.vavr.control.Try;
import one.profiler.AsyncProfiler;
import one.profiler.AsyncProfilerLoader;
import org.tinylog.Logger;

import java.util.function.Consumer;

public final class Profiler {
    public static void with(Consumer<AsyncProfiler> consumer) {
        if (!AsyncProfilerLoader.isSupported()) Logger.error("Async Profiler is not supported on this platform.");

        Try.run(() -> consumer.accept(AsyncProfilerLoader.load()))
           .onFailure(cause -> Logger.error(cause, () -> "Error trying to load Async Profiler."));
    }
}
