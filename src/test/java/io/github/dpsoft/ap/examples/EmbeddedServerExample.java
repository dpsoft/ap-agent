package io.github.dpsoft.ap.examples;

// tag::embedded[]
import com.sun.net.httpserver.HttpServer;
import io.github.dpsoft.ap.ProfilerService;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class EmbeddedServerExample {
    public static HttpServer start(int port) throws Exception {
        ProfilerService profiler = ProfilerService.create();

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/profiler/profile", profiler.httpHandler());
        server.setExecutor(Executors.newCachedThreadPool()); // concurrent requests get a 409 instead of waiting
        server.start();
        return server;
    }
}
// end::embedded[]
