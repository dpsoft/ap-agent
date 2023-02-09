package io.github.dpsoft.ap.context.api;


import java.util.concurrent.Callable;

public class ContextHandler {
    public Context currentContext() { return null;}
    public Storage.Scope storeContext(Context context) { return null; }
    public <T> T runWithContext(Context context, Callable<T> callable) { return null; }
}
