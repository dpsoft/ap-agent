package dpsoft.ap.command;

import one.profiler.Events;

import java.time.Duration;
import java.util.Map;

public class Command {
    public final String eventType;
    public final Duration durationSeconds;
    public final String interval;
    public final String file;

    public static Command from(Map<String, String> params) {
        var eventType = params.getOrDefault("event", Events.CPU);
        var duration = Duration.ofSeconds(Long.parseLong(params.getOrDefault("duration", "30")));
        var interval = params.getOrDefault("interval", "10000000");
        var file = params.getOrDefault("file", "profile.jfr");

        return new Command(eventType, duration, interval, file);
    }

    private Command(String eventType, Duration durationSeconds, String interval, String file) {
        this.eventType = eventType;
        this.durationSeconds = durationSeconds;
        this.interval = interval;
        this.file = file;
    }

    public String asFormatString(String absolutePath) {
        return String.format("start,jfr,event=%s,interval=%s,file=%s", eventType, interval, absolutePath);
    }

    public Duration getDuration() {
        return durationSeconds;
    }
}