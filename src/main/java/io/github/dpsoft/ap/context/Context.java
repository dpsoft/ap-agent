package io.github.dpsoft.ap.context;

import java.util.Map;

public final class Context {
    public final long contextId;
    public final Map<String, String> tags;

    public static Context of(long contextId, Map<String, String> tags) {
        return new Context(contextId, tags);
    }

    public static Context empty() {
        return new Context(0, Map.of());
    }
    
    public Context(long contextId, Map<String, String> tags) {
        this.contextId = contextId;
        this.tags = tags;
    }
}
