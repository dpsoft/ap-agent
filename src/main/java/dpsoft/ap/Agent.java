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

        Server.with(configuration.server, (server) -> {
            final var profilerHandler = new AsyncProfilerHandler(profiler, configuration);
            server.createContext("/profiler/profile", profilerHandler);
        });
    }


    public static void main(String[] args) throws IOException, InterruptedException {
        System.out.println("main");
        premain("", null);

        //heavy cpu consume task
        factorial(10000);
    }

    static int factorial(int n) throws InterruptedException {
       while (true) {
       }
    }
}
