package io.github.dpsoft.ap.functions;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class Functions {
    /**
     * Splits a query string apart into its component name/value pairs
     *
     * @return a map of query parameters
     */
    public static Map<String, String> splitQueryParams(URI uri) {
        final var queryPairs = new HashMap<String, String>();
        final var query = uri.getQuery();
        if (query == null) { return queryPairs; }
        final var pairs = query.split("&");

        for (String pair : pairs) {
            final int idx = pair.indexOf("=");
            final var key = pair.substring(0, idx);
            final var value = pair.substring(idx + 1);
            queryPairs.put(URLDecoder.decode(key, StandardCharsets.UTF_8), URLDecoder.decode(value, StandardCharsets.UTF_8));
        }
        return queryPairs;
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
