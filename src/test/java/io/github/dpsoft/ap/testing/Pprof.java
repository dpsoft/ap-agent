package io.github.dpsoft.ap.testing;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal decoder for the pprof protobuf (perftools.profiles.Profile): enough to read the sample types
 * and the number of samples without pulling a protobuf dependency into the tests.
 */
public final class Pprof {
    public final List<String> sampleTypes;
    public final List<String> sampleUnits;
    public final int samples;
    public final List<String> strings;

    private Pprof(List<String> sampleTypes, List<String> sampleUnits, int samples, List<String> strings) {
        this.sampleTypes = sampleTypes;
        this.sampleUnits = sampleUnits;
        this.samples = samples;
        this.strings = strings;
    }

    public static Pprof parse(byte[] raw) {
        final List<long[]> valueTypes = new ArrayList<>();
        final List<String> strings = new ArrayList<>();
        int samples = 0;
        final var in = new Reader(raw, 0, raw.length);
        while (in.hasMore()) {
            final long key = in.varint();
            final int field = (int) (key >>> 3);
            final int wire = (int) (key & 7);
            if (wire != 2) { in.skip(wire); continue; }
            final int length = in.length();
            final int start = in.pos;
            switch (field) {
                case 1: valueTypes.add(valueType(new Reader(raw, start, start + length))); break; // sample_type
                case 2: samples++; break;                                                          // sample
                case 6: strings.add(new String(raw, start, length, StandardCharsets.UTF_8)); break; // string_table
                default: break;
            }
            in.pos = start + length;
        }
        final List<String> types = new ArrayList<>();
        final List<String> units = new ArrayList<>();
        for (long[] vt : valueTypes) {
            types.add(strings.get((int) vt[0]));
            units.add(strings.get((int) vt[1]));
        }
        return new Pprof(types, units, samples, strings);
    }

    private static long[] valueType(Reader in) {
        final long[] typeAndUnit = new long[2];
        while (in.hasMore()) {
            final long key = in.varint();
            final int field = (int) (key >>> 3);
            if ((key & 7) != 0) { in.skip((int) (key & 7)); continue; }
            final long value = in.varint();
            if (field == 1) typeAndUnit[0] = value;
            if (field == 2) typeAndUnit[1] = value;
        }
        return typeAndUnit;
    }

    public boolean hasFunction(String name) {
        return strings.stream().anyMatch(s -> s.contains(name));
    }

    @Override
    public String toString() {
        return "Pprof{types=" + sampleTypes + ", units=" + sampleUnits + ", samples=" + samples + "}";
    }

    private static final class Reader {
        private final byte[] data;
        private final int end;
        int pos;

        Reader(byte[] data, int pos, int end) {
            this.data = data;
            this.pos = pos;
            this.end = end;
        }

        boolean hasMore() {
            return pos < end;
        }

        long varint() {
            long result = 0;
            for (int shift = 0; shift < 64; shift += 7) {
                if (pos >= end) throw new IllegalArgumentException("truncated varint at " + pos);
                final byte b = data[pos++];
                result |= (long) (b & 0x7f) << shift;
                if ((b & 0x80) == 0) return result;
            }
            throw new IllegalArgumentException("malformed varint");
        }

        /** Reads a length prefix and checks that the delimited segment fits in the current message. */
        int length() {
            final long length = varint();
            if (length < 0 || length > end - pos) {
                throw new IllegalArgumentException("truncated field: length " + length + " at " + pos + ", end " + end);
            }
            return (int) length;
        }

        private void advance(int bytes) {
            if (bytes > end - pos) throw new IllegalArgumentException("truncated field at " + pos);
            pos += bytes;
        }

        void skip(int wire) {
            switch (wire) {
                case 0: varint(); break;
                case 1: advance(8); break;
                case 2: { final int length = length(); pos += length; break; }
                case 5: advance(4); break;
                default: throw new IllegalArgumentException("unsupported wire type " + wire);
            }
        }
    }
}
