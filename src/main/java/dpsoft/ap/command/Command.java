package dpsoft.ap.command;

import one.profiler.Events;

import java.time.Duration;
import java.util.Map;

public class Command {
    public final String eventType;
    public final Duration durationSeconds;
    public final String interval;
    public final String file;
    public final String output;


    public static Command from(Map<String, String> params) {
        var eventType = params.getOrDefault("event", Events.CPU);
        var duration = getDuration(params);
        var interval = params.getOrDefault("interval", "10000000");
        var output = params.getOrDefault("output", "jfr");
        var file = params.getOrDefault("file", "profile.jfr");

        return new Command(eventType, duration, interval, file, output);
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
//        return String.format("start,jfr,event=%s,interval=%s,file=%s", eventType, interval, absolutePath);
        return String.format("start,jfr,event=%s,file=%s", eventType, absolutePath);
    }

    public Duration getDuration() {
        return durationSeconds;
    }
}