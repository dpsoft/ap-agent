package io.github.dpsoft.ap.instrumentation;

import io.github.dpsoft.ap.context.ContextStorage;
import io.github.dpsoft.ap.context.api.Context;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.utility.JavaModule;
import org.tinylog.Logger;

import java.lang.instrument.Instrumentation;
import java.util.concurrent.Callable;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;

import net.bytebuddy.agent.builder.AgentBuilder.Listener;


public final class InstrumentationLoader {
    public static void install(Instrumentation instrumentation) {
        new AgentBuilder.Default()
                .with(new ByteBuddyErrorListener())
                .type(named("io.github.dpsoft.ap.context.api.ContextHandler"))
                .transform((builder, typeDescription, classLoader, module, transformer) ->
                        builder.method(namedOneOf("runWithContext", "currentContext", "storeContext"))
                               .intercept(MethodDelegation.to(new ContextStorageInterceptor())))
                .installOn(instrumentation);
    }

    public static class ContextStorageInterceptor {
        @RuntimeType
        public Object onRunWithContext(Context context, Callable<?> callable) {
            return ContextStorage.INSTANCE.runWithContext(context, callable);
        }
        @RuntimeType
        public Context currentContext() { return ContextStorage.INSTANCE.currentContext();}
        @RuntimeType
        public Object storeContext(Context context) {
            return ContextStorage.INSTANCE.storeContext(context);
        }
    }

    private static class ByteBuddyErrorListener extends Listener.Adapter {
        @Override
        public void onError(String typeName, ClassLoader classLoader, JavaModule module, boolean loaded, Throwable cause) {
            Logger.error(cause, "Error trying to instrument class {}", typeName);
        }
    }
}
