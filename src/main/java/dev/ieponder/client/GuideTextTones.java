package dev.ieponder.client;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.createmod.catnip.theme.Color;
import net.createmod.ponder.api.PonderPalette;
import net.minecraft.client.Minecraft;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Author-selected intent for each localized display unit, independent of scene outlines. */
public final class GuideTextTones {
    public enum Tone {
        NORMAL(PonderPalette.WHITE), WARNING(PonderPalette.RED), HINT(PonderPalette.GREEN);

        private final PonderPalette palette;
        Tone(PonderPalette palette) { this.palette = palette; }
        public PonderPalette palette() { return palette; }
        /** Same bright RGB as native Ponder body text, without changing the shared palette. */
        // Catnip's getRGB includes alpha; Minecraft TextColor stores only the lower 24 bits.
        public int textColor() { return palette.getColorObject().copy().mixWith(new Color(-35), .5f).getRGB() & 0xFFFFFF; }
    }

    public record Unit(String text, Tone tone) {}

    private GuideTextTones() {}
    private static final class Holder {
        private static final Map<String, Map<String, List<Tone>>> ALL = load();
    }

    public static List<Unit> units(String key, String text) {
        return units(Minecraft.getInstance().getLanguageManager().getSelected(), key, text);
    }

    public static List<Unit> units(String locale, String key, String text) {
        var authored = Holder.ALL.getOrDefault(locale, Holder.ALL.get("en_us"));
        var tones = authored.get(key);
        var parts = CaptionUnits.split(text);
        if (tones == null || tones.size() != parts.size())
            throw new IllegalStateException("Localized tone/unit mismatch: " + locale + "/" + key);
        var result = new ArrayList<Unit>();
        for (int i = 0; i < parts.size(); i++) result.add(new Unit(parts.get(i), tones.get(i)));
        return List.copyOf(result);
    }

    private static Map<String, Map<String, List<Tone>>> load() {
        var type = new TypeToken<Map<String, Map<String, List<Tone>>>>() {}.getType();
        var stream = Objects.requireNonNull(GuideTextTones.class.getResourceAsStream(
                "/assets/ieponder/guide/text_tones.json"), "Missing authored text tones");
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            Map<String, Map<String, List<Tone>>> values = new Gson().fromJson(reader, type);
            if (values == null || !values.containsKey("en_us")) throw new IllegalStateException("Missing English text tones");
            values.forEach((locale, keys) -> keys.forEach((key, tones) -> {
                if (tones == null || tones.isEmpty() || tones.contains(null))
                    throw new IllegalStateException("Invalid authored text tone: " + locale + "/" + key);
            }));
            return Map.copyOf(values);
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Unable to read authored text tones", error);
        }
    }
}
