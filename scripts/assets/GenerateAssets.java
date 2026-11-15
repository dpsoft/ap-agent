import com.microsoft.playwright.*;
import com.microsoft.playwright.options.ScreenshotType;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Regenerates docs/assets: runs the DemoShop workload with the agent, saves the interactive HTML profiles, takes
 * screenshots with Playwright (the installed Chrome) and records the README demo, converted to GIF with ffmpeg.
 *
 *   mvn -Passets verify -Dgpg.skip=true
 *
 * args: agent jar, async-profiler API jar, test classes dir, output dir
 */
public class GenerateAssets {
    static final int WIDTH = 1280, HEIGHT = 720, FLAME_HEIGHT = 440; // flame graphs: no blank area below the stacks
    static final HttpClient HTTP = HttpClient.newHttpClient();

    public static void main(String[] args) throws Exception {
        final Path agent = Paths.get(args[0]), api = Paths.get(args[1]), classes = Paths.get(args[2]), out = Paths.get(args[3]);
        Files.createDirectories(out);
        final int port = freePort();
        final var javaBin = Paths.get(System.getProperty("java.home"), "bin", "java").toString();
        final var log = Files.createTempFile("demo-shop", ".log");
        final var app = new ProcessBuilder(javaBin, "-XX:+IgnoreUnrecognizedVMOptions", "--enable-native-access=ALL-UNNAMED",
                "-javaagent:" + agent, "-Dap-agent.server.host=127.0.0.1", "-Dap-agent.server.port=" + port,
                "-Dap-agent.show-banner=false", "-cp", classes + java.io.File.pathSeparator + api,
                "io.github.dpsoft.ap.demo.DemoShop").redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            awaitStarted(app, log);
            Thread.sleep(5_000); // let the JIT settle so the profiles look like a warm service
            final var base = "http://127.0.0.1:" + port + "/profiler/profile?duration=10&output=";
            // root on top (icicle) and only the request path: JIT compiler threads have very deep stacks
            final var flame = "flame&params=inverted,include=.*OrderService.*";
            flame(base + flame + "&event=cpu", out.resolve("flame-cpu.html"), "CPU — every order");
            flame(base + flame + "&event=wall", out.resolve("flame-wall.html"), "Wall clock — CPU, lock waits and database waits");
            flame(base + flame + "&event=alloc", out.resolve("flame-alloc.html"), "Heap allocations");
            flame(base + flame + ",total&event=nativemem", out.resolve("flame-nativemem.html"), "Native memory (malloc), in bytes");
            flame(base + flame + "&event=cpu&tag=order%3Acustomer%3D3", out.resolve("flame-span-filter.html"),
                    "CPU — only the orders of customer 3 (tag=order:customer=3)");
            save(base.replace("duration=10", "duration=60") + "heatmap&event=cpu", out.resolve("heatmap.html"));
            final var pprof = Files.createTempFile("demo-shop", ".pb.gz");
            save(base + "pprof&event=cpu", pprof);

            try (var playwright = Playwright.create()) {
                final var browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setChannel("chrome"));
                for (var name : List.of("flame-cpu", "flame-wall", "flame-alloc", "flame-nativemem", "flame-span-filter", "heatmap")) {
                    screenshot(browser, out.resolve(name + ".html").toUri().toString(), out.resolve(name + ".png"));
                }
                pprofScreenshot(browser, pprof, out.resolve("pprof-web.png"));
                demo(browser, out);
                spanFilterDemo(browser, out);
                heatmapDemo(browser, out);
                browser.close();
            }
        } finally {
            app.destroy();
            if (!app.waitFor(10, TimeUnit.SECONDS)) app.destroyForcibly();
        }
    }

    static void awaitStarted(Process app, Path log) throws Exception {
        for (int i = 0; i < 600; i++) {
            final var text = Files.readString(log);
            if (text.contains("AP Agent started")) return;
            if (!app.isAlive()) throw new IllegalStateException("DemoShop exited:\n" + text);
            Thread.sleep(100);
        }
        throw new IllegalStateException("the agent did not start:\n" + Files.readString(log));
    }

    /** Saves a flame graph with a descriptive title, set on the saved HTML to keep the request URLs readable. */
    static void flame(String url, Path target, String title) throws Exception {
        save(url, target);
        final var html = Files.readString(target, StandardCharsets.UTF_8);
        if (!html.contains("<h1>Flame Graph</h1>")) throw new IllegalStateException("unexpected flame graph HTML: " + target);
        Files.writeString(target, html.replace("<h1>Flame Graph</h1>", "<h1>" + title + "</h1>")
                .replace("<title>Flame Graph</title>", "<title>" + title + "</title>"), StandardCharsets.UTF_8);
    }

    static void save(String url, Path target) throws Exception {
        final var response = HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(2)).build(),
                // ofFile(path) alone does not truncate: a shorter response would keep the tail of the previous file
                HttpResponse.BodyHandlers.ofFile(target, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                        StandardOpenOption.TRUNCATE_EXISTING));
        if (response.statusCode() != 200) throw new IllegalStateException(url + " -> " + response.statusCode() + ": " + Files.readString(target));
        System.out.println("saved " + target + " (" + Files.size(target) + " bytes)");
    }

    static void screenshot(Browser browser, String url, Path target) {
        final int height = url.contains("flame-") ? FLAME_HEIGHT : HEIGHT;
        try (var page = browser.newPage(new Browser.NewPageOptions().setViewportSize(WIDTH, height).setDeviceScaleFactor(2))) {
            page.navigate(url);
            page.waitForTimeout(1_000);
            page.screenshot(new Page.ScreenshotOptions().setPath(target).setType(ScreenshotType.PNG));
            System.out.println("screenshot " + target);
        }
    }

    /** The flame graph view of `go tool pprof -http`. */
    static void pprofScreenshot(Browser browser, Path profile, Path target) throws Exception {
        final int port = freePort();
        final var pprof = new ProcessBuilder("go", "tool", "pprof", "-no_browser", "-http=127.0.0.1:" + port, profile.toString())
                .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        try {
            final var url = "http://127.0.0.1:" + port + "/ui/flamegraph";
            for (int i = 0; i < 300; i++) {
                try {
                    if (HTTP.send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.discarding()).statusCode() == 200) break;
                } catch (IOException notYet) {
                    Thread.sleep(100);
                }
            }
            screenshot(browser, url, target);
        } finally {
            pprof.destroy();
        }
    }

    /** Records an interaction as screenshots ("hold" captures the current page n times at 10 fps) and makes a GIF. */
    interface Scene {
        void play(Page page, java.util.function.IntConsumer hold) throws Exception;
    }

    static void record(Browser browser, int height, Path gif, Scene scene) throws Exception {
        final var frames = Files.createTempDirectory("ap-agent-frames");
        final int[] count = {0};
        try (var page = browser.newPage(new Browser.NewPageOptions().setViewportSize(WIDTH, height))) {
            final java.util.function.IntConsumer hold = n -> {
                final var png = page.screenshot();
                try {
                    for (int i = 0; i < n; i++) Files.write(frames.resolve(String.format("f%05d.png", count[0]++)), png);
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            };
            scene.play(page, hold);
        }
        ffmpeg(frames, gif);
    }

    /** The README demo: sweep the CPU flame graph, zoom into a frame and back, search "regex", tree view. */
    static void demo(Browser browser, Path out) throws Exception {
        record(browser, FLAME_HEIGHT, out.resolve("demo.gif"), (page, hold) -> {
            page.onDialog(dialog -> dialog.accept("regex")); // highlights java.util.regex frames across the graph
            page.navigate(out.resolve("flame-cpu.html").toUri().toString());
            page.waitForTimeout(800);
            final var box = page.locator("#canvas").boundingBox();
            final double row = 16; // async-profiler draws 16px rows; inverted: root at the top
            hold.accept(12);
            for (int i = 0; i <= 14; i++) {
                page.mouse().move(box.x + box.width * (0.15 + i * 0.05), box.y + row * (5 + i % 4) + 8);
                hold.accept(2);
            }
            page.mouse().click(box.x + box.width * 0.30, box.y + row * 6 + 8);
            page.waitForTimeout(300);
            hold.accept(14);
            page.mouse().click(box.x + box.width * 0.5, box.y + 8); // click the root row: back to the whole graph
            page.waitForTimeout(300);
            hold.accept(6);
            page.keyboard().press("Control+f");
            page.waitForTimeout(400);
            hold.accept(16);
            page.keyboard().press("t");
            page.waitForTimeout(400);
            hold.accept(18);
        });
    }

    /** Span filtering: every order, then only customer 3's orders, where volumeDiscount stands out. */
    static void spanFilterDemo(Browser browser, Path out) throws Exception {
        record(browser, FLAME_HEIGHT, out.resolve("span-filter.gif"), (page, hold) -> {
            page.onDialog(dialog -> dialog.accept("volumeDiscount"));
            page.navigate(out.resolve("flame-cpu.html").toUri().toString());
            page.waitForTimeout(800);
            page.keyboard().press("Control+f"); // barely visible across every order
            page.waitForTimeout(400);
            hold.accept(25);
            page.navigate(out.resolve("flame-span-filter.html").toUri().toString());
            page.waitForTimeout(800);
            hold.accept(12);
            page.keyboard().press("Control+f"); // most of the bulk importer's orders
            page.waitForTimeout(400);
            hold.accept(30);
        });
    }

    /** Heatmap: select a time range by dragging, and the flame graph below shows only that range. */
    static void heatmapDemo(Browser browser, Path out) throws Exception {
        record(browser, HEIGHT, out.resolve("heatmap.gif"), (page, hold) -> {
            page.navigate(out.resolve("heatmap.html").toUri().toString());
            page.waitForTimeout(1_000);
            final var box = page.locator("#heatmap-canvas").boundingBox();
            hold.accept(12);
            // the recorded seconds only fill the left part of the canvas: drag inside them
            final double y = box.y + box.height * 0.5;
            page.mouse().move(box.x + box.width * 0.14, y - box.height * 0.3);
            page.mouse().down();
            for (int i = 1; i <= 10; i++) {
                page.mouse().move(box.x + box.width * (0.14 + i * 0.012), y + box.height * 0.3);
                hold.accept(2);
            }
            page.mouse().up();
            page.waitForTimeout(500);
            hold.accept(30);
        });
    }

    static void ffmpeg(Path frames, Path gif) throws Exception {
        final var input = frames.resolve("f%05d.png").toString();
        final var palette = Files.createTempFile("palette", ".png");
        run("ffmpeg", "-y", "-loglevel", "error", "-framerate", "10", "-i", input,
                "-vf", "scale=960:-1:flags=lanczos,palettegen=stats_mode=diff", palette.toString());
        run("ffmpeg", "-y", "-loglevel", "error", "-framerate", "10", "-i", input, "-i", palette.toString(), "-loop", "0",
                "-lavfi", "scale=960:-1:flags=lanczos[x];[x][1:v]paletteuse=dither=bayer:bayer_scale=5", gif.toString());
        System.out.println("demo " + gif + " (" + Files.size(gif) + " bytes)");
    }

    static void run(String... command) throws Exception {
        final var process = new ProcessBuilder(command).inheritIO().start();
        if (process.waitFor() != 0) throw new IllegalStateException(String.join(" ", command) + " failed");
    }

    static int freePort() throws IOException {
        try (var socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
