import com.microsoft.playwright.*;

import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Opens every htmlpreview "live" link of README.md and docs/*.md in headless Chrome (the installed one) and checks
 * that the async-profiler page really rendered: its canvas has painted pixels and there is no error message.
 *
 *   java -cp <playwright classpath> scripts/assets/CheckLiveLinks.java [branch]
 *
 * The docs link to the main branch; pass another branch to check the links before merging.
 */
public class CheckLiveLinks {
    public static void main(String[] args) throws Exception {
        final var branch = args.length > 0 ? args[0] : "main";
        final var links = new TreeSet<String>();
        final var pattern = Pattern.compile("https://htmlpreview\\.github\\.io/\\?[^)\\s]+");
        final var docs = new ArrayList<Path>(List.of(Paths.get("README.md")));
        try (var files = Files.list(Paths.get("docs"))) { files.filter(f -> f.toString().endsWith(".md")).forEach(docs::add); }
        for (var doc : docs) {
            final var m = pattern.matcher(Files.readString(doc));
            while (m.find()) links.add(m.group().replace("/blob/main/", "/blob/" + branch + "/"));
        }
        int failures = 0;
        try (var pw = Playwright.create()) {
            final var browser = pw.chromium().launch(new BrowserType.LaunchOptions().setChannel("chrome"));
            for (var url : links) {
                final var page = browser.newPage(new Browser.NewPageOptions().setViewportSize(1280, 720));
                String verdict;
                try {
                    page.navigate(url, new Page.NavigateOptions().setTimeout(60_000));
                    page.waitForFunction("() => document.querySelector('canvas') !== null", null,
                            new Page.WaitForFunctionOptions().setTimeout(30_000));
                    page.waitForTimeout(1_500);
                    // count painted (non-white, non-transparent) pixels of the largest canvas
                    final Object painted = page.evaluate("() => { let best = 0; for (const c of document.querySelectorAll('canvas')) {"
                            + " if (!c.width || !c.height) continue; const d = c.getContext('2d').getImageData(0, 0, c.width, c.height).data;"
                            + " let n = 0; for (let i = 0; i < d.length; i += 4) if (d[i + 3] > 0 && (d[i] < 245 || d[i + 1] < 245 || d[i + 2] < 245)) n++;"
                            + " best = Math.max(best, n); } return best; }");
                    final long pixels = ((Number) painted).longValue();
                    verdict = pixels > 1_000 ? "OK (" + pixels + " painted pixels)" : "EMPTY canvas (" + pixels + " painted pixels)";
                } catch (Exception e) {
                    final var body = page.locator("body").count() > 0 ? page.locator("body").innerText().trim() : "";
                    verdict = "FAILED: " + (body.isEmpty() ? e.getMessage().split("\n")[0] : body.split("\n")[0]);
                }
                if (!verdict.startsWith("OK")) failures++;
                System.out.println(verdict + "  " + url);
                page.close();
            }
            browser.close();
        }
        System.out.println(links.size() + " live links, " + failures + " failing");
        if (failures > 0) System.exit(1);
    }
}
