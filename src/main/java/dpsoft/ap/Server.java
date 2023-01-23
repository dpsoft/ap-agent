package dpsoft.ap;

import com.sun.net.httpserver.HttpServer;
import dpsoft.ap.config.AgentConfiguration;
import io.vavr.control.Try;
import org.tinylog.Logger;

import java.net.InetSocketAddress;
import java.util.function.Consumer;

public final class Server {
    public static void with(AgentConfiguration.Server config, Consumer<HttpServer> serverConsumer) {
        Try.run(() -> {
            final var server = HttpServer.create(new InetSocketAddress(config.host, config.port), 0);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));
            serverConsumer.accept(server);
            server.start();

        }).onSuccess((ignore) -> Logger.info("AP Agent started in: {}:{}", config.host, config.port))
          .onFailure((cause) -> Logger.error(cause, "It has not been possible to start the AP Agent."));
    }
}
