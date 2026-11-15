package io.github.dpsoft.ap.command;

import io.github.dpsoft.ap.config.AgentConfiguration;
import one.profiler.Events;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class Command {
    public final String eventType;
    public final Duration durationSeconds;
    public final List<String> eventParams;
    public final String interval;
    public final String file;
    public final Output output;

    public static Command from(String operation, Map<String, String> params, AgentConfiguration configuration) {
        final var output = getOutput(params, configuration.handler);
        final var eventType = getEventType(operation, params, output);
        final var duration = getDuration(params);
        final var eventParams = getEventParams(params);
        final var interval = getInterval(params, eventType, configuration.profiler);
        final var file = params.getOrDefault("file", "profile.jfr");

        return new Command(eventType, duration, eventParams, interval, file, output);
    }

    /** Events whose interval is a time, which the configured sampling interval applies to. */
    private static final java.util.Set<String> TIME_SAMPLED_EVENTS = java.util.Set.of(Events.CPU, Events.CTIMER, Events.ITIMER, Events.WALL);

    /**
     * An explicit {@code interval} always wins. Otherwise the configured interval (a time) only applies to CPU and
     * wall-clock events; alloc, lock and nativemem keep async-profiler's own defaults.
     */
    private static String getInterval(Map<String, String> params, String eventType, AgentConfiguration.Profiler configuration) {
        if (params.get("interval") != null) return params.get("interval");
        return TIME_SAMPLED_EVENTS.contains(eventType) ? configuration.interval : null;
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
        return Optional.ofNullable(params.get("output"))
                .flatMap(Output::get)
                .orElse(Output.JFR);
    }

    /**
     * An explicit {@code event} always wins. Without it, pprof output follows the Go endpoint naming
     * (profile -> cpu, allocs -> alloc, block -> lock) and everything else profiles CPU.
     */
    private static String getEventType(String segment, Map<String, String> params, Output output) {
        final var event = params.get("event");
        if (event != null && !event.isEmpty()) return event;
        if (Output.PPROF == output) return GOProfileTypes.get(segment).orElse(GOProfileTypes.PROFILE).event();
        return Events.CTIMER;
    }

    private static Duration getDuration(Map<String, String> params) {
        var raw = params.get("duration");
        if (raw == null) raw = params.get("seconds");
        if (raw == null) return Duration.ofSeconds(30); // default value
        final long seconds;
        try {
            seconds = Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("duration must be a number of seconds: " + raw);
        }
        if (seconds <= 0 || seconds > Long.MAX_VALUE / 1000) {
            throw new IllegalArgumentException("duration must be between 1 and " + Long.MAX_VALUE / 1000 + " seconds: " + raw);
        }
        return Duration.ofSeconds(seconds);
    }

    private static List<String> getEventParams(Map<String, String> parameters) {
        final var params = new ArrayList<String>();
        addConverterParams(parameters, params);
        addSpanFilters(parameters, params);
        return Collections.unmodifiableList(params);
    }

    /** {@code params=total,include=com.foo.*} becomes {@code --total --include com.foo.*}. */
    private static void addConverterParams(Map<String, String> parameters, List<String> params) {
        if (parameters.get("params") == null) return;
        for (var param : parameters.get("params").split(",")) {
            params.addAll(Arrays.asList(("--" + param).replace("=", ",").split(",")));
        }
    }

    /**
     * Span filters (async-profiler 4.5+ Span API): keep only the samples recorded inside spans of the same thread
     * whose tag fully matches {@code tag} (a regex, which may contain commas) and/or that lasted at least
     * {@code latency} milliseconds. They get dedicated parameters because {@code params} is split on commas.
     */
    private static void addSpanFilters(Map<String, String> parameters, List<String> filters) {
        final var tag = parameters.get("tag");
        if (tag != null && !tag.isEmpty()) filters.addAll(List.of("--tag", tag));
        final var latency = parameters.get("latency");
        if (latency != null && !latency.isEmpty()) {
            final long millis;
            try {
                millis = Long.parseLong(latency.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("latency must be a number of milliseconds: " + latency);
            }
            // the converter works in nanoseconds: reject values that would overflow and silently disable the filter
            if (millis < 0 || millis > Long.MAX_VALUE / 1_000_000) {
                throw new IllegalArgumentException("latency must be between 0 and " + Long.MAX_VALUE / 1_000_000 + " ms: " + latency);
            }
            filters.addAll(List.of("--latency", Long.toString(millis)));
        }
    }

    /**
     * The async-profiler start command. async-profiler only applies {@code interval} to CPU and wall-clock events: the
     * sampling of alloc, lock and nativemem has its own options, so an explicit interval is passed through them.
     */
    public String asFormatString(String absolutePath) {
        final var sb = new StringBuilder().append("start,jfr,event=").append(eventType);
        if (interval != null) sb.append(',').append(intervalOption(eventType)).append('=').append(interval);
        sb.append(",file=").append(absolutePath);
        return sb.toString();
    }

    private static String intervalOption(String eventType) {
        switch (eventType) {
            case Events.ALLOC: return "alloc";        // bytes
            case Events.LOCK: return "lock";          // total wait time threshold
            case "nativemem": return "nativemem";     // bytes
            default: return "interval";
        }
    }

    public Duration getDuration() {
        return durationSeconds;
    }

    public boolean shouldCompress() {
        return Output.JFR == output || Output.PPROF == output;
    }

    public enum Output {
        PPROF("pprof"),
        JFR("jfr"),
        FLAME_GRAPH("flamegraph"),
        FLAME("flame"),
        HEATMAP("heatmap"),
        COLLAPSED("collapsed");

        private final String value;

        Output(String value) {
            this.value = value;
        }

        public static Optional<Output> get(String value) {
            return Arrays.stream(Output.values())
                    .filter(output -> output.value.equals(value))
                    .findFirst();
        }
    }

    public enum GOProfileTypes {
        PROFILE("profile", Events.CTIMER),
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

        public static Optional<GOProfileTypes> get(String value) {
            return Arrays.stream(GOProfileTypes.values())
                    .filter(profile -> profile.profile.equals(value))
                    .findFirst();
        }
    }
}