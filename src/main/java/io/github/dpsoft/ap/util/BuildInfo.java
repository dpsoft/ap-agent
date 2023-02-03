package io.github.dpsoft.ap.util;


import io.vavr.control.Try;

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
        return Try.of(() -> {
            final var properties = new Properties();
            final var is = BuildInfo.class.getResourceAsStream("/build-info.properties");
            properties.load(is);
            return properties;
        }).getOrElseThrow((cause) -> new RuntimeException("Error trying to read build-info.properties", cause));
    }
}