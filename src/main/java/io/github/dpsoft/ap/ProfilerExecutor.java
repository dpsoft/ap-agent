package io.github.dpsoft.ap;

import io.github.dpsoft.ap.command.Command;
import io.github.dpsoft.ap.config.AgentConfiguration;
import io.github.dpsoft.ap.converters.experimental.hotcold.HotColdFlameGraph;
import io.github.dpsoft.ap.converters.experimental.hotcold.jfr2hotcoldflame;
import io.vavr.control.Try;
import one.converter.*;
import one.jfr.JfrReader;
import one.jfr.event.ExecutionSample;
import one.profiler.AsyncProfiler;
import org.tinylog.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.zip.GZIPOutputStream;

import static io.vavr.API.*;
import static io.vavr.Predicates.*;

public class ProfilerExecutor {

    private final AsyncProfiler profiler;
    private final AgentConfiguration.Handler configuration;
    private final File file;

    public static ProfilerExecutor with(AsyncProfiler profiler, AgentConfiguration.Handler configuration) {
        return new ProfilerExecutor(profiler, configuration);
    }

    private ProfilerExecutor(AsyncProfiler profiler, AgentConfiguration.Handler configuration) {
        this.configuration = configuration;
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

    public void pipeTo(OutputStream out, String output) {
        final var result = Match(output).of(
                Case($(is("pprof")), () -> toPProf(out)),
                Case($(is("jfr")), () ->  toJFR(out)),
                Case($(is("nflx")), () ->  toFlameScope(out)),
                Case($(isIn("flamegraph", "flame", "collapsed", "hotcold")),(type) -> toFlame(type, out)),
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

    private Try<Void> toFlame(String type, OutputStream out) {
        if("hotcold".equals(type)) return toHotColdFlame(out);
        return Try.run(() -> {
            final var args = new Arguments("--cpu", "--lines");
            final var flame = "collapsed".equals(type) ? new CollapsedStacks(args) : new FlameGraph(args);

            try (var reader = new JfrReader(file.getAbsolutePath()); var outputStream = new PrintStream(out)) {
                new jfr2flame(reader, args).convert(flame);
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
