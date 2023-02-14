package io.github.dpsoft.ap.context.api.storage;

import io.github.dpsoft.ap.context.api.context.Context;

public interface ContextStorageListener {
    void onContextStored(Context context);
    void onContextRestored(Context context);
    enum Noop implements ContextStorageListener {
        INSTANCE;
        @Override
        public void onContextStored(Context context) {}
        @Override
        public void onContextRestored(Context context) {}
    }
}
