package io.github.dpsoft.ap.util;

import io.github.dpsoft.ap.config.AgentConfiguration;
import io.github.dpsoft.ap.functions.Functions;
import io.vavr.control.Try;
import org.tinylog.Logger;

import java.util.Map;
import java.util.Scanner;

public final class Banner {
    public static void show(AgentConfiguration configuration) {
        if (configuration.showBanner()) {
            final var version = (BuildInfo.version() == null ? "" : " v" + BuildInfo.version() + "");
            final var loaderVersion = (BuildInfo.apLoaderVersion() == null ? "" : "v" + BuildInfo.apLoaderVersion() + "");

            final var welcomeMessage = Functions.padString(green("::: Async Profiler Agent :::") + version, 72);
            final var poweredByMessage = Functions.padString(green(":-> Powered by AP-Loader ") + "(" + loaderVersion + ")", 71);

            final var substitutor = new StrSubstitutor(Map.of("welcome", welcomeMessage, "powered", poweredByMessage));

            Try.withResources(() -> Banner.class.getResourceAsStream("/banner.txt")).of((stream) -> {
                final var banner = substitutor.replace(new String(stream.readAllBytes()));
                final var scanner = new Scanner(banner);

                int count = 0;
                while (scanner.hasNextLine()) {
                    count++;
                    final var line = scanner.nextLine();
                    if (count <= 4) { // red the first 4 lines
                        System.out.println(red(line));
                        continue;
                    }
                    System.out.println(line);
                }
                System.out.println();
                return null; // void :(

            }).onFailure((error) -> Logger.warn(error, () -> "It has not been possible to show the banner."));
        }
    }

    private static String red(String text) { return bold("\u001b[31m" + text + "\u001b[0m"); }
    private static String green(String text) { return bold("\u001b[32m" + text + "\u001b[0m"); }
    private static String bold(String text) { return "\u001b[1m" + text + "\u001b[0m";}
}
