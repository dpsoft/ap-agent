package io.github.dpsoft.ap.context.api;

public interface Storage {
    Context current();

    Storage.Scope store(Context context);

    interface Scope extends AutoCloseable {
        Context context();

        void close();
    }
}