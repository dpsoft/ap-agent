package dpsoft.ap;

import dpsoft.ap.command.Command;
import dpsoft.ap.config.AgentConfiguration;
import io.vavr.control.Try;
import one.converter.Arguments;
import one.converter.CollapsedStacks;
import one.converter.FlameGraph;
import one.converter.jfr2flame;
import one.jfr.JfrReader;
import one.profiler.AsyncProfiler;
import org.tinylog.Logger;

import static io.vavr.API.*;
import static io.vavr.Predicates.*;

import java.io.*;
import java.util.zip.GZIPOutputStream;

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
        var result = Match(output).of(
                Case($(is("pprof")), () -> toPProf(out)),
                Case($(is("jfr")), () ->  toJFR(out)),
                Case($(isIn("flamegraph", "flame", "collapsed")),(type) -> toFlame(type, out)),
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
                new one.converter.jfr2pprof(reader).dump(outputStream);
            }
        });
    }
}
