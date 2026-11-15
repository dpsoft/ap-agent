package io.github.dpsoft.ap.testing;

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
        final var worker = start();
        worker.join();
    }

    public static Thread start() {
        final var thread = new Thread(FixtureApp::loop, "fixture-workload");
        thread.setDaemon(true);
        thread.start();
        return thread;
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
