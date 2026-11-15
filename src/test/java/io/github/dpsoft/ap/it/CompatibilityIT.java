package io.github.dpsoft.ap.it;

import io.github.dpsoft.ap.testing.Pprof;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.github.dpsoft.ap.testing.Profiles.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Differential regression test: sends the same requests to the last published agent and to the current build,
 * both attached to the same workload, and compares the shape of every response (status, payload kind, sample
 * types, whether it has samples). Every difference must be listed in {@link #EXPECTED_CHANGES} with both the
 * old and the new shape; anything else fails the build. A report is written to target/compatibility-report.md.
 */
class CompatibilityIT {
    private static final String BASELINE = System.getProperty("ap-agent.it.baseline-version", "baseline");

    private static final List<String> STANDARD_REQUESTS = List.of(
            "/profiler/profile?duration=1",
            "/profiler/profile?duration=1&output=jfr&interval=5000000",
            "/profiler/profile?duration=1&output=pprof",
            "/profiler/profile?duration=1&output=flame",
            "/profiler/profile?duration=1&output=flamegraph",
            "/profiler/profile?duration=1&output=heatmap",
            "/profiler/profile?duration=1&output=collapsed",
            "/profiler/profile?duration=1&output=flame&params=reverse",
            "/profiler/profile?seconds=1&event=itimer&output=flame",
            "/profiler/profile?duration=1&event=alloc&output=flame",
            "/profiler/profile?duration=1&event=lock&output=flame",
            "/profiler/profile?duration=1&event=nativemem&output=flame",
            "/profiler/profile?duration=1&event=nativemem&output=flame&params=total",
            "/profiler/profile?duration=1&event=wall&output=flame",
            "/profiler/profile?duration=1&event=nativemem&output=pprof",
            "/profiler/profile?duration=abc",
            "/profiler/profile?debug&duration=1&output=flame",
            "/nope");

    private static final List<String> GO_REQUESTS = List.of(
            "/debug/pprof/profile?seconds=1",
            "/debug/pprof/allocs?seconds=1",
            "/debug/pprof/block?seconds=1",
            "/profiler/profile?duration=1");

    /** request -> {shape in the baseline, shape now, why}. */
    private static final Map<String, String[]> EXPECTED_CHANGES = Map.of(
            "/profiler/profile?duration=1&output=collapsed",
            new String[]{"200 jfr.gz samples", "200 collapsed samples", "collapsed returned the JFR recording"},
            "/profiler/profile?duration=1&event=wall&output=flame",
            new String[]{"200 html-flame empty", "200 html-flame samples", "wall samples were read as CPU samples"},
            "/profiler/profile?duration=1&event=nativemem&output=pprof",
            new String[]{"200 pprof[cpu] samples", "200 pprof[malloc] samples", "pprof ignored the event parameter"},
            "/profiler/profile?duration=abc",
            new String[]{"connection-dropped", "400 text", "invalid parameters dropped the connection, now 400 with a message"},
            "/profiler/profile?debug&duration=1&output=flame",
            new String[]{"connection-dropped", "200 html-flame samples", "parameters without '=' dropped the connection"},
            "/nope",
            new String[]{"404 empty", "404 text", "404 responses carry a message"});

    private static final List<String[]> REPORT = new ArrayList<>();

    private static java.nio.file.Path baselineJar;

    /**
     * Downloads the published jar straight from Maven Central and checks its SHA-1: resolving it through Maven would
     * pick the current build whenever the project version still equals the baseline version.
     */
    @BeforeAll
    static void downloadBaseline() throws Exception {
        REPORT.clear();
        final var base = "https://repo1.maven.org/maven2/io/github/dpsoft/ap-agent/" + BASELINE + "/ap-agent-" + BASELINE + ".jar";
        final var dir = Paths.get(System.getProperty("ap-agent.it.baseline-dir"));
        Files.createDirectories(dir);
        baselineJar = dir.resolve("ap-agent-" + BASELINE + ".jar");
        final var client = java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(20))
                .followRedirects(java.net.http.HttpClient.Redirect.NORMAL).build();
        final var checksum = client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + ".sha1"))
                .timeout(java.time.Duration.ofSeconds(30)).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, checksum.statusCode(), "download of " + base + ".sha1");
        final var expectedSha1 = checksum.body().trim().split("\\s+")[0];
        assertTrue(expectedSha1.matches("[0-9a-f]{40}"), "unexpected checksum file: " + checksum.body());
        if (!Files.exists(baselineJar) || !sha1(baselineJar).equals(expectedSha1)) {
            final var download = client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(base))
                    .timeout(java.time.Duration.ofMinutes(2)).build(), HttpResponse.BodyHandlers.ofFile(baselineJar));
            assertEquals(200, download.statusCode(), "download of " + base);
        }
        assertEquals(expectedSha1, sha1(baselineJar), "the baseline jar must be the one published on Maven Central");
    }

    private static String sha1(java.nio.file.Path file) throws Exception {
        final var digest = java.security.MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(file));
        final var hex = new StringBuilder();
        for (byte b : digest) hex.append(String.format("%02x", b));
        return hex.toString();
    }

    @Test
    void standardModeIsCompatible() throws Exception {
        compare(List.of(), STANDARD_REQUESTS);
    }

    @Test
    void goModeIsCompatible() throws Exception {
        compare(List.of("ap-agent.handler.go-mode=true"), GO_REQUESTS);
    }

    private static void compare(List<String> properties, List<String> requests) throws Exception {
        final Map<String, String> before;
        final Map<String, String> after;
        // the baseline predates the Span API: run the workload without spans on both sides
        try (var baseline = AgentProcess.start(baselineJar, properties, List.of("--no-spans"))) {
            before = shapes(baseline, requests);
        }
        try (var current = AgentProcess.start(AgentProcess.requiredJar("ap-agent.it.jar"), properties, List.of("--no-spans"))) {
            after = shapes(current, requests);
        }

        final var unexpected = new ArrayList<String>();
        for (var request : requests) {
            final var old = before.get(request);
            final var now = after.get(request);
            final var expected = EXPECTED_CHANGES.get(request);
            final String verdict;
            if (expected != null) {
                verdict = expected[0].equals(old) && expected[1].equals(now) ? "expected change: " + expected[2] : "UNEXPECTED";
            } else {
                verdict = old.equals(now) ? "same" : "UNEXPECTED";
            }
            if (verdict.equals("UNEXPECTED")) unexpected.add(request + "\n    " + BASELINE + ": " + old + "\n    current: " + now);
            REPORT.add(new String[]{String.join(" ", properties), request, old, now, verdict});
        }
        assertTrue(unexpected.isEmpty(), "Unexpected differences against " + BASELINE + ":\n  " + String.join("\n  ", unexpected));
    }

    private static Map<String, String> shapes(AgentProcess agent, List<String> requests) throws Exception {
        final var shapes = new LinkedHashMap<String, String>();
        for (var request : requests) shapes.put(request, shape(agent, request));
        return shapes;
    }

    /** A version-independent description of a response, e.g. "200 pprof[cpu] samples" or "400 text". */
    static String shape(AgentProcess agent, String request) throws Exception {
        final HttpResponse<byte[]> response;
        try {
            response = agent.get(request);
        } catch (IOException dropped) {
            return "connection-dropped";
        }
        final var body = response.body();
        final var status = response.statusCode() + " ";
        if (body.length == 0) return status + "empty";
        if (isGzip(body)) {
            final var raw = gunzip(body);
            if (raw.length == 0) return status + "gzip-empty";
            if (isJfr(raw)) return status + "jfr.gz " + (jfrEventCounts(raw).keySet().stream().anyMatch(e -> e.endsWith("Sample") || e.startsWith("profiler.")) ? "samples" : "empty");
            final Pprof pprof = Pprof.parse(raw);
            return status + "pprof" + pprof.sampleTypes + " " + (pprof.samples > 0 ? "samples" : "empty");
        }
        final var text = text(body);
        if (text.contains("Flame Graph") || text.contains("flame")) {
            if (text.startsWith("<!DOCTYPE html>") && flameRootTotal(text) == -1 && !text.contains("<h1>Flame Graph")) {
                return status + "html-heatmap";
            }
            return status + "html-flame " + (flameRootTotal(text) > 0 ? "samples" : "empty");
        }
        if (text.startsWith("<!DOCTYPE html>")) return status + "html-heatmap";
        if (text.lines().anyMatch(line -> line.matches(".+;.+ \\d+"))) return status + "collapsed samples";
        return status + "text";
    }

    @AfterAll
    static void writeReport() throws IOException {
        final var out = new StringBuilder("# Compatibility report: " + BASELINE + " vs current build\n\n")
                .append("| mode | request | ").append(BASELINE).append(" | current | verdict |\n|---|---|---|---|---|\n");
        for (var row : REPORT) {
            out.append("| ").append(row[0].isEmpty() ? "standard" : "go").append(" | `").append(row[1]).append("` | ")
               .append(row[2]).append(" | ").append(row[3]).append(" | ").append(row[4]).append(" |\n");
        }
        final var target = Paths.get(System.getProperty("ap-agent.it.test-classes")).getParent().resolve("compatibility-report.md");
        Files.writeString(target, out.toString(), StandardCharsets.UTF_8);
    }
}
