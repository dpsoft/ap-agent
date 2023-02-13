package io.github.dpsoft.ap.context.api;


import java.util.function.Supplier;

/**
 * This class is used to store and retrieve the current context.
 * Its implementation is Noop by default, but it can be replaced by an agent in runtime.
 */
public class ContextHandler  {
    /**
     * Returns the current context.
     */
    public static Context currentContext() { return Context.EMPTY; }
    /**
     * Stores the current context and returns a scope that can be used to restore it.
     */
    public static Storage.Scope storeContext(Context context) { return Storage.Scope.Empty.INSTANCE;}

    /**
     * Runs the given supplier with the given context.
     * The context is restored after the supplier returns.
     */
    public static <T> T runWithContext(Context context, Supplier<T> supplier) {
        try (var ignored = storeContext(context)) {
            return supplier.get();
        }
    }

    /**
     * Runs the given runnable with the given context.
     * The context is restored after the runnable returns.
     */
    public static void runWitContext(Context context, Runnable runnable) {
        try (var ignored = storeContext(context)) {
            runnable.run();
        }
    }
}
