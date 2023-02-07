package io.github.dpsoft.ap.util;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Taken from :<a href="https://stackoverflow.com/a/14045462">...</a>
 */
public final class StrSubstitutor {
    private final Map<String, String> map;
    private static final Pattern p = Pattern.compile("\\$\\{(.+?)\\}");

    public StrSubstitutor(Map<String, String> map) {
        this.map = map;
    }

    public String replace(String str) {
        final var m = p.matcher(str);
        final var sb = new StringBuilder();
        while (m.find()) {
            final var var = m.group(1);
            final var replacement = map.get(var);
            m.appendReplacement(sb, replacement);
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
