package dpsoft.ap.config;


import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigParseOptions;
import io.vavr.control.Try;
import org.tinylog.Logger;

public final class AgentConfiguration {
    private static final AgentConfiguration INSTANCE = new AgentConfiguration(loadConfig());

    public final Server server;

    private AgentConfiguration(Config config) {
        this.server = new Server(config);
    }

    public static class Server {
        public final int port;
        public final String host;

        public Server(Config config) {
            this.port = config.getInt("server.port");
            this.host = config.getString("server.host");
        }
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
