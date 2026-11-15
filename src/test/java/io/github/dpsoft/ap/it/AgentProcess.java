package io.github.dpsoft.ap.it;

import io.github.dpsoft.ap.testing.FixtureApp;

import java.io.File;
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
        return start(java.util.List.of(systemProperties), java.util.List.of());
    }

    /** The current agent, with the async-profiler API on the application classpath (needed for spans). */
    static AgentProcess start(List<String> systemProperties, List<String> appArgs) throws Exception {
        return start(requiredPath("ap-agent.it.jar"), systemProperties, appArgs,
                List.of(requiredPath("ap-agent.it.async-profiler-api")), true);
    }

    /** Any agent jar, with only the test classes (no extra jars) on the application classpath. */
    static AgentProcess start(Path agentJar, List<String> systemProperties, List<String> appArgs) throws Exception {
        return start(agentJar, systemProperties, appArgs, List.of(), true);
    }

    static Path requiredJar(String property) {
        return requiredPath(property);
    }

    /**
     * @param appClasspath  jars added to the application classpath after the test classes
     * @param awaitStarted  wait for the agent server; otherwise only wait for the agent to finish its startup
     */
    static AgentProcess start(Path agentJar, List<String> systemProperties, List<String> appArgs,
                              List<Path> appClasspath, boolean awaitStarted) throws Exception {
        final var testClasses = requiredPath("ap-agent.it.test-classes");
        final int port = freePort();
        final var java = Paths.get(System.getProperty("java.home"), "bin", "java").toString();

        final List<String> command = new ArrayList<>();
        command.add(java);
        command.add("-XX:+IgnoreUnrecognizedVMOptions");
        command.add("--enable-native-access=ALL-UNNAMED");
        coverageAgent(agentJar).ifPresent(command::add);
        command.add("-javaagent:" + agentJar);
        command.add("-Dap-agent.server.host=127.0.0.1");
        command.add("-Dap-agent.server.port=" + port);
        for (var property : systemProperties) command.add("-D" + property);
        command.add("-cp");
        // the application brings the async-profiler API (one.profiler.Span) itself, as documented for span filters
        final var classpath = new StringBuilder(testClasses.toString());
        for (var jar : appClasspath) classpath.append(File.pathSeparator).append(jar);
        command.add(classpath.toString());
        command.add(FixtureApp.class.getName());
        command.addAll(appArgs);

        final var log = Files.createTempFile("ap-agent-it", ".log");
        final Process process;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(log);
            throw e;
        }
        final var agent = new AgentProcess(process, log, port);
        try {
            if (awaitStarted) agent.awaitOutput(Duration.ofSeconds(60), STARTED, STARTUP_COMPLETED);
            else agent.awaitOutput(Duration.ofSeconds(60), STARTUP_COMPLETED);
            return agent;
        } catch (Exception | Error failure) {
            // start() never returns the AutoCloseable on failure, so the caller cannot clean up: do it here
            try {
                agent.close();
            } catch (Exception cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    static final String STARTED = "AP Agent started";
    static final String STARTUP_COMPLETED = "Startup completed in";

    /**
     * The JaCoCo agent for the JVM running the current agent jar, so premain, Runner and Server are covered too.
     * The baseline release is never instrumented: its classes would not match the current ones. Only the agent's
     * own classes are recorded, not its relocated dependencies. The data is written when the JVM exits normally
     * ({@link #close} uses destroy(), i.e. SIGTERM, which runs the shutdown hooks).
     */
    private static java.util.Optional<String> coverageAgent(Path agentJar) {
        final var jacoco = System.getProperty("ap-agent.it.jacoco-agent");
        final var destfile = System.getProperty("ap-agent.it.jacoco-destfile");
        final var current = System.getProperty("ap-agent.it.jar");
        if (jacoco == null || destfile == null || current == null || !agentJar.equals(Paths.get(current))) return java.util.Optional.empty();
        if (!Files.exists(Paths.get(jacoco))) return java.util.Optional.empty();
        return java.util.Optional.of("-javaagent:" + jacoco + "=destfile=" + destfile
                + ",append=true,includes=io.github.dpsoft.ap.*,excludes=io.github.dpsoft.ap.libs.*");
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

    /** Waits until every marker is present in the child output (they are logged at different moments). */
    private void awaitOutput(Duration timeout, String... markers) throws Exception {
        final long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            final var output = output();
            if (java.util.Arrays.stream(markers).allMatch(output::contains)) return;
            if (!process.isAlive()) throw new IllegalStateException("Agent process exited early:\n" + output);
            Thread.sleep(100);
        }
        throw new IllegalStateException("Agent output did not contain " + java.util.Arrays.toString(markers)
                + " within " + timeout + ":\n" + output());
    }

    boolean isAlive() {
        return process.isAlive();
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
        try {
            process.destroy();
            if (!process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) process.destroyForcibly().waitFor();
        } finally {
            Files.deleteIfExists(log);
        }
    }
}
