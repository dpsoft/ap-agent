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
 * args: agent jar, async-profiler API jar, test classes dir, output dir [, promo video .mp4]
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
            flame(base + flame + "&event=lock", out.resolve("flame-lock.html"), "Lock contention — waiting for the stock lock");
            flame(base + flame + "&event=cpu&tag=order%3Acustomer%3D3", out.resolve("flame-span-filter.html"),
                    "CPU — only the orders of customer 3 (tag=order:customer=3)");
            save(base.replace("duration=10", "duration=60") + "heatmap&event=cpu", out.resolve("heatmap.html"));
            final var pprof = Files.createTempFile("demo-shop", ".pb.gz");
            save(base + "pprof&event=cpu", pprof);

            try (var playwright = Playwright.create()) {
                final var browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setChannel("chrome"));
                for (var name : List.of("flame-cpu", "flame-wall", "flame-alloc", "flame-nativemem", "flame-lock", "flame-span-filter", "heatmap")) {
                    screenshot(browser, out.resolve(name + ".html").toUri().toString(), out.resolve(name + ".png"));
                }
                final var pprofServer = startPprof(pprof);
                try {
                    screenshot(browser, pprofServer.url, out.resolve("pprof-web.png"));
                    demo(browser, out);
                    spanFilterDemo(browser, out);
                    heatmapDemo(browser, out);
                    if (args.length > 4) Promo.record(browser, out, pprofServer.url, Paths.get(args[4]));
                } finally {
                    pprofServer.process.destroy();
                }
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

    static final class PprofServer {
        final Process process;
        final String url;

        PprofServer(Process process, String url) {
            this.process = process;
            this.url = url;
        }
    }

    /** `go tool pprof -http` on the saved profile; its flame graph view is screenshotted and shown in the promo. */
    static PprofServer startPprof(Path profile) throws Exception {
        final int port = freePort();
        final var process = new ProcessBuilder("go", "tool", "pprof", "-no_browser", "-http=127.0.0.1:" + port, profile.toString())
                .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        final var url = "http://127.0.0.1:" + port + "/ui/flamegraph";
        for (int i = 0; i < 300; i++) {
            try {
                if (HTTP.send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.discarding()).statusCode() == 200) break;
            } catch (IOException notYet) {
                Thread.sleep(100);
            }
        }
        return new PprofServer(process, url);
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

/**
 * A ~45 s promo video (1280x720, H.264): title card, the curl command, every profile type with a caption, span
 * filtering, go tool pprof and a closing card. Frames are screenshots assembled by ffmpeg at 15 fps.
 */
final class Promo {
    static final int FPS = 15;
    private final Path frames;
    private int count;

    private Promo(Path frames) {
        this.frames = frames;
    }

