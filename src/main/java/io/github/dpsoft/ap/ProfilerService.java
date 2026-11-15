package io.github.dpsoft.ap;

import com.sun.net.httpserver.HttpHandler;
import io.github.dpsoft.ap.command.Command;
import io.github.dpsoft.ap.config.AgentConfiguration;
import io.github.dpsoft.ap.functions.Functions;
import io.github.dpsoft.ap.handler.AsyncProfilerHandler;
import io.github.dpsoft.ap.util.ProfilerExecutor;
import one.profiler.AsyncProfiler;
import one.profiler.AsyncProfilerLoader;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Entry point to use ap-agent as a library, for example to serve profiles from your own HTTP endpoints instead of
 * attaching the agent with {@code -javaagent}.
 *
 * <pre>{@code
 * ProfilerService profiler = ProfilerService.create();
 * try (Profile profile = profiler.profile("/profiler/profile", Map.of("event", "cpu", "output", "flame", "duration", "30"))) {
 *     profile.writeTo(out);
 * }
 * }</pre>
 *
 * Only one profiling session can run at a time per JVM, across every instance and the agent itself: concurrent calls
 * fail fast with {@link ProfilerBusyException}.
 */
public final class ProfilerService {
    private final AsyncProfiler profiler;
    private final AgentConfiguration configuration;
    /** Async Profiler is one per JVM: every service instance (and the agent) shares this session lock. */
    private static final ReentrantLock SESSION = new ReentrantLock();

    ProfilerService(AsyncProfiler profiler, AgentConfiguration configuration) {
        this.profiler = profiler;
        this.configuration = configuration;
    }

    /**
     * Loads Async Profiler (bundled, nothing to install) with the configuration from {@code reference.conf} and
     * {@code -Dap-agent.*} system properties.
     *
     * @throws IOException when the native library cannot be extracted or loaded on this platform
     */
    public static ProfilerService create() throws IOException {
        return new ProfilerService(AsyncProfilerLoader.load(), AgentConfiguration.instance());
    }

    /**
     * Records and converts a profile, accepting the same parameters as the agent endpoints.
     *
     * @param path        request path; its last segment selects the Go profile ({@code profile}, {@code allocs},
     *                    {@code block}) when the output is pprof and no {@code event} is given
     * @param queryParams decoded query parameters ({@code event}, {@code output}, {@code duration}, {@code tag}...)
     * @return the converted profile; close it to delete its temporary file
     * @throws IllegalArgumentException when a parameter is invalid (an HTTP 400)
     * @throws ProfilerBusyException    when another profiling session is running (an HTTP 409)
     * @throws IOException              when the profiler cannot record or convert (an HTTP 500); an interruption
     *                                  is reported as {@link java.io.InterruptedIOException}
     */
    public Profile profile(String path, Map<String, String> queryParams) throws IOException {
        // validate everything first (400 even while busy); nothing is allocated before the session is taken
        final var command = Command.from(Functions.lastSegment(path), queryParams, configuration);
        final var executor = ProfilerExecutor.with(profiler, command); // IllegalArgumentException: converter options
        if (!SESSION.tryLock()) throw new ProfilerBusyException();
        try {
            try {
                executor.run();
            } catch (java.io.InterruptedIOException interrupted) {
                throw interrupted;
            } catch (IOException | RuntimeException e) {
                // includes options the profiler rejects (e.g. interval=0): not a syntax error of the request
                throw new IOException("Profiling failed: " + e.getMessage(), e);
            }
            try {
                return new Profile(executor.convert(), contentType(command.output), command.shouldCompress() ? "gzip" : null);
            } catch (IOException | RuntimeException e) {
                // e.g. a tag filter on a recording without spans
                throw new IOException("Conversion failed: " + e.getMessage(), e);
            }
        } finally {
            SESSION.unlock();
        }
    }

    /**
     * An {@link HttpHandler} serving the configured agent endpoints ({@code /profiler/profile}, and the
     * {@code /debug/pprof/*} ones in Go mode), to mount on your own {@code com.sun.net.httpserver.HttpServer}.
     */
    public HttpHandler httpHandler() {
        return new AsyncProfilerHandler(this, configuration);
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
}
