package io.github.dpsoft.ap.instrumentation;

import io.github.dpsoft.ap.config.AgentConfiguration;
import io.github.dpsoft.ap.context.ContextStorage;
import io.github.dpsoft.ap.context.api.Context;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.utility.JavaModule;
import org.tinylog.Logger;

import java.lang.instrument.Instrumentation;
import java.util.concurrent.Callable;

import static net.bytebuddy.matcher.ElementMatchers.*;

import net.bytebuddy.agent.builder.AgentBuilder.Listener;


public final class ContextInstrumenter {
    /**
     * Installs the ContextInstrumenter, this approach is based on ByteBuddy Interceptors mechanism, maybe in the future we can use
     * ByteBuddy's Advice mechanism or MemberSubstitution.
     */
    public static void install(Instrumentation instrumentation, AgentConfiguration.Instrumenter config) {
        if (!config.shouldInstall()) Logger.info("Context Instrumenter is disabled, nothing to do...");
        new AgentBuilder.Default()
                .with(new ByteBuddyListener())
                .type(named("io.github.dpsoft.ap.context.api.ContextHandler"))
                .transform((builder, typeDescription, classLoader, module, transformer) ->
                        builder.method(namedOneOf("runWithContext", "currentContext", "storeContext"))
                               .intercept(MethodDelegation.to(ContextHandlerInterceptor.class)))
                .installOn(instrumentation);
    }

    public static class ContextHandlerInterceptor {
        @RuntimeType
        public static Object onRunWithContext(Context context, Callable<?> callable) { return ContextStorage.INSTANCE.runWithContext(context, callable);}
        @RuntimeType
        public static Context onCurrentContext() { return ContextStorage.INSTANCE.currentContext();}
        @RuntimeType
        public static Object onStoreContext(Context context) {
            return ContextStorage.INSTANCE.storeContext(context);
        }
    }

    private static class ByteBuddyListener extends Listener.Adapter {
        @Override
        public void onError(String typeName, ClassLoader classLoader, JavaModule module, boolean loaded, Throwable cause) {
            Logger.error(cause, "Error trying to instrument class {}", typeName);
        }

        @Override
        public void onTransformation(TypeDescription typeDescription, ClassLoader classLoader, JavaModule module, boolean loaded, DynamicType dynamicType) {
            Logger.info("Instrumented class {}", typeDescription.getName());
        }
    }
}
