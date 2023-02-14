package io.github.dpsoft.ap.instrumentation;

import io.github.dpsoft.ap.Agent;
import io.github.dpsoft.ap.context.api.Context;
import io.github.dpsoft.ap.context.api.ContextHandler;
import io.github.dpsoft.ap.context.api.Labels;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ContextInstrumenterTest {
    @Test
    void instrument() {
        Agent.premain(null, ByteBuddyAgent.install());

        final var contextTest = Context.of(Context.ContextID, 1L, Labels.of("a", "b"));
        final var context = ContextHandler.runWithContext(contextTest, ContextHandler::currentContext);

        Assertions.assertEquals(context.get(Context.ContextID), 1L);
        Assertions.assertEquals(ContextHandler.currentContext().get(Context.ContextID), 0L);
    }
}