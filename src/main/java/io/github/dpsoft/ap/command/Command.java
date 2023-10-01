package io.github.dpsoft.ap.command;

import io.github.dpsoft.ap.config.AgentConfiguration;
import io.vavr.collection.Array;
import io.vavr.collection.List;
import io.vavr.control.Option;
import one.profiler.Events;

import java.time.Duration;
import java.util.Arrays;
import java.util.Map;

public class Command {
    public final String eventType;
    public final Duration durationSeconds;
    public final List<String> eventParams;
    public final String interval;
    public final String file;
    public final Output output;

    public static Command from(String operation, Map<String, String> params){
        return from(operation, params, AgentConfiguration.instance());
    }

    public static Command from(String operation, Map<String, String> params, AgentConfiguration configuration) {
        final var output = getOutput(params, configuration.handler);
        final var eventType = getEventType(operation, params, output);
        final var duration = getDuration(params);
        final var eventParams = getEventParams(params);
        final var interval = getInterval(params, configuration.profiler);
        final var file = params.getOrDefault("file", "profile.jfr");

        return new Command(eventType, duration, eventParams, interval, file, output);
    }

    private static String getInterval(Map<String, String> params, AgentConfiguration.Profiler configuration) {
        if(params.get("interval") != null) return params.get("interval");
        return configuration.interval;
    }

    private Command(String eventType, Duration durationSeconds, List<String> eventParams, String interval, String file, Output output) {
        this.eventType = eventType;
        this.durationSeconds = durationSeconds;
        this.eventParams = eventParams;
        this.interval = interval;
        this.file = file;
        this.output = output;
    }

    private static Output getOutput(Map<String, String> params, AgentConfiguration.Handler configuration) {
        if (params.get("output") == null && configuration.isGoMode()) return Output.PPROF;
        return Option
                .of(params.get("output"))
                .flatMap(Output::get)
                .getOrElse(Output.JFR);
    }

    private static String getEventType(String segment, Map<String, String> params, Output output) {
        if (Output.PPROF == output) return GOProfileTypes.get(segment).getOrElse(GOProfileTypes.PROFILE).event();
        if (Output.HOT_COLD == output) return Events.WALL;
        return params.getOrDefault("event", Events.ITIMER);
    }

    private static Duration getDuration(Map<String, String> params) {
        if (params.get("duration") != null) return Duration.ofSeconds(Long.parseLong(params.get("duration")));
        if (params.get("seconds") != null) return Duration.ofSeconds(Long.parseLong(params.get("seconds")));
        return Duration.ofSeconds(Long.parseLong("30")); // default value
    }

    private static List<String> getEventParams(Map<String, String> parameters) {
        if (parameters.get("params") == null) return List.empty();
        return List.of(parameters.get("params").split(","))
                .map(param -> "--" + param)
                .map(param -> param.replace("=", ","))
                .map(param -> param.split(","))
                .flatMap(Array::of);
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

    public boolean shouldCompress() {
        return Output.JFR == output || Output.PPROF == output || Output.NFLX == output;
    }

    public enum Output {
        PPROF("pprof"),
        JFR("jfr"),
        NFLX("nflx"),
        FLAME_GRAPH("flamegraph"),
        FLAME("flame"),
        COLLAPSED("collapsed"),
        HOT_COLD("hotcold");

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

    public enum GOProfileTypes {
        PROFILE("profile", Events.ITIMER),
        ALLOC("allocs", Events.ALLOC),
        BLOCK("block", Events.LOCK);

        private final String profile;
        private final String event;

        GOProfileTypes(String profile, String event) {
            this.profile = profile;
            this.event = event;
        }

        public String event() {
            return event;
        }

        public static Option<GOProfileTypes> get(String value) {
            return Option.ofOptional(Arrays.stream(GOProfileTypes.values())
                    .filter(profile -> profile.profile.equals(value))
                    .findFirst());
        }
    }
}