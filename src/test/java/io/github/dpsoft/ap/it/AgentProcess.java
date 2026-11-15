package io.github.dpsoft.ap.it;

import io.github.dpsoft.ap.testing.FixtureApp;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * A separate JVM running {@link FixtureApp} with the shaded agent attached through -javaagent,
 * in the spirit of the OpenTelemetry Java agent smoke tests.
 */
final class AgentProcess implements AutoCloseable {
    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private final Process process;
    private final Path log;
    final int port;

    private AgentProcess(Process process, Path log, int port) {
        this.process = process;
        this.log = log;
        this.port = port;
    }

    static AgentProcess start(String... systemProperties) throws Exception {
        final var agentJar = requiredPath("ap-agent.it.jar");
        final var testClasses = requiredPath("ap-agent.it.test-classes");
        final int port = freePort();
        final var java = Paths.get(System.getProperty("java.home"), "bin", "java").toString();

        final List<String> command = new ArrayList<>();
        command.add(java);
        command.add("-XX:+IgnoreUnrecognizedVMOptions");
        command.add("--enable-native-access=ALL-UNNAMED");
        command.add("-javaagent:" + agentJar);
        command.add("-Dap-agent.server.host=127.0.0.1");
        command.add("-Dap-agent.server.port=" + port);
        for (var property : systemProperties) command.add("-D" + property);
        command.add("-cp");
        command.add(testClasses.toString());
        command.add(FixtureApp.class.getName());

        final var log = Files.createTempFile("ap-agent-it", ".log");
        final var process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        final var agent = new AgentProcess(process, log, port);
        agent.awaitStarted(Duration.ofSeconds(60));
        return agent;
    }

    private static Path requiredPath(String property) {
        final var value = System.getProperty(property);
        if (value == null) throw new IllegalStateException("Missing system property " + property + " (run through maven-failsafe)");
        final var path = Paths.get(value);
        if (!Files.exists(path)) throw new IllegalStateException(path + " does not exist; run `mvn verify`");
        return path;
    }

    private static int freePort() throws IOException {
        try (var socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private void awaitStarted(Duration timeout) throws Exception {
        final long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (!process.isAlive()) throw new IllegalStateException("Agent process exited early:\n" + output());
            if (output().contains("AP Agent started")) return;
            Thread.sleep(100);
        }
        throw new IllegalStateException("Agent did not start within " + timeout + ":\n" + output());
    }

    String output() throws IOException {
        return Files.readString(log, StandardCharsets.UTF_8);
    }

    HttpResponse<byte[]> get(String pathAndQuery) throws IOException, InterruptedException {
        final var uri = URI.create("http://127.0.0.1:" + port + pathAndQuery);
        return CLIENT.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(60)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
    }

    @Override
    public void close() throws Exception {
        process.destroy();
        if (!process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) process.destroyForcibly().waitFor();
        Files.deleteIfExists(log);
    }
}
