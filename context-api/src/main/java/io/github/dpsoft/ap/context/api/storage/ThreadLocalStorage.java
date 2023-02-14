package io.github.dpsoft.ap.context.api.storage;


import io.github.dpsoft.ap.context.api.context.Context;

/**
 * Default implementation of {@link Storage} that stores the context in a {@link ThreadLocal}.
 */
public class ThreadLocalStorage implements Storage {
    private final ThreadLocal<Context> tls = ThreadLocal.withInitial(() -> Context.EMPTY);

    private final ContextStorageListener listener;

    public ThreadLocalStorage(ContextStorageListener listener) {
        this.listener = listener;
    }

    @Override
    public Context current() { return tls.get(); }

    @Override
    public Scope store(Context context) {
        final var previous = tls.get();
        tls.set(context);
        listener.onContextStored(context);
        return new Scope() {
            @Override
            public Context context() { return context; }

            @Override
            public void close() {
                tls.set(previous);
                listener.onContextRestored(previous);
            }
        };
    }
}
