package io.github.dpsoft.ap.util;

import io.github.dpsoft.ap.command.Command;
import io.github.dpsoft.ap.command.Command.Output;
import io.vavr.CheckedFunction0;
import io.vavr.collection.List;
import io.vavr.control.Try;
import one.convert.*;
import one.jfr.JfrReader;
import one.profiler.AsyncProfiler;
import one.profiler.Events;
import org.tinylog.Logger;

import java.io.*;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

import static io.vavr.API.*;
import static io.vavr.Predicates.*;

public final class ProfilerExecutor {

    private static final String NATIVE_MEM = "nativemem";
    private static final Set<String> EventTypes = Set.of(Events.ALLOC, Events.CPU, Events.LOCK , NATIVE_MEM);

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

    public Try<ProfilerExecutor> run() {
        return Try.of(() -> {
            profiler.execute(command.asFormatString(file.getAbsolutePath()));
            Thread.sleep(command.getDuration().toMillis());
            profiler.stop();
            return this;
        });
    }

    public void pipeTo(OutputStream out) {
        final var arguments = getArguments(command);

        final var result = Match(command.output).of(
            Case($(is(Output.PPROF)), () -> toPProf(arguments, out)),
            Case($(isIn(Output.JFR, Output.COLLAPSED)), () -> toGZIP(out)),
            Case($(is(Output.HEATMAP)), () -> toHeatmap(arguments, out)),
            Case($(isIn(Output.FLAME_GRAPH, Output.FLAME)), () -> toFlame(arguments, out)),
            Case($(isNull()), () -> toGZIP(out)));

        result.andFinally(file::delete);
        result.onFailure(cause -> Logger.error(cause, "It has not been possible to pipe the profiler result to the output stream."));
    }

    public void pipeTo(CheckedFunction0<OutputStream> consumer) {
        Try.of(consumer)
                .onFailure(cause -> Logger.error(cause, "It has not been possible to create the output stream."))
                .onSuccess(this::pipeTo);
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
        final var eventType = EventTypes.contains(command.eventType) ? command.eventType : Events.CPU;
        final var params = command.eventParams.appendAll(List.of(eventType).map(event -> "--" + event));
        return new Arguments(params.toJavaArray(String[]::new));
    }
}
