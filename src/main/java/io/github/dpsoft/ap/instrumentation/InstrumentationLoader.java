package io.github.dpsoft.ap.instrumentation;

import io.github.dpsoft.ap.context.Context;
import io.github.dpsoft.ap.context.ContextStorage;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.RedefinitionStrategy;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;

import java.lang.instrument.Instrumentation;
import java.util.concurrent.Callable;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;

public class InstrumentationLoader {
    public static void install(Instrumentation instrumentation) {
        new AgentBuilder.Default()
//                .with(AgentBuilder.Listener.StreamWriting.toSystemError())
                .with(RedefinitionStrategy.RETRANSFORMATION)
                .type(named("io.github.dpsoft.ap.ClassToOverride"))
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
        public Context currentContext() {
            return ContextStorage.INSTANCE.currentContext();
        }
        @RuntimeType
        public Object storeContext(Context context) {
            return ContextStorage.INSTANCE.storeContext(context);
        }
    }
}
