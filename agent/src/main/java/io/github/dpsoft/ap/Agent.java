package io.github.dpsoft.ap;

import io.github.dpsoft.ap.handler.AsyncProfilerHandler;
import io.github.dpsoft.ap.instrumentation.InstrumentationLoader;
import io.github.dpsoft.ap.util.Banner;
import io.github.dpsoft.ap.util.Runner;
import io.github.dpsoft.ap.util.Server;

import java.lang.instrument.Instrumentation;

public final class Agent {
    public static void premain(String args, Instrumentation inst)  {
        Runner.runWith((profiler, configuration) -> {

            InstrumentationLoader.install(inst);

            Banner.show(configuration);

            Server.with(configuration, (server) -> {
                final var profilerHandler = new AsyncProfilerHandler(profiler, configuration.handler);
                server.createContext("/", profilerHandler);
            });
        });
    }

    public static void main(String[] args) {
//        premain(null, ByteBuddyAgent.install());
//
//        var contextStorage = new ContextHandler();
//
//        var xx = contextStorage.runWithContext(new Context(1, Map.of("a", "b")), () -> {
//            var currentContext = contextStorage.currentContext();
//            return currentContext;
//        });
//
////       var xxxx =  contextStorage.storeContext(new Context(2, Map.of("c", "d")));
//
//        System.out.println(xx.contextId);
//        System.out.println(xx.tags);
//
//        System.out.println(contextStorage.currentContext().contextId);
//
//        while (true) {}
    }
}
