package io.github.dpsoft.ap.examples;

// tag::servlet[]
import io.github.dpsoft.ap.ProfilerBusyException;
import io.github.dpsoft.ap.ProfilerService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** Serves /debug/pprof/profile, /debug/pprof/allocs, /debug/pprof/block... from your own application. */
public class ProfilerServlet extends HttpServlet {
    private ProfilerService profiler;

    @Override
    public void init() throws ServletException {
        try {
            profiler = ProfilerService.create();
        } catch (IOException e) {
            throw new ServletException("Async Profiler is not available", e);
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Map<String, String> params = new HashMap<>();
        request.getParameterMap().forEach((name, values) -> params.put(name, values[values.length - 1]));

        try (var profile = profiler.profile(request.getRequestURI(), params)) {
            response.setContentType(profile.contentType());
            profile.contentEncoding().ifPresent(encoding -> response.setHeader("Content-Encoding", encoding));
            response.setContentLengthLong(profile.size());
            profile.writeTo(response.getOutputStream());
        } catch (IllegalArgumentException invalid) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, invalid.getMessage());
        } catch (ProfilerBusyException busy) {
            response.sendError(HttpServletResponse.SC_CONFLICT, busy.getMessage());
        }
    }
}
// end::servlet[]
