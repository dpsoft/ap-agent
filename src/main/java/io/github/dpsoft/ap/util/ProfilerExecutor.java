package io.github.dpsoft.ap.util;

import io.github.dpsoft.ap.command.Command;
import io.github.dpsoft.ap.command.Command.Output;
import io.github.dpsoft.ap.converters.experimental.hotcold.HotColdFlameGraph;
import io.github.dpsoft.ap.converters.experimental.hotcold.jfr2hotcoldflame;
import io.github.dpsoft.ap.converters.experimental.pprof.jfr2pprof;
import io.vavr.CheckedFunction0;
import io.vavr.collection.List;
import io.vavr.control.Try;
import one.converter.*;
import one.jfr.JfrReader;
import one.jfr.event.ExecutionSample;
import one.profiler.AsyncProfiler;
import one.profiler.Events;
import org.tinylog.Logger;

import java.io.*;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

import static io.vavr.API.*;
import static io.vavr.Predicates.*;

public final class ProfilerExecutor {

    private static final Set<String> EventTypes = Set.of(Events.ALLOC, Events.CPU, Events.LOCK);

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
        final var result = Match(command.output).of(
            Case($(is(Output.PPROF)), () -> toPProf(command, out)),
            Case($(is(Output.JFR)), () -> toJFR(out)),
            Case($(is(Output.NFLX)), () -> toFlameScope(out)),
            Case($(isIn(Output.FLAME_GRAPH, Output.FLAME, Output.COLLAPSED, Output.HOT_COLD)), () -> toFlame(command, out)),
            Case($(isNull()), () -> toJFR(out)));

        result.andFinally(file::delete);
        result.onFailure(cause -> Logger.error(cause, "It has not been possible to pipe the profiler result to the output stream."));
    }

    public void pipeTo(CheckedFunction0<OutputStream> consumer) {
        Try.of(consumer)
                .onFailure(cause -> Logger.error(cause, "It has not been possible to create the output stream."))
                .onSuccess(this::pipeTo);
    }

    private Try<Void> toJFR(OutputStream out){
        return Try.run(() -> {
            Logger.info("Writing JFR file to {}", file.getAbsolutePath());
            try (var fileReader = new FileInputStream(file.getAbsolutePath()); var outputStream = new BufferedOutputStream(out)) {
                fileReader.transferTo(outputStream);
            }
        });
    }

    private Try<Void> toFlame(Command command, OutputStream out) {
        if(Output.HOT_COLD == command.output) return toHotColdFlame(out);
        return Try.run(() -> {
            final var eventType = EventTypes.contains(command.eventType) ? command.eventType : Events.CPU;
            final var params = command.eventParams.appendAll(List.of(eventType).map(event -> "--" + event));
            final var arguments = new Arguments(params.toJavaArray(String[]::new));

            final var flame = (Output.COLLAPSED == command.output || arguments.collapsed) ? new CollapsedStacks(arguments) : new FlameGraph(arguments);

            try (var reader = new JfrReader(file.getAbsolutePath()); var outputStream = new PrintStream(out)) {
                new jfr2flame(reader, arguments).convert(flame);
                flame.dump(outputStream);
            }
        });
    }

    private Try<Void> toPProf(Command command, OutputStream out) {
        return Try.run(() -> {
            try (var reader = new JfrReader(file.getAbsolutePath()); var outputStream = new GZIPOutputStream(out)) {
                final var profileType = command.eventType.equals(Events.ITIMER) ? io.github.dpsoft.ap.converters.experimental.pprof.jfr2pprof.TYPE_CPU : command.eventType;
                new jfr2pprof(reader).dump(outputStream, profileType);
            }
        });
    }

    private Try<Void> toFlameScope(OutputStream out) {
        return Try.run(() -> {
            try (var reader = new JfrReader(file.getAbsolutePath()); var outputStream = new GZIPOutputStream(out)) {
                new jfr2nflx(reader).dump(outputStream);
            }
        });
    }

    private Try<Void> toHotColdFlame(OutputStream out) {
        return Try.run(() -> {
            final var flame = new HotColdFlameGraph("--threads", "--lines", "--hotcold", "--title", "HotCold Flame Graph");
            try (var reader = new JfrReader(file.getAbsolutePath()); var outputStream = new PrintStream(out)) {
                new jfr2hotcoldflame(reader).convert(flame, true, false, true, false, ExecutionSample.class);
                flame.dump(outputStream);
            }
        });
    }
}
