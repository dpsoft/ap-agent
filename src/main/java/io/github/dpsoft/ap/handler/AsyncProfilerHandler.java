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
import java.nio.file.Files;

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
            final ProfilerExecutor executor;
            try {
                command = Command.from(Functions.lastSegment(path), Functions.splitQueryParams(exchange.getRequestURI()), configuration);
                executor = ProfilerExecutor.with(asyncProfiler, command); // validates converter arguments (tag regex...)
            } catch (IllegalArgumentException invalid) {
                sendText(exchange, HttpURLConnection.HTTP_BAD_REQUEST, "Invalid parameter: " + invalid.getMessage());
                return;
            }

            final var recording = executor.run();
            if (recording.isFailure()) {
                final var cause = recording.getCause();
                Logger.error(cause, "It has not been possible to execute the profiler command.");
                sendText(exchange, HttpURLConnection.HTTP_INTERNAL_ERROR, "Profiling failed: " + cause.getMessage());
                return;
            }

            // convert before committing to 200, so that conversion errors (e.g. a tag filter without spans) are reported
            final var converted = executor.convert();
            if (converted.isFailure()) {
                final var cause = converted.getCause();
                Logger.error(cause, "It has not been possible to convert the profile.");
                sendText(exchange, HttpURLConnection.HTTP_INTERNAL_ERROR, "Conversion failed: " + cause.getMessage());
                return;
            }

            final var body = converted.get();
            try {
                final var headers = exchange.getResponseHeaders();
                headers.set("Content-Type", contentType(command.output));
                if (command.shouldCompress()) headers.set("Content-Encoding", "gzip");
                exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, Files.size(body));
                try (var out = exchange.getResponseBody()) {
                    Files.copy(body, out);
                }
            } finally {
                Files.deleteIfExists(body);
            }
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
