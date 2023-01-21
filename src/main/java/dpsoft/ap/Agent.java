package dpsoft.ap;

import com.sun.net.httpserver.HttpServer;
import dpsoft.ap.config.AgentConfiguration;
import dpsoft.ap.route.AsyncProfilerHandler;
import one.profiler.AsyncProfilerLoader;
import org.tinylog.Logger;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.net.InetSocketAddress;

public class Agent {
    public static void premain(String args, Instrumentation inst) throws IOException {
        final var configuration = AgentConfiguration.instance();
        final var profiler = AsyncProfilerLoader.load();
        final var server = HttpServer.create(new InetSocketAddress(configuration.serverConfig.host, configuration.serverConfig.port), 0);

        server.createContext("/", new AsyncProfilerHandler(profiler, configuration));
        server.start();

        Logger.info("AP Agent started in host and port: {}:{}", configuration.serverConfig.host, configuration.serverConfig.port);
    }

    public static void main(String[] args) throws IOException {
        System.out.println("main");
        premain("", null);
    }
}
