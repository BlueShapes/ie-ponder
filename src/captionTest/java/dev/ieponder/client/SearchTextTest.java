package dev.ieponder.client;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Dependency-free search normalization regression checks. */
public final class SearchTextTest {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        Map<String, Map<String, String>> corpus = readCorpus();
        testTurkish(corpus);
        testCanonicalEquivalence();
        testWhitespaceAndAscii();
        testCanonicalCorpus(corpus);
        System.out.println("PASS: " + assertions + " search normalization assertions; "
                + corpus.size() + " locales, " + corpus.values().iterator().next().size()
                + " keys per locale");
    }

    private static void testTurkish(Map<String, Map<String, String>> corpus) {
        Locale tr = Locale.forLanguageTag("tr-TR");
        same("ı", SearchText.normalize("I", tr));
        same("ı", SearchText.normalize("ı", tr));
        same("i", SearchText.normalize("İ", tr));
        same("i", SearchText.normalize("I\u0307", tr));

        String summary = required(corpus, "tr_tr", "ieponder.lesson.coke_oven.summary");
        String canonicalNeedle = "Kömürü";
        if (!summary.contains(canonicalNeedle)) throw new AssertionError("Current Turkish coke_oven.summary changed");
        String decomposedNeedle = canonicalNeedle.replace("ö", "o\u0308");
        String normalizedSummary = SearchText.normalize(summary, tr);
        String normalizedQuery = SearchText.normalize(decomposedNeedle, tr);
        if (!normalizedSummary.contains(normalizedQuery))
            throw new AssertionError("Decomposed Turkish query did not match current coke_oven.summary");
        assertions++;
    }

    private static void testCanonicalEquivalence() {
        same(SearchText.normalize("Café", Locale.ENGLISH),
                SearchText.normalize("Cafe\u0301", Locale.ENGLISH));
        same(SearchText.normalize("が", Locale.JAPANESE),
                SearchText.normalize("か\u3099", Locale.JAPANESE));

        Locale arabic = Locale.forLanguageTag("ar-SA");
        String composedHamza = SearchText.normalize("أَل", arabic);
        String decomposedHamza = SearchText.normalize("ا\u0654َل", arabic);
        same(composedHamza, decomposedHamza);
        same("\u0623\u064e\u0644", composedHamza);
        // Search text stays in logical storage order; this helper does no Arabic shaping or bidi reordering.
        same("\u0644\u0627", SearchText.normalize("لا", arabic));
    }

    private static void testWhitespaceAndAscii() {
        same("search text", SearchText.normalize("\t  Search\n  Text \r", Locale.ENGLISH));
        String ascii = "Conveyor Belt 1.21.1";
        same(ascii.toLowerCase(Locale.ENGLISH), SearchText.normalize("  " + ascii + "  ", Locale.ENGLISH));
    }

    private static void testCanonicalCorpus(Map<String, Map<String, String>> corpus) {
        Set<String> canonicalizationOnly = new HashSet<>();
        Set<String> expectedCanonicalizationOnly = Set.of(
                "ar_sa/ieponder.ponder.shared.garden_cloche_3");
        int total = 0;
        int rawUnchanged = 0;
        for (var localeEntry : corpus.entrySet()) {
            Locale locale = Locale.forLanguageTag(localeEntry.getKey().replace('_', '-'));
            for (var textEntry : localeEntry.getValue().entrySet()) {
                String raw = textEntry.getValue();
                String old = raw.trim().replaceAll("\\s+", " ").toLowerCase(locale);
                String current = SearchText.normalize(raw, locale);
                total++;
                if (old.equals(current)) rawUnchanged++;
                if (!Normalizer.normalize(old, Normalizer.Form.NFC).equals(current))
                    throw new AssertionError("NFC(old) differs from new at " + localeEntry.getKey() + "/"
                            + textEntry.getKey() + " old=" + escaped(old) + " new=" + escaped(current));
                if (old.equals(current)) continue;
                String identity = localeEntry.getKey() + "/" + textEntry.getKey();
                canonicalizationOnly.add(identity);
                System.out.println("NFC-ONLY corpus change " + identity
                        + " old=" + escaped(old) + " new=" + escaped(current));
            }
        }
        if (total != 7_040 || rawUnchanged != 7_039)
            throw new AssertionError("Expected NFC(old)==new for 7,040 entries and raw old==new for 7,039; got "
                    + total + " and " + rawUnchanged);
        if (!canonicalizationOnly.equals(expectedCanonicalizationOnly))
            throw new AssertionError("Unexpected canonical corpus normalization changes: " + canonicalizationOnly);
        assertions++;
    }

    private static Map<String, Map<String, String>> readCorpus() throws Exception {
        Map<String, Map<String, String>> corpus = new HashMap<>();
        var input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        for (String line; (line = input.readLine()) != null;) {
            String[] fields = line.split("\t", 3);
            if (fields.length != 3) throw new AssertionError("Malformed corpus line");
            String value = new String(Base64.getDecoder().decode(fields[2]), StandardCharsets.UTF_8);
            String previous = corpus.computeIfAbsent(fields[0], ignored -> new HashMap<>()).put(fields[1], value);
            if (previous != null) throw new AssertionError("Duplicate corpus key " + fields[0] + "/" + fields[1]);
        }
        if (corpus.size() != 22 || corpus.values().stream().anyMatch(rows -> rows.size() != 320))
            throw new AssertionError("Expected 22 locales × 320 keys; got " + corpus.size() + " locales, key counts "
                    + corpus.values().stream().map(Map::size).distinct().toList());
        return corpus;
    }

    private static String required(Map<String, Map<String, String>> corpus, String locale, String key) {
        String value = corpus.getOrDefault(locale, Map.of()).get(key);
        if (value == null) throw new AssertionError("Missing current corpus key " + locale + "/" + key);
        return value;
    }

    private static String escaped(String value) {
        StringBuilder result = new StringBuilder();
        value.codePoints().forEach(codePoint -> {
            if (codePoint >= 0x20 && codePoint <= 0x7e) result.appendCodePoint(codePoint);
            else result.append(String.format("\\u{%X}", codePoint));
        });
        return result.toString();
    }

    private static void same(String expected, String actual) {
        if (!expected.equals(actual)) throw new AssertionError("Expected <" + escaped(expected)
                + "> but got <" + escaped(actual) + ">");
        assertions++;
    }
}
