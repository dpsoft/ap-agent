package io.github.dpsoft.ap.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import io.github.dpsoft.ap.util.ProfilerExecutor;
import io.github.dpsoft.ap.command.Command;
import io.github.dpsoft.ap.config.AgentConfiguration;
import io.github.dpsoft.ap.functions.Functions;
import one.profiler.AsyncProfiler;
import org.tinylog.Logger;

import java.io.IOException;
import java.net.HttpURLConnection;

public class AsyncProfilerHandler implements HttpHandler {
    private final AsyncProfiler asyncProfiler;
    private final AgentConfiguration configuration;

    public AsyncProfilerHandler(AsyncProfiler profiler, AgentConfiguration configuration) {
        this.asyncProfiler = profiler;
        this.configuration = configuration;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        final var path = exchange.getRequestURI().getPath();

        if(!configuration.handler.context().contains(path)) {
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_NOT_FOUND, 0L);
            exchange.close();
        } else {
            exchange.getRequestBody().close();
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, 0);

            final var queryParamsMap = Functions.splitQueryParams(exchange.getRequestURI());
            final var operation = Functions.lastSegment(path);
            final var command = Command.from(operation, queryParamsMap, configuration);

            if(configuration.handler.isGoMode()) exchange.getResponseHeaders().set("Content-Encoding", "gzip");

            ProfilerExecutor
                    .with(asyncProfiler, command)
                    .run()
                    .onSuccess(result -> result.pipeTo(exchange.getResponseBody()))
                    .onFailure(cause -> Logger.error(cause, "It has not been possible to execute the profiler command."))
                    .andFinally(exchange::close);
        }
    }
}

