package io.github.dpsoft.ap.context.api;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LabelsTest {
        @Test
         void test() {
            final var labels = Labels.of("a", "b").withLabel("c", "d");
            assertEquals(2, labels.all().size());
        }

        @Test
         void test2() {
            final var labels = Labels.from(Map.of("a", "b", "c", "d"));
            assertEquals(2, labels.all().size());
        }

        @Test
         void test3() {
            final var labels = Labels.EMPTY;
            assertEquals(0, labels.all().size());
        }

        @Test
         void test4() {
            final var labels =  Labels.from(Map.of("a", "b", "c", "d")).withLabel("e", "f");
            assertEquals(3, labels.all().size());
        }
}