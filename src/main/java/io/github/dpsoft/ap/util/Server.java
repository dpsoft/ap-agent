package io.github.dpsoft.ap.util;

import com.sun.net.httpserver.HttpServer;
import io.github.dpsoft.ap.config.AgentConfiguration;
import io.vavr.control.Try;
import org.tinylog.Logger;

import java.net.InetSocketAddress;
import java.util.function.Consumer;

public final class Server {
    public static void with(AgentConfiguration config, Consumer<HttpServer> consumer) {
        Try.run(() -> {
            final var server = HttpServer.create(new InetSocketAddress(config.server.host, config.server.port), 0);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));
            consumer.accept(server);
            server.start();

        }).onSuccess((ignore) -> Logger.info("AP Agent started in: {}:{}{}", config.server.host, config.server.port, config.handler.context()))
          .onFailure((cause) -> Logger.error(cause, "It has not been possible to start the AP Agent."));
    }
}
