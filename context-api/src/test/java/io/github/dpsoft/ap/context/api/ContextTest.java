package io.github.dpsoft.ap.context.api;

import org.junit.jupiter.api.Test;

public class ContextTest {

    @Test
    public void test() {
        var context = Context.of(Labels.of("a", "b"));
        assert context.get(Context.ContextID) == 0L;
    }

    @Test
    public void test2() {
        var stringKey = Context.key("a", "b");
        var context = Context.of(stringKey, "c");
        assert context.get(stringKey).equals("c");
    }

    @Test
    public void test3() {
        var stringKey = Context.key("a", "b");
        var context = Context.of(stringKey, "c", Labels.of("a", "b"));
        assert context.get(stringKey).equals("c");
    }

    @Test
    public void test4() {
        var stringKey = Context.key("a", "b");
        var context = Context.of(stringKey, "c", Labels.of("a", "b")).withEntry(stringKey, "d");
        assert context.get(stringKey).equals("d");
    }

    @Test
    public void test5() {
        var stringKey = Context.key("a", "b");
        var context = Context.of(stringKey, "c", Labels.of("a", "b")).withEntry(stringKey, "d");
        assert context.labels().get("a").get().key.equals("a");
    }
}
