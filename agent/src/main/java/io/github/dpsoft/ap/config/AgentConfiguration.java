package io.github.dpsoft.ap.config;


import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigParseOptions;
import io.vavr.control.Try;
import org.tinylog.Logger;

public final class AgentConfiguration {
    private static final AgentConfiguration INSTANCE = new AgentConfiguration(loadConfig());

    public final Server server;
    public final Handler handler;

    public Instrumenter instrumenter;

    public final boolean showBanner;


    private AgentConfiguration(Config config) {
        this.server = new Server(config);
        this.handler = new Handler(config);
        this.instrumenter = new Instrumenter(config);
        this.showBanner = config.getBoolean("show-banner");
    }

    public static class Server {
        public final int port;
        public final String host;

        public Server(Config config) {
            this.port = config.getInt("server.port");
            this.host = config.getString("server.host");
        }
    }

    public static class Handler {
        public final boolean goMode;
        public final String goContext;
        public final String context;

        public Handler(Config config) {
            this.goMode = config.getBoolean("handler.go-mode");
            this.goContext = config.getString("handler.go-context");
            this.context = config.getString("handler.context");
        }

        public boolean isGoMode() {
            return goMode;
        }

        public String context() { return isGoMode() ? goContext : context;}
    }

    public static class Instrumenter {
        public final boolean enabled;
        public Instrumenter(Config config) {
            this.enabled = config.getBoolean("context-instrumenter.enabled");
        }

        public boolean shouldInstall() {
            return enabled;
        }
    }

    public boolean showBanner() {
        return this.showBanner;
    }


    private static Config loadConfig() {
        return Try.of(() -> loadDefaults().getConfig("ap-agent"))
                .onFailure(missing -> Logger.error(missing, () -> "It has not been found any configuration for AP Agent."))
                .get();
    }

    private static Config loadDefaults() {
        return ConfigFactory
                .load(Thread.currentThread().getContextClassLoader(), ConfigParseOptions.defaults().setAllowMissing(true));
    }

    public static AgentConfiguration instance() {
        return INSTANCE;
    }
}
