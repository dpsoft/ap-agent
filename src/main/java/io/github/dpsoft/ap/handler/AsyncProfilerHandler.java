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
import java.nio.charset.StandardCharsets;

public class AsyncProfilerHandler implements HttpHandler {
    private final AsyncProfiler asyncProfiler;
    private final AgentConfiguration configuration;

    public AsyncProfilerHandler(AsyncProfiler profiler, AgentConfiguration configuration) {
        this.asyncProfiler = profiler;
        this.configuration = configuration;
    }

    /**
     * Status codes: 404 for unknown paths, 400 for invalid parameters, 500 when the profiler cannot record.
     * The 200 headers are only sent once the recording succeeded, right before streaming the converted profile.
     */
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            final var path = exchange.getRequestURI().getPath();
            exchange.getRequestBody().close();

            if (!configuration.handler.context().contains(path)) {
                sendText(exchange, HttpURLConnection.HTTP_NOT_FOUND, "Not found: " + path);
                return;
            }

            final Command command;
            try {
                command = Command.from(Functions.lastSegment(path), Functions.splitQueryParams(exchange.getRequestURI()), configuration);
            } catch (IllegalArgumentException invalid) {
                sendText(exchange, HttpURLConnection.HTTP_BAD_REQUEST, "Invalid parameter: " + invalid.getMessage());
                return;
            }

            final var recording = ProfilerExecutor.with(asyncProfiler, command).run();
            if (recording.isFailure()) {
                final var cause = recording.getCause();
                Logger.error(cause, "It has not been possible to execute the profiler command.");
                sendText(exchange, HttpURLConnection.HTTP_INTERNAL_ERROR, "Profiling failed: " + cause.getMessage());
                return;
            }

            final var headers = exchange.getResponseHeaders();
            headers.set("Content-Type", contentType(command.output));
            if (command.shouldCompress()) headers.set("Content-Encoding", "gzip");
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, 0);
            recording.get().pipeTo(exchange.getResponseBody());
        } finally {
            exchange.close();
        }
    }

    private static String contentType(Command.Output output) {
        if (output == null) return "application/octet-stream";
        switch (output) {
            case FLAME:
            case FLAME_GRAPH:
            case HEATMAP: return "text/html; charset=utf-8";
            case COLLAPSED: return "text/plain; charset=utf-8";
            default: return "application/octet-stream";
        }
    }

    private static void sendText(HttpExchange exchange, int status, String message) throws IOException {
        final var body = (message + "\n").getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, body.length);
        try (var out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
