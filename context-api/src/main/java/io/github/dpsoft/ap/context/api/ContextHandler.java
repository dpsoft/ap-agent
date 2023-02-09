package io.github.dpsoft.ap.context.api;


import java.util.concurrent.Callable;

/**
 * This class is used to store and retrieve the current context.
 * Its implementation is Noop by default, but it can be replaced by an agent in runtime.
 */
public class ContextHandler {
    /**
     * Returns the current context.
     */
    public Context currentContext() { return Context.empty(); }
    /**
     * Stores the current context and returns a scope that can be used to restore it.
     */
    public Storage.Scope storeContext(Context context) { return Storage.Scope.Empty.INSTANCE;}

    /**
     * Runs the given callable with the given context.
     * The context is restored after the callable is executed.
     */
    public <T> T runWithContext(Context context, Callable<T> callable) {
        try (var ignore = storeContext(currentContext())) {}
        try { return callable.call(); }
        catch (Exception e) { throw new RuntimeException(e);}
    }
}
