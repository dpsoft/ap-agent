package io.github.dpsoft.ap.util;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StrSubstitutorTest {

    @Test
    void replacesKnownVariables() {
        final var substitutor = new StrSubstitutor(Map.of("a", "1", "b", "two"));
        assertEquals("x 1 y two z 1", substitutor.replace("x ${a} y ${b} z ${a}"));
    }

    @Test
    void leavesTextWithoutVariablesUntouched() {
        assertEquals("plain $text {here}", new StrSubstitutor(Map.of()).replace("plain $text {here}"));
    }

    @Test
    void unknownVariableCurrentlyThrows() {
        assertThrows(NullPointerException.class, () -> new StrSubstitutor(Map.of()).replace("${missing}"));
    }
}
