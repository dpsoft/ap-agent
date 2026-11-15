package io.github.dpsoft.ap.handler;

import com.sun.net.httpserver.HttpServer;
import io.github.dpsoft.ap.command.Command;
import io.github.dpsoft.ap.config.AgentConfiguration;
import io.github.dpsoft.ap.testing.FixtureApp;
import one.profiler.AsyncProfilerLoader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import static io.github.dpsoft.ap.testing.Profiles.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Drives the real handler and the real Async Profiler inside the test JVM, with a background workload.
 * Each request records for one second.
 */
class AsyncProfilerHandlerTest {
    private static HttpServer server;
    private static FixtureApp.Workload workload;
    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @BeforeAll
    static void startServer() throws Exception {
        assumeTrue(AsyncProfilerLoader.isSupported(), "Async Profiler is not supported on this platform");
        final var profiler = AsyncProfilerLoader.load(); // load first so the workload spans are recorded from the start
        workload = FixtureApp.start();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", new AsyncProfilerHandler(profiler, AgentConfiguration.instance()));
        server.start();
    }

    @AfterAll
    static void stopServer() throws InterruptedException {
        if (server != null) server.stop(0);
        if (workload != null) workload.close();
    }

    private static HttpResponse<byte[]> get(String pathAndQuery) throws IOException, InterruptedException {
        final var uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + pathAndQuery);
        return CLIENT.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(60)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
    }

    private static HttpResponse<byte[]> profile(String query) throws IOException, InterruptedException {
        return get("/profiler/profile?duration=1&" + query);
    }

    @Test
    void unknownPathReturns404() throws Exception {
        assertEquals(404, get("/nope").statusCode());
        assertEquals(404, get("/debug/pprof/profile").statusCode(), "go endpoints are disabled by default");
    }

    @Test
    void defaultOutputIsGzippedJfrWithCpuSamples() throws Exception {
        final var response = profile("");
        assertEquals(200, response.statusCode());
        assertTrue(isGzip(response.body()));
        final var raw = gunzip(response.body());
        assertTrue(isJfr(raw));
        assertTrue(jfrEventCounts(raw).getOrDefault("jdk.ExecutionSample", 0L) > 0, jfrEventCounts(raw).toString());
    }

    @Test
    void allocEventRecordsAllocationSamples() throws Exception {
        final var counts = jfrEventCounts(gunzip(profile("event=alloc&output=jfr").body()));
        final long allocations = counts.getOrDefault("jdk.ObjectAllocationInNewTLAB", 0L)
                + counts.getOrDefault("jdk.ObjectAllocationOutsideTLAB", 0L)
                + counts.getOrDefault("jdk.ObjectAllocationSample", 0L);
        assertTrue(allocations > 0, counts.toString());
    }

    @Test
    void nativememEventRecordsMallocSamples() throws Exception {
        final var counts = jfrEventCounts(gunzip(profile("event=nativemem&output=jfr").body()));
        assertTrue(counts.getOrDefault("profiler.Malloc", 0L) > 0, counts.toString());
    }

    @Test
    void flameOutputIsHtmlWithSamples() throws Exception {
        for (var output : new String[]{"flame", "flamegraph"}) {
            final var response = profile("output=" + output);
            assertEquals(200, response.statusCode());
            final var html = text(response.body());
            assertTrue(html.contains("Flame Graph"), output);
            assertTrue(flameRootTotal(html) > 0, output);
        }
    }

    @Test
    void nativememFlameWithTotalIsMeasuredInBytes() throws Exception {
        final var counted = text(profile("event=nativemem&output=flame").body());
        final var total = text(profile("event=nativemem&output=flame&params=total").body());
        assertTrue(flameRootTotal(counted) > 0);
        // each sampled malloc in the workload is at least 64 KiB, so bytes dwarf the number of samples
        // (async-profiler 4.6+ also labels the graph with units = 'bytes')
        assertTrue(flameRootTotal(total) > 64 * flameRootTotal(counted),
                "params=total should accumulate bytes: " + flameRootTotal(total) + " vs " + flameRootTotal(counted));
    }

    @Test
    void jfrContainsSpansWithTheirTags() throws Exception {
        final var raw = gunzip(profile("output=jfr").body());
        final var tags = spanTags(raw);
        assertTrue(tags.contains(FixtureApp.ORDERS_TAG) && tags.contains(FixtureApp.USERS_TAG), tags.toString());
    }

    @Test
    void tagFilterReachesTheConverterThroughHttp() throws Exception {
        // the regexes contain a comma and a '+': both must survive the query string and params splitting
        assertTrue(flameRootTotal(text(profile("output=flame&tag=" + encode("fixture:kind=orders,.*")).body())) > 0);
        assertTrue(flameRootTotal(text(profile("output=flame&tag=" + encode("fixture:kind=users,id=.+")).body())) > 0);
    }

    /** Subset relations are only meaningful on the same recording, so convert one JFR with each filter. */
    @Test
    void tagFilterSelectsASubsetOfTheSameRecording() throws Exception {
        final var jfr = java.nio.file.Files.createTempFile("ap-agent-test", ".jfr");
        try {
            java.nio.file.Files.write(jfr, gunzip(profile("output=jfr").body()));
            final long all = flameTotal(jfr, Map.of());
            final long fixtures = flameTotal(jfr, Map.of("tag", "fixture:.+"));
            final long orders = flameTotal(jfr, Map.of("tag", "fixture:kind=orders,.*"));
            final long users = flameTotal(jfr, Map.of("tag", "fixture:kind=users,.*"));
            assertTrue(orders > 0 && users > 0, "orders=" + orders + " users=" + users);
            assertEquals(fixtures, orders + users, "fixture spans are exactly orders + users");
            assertTrue(fixtures < all, "fixtures=" + fixtures + " all=" + all);
        } finally {
            java.nio.file.Files.deleteIfExists(jfr);
        }
    }

    /** Converts with the converter arguments the agent derives from the given query parameters. */
    private static long flameTotal(java.nio.file.Path jfr, Map<String, String> query) throws Exception {
        final var command = Command.from("profile", query, AgentConfiguration.instance());
        final var args = new one.convert.Arguments(command.eventParams.append("--cpu").toJavaArray(String[]::new));
        final var out = new java.io.ByteArrayOutputStream();
        try (var reader = new one.jfr.JfrReader(jfr.toString())) {
            final var converter = new one.convert.JfrToFlame(reader, args);
            converter.convert();
            converter.dump(out);
        }
        return Math.max(0, flameRootTotal(out.toString(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    void tagFilterWithoutMatchesProducesAnEmptyGraph() throws Exception {
        final var response = profile("output=flame&tag=nomatch");
        assertEquals(200, response.statusCode());
        assertEquals(-1, flameRootTotal(text(response.body())));
    }

    @Test
    void latencyFilterKeepsOnlySlowSpans() throws Exception {
        assertTrue(flameRootTotal(text(profile("output=flame&latency=0").body())) > 0);
        assertEquals(-1, flameRootTotal(text(profile("output=flame&latency=60000").body())));
    }

    @Test
    void tagFilterAppliesToPprof() throws Exception {
        final var matching = pprof(profile("output=pprof&tag=" + encode("fixture:kind=users,.*")).body());
        assertTrue(matching.samples > 0, matching.toString());
        final var none = pprof(profile("output=pprof&tag=nomatch").body());
        assertEquals(0, none.samples, none.toString());
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    @Test
    void heatmapOutputIsHtml() throws Exception {
        final var response = profile("output=heatmap");
        assertEquals(200, response.statusCode());
        assertTrue(text(response.body()).startsWith("<!DOCTYPE html>"));
    }

    @Test
    void pprofOutputIsGzippedCpuProfile() throws Exception {
        final var response = profile("output=pprof");
        assertEquals(200, response.statusCode());
        assertTrue(isGzip(response.body()));
        final var profile = pprof(response.body());
        assertEquals(java.util.List.of("cpu"), profile.sampleTypes, profile.toString());
        assertTrue(profile.samples > 0, profile.toString());
        assertTrue(profile.hasFunction("FixtureApp"), profile.toString());
    }

    @Test
    void responseCurrentlyLacksContentEncodingHeader() throws Exception {
        assertTrue(profile("output=jfr").headers().firstValue("Content-Encoding").isEmpty());
    }

    @Test
    @Disabled("Known bug (phase 5): Content-Encoding is set after sendResponseHeaders and never reaches the client")
    void compressedResponsesDeclareContentEncoding() throws Exception {
        assertEquals("gzip", profile("output=jfr").headers().firstValue("Content-Encoding").orElse(null));
        assertEquals("gzip", profile("output=pprof").headers().firstValue("Content-Encoding").orElse(null));
    }

    @Test
    void collapsedOutputCurrentlyReturnsGzippedJfr() throws Exception {
        assertTrue(isJfr(gunzip(profile("output=collapsed").body())));
    }

    @Test
    @Disabled("Known bug (phase 5): output=collapsed must return collapsed stacks, not the JFR recording")
    void collapsedOutputReturnsCollapsedStacks() throws Exception {
        final var body = profile("output=collapsed").body();
        final var text = text(isGzip(body) ? gunzip(body) : body);
        assertTrue(text.lines().anyMatch(line -> line.matches(".+;.+ \\d+")), text);
    }

    @Test
    @Disabled("Known bug (phase 5): /profiler/profile?output=pprof ignores the event parameter and always records CPU")
    void pprofOutputHonoursEvent() throws Exception {
        final var profile = pprof(profile("output=pprof&event=nativemem").body());
        assertEquals(java.util.List.of("malloc"), profile.sampleTypes, profile.toString());
    }

    @Test
    void invalidDurationCurrentlyReturnsOkWithEmptyBody() throws Exception {
        // sendResponseHeaders(200) happens before the command is parsed; the failure is only visible as an empty body
        assertThrowsOrEmpty(() -> get("/profiler/profile?duration=abc"));
    }

    private static void assertThrowsOrEmpty(ThrowingSupplier call) {
        try {
            final var response = call.get();
            assertEquals(200, response.statusCode());
            assertEquals(0, response.body().length);
        } catch (IOException e) {
            // the connection may also be dropped by the server: acceptable for the current behaviour
        } catch (Exception e) {
            fail(e);
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier {
        HttpResponse<byte[]> get() throws Exception;
    }
}