    static void record(Browser browser, Path assets, String pprofUrl, Path mp4) throws Exception {
        final var promo = new Promo(Files.createTempDirectory("ap-agent-promo"));
        final var lockup = Files.readString(assets.resolveSibling("brand").resolve("ap-agent-lockup.svg"));
        // 960x540 CSS pixels rendered at 4/3: a 1280x720 video where everything is a third larger (readable on X)
        try (var page = browser.newPage(new Browser.NewPageOptions().setViewportSize(960, 540).setDeviceScaleFactor(4.0 / 3))) {
            page.onDialog(dialog -> dialog.accept(promo.nextSearch));

            promo.card(page, lockup, "async-profiler over HTTP", "One <code>-javaagent</code>. Nothing to install.",
                    "Powered by <b>async-profiler</b> and <b>AP-Loader</b>", 3.0);
            promo.terminal(page);

            promo.flame(page, assets, "flame-cpu.html", "CPU", "where the time goes, per request path", true);
            promo.flame(page, assets, "flame-wall.html", "Wall clock", "CPU plus lock waits and database waits", false);
            promo.flame(page, assets, "flame-alloc.html", "Heap allocations", "who allocates, and how much", false);
            promo.flame(page, assets, "flame-lock.html", "Lock contention", "threads queuing on the stock lock", false);
            promo.flame(page, assets, "flame-nativemem.html", "Native memory", "malloc, in bytes: here zlib's Deflater", false);
            promo.heatmap(page, assets);
            promo.spans(page, assets);

            page.navigate(pprofUrl);
            page.waitForTimeout(1_200);
            promo.caption(page, "Go pprof compatible", "go tool pprof http://host:8080/debug/pprof/profile");
            promo.snap(page, 3.0);

            promo.card(page, lockup, "ap-agent 0.2.0 on Maven Central",
                    "async-profiler 4.6 · span filters · Java 11+<br><code>github.com/dpsoft/ap-agent</code>",
                    "Powered by <b>async-profiler</b> <code>github.com/async-profiler/async-profiler</code><br>"
                            + "and <b>AP-Loader</b> <code>github.com/jvm-profiling-tools/ap-loader</code>", 4.5);
        }
        GenerateAssets.run("ffmpeg", "-y", "-loglevel", "error", "-framerate", String.valueOf(FPS),
                "-i", promo.frames.resolve("f%05d.png").toString(), "-c:v", "libx264", "-pix_fmt", "yuv420p",
                "-crf", "20", "-preset", "slow", "-movflags", "+faststart", mp4.toString());
        System.out.println("promo " + mp4 + " (" + Files.size(mp4) + " bytes, " + promo.count / FPS + " s)");
    }

    private String nextSearch = "";

