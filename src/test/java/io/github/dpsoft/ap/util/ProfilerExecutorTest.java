package io.github.dpsoft.ap.util;

import io.github.dpsoft.ap.command.Command;
import io.github.dpsoft.ap.config.AgentConfiguration;
import one.profiler.AsyncProfilerLoader;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ProfilerExecutorTest {

    @Test
    void invalidConverterArgumentsAreRejectedUpFront() {
        final var command = Command.from("profile", Map.of("tag", "["), AgentConfiguration.instance());
        assumeTrue(AsyncProfilerLoader.isSupported());
        assertThrows(IllegalArgumentException.class, () -> ProfilerExecutor.with(AsyncProfilerLoader.load(), command));
    }

    @Test
    void validatingARequestAllocatesNothing() throws Exception {
        assumeTrue(AsyncProfilerLoader.isSupported());
        final var executor = ProfilerExecutor.with(AsyncProfilerLoader.load(),
                Command.from("profile", Map.of("duration", "1"), AgentConfiguration.instance()));
        assertNull(executor.recordingFile(), "the recording file is only created by run()");
        assertThrows(IllegalStateException.class, executor::convert);
    }

    @Test
    void interruptionStopsTheProfilerRestoresTheFlagAndDeletesTheRecording() throws Exception {
        assumeTrue(AsyncProfilerLoader.isSupported());
        final var profiler = AsyncProfilerLoader.load();
        final var executor = ProfilerExecutor.with(profiler, Command.from("profile", Map.of("duration", "30"), AgentConfiguration.instance()));
        final var failure = new AtomicReference<Throwable>();
        final var interrupted = new AtomicReference<Boolean>();
        final var worker = new Thread(() -> {
            try {
                executor.run();
            } catch (Throwable e) {
                failure.set(e);
            }
            interrupted.set(Thread.currentThread().isInterrupted());
        });
        worker.start();
        Thread.sleep(300);
        worker.interrupt();
        worker.join(10_000);

        assertFalse(worker.isAlive());
        assertInstanceOf(java.io.InterruptedIOException.class, failure.get());
        assertInstanceOf(InterruptedException.class, failure.get().getCause());
        assertTrue(interrupted.get(), "the interrupt status must be restored");
        assertFalse(executor.recordingFile().exists(), "the temporary recording must be deleted");
        // the profiler was stopped: a new session can start right away
        final var next = ProfilerExecutor.with(profiler, Command.from("profile", Map.of("duration", "1", "output", "flame"), AgentConfiguration.instance()));
        next.run();
        final var converted = next.convert();
        assertTrue(java.nio.file.Files.size(converted) > 0);
        java.nio.file.Files.delete(converted);
        assertFalse(next.recordingFile().exists());
    }
}
