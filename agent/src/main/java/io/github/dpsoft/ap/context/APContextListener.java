package io.github.dpsoft.ap.context;

import io.github.dpsoft.ap.context.api.context.Context;
import io.github.dpsoft.ap.context.api.storage.ContextStorageListener;

/**
 * This listener is used to store the current context id in AsyncProfiler and restore it when the context is restored.
 */
public final class APContextListener implements ContextStorageListener {
    @Override
    public void onContextStored(Context context) {
        System.out.println("stored::AsyncProfiler.setContextId(" + context.get(Context.ContextID) + ")");
    }
    @Override
    public void onContextRestored(Context context) {
        System.out.println("restored::AsyncProfiler.setContextId(" + context.get(Context.ContextID) + ")");
    }
}
