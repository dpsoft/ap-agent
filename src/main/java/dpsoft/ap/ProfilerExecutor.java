package dpsoft.ap;

import dpsoft.ap.command.Command;
import io.vavr.control.Try;
import one.converter.Arguments;
import one.converter.FlameGraph;
import one.converter.jfr2flame;
import one.jfr.JfrReader;
import one.profiler.AsyncProfiler;
import org.tinylog.Logger;

import java.io.*;
import java.time.Duration;
import java.util.zip.GZIPOutputStream;

public class ProfilerExecutor {

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

//            pipeTo(out, command.output);
        });
    }

    public Try<Void> pipeTo(OutputStream out, String output) {
        if (output.equals("pprof")) {
               return Try.run(() -> {
                   try (var reader = new JfrReader(file.getAbsolutePath()); var outputStream = new GZIPOutputStream(out)) {
                       new one.converter.jfr2pprof(reader).dump(outputStream);
                   }
                   file.delete();
               });
        }
        if (output.equals("flamegraph")) {
            return Try.run(() -> {
                var args = new one.converter.Arguments();
                var flame = new FlameGraph(args);

                try (var reader = new JfrReader(file.getAbsolutePath())) {
                    new jfr2flame(reader, args).convert(flame);
                }

                try (var outputStream = new PrintStream(out)) {
                    flame.dump(outputStream);
                }
                file.delete();
            });
        }
        //default output is jfr
        return Try.run(() -> {
            try (var fileReader = new FileInputStream(file.getAbsolutePath()); var outputStream = new GZIPOutputStream(out)) {
                fileReader.transferTo(outputStream);
            }
            file.delete();
        });
    }
}
