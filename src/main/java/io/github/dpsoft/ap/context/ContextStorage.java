package io.github.dpsoft.ap.context;

import io.vavr.control.Try;
import org.tinylog.Logger;

import java.util.concurrent.Callable;

public final class ContextStorage {

    public static final ContextStorage INSTANCE = new ContextStorage();

    private final Storage storage = new Storage.ThreadLocalStorage();

    public Context currentContext() { return storage.current();}
    public Storage.Scope storeContext(Context context) { return storage.store(context); }

    public <T> T runWithContext(Context context, Callable<T> callable) {
        return Try.withResources(() -> storeContext(context))
                  .of(scope -> callable.call())
                  .onFailure((cause) -> Logger.error(cause, "Error running callable with context"))
                  .get();
    }
}
