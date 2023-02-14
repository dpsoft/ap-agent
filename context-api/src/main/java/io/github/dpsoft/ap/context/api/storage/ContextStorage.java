package io.github.dpsoft.ap.context.api.storage;

import io.github.dpsoft.ap.context.api.context.Context;

import java.util.ServiceLoader;

/**
 * This class is used to store and retrieve the current context.
 */
public final class ContextStorage {
    public static final ContextStorage INSTANCE = new ContextStorage();

    private final Storage storage = buildStorage();

    public Context currentContext() { return storage.current();}

    public Storage.Scope storeContext(Context context) { return storage.store(context); }

    private Storage buildStorage() {
        final var serviceLoader = ServiceLoader.load(ContextStorageListener.class);
        final var contextStorageListener = serviceLoader.findFirst().orElse(ContextStorageListener.Noop.INSTANCE);

        return new ThreadLocalStorage(contextStorageListener);
    }
}
