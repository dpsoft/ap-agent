package io.github.dpsoft.ap.context.api;

public interface Storage {
    Context current();

    Storage.Scope store(Context context);

    interface Scope extends AutoCloseable {
        Context context();

        void close();

        enum Empty implements Scope {
            INSTANCE;

            @Override
            public Context context() { return Context.empty(); }

            @Override
            public void close() { }
        }
    }

    enum Empty implements Storage {
        INSTANCE;

        @Override
        public Context current() { return Context.empty(); }

        @Override
        public Scope store(Context context) {
            return Scope.Empty.INSTANCE;
        }
    }
}