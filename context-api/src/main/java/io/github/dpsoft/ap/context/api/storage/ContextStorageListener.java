package io.github.dpsoft.ap.context.api.storage;

import io.github.dpsoft.ap.context.api.context.Context;

/**
 * This interface is used to listen to context storage events like when a context is stored or restored.
 */
public interface ContextStorageListener {
    void onContextStored(Context context);
    void onContextRestored(Context context);
    /**
     * Noop implementation of {@link ContextStorageListener}.
     */
    enum Noop implements ContextStorageListener {
        INSTANCE;
        @Override
        public void onContextStored(Context context) {}
        @Override
        public void onContextRestored(Context context) {}
    }
}
