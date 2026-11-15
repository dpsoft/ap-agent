package io.github.dpsoft.ap.config;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Defaults from reference.conf. Overrides via -D are covered by AgentSmokeIT (the configuration is a singleton). */
class AgentConfigurationTest {
    private final AgentConfiguration config = AgentConfiguration.instance();

    @Test
    void isASingleton() {
        assertSame(config, AgentConfiguration.instance());
    }

    @Test
    void serverDefaults() {
        assertEquals("0.0.0.0", config.server.host);
        assertEquals(8080, config.server.port);
    }

    @Test
    void profilerDefaults() {
        assertEquals("10000000", config.profiler.interval);
    }

    @Test
    void handlerDefaults() {
        assertFalse(config.handler.isGoMode());
        assertEquals(Set.of("/profiler/profile"), config.handler.context());
    }

    @Test
    void bannerIsShownByDefault() {
        assertTrue(config.showBanner());
    }
}
