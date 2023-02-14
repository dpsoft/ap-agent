package io.github.dpsoft.ap.context.api.storage;

import io.github.dpsoft.ap.context.api.context.Context;

/**
 * A temporary space to store a Context instance.
 */
public interface Storage {
    /**
     * Returns the Context instance held in the Storage, or Context.Empty if nothing is stored.
     */
    Context current();

    /**
     * Temporarily puts a Context instance in the Storage.
     */
    Storage.Scope store(Context context);

    /**
     * Encapsulates the extent during which a Context is held by a Storage implementation. Once a Scope is closed, the
     * Context will be removed from the Storage that created the Scope.
     */
    interface Scope extends AutoCloseable {
        /**
         * Returns the Context managed by this Scope.
         */
        Context context();
        /**
         * Removes the Context from the Storage. Implementations will typically have a reference to the Context that was
         * present before the Scope was created and put it back in the Storage upon closing.
         */
        void close();

        /**
         * A Scope instance that doesn't carry any context and does nothing on close.
         */
        enum Empty implements Scope {
            INSTANCE;

            @Override
            public Context context() { return Context.EMPTY; }

            @Override
            public void close() { }
        }
    }

    enum Empty implements Storage {
        INSTANCE;

        @Override
        public Context current() { return Context.EMPTY; }

        @Override
        public Scope store(Context context) { return Scope.Empty.INSTANCE; }
    }
}