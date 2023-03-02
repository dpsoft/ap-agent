package io.github.dpsoft.ap.context.api.storage;


import io.github.dpsoft.ap.context.api.context.Context;

/**
 * Default implementation of {@link Storage} that stores the context in a {@link ThreadLocal}.
 */
public class ThreadLocalStorage implements Storage {
    private final ThreadLocal<Context> tls = ThreadLocal.withInitial(() -> Context.EMPTY);

    private final ContextStorageListener storageListener;

    public ThreadLocalStorage(ContextStorageListener storageListener) {
        this.storageListener = storageListener;
    }

    @Override
    public Context current() { return tls.get(); }

    @Override
    public Scope store(Context context) {
        final var previous = tls.get();
        tls.set(context);
        storageListener.onContextStored(context);
        return new Scope() {
            @Override
            public Context context() { return context; }

            @Override
            public void close() {
                tls.set(previous);
                storageListener.onContextRestored(previous);
            }
        };
    }
}
