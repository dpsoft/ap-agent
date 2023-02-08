package io.github.dpsoft.ap.util;

import io.github.dpsoft.ap.command.Command;
import io.github.dpsoft.ap.command.Command.Output;
import io.github.dpsoft.ap.converters.experimental.hotcold.HotColdFlameGraph;
import io.github.dpsoft.ap.converters.experimental.hotcold.jfr2hotcoldflame;
import io.vavr.collection.List;
import io.vavr.control.Try;
import one.converter.*;
import one.jfr.JfrReader;
import one.jfr.event.ExecutionSample;
import one.profiler.AsyncProfiler;
import one.profiler.Events;
import org.tinylog.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Paths;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

import static io.vavr.API.*;
import static io.vavr.Predicates.*;

import me.bechberger.jfrtofp.processor.SimpleProcessor;
import me.bechberger.jfrtofp.processor.Config;

public final class ProfilerExecutor {

    private static final Set<String> EventTypes = Set.of(Events.ALLOC, Events.CPU, Events.LOCK);

    private final AsyncProfiler profiler;
    private final File file;

    public static ProfilerExecutor with(AsyncProfiler profiler) {
        return new ProfilerExecutor(profiler);
    }

    private ProfilerExecutor(AsyncProfiler profiler) {
        this.profiler = profiler;
        this.file = Try.of(() -> File.createTempFile("ap-agent", ".jfr"))
                .onSuccess(File::deleteOnExit)
                .getOrElseThrow(() -> new RuntimeException("It has not been possible to create a temporal file for JFR."));
    }

    public Try<ProfilerExecutor> run(Command command) {
        return Try.of(() -> {
            profiler.execute(command.asFormatString(file.getAbsolutePath()));
            Thread.sleep(command.getDuration().toMillis());
            profiler.stop();
            return this;
        });
    }

    public void pipeTo(OutputStream out, Command command) {
        final var result = Match(command.output).of(
            Case($(is(Output.PPROF)), () -> toPProf(out)),
            Case($(is(Output.JFR)), () -> toJFR(out)),
            Case($(is(Output.NFLX)), () -> toFlameScope(out)),
            Case($(isIn(Output.FLAME_GRAPH, Output.FLAME, Output.COLLAPSED, Output.HOT_COLD)), () -> toFlame(command, out)),
            Case($(is(Output.FIREFOX_PROFILER)), () -> toFirefoxProfiler(out)),
            Case($(isNull()), () -> toJFR(out)));

        result.andFinally(file::delete);
        result.onFailure(cause -> Logger.error(cause, "It has not been possible to pipe the profiler result to the output stream."));
    }

    private Try<Void> toJFR(OutputStream out){
        return Try.run(() -> {
            try (var fileReader = new FileInputStream(file.getAbsolutePath()); var outputStream = new GZIPOutputStream(out)) {
                fileReader.transferTo(outputStream);
            }
        });
    }

    private Try<Void> toFirefoxProfiler(OutputStream out) {
        return Try.run(() -> {
            final var processor = new SimpleProcessor(new Config(), Paths.get(file.getAbsolutePath()));
            try (var outputStream = new PrintStream(out)) {
                processor.processZipped(outputStream);
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

    private Try<Void> toPProf(OutputStream out) {
        return Try.run(() -> {
            try (var reader = new JfrReader(file.getAbsolutePath()); var outputStream = new GZIPOutputStream(out)) {
                new jfr2pprof(reader).dump(outputStream);
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
