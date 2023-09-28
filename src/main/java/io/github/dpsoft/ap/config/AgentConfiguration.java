package io.github.dpsoft.ap.config;


import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigParseOptions;
import io.vavr.control.Try;
import org.tinylog.Logger;

import java.util.HashSet;
import java.util.Set;

public final class AgentConfiguration {
    private static final AgentConfiguration INSTANCE = new AgentConfiguration(loadConfig());

    public final Server server;
    public final Handler handler;
    public final Profiler profiler;
    public final boolean showBanner;

    private AgentConfiguration(Config config) {
        this.server = new Server(config);
        this.handler = new Handler(config);
        this.profiler = new Profiler(config);
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

    public static class Profiler {
        public final String interval;

        public Profiler(Config config) {
            this.interval = config.getString("profiler.interval");
        }
    }

    public static class Handler {
        private final boolean goMode;
        private final Set<String> goContext;
        private final Set<String> context;

        public Handler(Config config) {
            this.goMode = config.getBoolean("handler.go-mode");
            this.goContext = new HashSet<>(config.getStringList("handler.go-context"));
            this.context = new HashSet<>(){{ add(config.getString("handler.context")); }};
        }

        public boolean isGoMode() {
            return goMode;
        }

        public Set<String> context() {
            if (isGoMode()) return new HashSet<>(goContext) {{ addAll(context); }};
            return context;
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
