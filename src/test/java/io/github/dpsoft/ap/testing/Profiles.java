package io.github.dpsoft.ap.testing;

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/** Helpers to inspect the payloads returned by the agent. */
public final class Profiles {
    private static final Pattern FLAME_ROOT = Pattern.compile("(?m)^n\\(3,(\\d+)\\)");

    public static boolean isGzip(byte[] body) {
        return body.length > 2 && (body[0] & 0xff) == 0x1f && (body[1] & 0xff) == 0x8b;
    }

    public static byte[] gunzip(byte[] body) throws IOException {
        try (var in = new GZIPInputStream(new ByteArrayInputStream(body))) {
            return in.readAllBytes();
        }
    }

    public static boolean isJfr(byte[] raw) {
        return raw.length > 4 && raw[0] == 'F' && raw[1] == 'L' && raw[2] == 'R' && raw[3] == 0;
    }

    /** Counts JFR events by type name. */
    public static Map<String, Long> jfrEventCounts(byte[] raw) throws IOException {
        final var file = Files.createTempFile("ap-agent-test", ".jfr");
        try {
            Files.write(file, raw);
            final var counts = new TreeMap<String, Long>();
            for (RecordedEvent event : RecordingFile.readAllEvents(file)) {
                counts.merge(event.getEventType().getName(), 1L, Long::sum);
            }
            return counts;
        } finally {
            Files.deleteIfExists(file);
        }
    }

    public static String text(byte[] body) {
        return new String(body, StandardCharsets.UTF_8);
    }

    /** Total value of the root frame of an async-profiler HTML flame graph, or -1 when absent. */
    public static long flameRootTotal(String html) {
        final var m = FLAME_ROOT.matcher(html);
        return m.find() ? Long.parseLong(m.group(1)) : -1;
    }

    /** True when the (decompressed) pprof protobuf string table contains the given string. */
    public static boolean pprofHasString(byte[] rawPprof, String value) {
        return text(rawPprof).contains(value);
    }

    private Profiles() {}
}
