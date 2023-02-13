package io.github.dpsoft.ap.context.api;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class Labels {
    public final static Labels EMPTY = new Labels(Map.of());
    private final Map<String, String> _underlying;

    private Labels(Map<String, String> underlying) {
        this._underlying =  underlying;
    }

    public static Labels from(Map<String, String> underlying) {
        return new Labels(underlying);
    }

    public static Labels of(String key, String value) {
        return new Labels(Map.of(key, value));
    }

    public Labels withLabel(String key, String value) {
        var mergedMap = new HashMap<String, String>(_underlying.size() + 1);
        mergedMap.putAll(_underlying);
        mergedMap.put(key, value);

        return new Labels(mergedMap);
    }

    public Set<Label> all() {
        return _underlying.entrySet()
                .stream()
                .map(e -> new Label(e.getKey(), e.getValue()))
                .collect(Collectors.toSet());
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
