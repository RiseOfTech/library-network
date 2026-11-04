package io.github.riseoftech.library.catalog.book;

import java.util.regex.Pattern;

/**
 * ISBN-13 rules: input may contain hyphens and spaces, the stored form is 13 digits.
 */
public final class Isbn {

    private static final Pattern SEPARATORS = Pattern.compile("[-\\s]");
    private static final Pattern THIRTEEN_DIGITS = Pattern.compile("\\d{13}");

    private Isbn() {
    }

    /** Drops hyphens and spaces. Does not validate. */
    public static String normalize(String raw) {
        return SEPARATORS.matcher(raw).replaceAll("");
    }

    /** True if the input, after normalization, is 13 digits with a correct check digit. */
    public static boolean isValid(String raw) {
        if (raw == null) {
            return false;
        }
        String digits = normalize(raw);
        if (!THIRTEEN_DIGITS.matcher(digits).matches()) {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            int digit = digits.charAt(i) - '0';
            sum += (i % 2 == 0) ? digit : digit * 3;
        }
        int check = (10 - sum % 10) % 10;
        return check == digits.charAt(12) - '0';
    }
}
