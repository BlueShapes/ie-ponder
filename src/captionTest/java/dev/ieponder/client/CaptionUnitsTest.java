package dev.ieponder.client;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/** Dependency-free regression tests, runnable without starting Minecraft. */
public final class CaptionUnitsTest {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("--segment")) {
            var input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
            for (String line; (line = input.readLine()) != null;) {
                String text = new String(Base64.getDecoder().decode(line), StandardCharsets.UTF_8);
                System.out.println(Base64.getEncoder().encodeToString(
                        String.join("\n", CaptionUnits.split(text)).getBytes(StandardCharsets.UTF_8)));
            }
            return;
        }
        equal("The processing machine does not accept fluids. Supply power before starting.",
                "The processing machine does not accept fluids.", "Supply power before starting.");
        equal("Dr. Smith uses e.g. minecraft:stone and version 1.21.1. Supply 3.5 units.",
                "Dr. Smith uses e.g. minecraft:stone and version 1.21.1.", "Supply 3.5 units.");
        equal("Use mod:item.variant. Check the output.", "Use mod:item.variant.", "Check the output.");
        equal("Use mod:dr. Supply power.", "Use mod:dr.", "Supply power.");
        equal("Use ca. 3.5 units. Supply power.", "Use ca. 3.5 units.", "Supply power.");
        equal("Skieruj koniec do ekwipunku, np. skrzyni.", "Skieruj koniec do ekwipunku, np. skrzyni.");
        equal("Použijte např. minecraft:stone.", "Použijte např. minecraft:stone.");
        equal("Utilisez p.ex. minecraft:stone.", "Utilisez p.ex. minecraft:stone.");
        equal("डॉ. शर्मा पानी देते हैं।", "डॉ. शर्मा पानी देते हैं।");
        equal("水を供給してください。電力も必要です。", "水を供給してください。", "電力も必要です。");
        equal("请供水。然后供电！", "请供水。", "然后供电！");
        equal("पानी दें। ऊर्जा दें।", "पानी दें।", "ऊर्जा दें।");
        equal("هل توجد طاقة؟ افحص الخرج.", "هل توجد طاقة؟", "افحص الخرج.");
        equal("It waits… then starts.", "It waits… then starts.");
        equal("A single extremelylongunbrokenword".repeat(30) + ".",
                "A single extremelylongunbrokenword".repeat(30) + ".");
        equal("The machine does not accept fluids.\nSupply power before starting.",
                "The machine does not accept fluids.", "Supply power before starting.");
        String continued = "If the available power cannot meet demand…\n…all connected machines stop";
        equal(continued, "If the available power cannot meet demand…", "…all connected machines stop");
        valid(continued);
        String triple = "When the source runs out of power…\n…and the buffer becomes empty…\n…processing stops";
        equal(triple, "When the source runs out of power…", "…and the buffer becomes empty…", "…processing stops");
        valid(triple);
        String japaneseContinuation = "応力が供給量を超えると…\n…つながった装置はすべて止まります";
        equal(japaneseContinuation, "応力が供給量を超えると…", "…つながった装置はすべて止まります");
        valid(japaneseContinuation);
        invalid("…");
        invalid("…The machine stops.");
        invalid("If the power runs out…");
        invalid("If the power runs out…\nThe machine stops.");
        invalid("Power is required.\n…The machine stops.");
        invalid("When power runs out…\n…\n…processing stops.");
        invalid("The processing machine does…\n…not accept fluids.");
        invalid("The processing machine does\nnot accept fluids.");
        invalid("Power is required. Check the output.\nSupply ingredients.");
        valid("Dr. Smith uses version 1.21.1\nSupply 3.5 units of minecraft:water");
        equal("The processing machine does not accept fluids\nSupply power before starting",
                "The processing machine does not accept fluids", "Supply power before starting");
        equal("入力された液体を接続先へ運びます\nタンクから自動で吸い出しません",
                "入力された液体を接続先へ運びます", "タンクから自動で吸い出しません");
        equal("पानी दें\nऊर्जा दें", "पानी दें", "ऊर्जा दें");
        equal("زوّد الخزان\nلا تسحب الأنابيب السائل تلقائيًا", "زوّد الخزان", "لا تسحب الأنابيب السائل تلقائيًا");
        valid("The processing machine does not accept fluids");
        valid("Supply version 1.21.1 at example.ie and use Dr. Smith's blueprint");
        valid("Move ie:transformer.advanced through example.ie and keep version 1.21.1");
        invalid("The processing machine does");
        invalid("The processing machine will");
        invalid("The machine stops without");
        invalid("The machine stops without…\n…power");
        invalid("Place the bricks in");
        invalid("Power is required\n…The machine stops");
        valid("If the available power cannot meet demand…\n…all connected machines stop");
        invalid("First instruction\n…unpaired continuation");
        invalid("First instruction…\ncontinuation prefix missing");
        invalid("The machine stops.");
        invalid("液体を送ります。");
        invalid("पानी दें।");
        invalid("زوّد الخزان۔");
        validFor("tr_tr", "Model, kurulumdaki dizilimi gösterir");
        valid("Ask Dr.");
        if (args.length > 0 && args[0].equals("--audit")) {
            int entries = 0, units = 0, failures = 0;
            var input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
            for (String line; (line = input.readLine()) != null;) {
                var fields = line.split("\t", 3);
                String text = new String(Base64.getDecoder().decode(fields[2]), StandardCharsets.UTF_8);
                var problems = CaptionUnits.problems(text, fields[0]);
                if (!problems.isEmpty()) {
                    System.out.println(fields[0] + "/" + fields[1] + ": " + problems);
                    failures++;
                }
                entries++;
                units += CaptionUnits.split(text).size();
            }
            if (failures > 0) throw new AssertionError(failures + " caption entries need authoring correction");
            System.out.println("PASS: " + entries + " localized captions, " + units + " authored complete units");
        }
        System.out.println("PASS: " + assertions + " sentence/continuation regression assertions");
    }

    private static void equal(String input, String... expected) {
        if (!CaptionUnits.split(input).equals(List.of(expected))) throw new AssertionError(input);
        assertions++;
    }

    private static void valid(String input) {
        if (!CaptionUnits.problems(input).isEmpty()) throw new AssertionError(input + ": " + CaptionUnits.problems(input));
        assertions++;
    }

    private static void invalid(String input) {
        if (CaptionUnits.problems(input).isEmpty()) throw new AssertionError("Invalid unit accepted: " + input);
        assertions++;
    }

    private static void validFor(String locale, String input) {
        if (!CaptionUnits.problems(input, locale).isEmpty())
            throw new AssertionError(locale + "/" + input + ": " + CaptionUnits.problems(input, locale));
        assertions++;
    }
}
