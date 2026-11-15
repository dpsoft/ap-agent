package io.github.dpsoft.ap.functions;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class Functions {
    /**
     * Splits a query string apart into its component name/value pairs.
     * <p>
     * Pairs are split on the raw (still encoded) query and each part is percent-decoded exactly once, so encoded
     * {@code &}, {@code =} or {@code %} inside a value survive. A literal {@code +} is kept as {@code +} (not a space):
     * values such as span tag regexes use it, and this endpoint does not receive HTML forms. Use {@code %20} for spaces.
     *
     * @return a map of query parameters
     */
    public static Map<String, String> splitQueryParams(URI uri) {
        final var queryPairs = new HashMap<String, String>();
        final var query = uri.getRawQuery();
        if (query == null) { return queryPairs; }
        final var pairs = query.split("&");

        for (String pair : pairs) {
            final int idx = pair.indexOf("=");
            final var key = pair.substring(0, idx);
            final var value = pair.substring(idx + 1);
            queryPairs.put(decode(key), decode(value));
        }
        return queryPairs;
    }

    private static String decode(String component) {
        return URLDecoder.decode(component.replace("+", "%2B"), StandardCharsets.UTF_8);
    }

    public static String padString(String str, int length) {
        if (str.length() > length) { return str; }

        return str + " ".repeat(length - str.length());
    }

    public static String lastSegment(String path) {
        final var lastSlash = path.lastIndexOf("/");
        return path.substring(lastSlash + 1);
    }
}
