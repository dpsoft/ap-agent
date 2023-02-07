package io.github.dpsoft.ap;

import io.github.dpsoft.ap.config.AgentConfiguration;
import io.github.dpsoft.ap.handler.AsyncProfilerHandler;
import io.github.dpsoft.ap.util.Banner;
import one.profiler.AsyncProfilerLoader;

import java.io.IOException;
import java.lang.instrument.Instrumentation;

public final class Agent {
    public static void premain(String args, Instrumentation inst) throws IOException {
        final var configuration = AgentConfiguration.instance();
        final var profiler = AsyncProfilerLoader.load();

        Banner.show(configuration);

        Server.with(configuration, (server) -> {
            final var profilerHandler = new AsyncProfilerHandler(profiler, configuration.handler);
            server.createContext("/", profilerHandler);
        });
    }
}
