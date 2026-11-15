package io.github.dpsoft.ap.command;

import io.github.dpsoft.ap.command.Command.GOProfileTypes;
import io.github.dpsoft.ap.command.Command.Output;
import io.github.dpsoft.ap.config.AgentConfiguration;
import one.profiler.Events;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CommandTest {
    private static final AgentConfiguration CONFIG = AgentConfiguration.instance();

    private static Command command(Map<String, String> params) {
        return Command.from("profile", params, CONFIG);
    }

    @Test
    void defaults() {
        final var command = command(Map.of());
        assertEquals(Output.JFR, command.output);
        assertEquals(Events.CTIMER, command.eventType);
        assertEquals(Duration.ofSeconds(30), command.getDuration());
        assertEquals("10000000", command.interval);
        assertEquals("profile.jfr", command.file);
        assertTrue(command.eventParams.isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"pprof,PPROF", "jfr,JFR", "flamegraph,FLAME_GRAPH", "flame,FLAME", "heatmap,HEATMAP", "collapsed,COLLAPSED"})
    void parsesOutput(String value, Output expected) {
        assertEquals(expected, command(Map.of("output", value)).output);
    }

    @Test
    void unknownOutputFallsBackToJfr() {
        assertEquals(Output.JFR, command(Map.of("output", "svg")).output);
    }

    @Test
    void eventIsTakenFromParams() {
        assertEquals("alloc", command(Map.of("event", "alloc")).eventType);
        assertEquals("nativemem", command(Map.of("event", "nativemem", "output", "flame")).eventType);
    }

    @Test
    void durationAndSecondsAliases() {
        assertEquals(Duration.ofSeconds(5), command(Map.of("duration", "5")).getDuration());
        assertEquals(Duration.ofSeconds(7), command(Map.of("seconds", "7")).getDuration());
        assertEquals(Duration.ofSeconds(5), command(Map.of("duration", "5", "seconds", "7")).getDuration());
    }

    @ParameterizedTest
    @CsvSource({"abc", "1O", "0", "-5", "''"})
    void invalidDurationIsRejected(String value) {
        final var error = assertThrows(IllegalArgumentException.class, () -> command(Map.of("duration", value)));
        assertTrue(error.getMessage().contains("duration"), error.getMessage());
    }

    @Test
    void durationIsTrimmed() {
        assertEquals(Duration.ofSeconds(3), command(Map.of("seconds", " 3 ")).getDuration());
    }

    @ParameterizedTest
    @CsvSource({"cpu,10000000", "ctimer,10000000", "itimer,10000000", "wall,10000000"})
    void configuredIntervalAppliesToTimeSampledEvents(String event, String expected) {
        assertEquals(expected, command(Map.of("event", event)).interval);
    }

    @ParameterizedTest
    @CsvSource({"alloc", "lock", "nativemem"})
    void otherEventsKeepTheProfilerDefaults(String event) {
        final var command = command(Map.of("event", event));
        assertNull(command.interval);
        assertEquals("start,jfr,event=" + event + ",file=/tmp/x.jfr", command.asFormatString("/tmp/x.jfr"));
        // async-profiler ignores `interval` for these events: it is passed through their own option, normalized
        final var normalized = event.equals("lock") ? "1000000" : "1048576"; // 1m: one millisecond, or one MiB
        assertEquals("start,jfr,event=" + event + "," + event + "=" + normalized + ",file=/tmp/x.jfr",
                command(Map.of("event", event, "interval", "1m")).asFormatString("/tmp/x.jfr"));
    }

    @ParameterizedTest
    @CsvSource({"alloc,abc", "alloc,-1", "alloc,10ms", "alloc,10s", "nativemem,1.5m", "lock,-1", "lock,10k", "lock,1g",
            "cpu,abc", "cpu,-5", "cpu,0x10", "wall,99999999999999999999",
            // overflow once multiplied by the unit
            "alloc,8589934592g", "nativemem,8589934592g", "lock,9223372036855ms", "cpu,9223372037s"})
    void invalidIntervalsAreRejected(String event, String interval) {
        // never let async-profiler silently fall back to CPU profiling
        final var error = assertThrows(IllegalArgumentException.class, () -> command(Map.of("event", event, "interval", interval)));
        assertTrue(error.getMessage().contains("interval"), error.getMessage());
    }

    /** Normalized to base units in decimal: async-profiler would read leading zeros as octal and overflow silently. */
    @ParameterizedTest
    @CsvSource({"alloc,0,0", "alloc,512k,524288", "alloc,2M,2097152", "alloc,64kb,65536", "alloc,08,8", "nativemem,1g,1073741824",
            "lock,0,0", "lock,1ms,1000000", "lock,1m,1000000", "lock,10US,10000", "lock,500ns,500", "lock,09ms,9000000",
            "cpu,1000000,1000000", "wall,5ms,5000000", "itimer,1s,1000000000"})
    void validIntervalsAreNormalized(String event, String interval, String normalized) {
        assertEquals(normalized, command(Map.of("event", event, "interval", interval)).interval);
    }

    @Test
    void intervalFromParamsOverridesConfiguration() {
        assertEquals("1000", command(Map.of("interval", "1000")).interval);
    }

    @Test
    void fileFromParams() {
        assertEquals("out.jfr", command(Map.of("file", "out.jfr")).file);
    }

    @Test
    void paramsAreSplitPrefixedAndKeyValuesExpanded() {
        final var command = command(Map.of("params", "total,include=com.foo.*,minwidth=1"));
        assertEquals(List.of("--total", "--include", "com.foo.*", "--minwidth", "1"), command.eventParams);
    }

    @Test
    void spanFiltersKeepCommasAndFollowConverterParams() {
        final var command = command(Map.of("params", "total", "tag", "SparkTask:.*,taskAttemptId=123,.*", "latency", "50"));
        assertEquals(List.of("--total", "--tag", "SparkTask:.*,taskAttemptId=123,.*", "--latency", "50"),
                command.eventParams);
    }

    @Test
    void emptySpanFiltersAreIgnored() {
        assertTrue(command(Map.of("tag", "", "latency", "")).eventParams.isEmpty());
    }

    @Test
    void invalidLatencyIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> command(Map.of("latency", "slow")));
        assertThrows(IllegalArgumentException.class, () -> command(Map.of("latency", "-1")));
        assertThrows(IllegalArgumentException.class, () -> command(Map.of("latency", "9223372036855")));
        assertEquals(List.of("--latency", "9223372036854"), command(Map.of("latency", "9223372036854")).eventParams);
    }

    @Test
    void asFormatStringBuildsStartCommand() {
        final var command = command(Map.of("event", "alloc", "interval", "512k"));
        assertEquals("start,jfr,event=alloc,alloc=524288,file=/tmp/x.jfr", command.asFormatString("/tmp/x.jfr"));
    }

    @ParameterizedTest
    @CsvSource({"jfr,true", "pprof,true", "flame,false", "flamegraph,false", "heatmap,false", "collapsed,false"})
    void shouldCompress(String output, boolean expected) {
        assertEquals(expected, command(Map.of("output", output)).shouldCompress());
    }

    @ParameterizedTest
    @CsvSource({"profile,ctimer", "allocs,alloc", "block,lock", "unknown,ctimer"})
    void pprofOutputMapsEventFromPathSegment(String segment, String expectedEvent) {
        final var command = Command.from(segment, Map.of("output", "pprof"), CONFIG);
        assertEquals(expectedEvent, command.eventType);
    }

    @Test
    void pprofOutputHonoursEventParameter() {
        assertEquals("nativemem", command(Map.of("output", "pprof", "event", "nativemem")).eventType);
    }

    @Test
    void outputAndGoTypeLookups() {
        assertEquals(Output.HEATMAP, Output.get("heatmap").get());
        assertTrue(Output.get("nope").isEmpty());
        assertEquals(GOProfileTypes.BLOCK, GOProfileTypes.get("block").get());
        assertEquals(Events.LOCK, GOProfileTypes.BLOCK.event());
        assertTrue(GOProfileTypes.get("heap").isEmpty());
    }
}
