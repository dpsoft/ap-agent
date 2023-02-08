package io.github.dpsoft.ap;

import io.github.dpsoft.ap.config.AgentConfiguration;
import io.github.dpsoft.ap.handler.AsyncProfilerHandler;
import io.github.dpsoft.ap.util.Banner;
import io.github.dpsoft.ap.util.Profiler;
import io.github.dpsoft.ap.util.Server;

import java.lang.instrument.Instrumentation;

public final class Agent {
    public static void premain(String args, Instrumentation inst)  {
        Profiler.with((profiler) -> {
            final var configuration = AgentConfiguration.instance();

            Banner.show(configuration);

            Server.with(configuration, (server) -> {
                final var profilerHandler = new AsyncProfilerHandler(profiler, configuration.handler);
                server.createContext("/", profilerHandler);
            });
        });
    }
}
