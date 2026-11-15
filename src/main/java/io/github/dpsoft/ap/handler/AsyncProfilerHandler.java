package io.github.dpsoft.ap.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import io.github.dpsoft.ap.ProfilerBusyException;
import io.github.dpsoft.ap.ProfilerService;
import io.github.dpsoft.ap.config.AgentConfiguration;
import io.github.dpsoft.ap.functions.Functions;
import org.tinylog.Logger;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;

/**
 * Serves the agent endpoints. Status codes: 404 for unknown paths, 400 for invalid parameters, 409 while another
 * profiling session runs, 500 when the profiler cannot record or convert. The 200 headers are only sent once the
 * profile is ready, with its exact length.
 */
public class AsyncProfilerHandler implements HttpHandler {
    private final ProfilerService profiler;
    private final AgentConfiguration configuration;

    public AsyncProfilerHandler(ProfilerService profiler, AgentConfiguration configuration) {
        this.profiler = profiler;
        this.configuration = configuration;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            final var path = exchange.getRequestURI().getPath();
            exchange.getRequestBody().close();

            if (!configuration.handler.context().contains(path)) {
                sendText(exchange, HttpURLConnection.HTTP_NOT_FOUND, "Not found: " + path);
                return;
            }

            try (var profile = profiler.profile(path, Functions.splitQueryParams(exchange.getRequestURI()))) {
                final var headers = exchange.getResponseHeaders();
                headers.set("Content-Type", profile.contentType());
                profile.contentEncoding().ifPresent(encoding -> headers.set("Content-Encoding", encoding));
                exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, profile.size());
                try (var out = exchange.getResponseBody()) {
                    profile.writeTo(out);
                }
            } catch (ProfilerBusyException busy) {
                sendText(exchange, HttpURLConnection.HTTP_CONFLICT, busy.getMessage());
            } catch (IllegalArgumentException invalid) {
                sendText(exchange, HttpURLConnection.HTTP_BAD_REQUEST, "Invalid parameter: " + invalid.getMessage());
            } catch (IOException | RuntimeException failure) {
                Logger.error(failure, "It has not been possible to profile.");
                sendText(exchange, HttpURLConnection.HTTP_INTERNAL_ERROR, failure.getMessage());
            }
        } finally {
            exchange.close();
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
