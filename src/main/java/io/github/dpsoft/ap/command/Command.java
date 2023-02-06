package io.github.dpsoft.ap.command;

import io.github.dpsoft.ap.config.AgentConfiguration;
import io.vavr.control.Option;
import one.profiler.Events;

import java.time.Duration;
import java.util.Arrays;
import java.util.Map;


public class Command {
    public final String eventType;
    public final Duration durationSeconds;
    public final String interval;
    public final String file;
    public final Output output;

    public static Command from(Map<String, String> params, AgentConfiguration.Handler configuration) {
        final var output = getOutput(params, configuration);
        final var eventType = getEventType(params, output);
        final var duration = getDuration(params);
        final var interval = params.get("interval");
        final var file = params.getOrDefault("file", "profile.jfr");

        return new Command(eventType, duration, interval, file, output);
    }

    private Command(String eventType, Duration durationSeconds, String interval, String file, Output output) {
        this.eventType = eventType;
        this.durationSeconds = durationSeconds;
        this.interval = interval;
        this.file = file;
        this.output = output;
    }

    private static Output getOutput(Map<String, String> params, AgentConfiguration.Handler configuration) {
        if (configuration.isGoMode()) return Output.PPROF;
        return Option
                .of(params.get("output"))
                .flatMap(Output::get)
                .getOrElse(Output.JFR);
    }

    private static String getEventType(Map<String, String> params, Output output) {
        if (Output.HOT_COLD == output) return Events.WALL;
        return params.getOrDefault("event", Events.ITIMER);
    }

    private static Duration getDuration(Map<String, String> params) {
        if (params.get("duration") != null) return Duration.ofSeconds(Long.parseLong(params.get("duration")));
        if (params.get("seconds") != null) return Duration.ofSeconds(Long.parseLong(params.get("seconds")));
        return Duration.ofSeconds(Long.parseLong("30")); // default value
    }

    public String asFormatString(String absolutePath) {
        final var sb = new StringBuilder().append("start,jfr,event=").append(eventType);
        if (interval != null) sb.append(",interval=").append(interval);
        sb.append(",file=").append(absolutePath);
        return sb.toString();
    }

    public Duration getDuration() {
        return durationSeconds;
    }

    public enum Output {
        PPROF("pprof"),
        JFR("jfr"),
        NFLX("nflx"),
        FLAME_GRAPH("flamegraph"),
        FLAME("flame"),
        COLLAPSED("collapsed"),
        HOT_COLD("hotcold"),
        FIREFOX_PROFILER("fp");

        private final String value;

        Output(String value) {
            this.value = value;
        }

        public static Option<Output> get(String value) {
            return Option.ofOptional(Arrays.stream(Output.values())
                    .filter(output -> output.value.equals(value))
                    .findFirst());
        }
    }
}