    void snap(Page page, double seconds) {
        final var png = page.screenshot();
        final int n = Math.max(1, (int) Math.round(seconds * FPS));
        try {
            for (int i = 0; i < n; i++) Files.write(frames.resolve(String.format("f%05d.png", count++)), png);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    static final String CARD_CSS = "body{margin:0;height:100vh;display:flex;flex-direction:column;align-items:center;"
            + "justify-content:center;background:#0d1117;color:#e6edf3;font-family:ui-sans-serif,system-ui,sans-serif}"
            + "h1{font-size:40px;margin:22px 0 8px;font-weight:650;letter-spacing:-.5px}p{font-size:22px;color:#9da7b3;"
            + "margin:6px;text-align:center;line-height:1.5}code{font-family:ui-monospace,Menlo,monospace;color:#7fd9d9}"
            + ".wm-2{fill:#d4d1dc!important}"
            + "footer{position:fixed;bottom:26px;font-size:15px;color:#8b949e;text-align:center;line-height:1.7}"
            + "footer b{color:#c9d1d9;font-weight:600}footer code{color:#7fd9d9;font-size:14px;margin-left:4px}";

    void card(Page page, String lockup, String title, String subtitle, String footer, double seconds) {
        page.setContent("<html><head><style>" + CARD_CSS + "</style></head><body><div style='transform:scale(1.6)'>"
                + lockup + "</div><h1>" + title + "</h1><p>" + subtitle + "</p>"
                + "<footer>" + footer + "</footer></body></html>");
        page.waitForTimeout(300);
        snap(page, seconds);
    }

    /** Types the two commands of the quick start in a terminal-looking card. */
    void terminal(Page page) {
        page.setContent("<html><head><style>body{margin:0;height:100vh;display:flex;align-items:center;justify-content:center;"
                + "background:#0d1117}#t{width:840px;min-height:200px;background:#161b22;border-radius:12px;padding:32px 36px;"
                + "font:19px/1.7 ui-monospace,Menlo,Consolas,monospace;color:#e6edf3;white-space:pre-wrap;box-shadow:0 10px 40px #000a}"
                + ".p{color:#3fbfbf}.c{color:#8b949e}</style></head><body><div id='t'></div></body></html>");
        final var lines = List.of(
                "java -javaagent:ap-agent.jar -jar shop.jar",
                "curl -o cpu.html \\\n  \"localhost:8080/profiler/profile?event=cpu&output=flame&duration=30\"");
        final var done = new StringBuilder();
        for (var line : lines) {
            for (int i = 1; i <= line.length(); i += 3) {
                setTerminal(page, done + "<span class='p'>$</span> " + escape(line.substring(0, Math.min(i, line.length()))) + "▌");
                snap(page, 1.0 / FPS);
            }
            done.append("<span class='p'>$</span> ").append(escape(line)).append("\n");
            setTerminal(page, done.toString());
            snap(page, 0.6);
        }
        setTerminal(page, done + "<span class='c'># 30 seconds later: an interactive flame graph</span>");
        snap(page, 1.0);
    }

    private static void setTerminal(Page page, String html) {
        page.evaluate("h => document.getElementById('t').innerHTML = h", html);
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** A caption pill over the page (bottom left). */
    void caption(Page page, String title, String subtitle) {
        page.evaluate("([t, s]) => { let c = document.getElementById('promo-caption'); if (!c) { c = document.createElement('div');"
                + " c.id = 'promo-caption'; document.body.appendChild(c); }"
                + " c.style.cssText = 'position:fixed;left:28px;bottom:28px;z-index:99999;background:#0d1117ee;color:#fff;"
                + "padding:10px 18px;border-radius:10px;font:600 24px ui-sans-serif,system-ui,sans-serif;box-shadow:0 6px 24px #0006';"
                + " c.innerHTML = t + '<div style=\"font-weight:400;font-size:15px;color:#b6bec8;margin-top:4px\">' + s + '</div>'; }",
                List.of(title, subtitle));
    }

    void flame(Page page, Path assets, String file, String title, String subtitle, boolean explore) {
        page.navigate(assets.resolve(file).toUri().toString());
        page.waitForTimeout(700);
        caption(page, title, subtitle);
        snap(page, explore ? 1.2 : 2.6);
        if (!explore) return;
        final var box = page.locator("#canvas").boundingBox();
        for (int i = 0; i <= 10; i++) { // sweep, zoom into a frame, back, and search
            page.mouse().move(box.x + box.width * (0.15 + i * 0.06), box.y + 16 * (5 + i % 4) + 8);
            snap(page, 2.0 / FPS);
        }
        page.mouse().click(box.x + box.width * 0.30, box.y + 16 * 6 + 8);
        page.waitForTimeout(300);
        caption(page, title, "click to zoom into any frame");
        snap(page, 1.6);
        page.mouse().click(box.x + box.width * 0.5, box.y + 8);
        page.waitForTimeout(300);
        nextSearch = "regex";
        page.keyboard().press("Control+f");
        page.waitForTimeout(400);
        caption(page, title, "search with a regex, see how much it weighs");
        snap(page, 1.8);
    }

    void heatmap(Page page, Path assets) {
        page.navigate(assets.resolve("heatmap.html").toUri().toString());
        page.waitForTimeout(900);
        caption(page, "Heatmap", "select a time range, get its flame graph");
        snap(page, 1.0);
        final var box = page.locator("#heatmap-canvas").boundingBox();
        final double y = box.y + box.height * 0.5;
        page.mouse().move(box.x + box.width * 0.14, y - box.height * 0.3);
        page.mouse().down();
        for (int i = 1; i <= 10; i++) {
            page.mouse().move(box.x + box.width * (0.14 + i * 0.012), y + box.height * 0.3);
            snap(page, 2.0 / FPS);
        }
        page.mouse().up();
        page.waitForTimeout(500);
        snap(page, 2.0);
    }

    void spans(Page page, Path assets) {
        nextSearch = "volumeDiscount";
        page.navigate(assets.resolve("flame-cpu.html").toUri().toString());
        page.waitForTimeout(700);
        page.keyboard().press("Control+f");
        page.waitForTimeout(400);
        caption(page, "Span filtering", "every order: volumeDiscount is a thin slice");
        snap(page, 2.4);
        page.navigate(assets.resolve("flame-span-filter.html").toUri().toString());
        page.waitForTimeout(700);
        page.keyboard().press("Control+f");
        page.waitForTimeout(400);
        caption(page, "Span filtering", "tag=order:customer=3 → volumeDiscount is most of it");
        snap(page, 3.0);
    }
}
