package dpsoft.ap.command;

import dpsoft.ap.config.AgentConfiguration;
import one.profiler.Events;

import java.time.Duration;
import java.util.Map;

public class Command {
    public final String eventType;
    public final Duration durationSeconds;
    public final String interval;
    public final String file;
    public final String output;


    public static Command from(Map<String, String> params, AgentConfiguration.Handler configuration) {
        final var eventType = params.getOrDefault("event", Events.CPU);
        final var duration = getDuration(params);
        final var output = getOutput(params, configuration);
        final var interval = params.get("interval");
        final var file = params.getOrDefault("file", "profile.jfr");

        return new Command(eventType, duration, interval, file, output);
    }

    private static String getOutput(Map<String, String> params, AgentConfiguration.Handler configuration) {
        if(configuration.isGoMode()) return "pprof";
        return params.getOrDefault("output", "jfr");
    }

    private static Duration getDuration(Map<String, String> params) {
        if (params.get("duration") != null) return Duration.ofSeconds(Long.parseLong(params.get("duration")));
        if (params.get("seconds" ) != null) return Duration.ofSeconds(Long.parseLong(params.get("seconds")));
        return Duration.ofSeconds(Long.parseLong("30")); // default value
    }

    private Command(String eventType, Duration durationSeconds, String interval, String file, String output) {
        this.eventType = eventType;
        this.durationSeconds = durationSeconds;
        this.interval = interval;
        this.file = file;
        this.output = output;
    }

    public String asFormatString(String absolutePath) {
        final var sb =  new StringBuilder().append("start,jfr,event=").append(eventType);
        if (interval != null) sb.append(",interval=").append(interval);
        sb.append(",file=").append(absolutePath);

        return sb.toString();
    }

    public Duration getDuration() {
        return durationSeconds;
    }
}