package dpsoft.ap;

import dpsoft.ap.config.AgentConfiguration;
import dpsoft.ap.handler.AsyncProfilerHandler;
import one.profiler.AsyncProfilerLoader;

import java.io.IOException;
import java.lang.instrument.Instrumentation;

public class Agent {
    public static void premain(String args, Instrumentation inst) throws IOException {
        final var configuration = AgentConfiguration.instance();
        final var profiler = AsyncProfilerLoader.load();

        Server.with(configuration, (server) -> {
            final var profilerHandler = new AsyncProfilerHandler(profiler, configuration.handler);
            server.createContext("/", profilerHandler);
        });
    }
}
