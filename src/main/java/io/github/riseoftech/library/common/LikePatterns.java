package io.github.riseoftech.library.common;

import java.util.Locale;

/**
 * Builds patterns for case-insensitive substring search with SQL {@code LIKE}.
 * Queries using them must compare against a lower-cased column with {@code escape '\'}.
 */
public final class LikePatterns {

    private LikePatterns() {
    }

    /**
     * Lower-cases {@code text}, escapes {@code \}, {@code %} and {@code _} with {@code \}
     * and wraps the result in {@code %}, so it matches {@code text} literally anywhere in a value.
     */
    public static String contains(String text) {
        String escaped = text.toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
