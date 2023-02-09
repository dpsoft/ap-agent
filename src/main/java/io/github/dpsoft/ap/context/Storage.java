package io.github.dpsoft.ap.context;

import org.tinylog.Logger;

public interface Storage {
    Context current();
    Storage.Scope store(Context context);

    interface Scope extends AutoCloseable {
        Context context();
        void close();
    }

    class ThreadLocalStorage implements Storage {
        private final ThreadLocal<Context> tls = ThreadLocal.withInitial(Context::empty);

        @Override
        public Context current() { return tls.get(); }

        @Override
        public Scope store(Context context) {
            final var previous = tls.get();
            tls.set(context);
            Logger.info("store::AsyncProfiler.setContextId(" +context.contextId+")");

//            AsyncProfilerUtils.load().setContextId(context.contextId);

            return new Scope() {
                @Override
                public Context context() { return context; }

                @Override
                public void close() {
                    tls.set(previous);
                    Logger.info("close::AsyncProfiler.setContextId(" +context.contextId+")");
                    //AsyncProfiler.setContextId(context.contextId);
                }
            };
        }
    }
}

