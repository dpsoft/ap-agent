package io.github.dpsoft.ap.util;

import io.github.dpsoft.ap.command.Command;
import one.convert.*;
import one.jfr.JfrReader;
import one.profiler.AsyncProfiler;
import one.profiler.Events;
import org.tinylog.Logger;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;

/**
 * One profiling session: {@link #with} validates the request, {@link #run} records to a temporary JFR file and
 * {@link #convert} turns it into the requested output. Temporary files are removed on every path.
 */
public final class ProfilerExecutor {

    private static final String NATIVE_MEM = "nativemem";

    private final AsyncProfiler profiler;
    private final Command command;
    private final Arguments arguments;
    private final File file;

    /**
     * @throws IllegalArgumentException when the converter rejects the request parameters (e.g. an invalid tag
     *                                  regex), before anything is recorded
     */
    public static ProfilerExecutor with(AsyncProfiler profiler, Command command) {
        return new ProfilerExecutor(profiler, command);
    }

    private ProfilerExecutor(AsyncProfiler profiler, Command command) {
        this.profiler = profiler;
        this.command = command;
        this.arguments = getArguments(command);
        try {
            // no deleteOnExit(): every path deletes the file, and the JVM would keep each registered path forever
            this.file = File.createTempFile("ap-agent", ".jfr");
        } catch (IOException e) {
            throw new UncheckedIOException("It has not been possible to create a temporal file for JFR.", e);
        }
    }

    /**
     * Records for the requested duration. The profiler is always stopped once it started; on failure the temporary
     * recording is deleted and, if the thread was interrupted, its interrupt status is restored.
     *
     * @throws InterruptedIOException when the thread is interrupted while recording
     * @throws IOException            or a runtime exception when the profiler cannot record (e.g. invalid options)
     */
    public ProfilerExecutor run() throws IOException {
        try {
            profiler.execute(command.asFormatString(file.getAbsolutePath()));
            try {
                Thread.sleep(command.getDuration().toMillis());
            } finally {
                profiler.stop();
            }
            return this;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            file.delete();
            final var interrupted = new InterruptedIOException("Profiling was interrupted");
            interrupted.initCause(e);
            throw interrupted;
        } catch (IOException | RuntimeException e) {
            file.delete();
            throw e;
        }
    }

    /**
     * Converts the recording into a new temporary file holding the response body; the caller owns (and deletes)
     * it. The recording itself is always deleted.
     */
    public Path convert() throws IOException {
        try {
            final var output = Files.createTempFile("ap-agent", ".out");
            try (var out = new BufferedOutputStream(Files.newOutputStream(output))) {
                convert(arguments, out);
            } catch (IOException | RuntimeException | Error e) {
                Files.deleteIfExists(output);
                throw e;
            }
            return output;
        } finally {
            file.delete();
        }
    }

    /** Converts the recording straight into {@code out} (library use); failures are logged. */
    public void pipeTo(OutputStream out) {
        try {
            convert(arguments, out);
        } catch (Exception e) {
            Logger.error(e, "It has not been possible to pipe the profiler result to the output stream.");
        } finally {
            file.delete();
        }
    }

    private void convert(Arguments arguments, OutputStream out) throws IOException {
        if (command.output == null) { toGZIP(out); return; }
        switch (command.output) {
            case PPROF: toPProf(arguments, out); break;
            case HEATMAP: toHeatmap(arguments, out); break;
            case FLAME_GRAPH:
            case FLAME: toFlame(arguments, out); break;
            case COLLAPSED: toCollapsed(arguments, out); break;
            case JFR:
            default: toGZIP(out);
        }
    }

    private void toGZIP(OutputStream out) throws IOException {
        try (var in = new BufferedInputStream(new FileInputStream(file));
             var gzip = new GZIPOutputStream(new NonClosing(out))) {
            in.transferTo(gzip);
        }
    }

    private void toFlame(Arguments arguments, OutputStream out) throws IOException {
        try (var reader = new JfrReader(file.getAbsolutePath())) {
            var converter = new JfrToFlame(reader, arguments);
            converter.convert();
            converter.dump(out);
        }
    }

    /** Collapsed stacks, one "frame;frame;frame count" line per stack (the input format of flamegraph tools). */
    private void toCollapsed(Arguments arguments, OutputStream out) throws IOException {
        arguments.output = "collapsed";
        toFlame(arguments, out);
    }

    private void toPProf(Arguments arguments, OutputStream out) throws IOException {
        try (var reader = new JfrReader(file.getAbsolutePath());
             var gzip = new GZIPOutputStream(new NonClosing(out))) {
            var converter = new JfrToPprof(reader, arguments);
            converter.convert();
            converter.dump(gzip);
        }
    }

    private void toHeatmap(Arguments arguments, OutputStream out) throws IOException {
        try (var reader = new JfrReader(file.getAbsolutePath())) {
            var converter = new JfrToHeatmap(reader, arguments);
            converter.convert();
            converter.dump(out);
        }
    }

    private static Arguments getArguments(Command command) {
        final var params = new java.util.ArrayList<>(command.eventParams);
        params.add(converterEvent(command.eventType));
        return new Arguments(params.toArray(new String[0]));
    }

    /** The temporary recording (visible for tests). */
    File recordingFile() {
        return file;
    }

    /** Selects which JFR samples the converter reads; every CPU engine (cpu, ctimer, itimer) is an execution sample. */
    static String converterEvent(String eventType) {
        if (eventType == null) return "--cpu";
        switch (eventType) {
            case Events.ALLOC: return "--alloc";
            case Events.LOCK: return "--lock";
            case Events.WALL: return "--wall";
            case NATIVE_MEM: return "--nativemem";
            default: return "--cpu";
        }
    }

    /** Lets a GZIP stream finish without closing the caller's stream (the caller decides when to close it). */
    private static final class NonClosing extends FilterOutputStream {
        NonClosing(OutputStream out) { super(out); }
        @Override public void write(byte[] b, int off, int len) throws IOException { out.write(b, off, len); }
        @Override public void close() throws IOException { flush(); }
    }
}
