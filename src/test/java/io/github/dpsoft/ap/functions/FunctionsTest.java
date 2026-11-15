package io.github.dpsoft.ap.functions;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FunctionsTest {

    @Test
    void splitQueryParamsReturnsEmptyMapWithoutQuery() {
        assertTrue(Functions.splitQueryParams(URI.create("http://localhost/profiler/profile")).isEmpty());
    }

    @Test
    void splitQueryParamsParsesPairs() {
        final var params = Functions.splitQueryParams(URI.create("http://h/p?event=cpu&duration=10&output=flame"));
        assertEquals(Map.of("event", "cpu", "duration", "10", "output", "flame"), params);
    }

    @Test
    void splitQueryParamsDecodesKeysAndValues() {
        final var params = Functions.splitQueryParams(URI.create("http://h/p?params=include%3Dcom.foo.*%2Creverse&a%20b=c%20d"));
        assertEquals("include=com.foo.*,reverse", params.get("params"));
        assertEquals("c d", params.get("a b"));
    }

    @Test
    void splitQueryParamsDecodesExactlyOnce() {
        final var params = Functions.splitQueryParams(URI.create("http://h/p?tag=fixture%3A.%2B&t2=orders%26users&t3=100%25done&t4=a%3Db"));
        assertEquals("fixture:.+", params.get("tag"));
        assertEquals("orders&users", params.get("t2"));
        assertEquals("100%done", params.get("t3"));
        assertEquals("a=b", params.get("t4"));
        assertEquals(4, params.size());
    }

    @Test
    void splitQueryParamsKeepsLiteralPlus() {
        assertEquals("GET .+", Functions.splitQueryParams(URI.create("http://h/p?tag=GET%20.+")).get("tag"));
    }

    @Test
    void splitQueryParamsKeepsLastValueOfRepeatedKey() {
        assertEquals("2", Functions.splitQueryParams(URI.create("http://h/p?d=1&d=2")).get("d"));
    }

    @Test
    void splitQueryParamsAcceptsEmptyValue() {
        assertEquals("", Functions.splitQueryParams(URI.create("http://h/p?event=")).get("event"));
    }

    @Test
    void splitQueryParamsKeepsEverythingAfterTheFirstEquals() {
        assertEquals("a=b", Functions.splitQueryParams(URI.create("http://h/p?k=a=b")).get("k"));
    }

    @Test
    @Disabled("Known bug (phase 5, PR #57): a parameter without '=' throws StringIndexOutOfBoundsException")
    void splitQueryParamsToleratesParameterWithoutValue() {
        final var params = Functions.splitQueryParams(URI.create("http://h/p?debug&event=cpu"));
        assertEquals("cpu", params.get("event"));
    }

    @Test
    void splitQueryParamsCurrentlyFailsOnParameterWithoutValue() {
        assertThrows(StringIndexOutOfBoundsException.class,
                () -> Functions.splitQueryParams(URI.create("http://h/p?debug")));
    }

    @Test
    void padStringPadsToLength() {
        assertEquals("ab   ", Functions.padString("ab", 5));
    }

    @Test
    void padStringKeepsLongerStrings() {
        assertEquals("abcdef", Functions.padString("abcdef", 3));
        assertEquals("abc", Functions.padString("abc", 3));
    }

    @Test
    void lastSegmentReturnsTextAfterLastSlash() {
        assertEquals("profile", Functions.lastSegment("/profiler/profile"));
        assertEquals("allocs", Functions.lastSegment("/debug/pprof/allocs"));
        assertEquals("", Functions.lastSegment("/debug/pprof/"));
        assertEquals("plain", Functions.lastSegment("plain"));
    }
}
