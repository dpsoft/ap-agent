package io.github.dpsoft.ap.util;


import java.io.IOException;
import java.util.Properties;

public final class BuildInfo {

    private static final BuildInfo INSTANCE = new BuildInfo(loadProperties());

    private final String version;
    private final String apLoaderVersion;

    private BuildInfo(Properties properties) {
        this.version = properties.getProperty("version");
        this.apLoaderVersion = properties.getProperty("apLoaderVersion");
    }

    public static String version() {
        return BuildInfo.INSTANCE.version;
    }

    public static String apLoaderVersion() {
        return BuildInfo.INSTANCE.apLoaderVersion;
    }

    private static Properties loadProperties() {
        try (var is = BuildInfo.class.getResourceAsStream("/build-info.properties")) {
            if (is == null) throw new IOException("build-info.properties not found");
            final var properties = new Properties();
            properties.load(is);
            return properties;
        } catch (IOException cause) {
            throw new RuntimeException("Error trying to read build-info.properties", cause);
        }
    }
}