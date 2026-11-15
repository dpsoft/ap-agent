package io.github.dpsoft.ap.util;

import com.sun.net.httpserver.HttpServer;
import io.github.dpsoft.ap.config.AgentConfiguration;
import org.tinylog.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class Server {
    public static void with(AgentConfiguration config, Consumer<HttpServer> consumer) {
        try {
            final var server = HttpServer.create(new InetSocketAddress(config.server.host, config.server.port), 0);
            // handle requests concurrently: a request arriving during a recording gets an immediate 409 instead of
            // waiting in line; daemon threads never keep the application alive
            final var threads = new java.util.concurrent.atomic.AtomicInteger();
            server.setExecutor(Executors.newCachedThreadPool(runnable -> {
                final var thread = new Thread(runnable, "ap-agent-http-" + threads.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }));
            Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));
            consumer.accept(server);
            server.start();
            Logger.info("AP Agent started in: {}:{}{}", config.server.host, config.server.port, config.handler.context());
        } catch (IOException | RuntimeException cause) {
            Logger.error(cause, "It has not been possible to start the AP Agent.");
        }
    }

    private Server() {}
}
