package io.github.dpsoft.ap;

import io.github.dpsoft.ap.examples.EmbeddedServerExample;
import io.github.dpsoft.ap.examples.OrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static io.github.dpsoft.ap.testing.Profiles.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The Java examples of the README and docs/*.md live in src/test/java/io/github/dpsoft/ap/examples, between tag:: and end::
 * markers: they are compiled with the build, and this test checks that the docs show exactly that code.
 */
class ReadmeExamplesTest {

    @ParameterizedTest
    @CsvSource({"EmbeddedServerExample.java,embedded", "ProfilerServlet.java,servlet", "OrderService.java,spans"})
    void readmeShowsTheCompiledExamples(String file, String tag) throws Exception {
        final var source = Files.readString(Paths.get("src/test/java/io/github/dpsoft/ap/examples", file), StandardCharsets.UTF_8);
        final var matcher = Pattern.compile("// tag::" + tag + "\\[]\\n(.*?)// end::" + tag + "\\[]", Pattern.DOTALL).matcher(source);
        assertTrue(matcher.find(), "missing tag " + tag + " in " + file);
        final var snippet = normalize(matcher.group(1));
        final var docs = new StringBuilder(Files.readString(Paths.get("README.md"), StandardCharsets.UTF_8));
        try (var files = Files.list(Paths.get("docs"))) {
            for (var doc : (Iterable<java.nio.file.Path>) files.filter(f -> f.toString().endsWith(".md")).sorted()::iterator) {
                docs.append('\n').append(Files.readString(doc, StandardCharsets.UTF_8));
            }
        }
        assertTrue(normalize(docs.toString()).contains(snippet),
                "README.md or docs/*.md must contain the " + tag + " example from " + file + ":\n" + snippet);
    }

    private static String normalize(String text) {
        return text.lines().map(String::stripTrailing).collect(Collectors.joining("\n")).trim();
    }

    @Test
    void embeddedServerExampleServesProfiles() throws Exception {
        final var server = EmbeddedServerExample.start(0);
        try {
            final var uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/profiler/profile?duration=1&output=flame");
            final var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(uri).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("Flame Graph"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void spanExampleRuns() {
        new OrderService().placeOrder("42");
    }
}
