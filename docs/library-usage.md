# Using AP-Agent as a library

Add the dependency and serve profiles from your own application, with the same parameters and error handling as
the agent:

```xml
<dependency>
    <groupId>io.github.dpsoft</groupId>
    <artifactId>ap-agent</artifactId>
    <version>0.2.0</version>
</dependency>
```

Mount the endpoint on an HTTP server of your own:

```java
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
```

Or call it from any web framework, for example a servlet:

```java
import io.github.dpsoft.ap.ProfilerBusyException;
import io.github.dpsoft.ap.ProfilerService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** Serves /debug/pprof/profile, /debug/pprof/allocs and /debug/pprof/block from your own application. */
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
        params.putIfAbsent("output", "pprof"); // Go tools expect pprof; the path picks cpu, allocations or locks

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
```

`ProfilerService`, `Profile` and `ProfilerBusyException` (package `io.github.dpsoft.ap`) are the supported API; the
other classes are internal and may change. Async Profiler records one session per JVM: while one runs, other calls
fail fast with `ProfilerBusyException` (also across several `ProfilerService` instances and the agent). These
examples are compiled and run by the test suite.
