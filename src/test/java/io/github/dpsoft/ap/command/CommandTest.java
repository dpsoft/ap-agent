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
        assertEquals("start,jfr,event=alloc,interval=512k,file=/tmp/x.jfr", command.asFormatString("/tmp/x.jfr"));
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
