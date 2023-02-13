package io.github.dpsoft.ap.context.api;

import java.util.HashMap;
import java.util.Map;

public final class Context {
    public static final Context EMPTY = new Context(Map.of(), Labels.EMPTY);

    public static final Context.Key<Long> ContextID = new Context.Key<>("contextId", 0L);

    private final Map<String, Object> _underlying;

    private final Labels labels;

    public static Context of(Labels labels) {
        return new Context(Map.of(),  labels);
    }

    public static <T> Context of(Context.Key<T> key, T value) {
        return new Context(Map.of(key.name, value), Labels.EMPTY);
    }

    public static <T> Context of(Context.Key<T> key, T value, Labels labels) {
        return new Context(Map.of(key.name, value), labels);
    }

    public static Context empty() {
        return EMPTY;
    }

    private Context(Map<String, Object> underling, Labels labels) {
        this.labels = labels;
        this._underlying = underling;
    }

    public <T> Context withEntry(Context.Key<T> key, T value) {
        var x = new HashMap<String, Object>(_underlying.size() + 1);
        x.putAll(_underlying);
        x.put(key.name, value);

        return new Context(x, labels);
    }

    public <T> T get(Context.Key<T> key) {
        return (T) _underlying.getOrDefault(key.name, key.emptyValue);
    }

    public static <T> Context.Key<T> key(String name, T emptyValue) {
        return new Context.Key<>(name, emptyValue);
    }

    public static class Key<T> {
        public final String name;
        public final T emptyValue;

        public Key(String name, T emptyValue) {
            this.name = name;
            this.emptyValue = emptyValue;
        }
    }
}