package io.github.dpsoft.ap.testing;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The test-only pprof decoder must reject truncated input instead of reporting phantom samples. */
class PprofTest {

    private static void varint(ByteArrayOutputStream out, long value) {
        while ((value & ~0x7fL) != 0) {
            out.write((int) ((value & 0x7f) | 0x80));
            value >>>= 7;
        }
        out.write((int) value);
    }

    private static void bytesField(ByteArrayOutputStream out, int field, byte[] payload) {
        varint(out, ((long) field << 3) | 2);
        varint(out, payload.length);
        out.writeBytes(payload);
    }

    private static byte[] valueType(int type, int unit) {
        final var out = new ByteArrayOutputStream();
        varint(out, 1 << 3); varint(out, type);
        varint(out, 2 << 3); varint(out, unit);
        return out.toByteArray();
    }

    private static ByteArrayOutputStream profile() {
        final var out = new ByteArrayOutputStream();
        bytesField(out, 1, valueType(1, 2));                                  // sample_type {cpu, count}
        bytesField(out, 2, new byte[]{0x10, 0x01});                           // sample {value: 1}
        for (var s : new String[]{"", "cpu", "count", "FixtureApp.loop"}) {    // string_table
            bytesField(out, 6, s.getBytes(StandardCharsets.UTF_8));
        }
        return out;
    }

    @Test
    void decodesSampleTypesSamplesAndStrings() {
        final var pprof = Pprof.parse(profile().toByteArray());
        assertEquals(List.of("cpu"), pprof.sampleTypes);
        assertEquals(List.of("count"), pprof.sampleUnits);
        assertEquals(1, pprof.samples);
        assertTrue(pprof.hasFunction("FixtureApp"));
    }

    @Test
    void rejectsTruncatedLengthDelimitedField() {
        final var out = profile();
        varint(out, (2 << 3) | 2);
        varint(out, 127); // declares 127 bytes of sample and ends immediately
        assertThrows(IllegalArgumentException.class, () -> Pprof.parse(out.toByteArray()));
    }

    @Test
    void rejectsTruncatedVarint() {
        final var out = profile();
        out.write(0x80); // continuation bit without a following byte
        assertThrows(IllegalArgumentException.class, () -> Pprof.parse(out.toByteArray()));
    }

    @Test
    void skipsUnknownLengthDelimitedFieldsCompletely() {
        final var out = new ByteArrayOutputStream();
        bytesField(out, 9, new byte[300]); // unknown field with a 2-byte length prefix
        final var rest = profile().toByteArray();
        out.writeBytes(rest);
        assertEquals(1, Pprof.parse(out.toByteArray()).samples);
    }

    @Test
    void workloadCloseStopsEveryThread() throws Exception {
        final var workload = FixtureApp.start();
        assertTrue(workload.isAlive());
        workload.close();
        assertFalse(workload.isAlive());
    }
}
