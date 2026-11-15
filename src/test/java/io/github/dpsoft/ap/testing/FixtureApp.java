package io.github.dpsoft.ap.testing;

import one.profiler.Span;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.Deflater;

/**
 * Small workload used by the integration tests: burns CPU, allocates on the Java heap and
 * allocates native memory, so that cpu, alloc and nativemem profiles all have samples.
 */
public final class FixtureApp {
    private static volatile long sink;

    public static void main(String[] args) throws Exception {
        start().join();
    }

    private static final Object LOCK = new Object();

    /** Starts the workload: one CPU/heap/native thread plus two threads contending on a monitor. */
    public static Workload start() {
        final var threads = new ArrayList<Thread>();
        threads.add(daemon(FixtureApp::loop, "fixture-workload"));
        for (int i = 0; i < 2; i++) threads.add(daemon(FixtureApp::contend, "fixture-contention-" + i));
        threads.add(daemon(FixtureApp::spans, "fixture-spans"));
        return new Workload(threads);
    }

    /** Handle over every workload thread: closing it interrupts and joins all of them. */
    public static final class Workload implements AutoCloseable {
        private final List<Thread> threads;

        private Workload(List<Thread> threads) {
            this.threads = List.copyOf(threads);
        }

        public void join() throws InterruptedException {
            for (var thread : threads) thread.join();
        }

        public boolean isAlive() {
            return threads.stream().anyMatch(Thread::isAlive);
        }

        @Override
        public void close() throws InterruptedException {
            threads.forEach(Thread::interrupt);
            for (var thread : threads) thread.join(5_000);
        }
    }

    private static Thread daemon(Runnable task, String name) {
        final var thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    /** Tags emitted by {@link #spans()}; they contain commas on purpose (see the tag query parameter). */
    public static final String ORDERS_TAG = "fixture:kind=orders,id=1";
    public static final String USERS_TAG = "fixture:kind=users,id=2";

    /**
     * Burns CPU inside alternating spans (async-profiler Span API). Requires the profiler library to be loaded
     * before the first call, otherwise the Span API stays unavailable for the lifetime of the JVM.
     */
    private static void spans() {
        long n = 0;
        while (!Thread.currentThread().isInterrupted()) {
            final long start = Span.start();
            long acc = 0;
            for (int i = 0; i < 300_000; i++) acc += (long) Math.sqrt(i + n);
            sink += acc;
            Span.end(start, (n++ & 1) == 0 ? ORDERS_TAG : USERS_TAG);
        }
    }

    private static void contend() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                synchronized (LOCK) {
                    // hold the monitor long enough for the other thread to block (spinning is not contention)
                    Thread.sleep(2);
                    sink++;
                }
                // release outside the monitor so the threads alternate: monitors are unfair and an
                // immediate re-acquire would starve the other thread into a few very long waits
                Thread.sleep(1);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private static void loop() {
        final byte[] input = new byte[64 * 1024];
        final byte[] output = new byte[128 * 1024];
        final List<byte[]> retained = new ArrayList<>();
        while (!Thread.currentThread().isInterrupted()) {
            long acc = 0;
            for (int i = 0; i < 200_000; i++) acc += (long) Math.sqrt(i);
            retained.add(new byte[1024]);
            if (retained.size() > 1_000) retained.clear();
            final ByteBuffer direct = ByteBuffer.allocateDirect(64 * 1024);
            direct.put(0, (byte) acc);
            final Deflater deflater = new Deflater();
            deflater.setInput(input);
            deflater.finish();
            acc += deflater.deflate(output);
            deflater.end();
            sink += acc + direct.get(0);
        }
    }

    private FixtureApp() {}
}
