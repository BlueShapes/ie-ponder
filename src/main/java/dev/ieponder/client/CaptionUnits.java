package dev.ieponder.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Authored newlines are display boundaries, never font-width or word-count cutoffs. */
public final class CaptionUnits {
    private static final Set<String> ABBREVIATIONS = Set.of(
            "e.g.", "i.e.", "dr.", "mr.", "mrs.", "ms.", "prof.", "sr.", "jr.",
            "vs.", "etc.", "approx.", "fig.", "no.", "z.b.", "d.h.", "m.", "mme.",
            "np.", "itp.", "itd.", "m.in.", "ca.", "bzw.", "u.a.", "usw.", "vgl.", "ggf.",
            "p.ex.", "sra.", "dra.", "env.", "c.-à-d.", "örn.", "vb.", "např.", "atd.",
            "tj.", "tzn.", "ex.", "p.ej.", "aprox.", "mis.", "dll.", "dsb.", "cth.", "vd.", "v.v.", "डॉ.");
    private static final String STOPS = ".．!?。｡！？।॥؟۔";
    private static final String CLOSERS = "\"'”’»」』）)]";
    private static final Pattern DANGLING_ENGLISH = Pattern.compile(
            "(?iu).*\\b(?:does|do|did|is|are|was|were|will|would|can|could|must|the|a|an|not|to|in|on|at|for|of|from|with|without|by|and|or|but|if|when|because|as|that)\\s*$");

    private CaptionUnits() {}

    public static List<String> split(String text) {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("Empty guide caption");
        if (text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0) {
            // A newline is an explicit authored unit, including any paired continuation marks.
            var result = text.lines().map(String::strip).toList();
            if (result.stream().anyMatch(String::isEmpty))
                throw new IllegalArgumentException("Empty authored caption unit");
            return result;
        }
        // Legacy/external text may have several complete sentences, but no author boundaries.
        // Keep an unrecognized or oversized sentence whole; authoring checks report it.
        return sentences(text.strip());
    }

    private static List<String> sentences(String text) {
        var result = new ArrayList<String>();
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (STOPS.indexOf(c) < 0 || (c == '.' || c == '．') && protectedDot(text, i)) continue;
            int end = i + 1;
            while (end < text.length() && (STOPS.indexOf(text.charAt(end)) >= 0
                    || CLOSERS.indexOf(text.charAt(end)) >= 0)) end++;
            if ((c == '.' || c == '．') && end < text.length() && !Character.isWhitespace(text.charAt(end))) continue;
            String sentence = text.substring(start, end).strip();
            if (!sentence.isEmpty()) result.add(sentence);
            start = end;
            i = end - 1;
        }
        if (start < text.length() && !text.substring(start).isBlank()) result.add(text.substring(start).strip());
        return List.copyOf(result);
    }

    private static boolean protectedDot(String text, int i) {
        if (i > 0 && (text.charAt(i - 1) == '.' || text.charAt(i - 1) == '．')
                || i + 1 < text.length() && (text.charAt(i + 1) == '.' || text.charAt(i + 1) == '．')) return true;
        if (i > 0 && i + 1 < text.length() && Character.isLetterOrDigit(text.charAt(i - 1))
                && Character.isLetterOrDigit(text.charAt(i + 1))) return true;
        String prefix = text.substring(0, i + 1).toLowerCase(Locale.ROOT);
        for (String abbreviation : ABBREVIATIONS) {
            int start = prefix.length() - abbreviation.length();
            if (start >= 0 && prefix.endsWith(abbreviation)
                    && (start == 0 || Character.isWhitespace(prefix.charAt(start - 1))
                    || "(\"'[{«「『".indexOf(prefix.charAt(start - 1)) >= 0)) return true;
        }
        int start = i;
        while (start > 0 && (Character.isLetter(text.charAt(start - 1)) || text.charAt(start - 1) == '.')) start--;
        String token = text.substring(start, i + 1).toLowerCase(Locale.ROOT);
        if (token.matches("(?:[a-z]\\.){2,}")) return true;
        // Unknown short abbreviations followed by a lowercase word/number are ambiguous.
        // Keep that text intact rather than manufacture a fragment at its period.
        int next = i + 1;
        while (next < text.length() && Character.isWhitespace(text.charAt(next))) next++;
        return token.matches("[\\p{L}]{1,3}\\.") && next < text.length()
                && (Character.isLowerCase(text.charAt(next)) || Character.isDigit(text.charAt(next)));
    }

    /** Structural authoring checks supplement the recorded semantic review in every language. */
    public static List<String> problems(String text) {
        return problems(text, "en_us");
    }

    /** Apply the English fragment heuristic only to English source text. */
    public static List<String> problems(String text, String locale) {
        var problems = new ArrayList<String>();
        List<String> units;
        try { units = split(text); }
        catch (IllegalArgumentException error) { return List.of(error.getMessage()); }
        boolean expectsContinuation = false;
        for (int i = 0; i < units.size(); i++) {
            String unit = units.get(i);
            boolean before = unit.startsWith("…");
            boolean after = unit.endsWith("…");
            if (before != expectsContinuation) problems.add("unit " + i + ": unmatched continuation prefix/suffix");
            String core = unit.substring(before ? 1 : 0, unit.length() - (after && unit.length() > 1 ? 1 : 0)).strip();
            if (core.isEmpty() || core.equals("…")) problems.add("unit " + i + ": isolated continuation mark");
            else {
                if ("en_us".equals(locale) && DANGLING_ENGLISH.matcher(core).matches())
                    problems.add("unit " + i + ": incomplete English clause or continuation");
                if (!after && hasSentenceEndingStop(core))
                    problems.add("unit " + i + ": sentence-final punctuation should be omitted");
                if (sentences(core).size() > 1) problems.add("unit " + i + ": multiple sentences in one authored unit");
            }
            expectsContinuation = after;
        }
        if (expectsContinuation) problems.add("missing final continuation unit");
        return List.copyOf(problems);
    }

    private static boolean hasSentenceEndingStop(String core) {
        int end = core.length() - 1;
        while (end >= 0 && CLOSERS.indexOf(core.charAt(end)) >= 0) end--;
        if (end < 0 || ".．。｡।॥۔".indexOf(core.charAt(end)) < 0) return false;
        return !((core.charAt(end) == '.' || core.charAt(end) == '．') && protectedDot(core, end));
    }
}
