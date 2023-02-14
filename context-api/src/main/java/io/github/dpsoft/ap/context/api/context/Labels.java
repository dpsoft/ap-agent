package io.github.dpsoft.ap.context.api.context;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class Labels {
    public final static Labels EMPTY = new Labels(Map.of());
    private final Map<String, String> underlying;

    private Labels(Map<String, String> underlying) {
        this.underlying =  underlying;
    }

    public static Labels from(Map<String, String> underlying) {
        return new Labels(underlying);
    }

    public static Labels of(String key, String value) {
        return new Labels(Map.of(key, value));
    }

    public Labels withLabel(String key, String value) {
        var mergedMap = new HashMap<String, String>(underlying.size() + 1);
        mergedMap.putAll(underlying);
        mergedMap.put(key, value);

        return new Labels(mergedMap);
    }

    public Set<Label> all() {
        return underlying.entrySet()
                .stream()
                .map(e -> new Label(e.getKey(), e.getValue()))
                .collect(Collectors.toSet());
    }

    public Optional<Label> get(String key) {
        return Optional
                .ofNullable(underlying.get(key))
                .map(value -> new Label(key, value));
    }

    public static class Label {
        public final String key;
        public final String value;
        public Label(String key, String value) {
            this.key = key;
            this.value = value;
        }
    }
}
