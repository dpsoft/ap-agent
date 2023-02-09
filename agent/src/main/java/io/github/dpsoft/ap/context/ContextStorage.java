package io.github.dpsoft.ap.context;

import io.github.dpsoft.ap.context.api.Context;
import io.github.dpsoft.ap.context.api.Storage;
import io.vavr.control.Try;
import org.tinylog.Logger;

import java.util.concurrent.Callable;

/**
 * This class is used to store and retrieve the current context.
 * Its is the real implementation that is injected by the agent in runtime to replace the Noop one. see {@link io.github.dpsoft.ap.context.api.ContextHandler}
 */
public final class ContextStorage {
    public static final ContextStorage INSTANCE = new ContextStorage();
    private final Storage storage = new ThreadLocalStorage();

    public Context currentContext() { return storage.current();}
    public Storage.Scope storeContext(Context context) { return storage.store(context); }

    public <T> T runWithContext(Context context, Callable<T> callable) {
        return Try.withResources(() -> storeContext(context))
                  .of(scope -> callable.call())
                  .onFailure((cause) -> Logger.error(cause, "Error running callable with context"))
                  .get();
    }
}
