package io.github.dpsoft.ap.util;

import io.github.dpsoft.ap.command.Command;
import io.vavr.CheckedFunction0;
import io.vavr.collection.List;
import io.vavr.control.Try;
import one.convert.*;
import one.jfr.JfrReader;
import one.profiler.AsyncProfiler;
import one.profiler.Events;
import org.tinylog.Logger;

import java.io.*;
import java.util.zip.GZIPOutputStream;

public final class ProfilerExecutor {

    private static final String NATIVE_MEM = "nativemem";

    private final AsyncProfiler profiler;
    private final File file;
    private final Command command;

    public static ProfilerExecutor with(AsyncProfiler profiler, Command command){
        return new ProfilerExecutor(profiler, command);
    }

    private ProfilerExecutor(AsyncProfiler profiler, Command command) {
        this.profiler = profiler;
        this.command = command;
        this.file = Try.of(() -> File.createTempFile("ap-agent", ".jfr"))
                .onSuccess(File::deleteOnExit)
                .getOrElseThrow(() -> new RuntimeException("It has not been possible to create a temporal file for JFR."));
    }

    /** Records for the requested duration. The profiler is always stopped once it started, even on interruption. */
    public Try<ProfilerExecutor> run() {
        return Try.of(() -> {
            profiler.execute(command.asFormatString(file.getAbsolutePath()));
            try {
                Thread.sleep(command.getDuration().toMillis());
            } finally {
                profiler.stop();
            }
            return this;
        }).onFailure(cause -> file.delete());
    }

    public void pipeTo(OutputStream out) {
        final var result = convert(getArguments(command), out);
        result.andFinally(file::delete);
        result.onFailure(cause -> Logger.error(cause, "It has not been possible to pipe the profiler result to the output stream."));
    }

    public void pipeTo(CheckedFunction0<OutputStream> consumer) {
        Try.of(consumer)
                .onFailure(cause -> Logger.error(cause, "It has not been possible to create the output stream."))
                .onSuccess(this::pipeTo);
    }

    private Try<Void> convert(Arguments arguments, OutputStream out) {
        if (command.output == null) return toGZIP(out);
        switch (command.output) {
            case PPROF: return toPProf(arguments, out);
            case HEATMAP: return toHeatmap(arguments, out);
            case FLAME_GRAPH:
            case FLAME: return toFlame(arguments, out);
            case COLLAPSED: return toCollapsed(arguments, out);
            case JFR:
            default: return toGZIP(out);
        }
    }

    private Try<Void> toGZIP(OutputStream out){
        return Try.run(() -> {
            try (var bufferedFileReader = new BufferedInputStream(new FileInputStream(file));
                 var outputStream = new GZIPOutputStream(out);
                 var bufferedOutputStream = new BufferedOutputStream(outputStream)) {

                bufferedFileReader.transferTo(bufferedOutputStream);
            }
        });
    }

    private Try<Void> toFlame(Arguments arguments, OutputStream out) {
        return Try.run(() -> {
            try (var reader = new JfrReader(file.getAbsolutePath())) {
                var converter = new JfrToFlame(reader, arguments);
                converter.convert();
                converter.dump(out);
            }
        });
    }

    /** Collapsed stacks, one "frame;frame;frame count" line per stack (the input format of flamegraph tools). */
    private Try<Void> toCollapsed(Arguments arguments, OutputStream out) {
        arguments.output = "collapsed";
        return toFlame(arguments, out);
    }

    private Try<Void> toPProf(Arguments arguments, OutputStream out) {
        return Try.run(() -> {
            try (var reader = new JfrReader(file.getAbsolutePath()); var outputStream = new GZIPOutputStream(out)) {
                var converter = new JfrToPprof(reader, arguments);
                converter.convert();
                converter.dump(outputStream);
            }
        });
    }

    private Try<Void> toHeatmap(Arguments arguments, OutputStream out) {
        return Try.run(() -> {
            try (var reader = new JfrReader(file.getAbsolutePath())) {
                var converter = new JfrToHeatmap(reader, arguments);
                converter.convert();
                converter.dump(out);
            }
        });
    }

    private Arguments getArguments(Command command) {
        final var params = command.eventParams.append(converterEvent(command.eventType));
        return new Arguments(params.toJavaArray(String[]::new));
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
}
