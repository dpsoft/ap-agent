package io.github.dpsoft.ap.context.api;

import io.github.dpsoft.ap.context.api.context.Context;

import io.github.dpsoft.ap.context.api.storage.Storage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ContextHandlerTest {
    Context.Key<Integer> TestKey = Context.key("test-key", 42);
    Context.Key<Integer> AnotherKey = Context.key("another-key", 99);
    Context.Key<String> BroadcastKey = Context.key("broadcast", "i travel around");
    Context ScopeWithKey = Context.of(TestKey, 43);
    Context ContextWithAnotherKey = Context.of(AnotherKey, 98);

    @Test
    @DisplayName("Must return a empty context when no context has been set")
    public void test() {
       assertEquals(ContextHandler.currentContext(), Context.EMPTY);
    }

    @Test
    @DisplayName("Must return the empty value for keys that have not been set in the context")
    public void test2() {
        assertEquals(ContextHandler.currentContext().get(TestKey),42);
        assertEquals(ContextHandler.currentContext().get(AnotherKey), 99);
        assertEquals(ContextHandler.currentContext().get(BroadcastKey), "i travel around");

        assertEquals(ScopeWithKey.get(TestKey), 43);
        assertEquals(ScopeWithKey.get(AnotherKey), 99);
        assertEquals(ScopeWithKey.get(BroadcastKey), "i travel around");
    }

    @Test
    @DisplayName(" Allow setting a context as current and remove it when closing the Scope")
    public void test3() {
        assertEquals(ContextHandler.currentContext(), Context.EMPTY);

        var scope = ContextHandler.storeContext(ScopeWithKey);
        assertEquals(ContextHandler.currentContext(), ScopeWithKey);
        scope.close();

        assertEquals(ContextHandler.currentContext(), Context.EMPTY);
    }

    @Test
    @DisplayName("Allow closing the scope in a different thread than the original one")
    public void test4() throws ExecutionException, InterruptedException {
        AtomicReference<Storage.Scope> scope = new AtomicReference<>();

        var f1 = CompletableFuture.runAsync(() -> {
            ContextHandler.storeContext(ContextWithAnotherKey);
            scope.set(ContextHandler.storeContext(ScopeWithKey));
            assertEquals(ContextHandler.currentContext(), ScopeWithKey);
        });

        var f2 = CompletableFuture.runAsync(() -> {
            while (scope.get() == null) {} // wait for
            assertEquals(ContextHandler.currentContext(), Context.EMPTY);
            scope.get().close();
            assertEquals(ContextHandler.currentContext(), ContextWithAnotherKey);
        });

        f1.thenCompose(f -> f2).get();
    }
}
