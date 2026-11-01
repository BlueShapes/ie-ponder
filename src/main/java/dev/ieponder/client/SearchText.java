package dev.ieponder.client;

import java.text.Normalizer;
import java.util.Locale;

/** Pure normalization for logical search text; it does not shape or reorder script text. */
public final class SearchText {
    private SearchText() {}

    public static String normalize(String value, Locale locale) {
        String canonical = Normalizer.normalize(value, Normalizer.Form.NFC);
        String collapsed = canonical.trim().replaceAll("\\s+", " ").toLowerCase(locale);
        return Normalizer.normalize(collapsed, Normalizer.Form.NFC);
    }
}
