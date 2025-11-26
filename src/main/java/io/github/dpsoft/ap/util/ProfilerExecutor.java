package io.github.dpsoft.ap.util;

import io.github.dpsoft.ap.command.Command;
import io.github.dpsoft.ap.command.Command.Output;
import one.convert.*;
import one.jfr.JfrReader;
import one.profiler.AsyncProfiler;
import one.profiler.Events;
import org.tinylog.Logger;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

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
        try {
            this.file = File.createTempFile("ap-agent", ".jfr");
            this.file.deleteOnExit();
        } catch (IOException e) {
            throw new RuntimeException("It has not been possible to create a temporal file for JFR.", e);
        }
    }

    public ProfilerExecutor run() {
        try {
            profiler.execute(command.asFormatString(file.getAbsolutePath()));
            Thread.sleep(command.getDuration().toMillis());
            profiler.stop();
            return this;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void pipeTo(OutputStream out) {
        try {
            switch (command.output) {
                case PPROF:
                    toPProf(getArguments(command), out);
                    break;
                case JFR:
                case COLLAPSED:
                    toGZIP(out);
                    break;
                case FLAME_GRAPH:
                case FLAME:
                    toFlame(getArguments(command), out);
                    break;
                case HEATMAP:
                    toHeatmap(getArguments(command), out);
                    break;
                default:
                    toGZIP(out);
            }
        } catch (Exception e) {
            Logger.error(e, "It has not been possible to pipe the profiler result to the output stream.");
        } finally {
            try { file.delete(); } catch (Exception ignored) {}
        }
    }

    public void pipeTo(CheckedOutputStreamSupplier consumer) {
        try (OutputStream os = consumer.get()) {
            pipeTo(os);
        } catch (Exception e) {
            Logger.error(e, "It has not been possible to create the output stream.");
        }
    }

    private void toGZIP(OutputStream out){
        try (var bufferedFileReader = new BufferedInputStream(new FileInputStream(file));
             var outputStream = new GZIPOutputStream(out);
             var bufferedOutputStream = new BufferedOutputStream(outputStream)) {

            bufferedFileReader.transferTo(bufferedOutputStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void toFlame(Arguments arguments, OutputStream out) {
        try (var reader = new JfrReader(file.getAbsolutePath())) {
            var converter = new JfrToFlame(reader, arguments);
            converter.convert();
            converter.dump(out);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void toPProf(Arguments arguments, OutputStream out) {
        try (var reader = new JfrReader(file.getAbsolutePath()); var outputStream = new GZIPOutputStream(out)) {
            var converter = new JfrToPprof(reader, arguments);
            converter.convert();
            converter.dump(outputStream);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void toHeatmap(Arguments arguments, OutputStream out) {
        try (var reader = new JfrReader(file.getAbsolutePath())) {
            var converter = new JfrToHeatmap(reader, arguments);
            converter.convert();
            converter.dump(out);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Arguments getArguments(Command command) {
        final var eventType = EventTypes.contains(command.eventType) ? command.eventType : Events.CPU;
        final List<String> params = new ArrayList<>(command.eventParams);
        params.add("--" + eventType);
        return new Arguments(params.toArray(new String[0]));
    }

    @FunctionalInterface
    public interface CheckedOutputStreamSupplier {
        OutputStream get() throws Exception;
    }
}
