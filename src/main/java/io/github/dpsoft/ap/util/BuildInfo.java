package io.github.dpsoft.ap.util;

import java.io.InputStream;
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
        try {
            final var properties = new Properties();
            final InputStream is = BuildInfo.class.getResourceAsStream("/build-info.properties");
            if (is == null) throw new RuntimeException("build-info.properties not found");
            properties.load(is);
            return properties;
        } catch (Exception e) {
            throw new RuntimeException("Error trying to read build-info.properties", e);
        }
    }
}