package io.github.dpsoft.ap;

/** Another profiling session is running in this JVM: Async Profiler records one session at a time. */
public final class ProfilerBusyException extends IllegalStateException {
    public ProfilerBusyException() {
        super("Another profiling session is running; try again when it finishes");
    }
}
