package io.github.dpsoft.ap;

import io.github.dpsoft.ap.util.Banner;
import io.github.dpsoft.ap.util.Runner;
import io.github.dpsoft.ap.util.Server;

import java.lang.instrument.Instrumentation;

public final class Agent {
    public static void premain(String args, Instrumentation ignored) {
        Runner.runWith((profiler, configuration) -> {

            Banner.show(configuration);

            Server.with(configuration, (server) -> {
                server.createContext("/", new ProfilerService(profiler, configuration).httpHandler());
            });
        });
    }
}
