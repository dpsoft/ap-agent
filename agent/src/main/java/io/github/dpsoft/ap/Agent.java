package io.github.dpsoft.ap;

import io.github.dpsoft.ap.handler.AsyncProfilerHandler;
import io.github.dpsoft.ap.instrumentation.ContextInstrumenter;
import io.github.dpsoft.ap.util.Banner;
import io.github.dpsoft.ap.util.Runner;
import io.github.dpsoft.ap.util.Server;

import java.lang.instrument.Instrumentation;

public final class Agent {
    public static void premain(String args, Instrumentation instrumentation)  {
        Runner.runWith((profiler, configuration) -> {

            ContextInstrumenter.install(instrumentation, configuration.instrumenter);

            Banner.show(configuration);

            Server.with(configuration, (server) -> {
                final var profilerHandler = new AsyncProfilerHandler(profiler, configuration.handler);
                server.createContext("/", profilerHandler);
            });
        });
    }
}
