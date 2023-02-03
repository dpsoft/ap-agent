package io.github.dpsoft.ap.functions;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;

public class Functions {
    /**
     * Splits a query string apart into its component name/value pairs
     *
     * @param uri
     * @return a map of query parameters
     */
    public static Map<String, String> splitQueryParams(URI uri) throws UnsupportedEncodingException {
        final var queryPairs = new HashMap<String, String>();
        final var query = uri.getQuery();
        final var pairs = query.split("&");

        for (String pair : pairs) {
            final int idx = pair.indexOf("=");
            final var key = pair.substring(0, idx);
            final var value = pair.substring(idx + 1);
            queryPairs.put(URLDecoder.decode(key, "UTF-8"), URLDecoder.decode(value, "UTF-8"));
        }
        return queryPairs;
    }

    public static String padString(String str, int length) {
        final var strBuilder = new StringBuilder(str);
        for (var i = strBuilder.length(); i <= length; i++) { strBuilder.append(" "); }
        return strBuilder.toString();
    }
}
