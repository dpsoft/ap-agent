package io.github.dpsoft.ap;

import io.github.dpsoft.ap.context.Context;
import io.github.dpsoft.ap.context.Storage;

import java.util.concurrent.Callable;

public class ClassToOverride {
    public Context currentContext() { return null;}
    public Storage.Scope storeContext(Context context) { return null; }
    public <T> T runWithContext(Context context, Callable<T> callable) { return null; }
}
