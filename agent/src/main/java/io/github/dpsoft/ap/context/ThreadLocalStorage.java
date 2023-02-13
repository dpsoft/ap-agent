package io.github.dpsoft.ap.context;

import io.github.dpsoft.ap.context.api.Context;
import io.github.dpsoft.ap.context.api.Storage;
import org.tinylog.Logger;

/**
 * Default implementation of {@link Storage} that stores the context in a {@link ThreadLocal}.
 */
public class ThreadLocalStorage implements Storage {
    private final ThreadLocal<Context> tls = ThreadLocal.withInitial(Context::empty);

    @Override
    public Context current() { return tls.get(); }

    @Override
    public Scope store(Context context) {
        final var previous = tls.get();
        tls.set(context);
        Logger.info("store::AsyncProfiler.setContextId(" + context.get(Context.ContextID) + ")");
        //AsyncProfiler.setContextId(context.contextId);

        return new Scope() {
            @Override
            public Context context() {
                return context;
            }

            @Override
            public void close() {
                tls.set(previous);
                Logger.info("close::AsyncProfiler.setContextId(" + context.get(Context.ContextID) + ")");
                //AsyncProfiler.setContextId(context.contextId);
            }
        };
    }
}
