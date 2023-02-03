package io.github.dpsoft.ap.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import io.github.dpsoft.ap.ProfilerExecutor;
import io.github.dpsoft.ap.command.Command;
import io.github.dpsoft.ap.config.AgentConfiguration;
import io.github.dpsoft.ap.functions.Functions;
import one.profiler.AsyncProfiler;
import org.tinylog.Logger;

import java.io.IOException;
import java.net.HttpURLConnection;

public class AsyncProfilerHandler implements HttpHandler {
    private final AsyncProfiler asyncProfiler;
    private final AgentConfiguration.Handler configuration;

    public AsyncProfilerHandler(AsyncProfiler profiler, AgentConfiguration.Handler configuration) {
        this.asyncProfiler = profiler;
        this.configuration = configuration;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        final var path = exchange.getRequestURI().getPath();

        if (!path.equals(configuration.context())) {
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_NOT_FOUND, 0L);
            exchange.close();
        } else {
            exchange.getRequestBody().close();
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, 0);

            if(configuration.isGoMode()) exchange.getResponseHeaders().set("Content-Encoding", "gzip");

            final var queryParamsMap = Functions.splitQueryParams(exchange.getRequestURI());
            final var command = Command.from(queryParamsMap, configuration);

            ProfilerExecutor
                    .with(asyncProfiler, configuration)
                    .run(command)
                    .onSuccess(result -> result.pipeTo(exchange.getResponseBody(), command.output))
                    .onFailure(cause -> Logger.error(cause, "It has not been possible to execute the profiler command."))
                    .andFinally(exchange::close);
        }
    }
}
