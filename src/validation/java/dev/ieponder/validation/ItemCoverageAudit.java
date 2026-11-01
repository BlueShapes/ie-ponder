package dev.ieponder.validation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.GsonBuilder;
import com.mojang.serialization.JsonOps;
import dev.ieponder.GuideConstants;
import dev.ieponder.GuideResources;
import dev.ieponder.client.GuideBook;
import dev.ieponder.client.GuideChapter;
import dev.ieponder.client.GuideItemDescriptions;
import dev.ieponder.client.GuideNavigation;
import dev.ieponder.client.GuidePlugin;
import dev.ieponder.client.NativePonderItemStackContext;
import dev.ieponder.mixin.PonderUIAccessor;
import net.createmod.ponder.api.registration.StoryBoardEntry;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.PonderTooltipHandler;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.createmod.ponder.enums.PonderKeybinds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.DyeColor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarFile;

/** Independent runtime audit of IE item coverage, serialized variants and new tooltip fonts. */
final class ItemCoverageAudit {
    private static final String IE_NAMESPACE = "immersiveengineering";
    private static final int TOOLTIP_WIDTH = 240;
    private static final int MAX_TOOLTIP_LINES = 3;

    record StackCase(String key, ResourceLocation itemId, ItemStack stack, String entrance,
                     String lesson, String chapter) {
        ItemStack copyStack() { return stack.copy(); }
    }

    private record WTarget(String lesson, String chapter) {}

    private final Path expectationsPath;
    private final Path releaseJarPath;
    private final JsonObject expectations;
    private final RegistryOps<JsonElement> registryOps;
    private final List<String> allIeItemIds;
    private final Set<String> ponderItemIds;
    private final Set<String> ieStoryboardItemIds;
    private final Set<String> shiftItemIds;
    private final Set<String> registryExclusionIds;
    private final Map<ResourceLocation, WTarget> expectedW;
    private final Map<String, StackCase> stackCases;
    private final List<String> newDescriptionKeys;
    private final List<String> fontDescriptionKeys;
    private final Map<String, Map<String, Object>> localeFontResults = new LinkedHashMap<>();
    private final List<Map<String, Object>> fontProblems = new ArrayList<>();
    private final Map<String, String> releaseEntryHashes;
    private final String releaseJarSha256;
    private final Map<String, String> validationClassHashes;
    private int checkedShiftStacks;
    private int checkedShiftDescriptions;
    private int checkedFactoryCases;
    private int checkedPonderTooltipHints;
    private int checkedExpectedW;
    private int checkedManualExpectedIds;
    private int checkedVariantFamilies;
    private int checkedComponentShiftStacks;
    private int checkedRegistryExclusions;
    private int checkedTooltipUnits;
    private int checkedGlyphCodepoints;
    private int arabicShapedUnits;
    private int fontLocalesProcessed;
    private int maximumTooltipLines;
    private boolean arabicShapingChecked;
    private String fontProblemsFile;
    private Map<String, Object> canonicalDyeColorCoverage;

    private ItemCoverageAudit(Minecraft client) throws IOException {
        String expectationsProperty = System.getProperty("ieponder.coverageExpectations", "");
        if (expectationsProperty.isBlank())
            throw new IllegalStateException("Missing -D ieponder.coverageExpectations fixture path");
        expectationsPath = Path.of(expectationsProperty).toAbsolutePath().normalize();
        if (!Files.isRegularFile(expectationsPath))
            throw new IllegalStateException("Coverage expectation fixture does not exist: " + expectationsPath);
        expectations = JsonParser.parseString(Files.readString(expectationsPath)).getAsJsonObject();
        requireArray(expectations, "manualExpectedIds");
        requireArray(expectations, "variantFamilies");
        requireArray(expectations, "componentStackCases");
        JsonObject w = expectations.getAsJsonObject("expectedW");
        if (w == null) throw new IllegalStateException("Coverage fixture needs expectedW object");
        requireArray(expectations, "newDescriptionKeys");
        releaseJarPath = Path.of(requiredSystemProperty("ieponder.coverageJar")).toAbsolutePath().normalize();
        registryOps = RegistryOps.create(JsonOps.INSTANCE, client.level.registryAccess());
        releaseEntryHashes = new LinkedHashMap<>();
        releaseJarSha256 = verifyReleaseJar(releaseJarPath, releaseEntryHashes);
        validationClassHashes = Map.of(
                "GuideValidation.class", sha256(readResource(ItemCoverageAudit.class,
                        "/dev/ieponder/validation/GuideValidation.class")),
                "ItemCoverageAudit.class", sha256(readResource(ItemCoverageAudit.class,
                        "/dev/ieponder/validation/ItemCoverageAudit.class")));

        var entries = PonderIndex.getSceneAccess().getRegisteredEntries();
        var ponderIds = new TreeSet<String>();
        var ownedIds = new TreeSet<String>();
        var ownedEntries = new LinkedHashMap<ResourceLocation, List<StoryBoardEntry>>();
        for (var entry : entries) {
            String id = entry.getKey().toString();
            ponderIds.add(id);
            if (GuideConstants.MOD_ID.equals(entry.getValue().getNamespace())) {
                ownedIds.add(id);
                ownedEntries.computeIfAbsent(entry.getKey(), unused -> new ArrayList<>()).add(entry.getValue());
            }
        }
        ponderItemIds = Set.copyOf(ponderIds);
        ieStoryboardItemIds = Set.copyOf(ownedIds);
        allIeItemIds = BuiltInRegistries.ITEM.keySet().stream()
                .filter(id -> IE_NAMESPACE.equals(id.getNamespace()))
                .map(ResourceLocation::toString).sorted().toList();
        if (allIeItemIds.isEmpty()) throw new IllegalStateException("Immersive Engineering item registry is empty");

        var described = new LinkedHashSet<String>();
        var descriptionKeys = new LinkedHashSet<String>();
        GuideItemDescriptions.descriptions().forEach(description -> {
            description.items().forEach(item -> described.add(GuideResources.parse(item).toString()));
            descriptionKeys.add(description.translationKey());
        });
        fontDescriptionKeys = descriptionKeys.stream().sorted().toList();
        for (String key : stringArray(expectations.getAsJsonArray("newDescriptionKeys"), "newDescriptionKeys"))
            if (!descriptionKeys.contains(key))
                throw new IllegalStateException("Coverage fixture names a key that is not an item description: " + key);
        var actualShift = new TreeSet<String>();
        for (String textId : allIeItemIds) {
            ResourceLocation id = ResourceLocation.parse(textId);
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(id));
            JsonElement before = encodeStack(stack);
            var collapsed = new ArrayList<Component>();
            collapsed.add(stack.getHoverName());
            GuideItemDescriptions.append(stack, collapsed, false);
            var expanded = new ArrayList<Component>();
            expanded.add(stack.getHoverName());
            GuideItemDescriptions.append(stack, expanded, true);
            JsonElement after = encodeStack(stack);
            if (!before.equals(after))
                throw new IllegalStateException("Shift tooltip changed registered item data: " + textId);
            checkedShiftStacks++;
            if (collapsed.size() > 1 || expanded.size() > 1) {
                if (collapsed.size() < 2 || expanded.size() < 2)
                    throw new IllegalStateException("Shift description did not provide both native hint and expanded text: " + textId);
                actualShift.add(textId);
            }
            if (described.contains(textId)) {
                if (collapsed.size() < 2 || expanded.size() < 2)
                    throw new IllegalStateException("Registered Shift description did not append: " + textId);
                checkedShiftDescriptions++;
            }
        }
        shiftItemIds = Set.copyOf(actualShift);
        registryExclusionIds = parseRegistryExclusions(requireArray(expectations, "registryExclusions"));

        expectedW = parseExpectedW(w);
        stackCases = parseStackCases(expectations.getAsJsonArray("componentStackCases"), ownedEntries);
        newDescriptionKeys = stringArray(expectations.getAsJsonArray("newDescriptionKeys"), "newDescriptionKeys");
        if (new LinkedHashSet<>(newDescriptionKeys).size() != newDescriptionKeys.size())
            throw new IllegalStateException("Coverage fixture repeats a newDescriptionKey");

        verifyExpectedW(ownedEntries);
        verifyIndependentExpectations();
        verifyStackFactoryCases(client, ownedEntries);
        verifyShiftStackCases();
        verifyCanonicalDyeColorStacks();
        verifyItemCommandAliases();
        var result = new LinkedHashMap<String, Object>();
        result.put("expectationsPath", expectationsPath.toString());
        result.put("expectationsSha256", sha256(Files.readAllBytes(expectationsPath)));
        result.put("releaseJarPath", releaseJarPath.toString());
        result.put("releaseJarSha256", releaseJarSha256);
        result.put("releaseEntriesSha256", releaseEntryHashes);
        result.put("validationClassSha256", validationClassHashes);
        result.put("allIeItemIds", allIeItemIds);
        result.put("ieRegistryItemCount", allIeItemIds.size());
        result.put("allPonderItemIds", ponderIds);
        result.put("allPonderItemCount", ponderIds.size());
        result.put("ieponderStoryboardItemIds", ownedIds);
        result.put("ieponderStoryboardItemCount", ownedIds.size());
        result.put("actualShiftItemIds", new TreeSet<>(shiftItemIds));
        result.put("actualShiftItemCount", shiftItemIds.size());
        result.put("registryExclusions", expectations.getAsJsonArray("registryExclusions"));
        result.put("registryExclusionIdsChecked", new TreeSet<>(registryExclusionIds));
        result.put("registryExclusionCount", checkedRegistryExclusions);
        result.put("accountedForIeItemIds", accountedForIeItemIds());
        result.put("accountedForIeItemCount", accountedForIeItemIds().size());
        result.put("unaccountedIeItemIds", List.of());
        result.put("allIeRegistryItemsAccountedFor", checkedRegistryExclusions == registryExclusionIds.size()
                && accountedForIeItemIds().size() == allIeItemIds.size());
        result.put("shiftStacksChecked", checkedShiftStacks);
        result.put("shiftDescriptionItemsChecked", checkedShiftDescriptions);
        result.put("expectedW", expectedWReport());
        result.put("expectedWCount", expectedW.size());
        result.put("componentStackCases", stackCaseReport());
        result.put("componentStackCaseCount", stackCases.size());
        result.put("canonicalDyeColorShiftCoverage", canonicalDyeColorCoverage);
        result.put("manualExpectedIds", stringArray(expectations.getAsJsonArray("manualExpectedIds"), "manualExpectedIds"));
        result.put("manualExpectedIdCount", checkedManualExpectedIds);
        result.put("variantFamilies", expectations.getAsJsonArray("variantFamilies"));
        result.put("variantFamilyCount", checkedVariantFamilies);
        result.put("newDescriptionKeys", newDescriptionKeys);
        result.put("newDescriptionKeyCount", newDescriptionKeys.size());
        result.put("shiftProbe", "GuideItemDescriptions.append(false/true); physical keyboard Shift was not simulated");
        result.put("nativeFactoriesChecked", checkedFactoryCases);
        result.put("nativePonderTooltipHintsChecked", checkedPonderTooltipHints);
        result.put("expectedWChecked", checkedExpectedW);
        result.put("manualExpectedIdsChecked", checkedManualExpectedIds);
        result.put("variantFamiliesChecked", checkedVariantFamilies);
        result.put("componentShiftStacksChecked", checkedComponentShiftStacks);
        result.put("allRegistryShiftAppendsPreservedStackData", checkedShiftStacks == allIeItemIds.size());
        staticReport = result;
    }

    private Map<String, Object> staticReport;

    static ItemCoverageAudit begin(Minecraft client) throws IOException { return new ItemCoverageAudit(client); }

    StackCase firstPonderCase() {
        return stackCases.values().stream().filter(test -> "ponder".equals(test.entrance()))
                .sorted(Comparator.comparing(StackCase::key))
                .findFirst().orElseThrow(() -> new IllegalStateException("Coverage fixture needs a Ponder componentStackCase"));
    }

    StackCase stackCase(String key) {
        StackCase result = stackCases.get(key);
        if (result == null) throw new IllegalStateException("Unknown component stack case: " + key);
        return result;
    }

    List<String> itemCommands() { return List.of("manual", "crusher", "arc_furnace"); }

    GuideBook.Lesson commandLesson(String query) { return GuideNavigation.findLessonForItem(query); }

    void validateDescriptionLocale(Minecraft client, String locale) {
        int localeUnits = 0;
        int localeGlyphs = 0;
        int localeMaximumLines = 0;
        int localeVisualLines = 0;
        int localeArabicVisualLines = 0;
        int localeProblemStart = fontProblems.size();
        var checkedKeys = new ArrayList<String>();
        for (String key : fontDescriptionKeys) {
            try {
                String text = Component.translatable(key).getString();
                if (text.isBlank() || text.equals(key) || text.contains("ieponder.")) {
                    recordFontProblem(locale, key, -1, "missing_translation", null,
                            "Translation is blank or unresolved: " + text);
                    continue;
                }
                List<dev.ieponder.client.GuideTextTones.Unit> units;
                try {
                    units = dev.ieponder.client.GuideTextTones.units(locale, key, text);
                } catch (RuntimeException exception) {
                    recordFontProblem(locale, key, -1, "invalid_authored_units", null, exception.toString());
                    continue;
                }
                if (units.isEmpty()) {
                    recordFontProblem(locale, key, -1, "missing_authored_units", null,
                            "Description has no authored display units");
                    continue;
                }
                checkedKeys.add(key);
                for (int unitIndex = 0; unitIndex < units.size(); unitIndex++) {
                    var unit = units.get(unitIndex);
                    localeUnits++;
                    List<String> lines;
                    try {
                        lines = GuideItemDescriptions.splitLogicalLines(client.font, unit.text(), TOOLTIP_WIDTH);
                    } catch (RuntimeException exception) {
                        recordFontProblem(locale, key, unitIndex, "line_split_failure", unit.text(), exception.toString());
                        continue;
                    }
                    if (lines.isEmpty()) {
                        recordFontProblem(locale, key, unitIndex, "empty_split", unit.text(),
                                "Authored unit produced no logical tooltip lines");
                        continue;
                    }
                    localeMaximumLines = Math.max(localeMaximumLines, lines.size());
                    if (lines.size() > MAX_TOOLTIP_LINES)
                        recordFontProblem(locale, key, unitIndex, "line_count_overflow", unit.text(),
                                "Authored unit uses " + lines.size() + " lines; maximum is " + MAX_TOOLTIP_LINES);
                    for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
                        String logicalLine = lines.get(lineIndex);
                        String visualText;
                        try {
                            var visualSequence = Component.literal(logicalLine).getVisualOrderText();
                            int visualWidth = client.font.width(visualSequence);
                            visualText = visualText(visualSequence);
                            if (visualWidth > TOOLTIP_WIDTH)
                                recordFontProblem(locale, key, unitIndex, "visual_width_overflow", unit.text(),
                                        "Rendered line " + lineIndex + " is " + visualWidth + " pixels (maximum "
                                                + TOOLTIP_WIDTH + "): " + logicalLine);
                        } catch (RuntimeException exception) {
                            recordFontProblem(locale, key, unitIndex, "visual_measurement_failure", unit.text(),
                                    "Rendered line " + lineIndex + ": " + exception);
                            continue;
                        }
                        localeVisualLines++;
                        if (locale.equals("ar_sa")) localeArabicVisualLines++;
                        try {
                            var glyphAudit = auditGlyphs(client.font, visualText);
                            localeGlyphs += glyphAudit.checkedCodepoints();
                            if (!glyphAudit.missingCodepoints().isEmpty())
                                recordFontProblem(locale, key, unitIndex, "missing_glyphs", unit.text(),
                                        "Rendered line " + lineIndex + " is missing glyphs " + glyphAudit.missingCodepoints());
                        } catch (RuntimeException exception) {
                            recordFontProblem(locale, key, unitIndex, "glyph_inspection_failure", unit.text(),
                                    "Rendered line " + lineIndex + ": " + exception);
                        }
                    }
                }
            } catch (RuntimeException exception) {
                recordFontProblem(locale, key, -1, "font_audit_failure", null, exception.toString());
            }
        }
        if (locale.equals("ar_sa")) {
            if (!client.font.isBidirectional() || localeArabicVisualLines == 0)
                recordFontProblem(locale, "", -1, "arabic_visual_shaping_unavailable", null,
                        "No Arabic description line passed through the active bidirectional visual-order path");
            arabicShapedUnits = localeArabicVisualLines;
            arabicShapingChecked = localeArabicVisualLines > 0 && client.font.isBidirectional();
        }
        checkedTooltipUnits += localeUnits;
        checkedGlyphCodepoints += localeGlyphs;
        maximumTooltipLines = Math.max(maximumTooltipLines, localeMaximumLines);
        localeFontResults.put(locale, Map.of("keys", checkedKeys, "keyCount", checkedKeys.size(), "units", localeUnits,
                "maximumLinesPerUnit", localeMaximumLines, "visualLinesChecked", localeVisualLines,
                "glyphCodepoints", localeGlyphs, "problemCount", fontProblems.size() - localeProblemStart,
                "tooltipWidth", TOOLTIP_WIDTH));
        fontLocalesProcessed++;
    }

    boolean allFontsChecked(List<String> locales) { return localeFontResults.keySet().containsAll(locales); }

    Map<String, Object> writeFontProblems(Path outputPath) throws IOException {
        var report = new LinkedHashMap<String, Object>();
        report.put("complete", fontLocalesProcessed == supportedLocales().size());
        report.put("passed", fontProblems.isEmpty() && fontLocalesProcessed == supportedLocales().size());
        report.put("localesProcessed", fontLocalesProcessed);
        report.put("expectedLocaleCount", supportedLocales().size());
        report.put("descriptionKeys", fontDescriptionKeys);
        report.put("descriptionKeyCount", fontDescriptionKeys.size());
        report.put("newDescriptionKeys", newDescriptionKeys);
        report.put("newDescriptionKeyCount", newDescriptionKeys.size());
        report.put("localeResults", new LinkedHashMap<>(localeFontResults));
        report.put("authoredUnitsChecked", checkedTooltipUnits);
        report.put("visualLinesChecked", localeFontResults.values().stream()
                .mapToInt(locale -> ((Number) locale.get("visualLinesChecked")).intValue()).sum());
        report.put("measurementPath", "Component.literal(logicalLine).getVisualOrderText(); returned tooltip text remains logical");
        report.put("problemCount", fontProblems.size());
        report.put("problems", List.copyOf(fontProblems));
        Files.writeString(outputPath, new GsonBuilder().setPrettyPrinting().create().toJson(report));
        fontProblemsFile = outputPath.getFileName().toString();
        return fontFailureSummary();
    }

    boolean hasFontProblems() { return !fontProblems.isEmpty(); }

    boolean fontProblemsWritten() { return fontProblemsFile != null; }

    Map<String, Object> fontFailureSummary() {
        var summary = new LinkedHashMap<String, Object>();
        if (fontProblemsFile != null) summary.put("coverageFontProblemsFile", fontProblemsFile);
        summary.put("fontProblemCount", fontProblems.size());
        summary.put("fontLocalesProcessed", fontLocalesProcessed);
        summary.put("fontLocaleCountExpected", supportedLocales().size());
        summary.put("fontDescriptionKeyCount", fontDescriptionKeys.size());
        summary.put("newDescriptionKeyCount", newDescriptionKeys.size());
        summary.put("fontDescriptionKeys", fontDescriptionKeys);
        summary.put("fontDescriptionKeyEvaluations", fontLocalesProcessed * fontDescriptionKeys.size());
        summary.put("fontDescriptionKeysWithValidUnits", localeFontResults.values().stream()
                .mapToInt(locale -> ((Number) locale.get("keyCount")).intValue()).sum());
        summary.put("fontDescriptionUnitsChecked", checkedTooltipUnits);
        summary.put("fontGlyphCodepointsChecked", checkedGlyphCodepoints);
        summary.put("arabicVisualLinesChecked", arabicShapedUnits);
        return summary;
    }

    Map<String, Object> result(List<String> locales) {
        if (!allFontsChecked(locales)) throw new IllegalStateException("Font checks did not cover every supported locale");
        if (!arabicShapingChecked) throw new IllegalStateException("Arabic visual-order shaping check did not run");
        if (!fontProblems.isEmpty()) throw new IllegalStateException("Coverage tooltip font problems were not cleared");
        if (fontProblemsFile == null) throw new IllegalStateException("Coverage tooltip font report was not written");
        var result = new LinkedHashMap<>(staticReport);
        result.put("checkedLocaleFonts", new LinkedHashMap<>(localeFontResults));
        result.put("fontLocalesChecked", new ArrayList<>(localeFontResults.keySet()));
        result.put("fontDescriptionKeys", fontDescriptionKeys);
        result.put("fontDescriptionKeyCount", fontDescriptionKeys.size());
        result.put("fontMeasurementPath", "Component.literal(logicalLine).getVisualOrderText(); returned tooltip text remains logical");
        result.put("fontProblemsFile", fontProblemsFile);
        result.put("fontProblemCount", fontProblems.size());
        result.put("fontDescriptionUnitsChecked", checkedTooltipUnits);
        result.put("fontGlyphCodepointsChecked", checkedGlyphCodepoints);
        result.put("maximumTooltipLinesPerUnit", maximumTooltipLines);
        result.put("arabicVisualOrderShapingChecked", arabicShapingChecked);
        result.put("arabicVisualLinesChecked", arabicShapedUnits);
        result.put("coverageAssertions", Map.of("allIeItemsShiftChecked", checkedShiftStacks == allIeItemIds.size(),
                "allExpectedWChecked", checkedExpectedW == expectedW.size(),
                "allManualExpectedIdsChecked", checkedManualExpectedIds == stringArray(
                        expectations.getAsJsonArray("manualExpectedIds"), "manualExpectedIds").size(),
                "allVariantFamiliesChecked", checkedVariantFamilies == expectations.getAsJsonArray("variantFamilies").size(),
                "allComponentStackFactoriesChecked", checkedFactoryCases == stackCases.values().stream()
                        .filter(test -> "ponder".equals(test.entrance())).count() * 2,
                "allNativePonderTooltipHintsChecked", checkedPonderTooltipHints == stackCases.values().stream()
                        .filter(test -> "ponder".equals(test.entrance())).count(),
                "allComponentShiftStacksChecked", checkedComponentShiftStacks == stackCases.values().stream()
                        .filter(test -> "shift".equals(test.entrance())).count(),
                "allCanonicalDyeColorsChecked", canonicalDyeColorCoverage != null
                        && ((Number) canonicalDyeColorCoverage.get("dyeColorsPerFamily")).intValue() == DyeColor.values().length
                        && ((Number) canonicalDyeColorCoverage.get("stacksChecked")).intValue()
                        == DyeColor.values().length * 2,
                "allIeRegistryItemsAccountedFor", checkedRegistryExclusions == registryExclusionIds.size()
                        && accountedForIeItemIds().size() == allIeItemIds.size(),
                "allFontLocalesChecked", allFontsChecked(locales)));
        return result;
    }

    private void verifyCanonicalDyeColorStacks() {
        var itemIds = List.of(IE_NAMESPACE + ":strip_curtain", IE_NAMESPACE + ":earmuffs");
        var reports = new ArrayList<Map<String, Object>>();
        DataComponentType<?> colorComponent = BuiltInRegistries.DATA_COMPONENT_TYPE.getOptional(
                ResourceLocation.parse(IE_NAMESPACE + ":color")).orElseThrow(() ->
                new IllegalStateException("IE color data component is not registered"));
        for (String itemIdText : itemIds) {
            ResourceLocation itemId = ResourceLocation.parse(itemIdText);
            if (!shiftItemIds.contains(itemIdText))
                throw new IllegalStateException("Canonical dye color item has no actual Shift description: " + itemIdText);
            if (!BuiltInRegistries.ITEM.containsKey(itemId))
                throw new IllegalStateException("Canonical dye color item is not registered: " + itemIdText);
            var item = BuiltInRegistries.ITEM.get(itemId);
            for (DyeColor dyeColor : DyeColor.values()) {
                int diffuseRgb = dyeColor.getTextureDiffuseColor() & 0x00ff_ffff;
                Object ieColor = ieColorFromDiffuseRgb(diffuseRgb);
                int expectedArgb = 0xff00_0000 | diffuseRgb;
                if (color4ToInt(ieColor) != expectedArgb)
                    throw new IllegalStateException("IE Color4 conversion did not preserve DyeColor diffuse RGB: "
                            + dyeColor.getName() + "/" + itemIdText);

                ItemStack stack = new ItemStack(item);
                setComponent(stack, colorComponent, ieColor);
                JsonElement serialized = encodeStack(stack);
                ItemStack roundTrip = decodeStack(serialized.getAsJsonObject());
                Object decodedColor = getComponent(roundTrip, colorComponent);
                if (decodedColor == null || color4ToInt(decodedColor) != expectedArgb
                        || !ItemStack.isSameItemSameComponents(stack, roundTrip))
                    throw new IllegalStateException("DyeColor stack codec changed IE color data: "
                            + dyeColor.getName() + "/" + itemIdText);

                JsonElement before = encodeStack(stack);
                var collapsed = new ArrayList<Component>();
                collapsed.add(stack.getHoverName());
                GuideItemDescriptions.append(stack, collapsed, false);
                var expanded = new ArrayList<Component>();
                expanded.add(stack.getHoverName());
                GuideItemDescriptions.append(stack, expanded, true);
                if (collapsed.size() < 2 || expanded.size() < 2)
                    throw new IllegalStateException("DyeColor stack did not receive both Shift tooltip paths: "
                            + dyeColor.getName() + "/" + itemIdText);
                if (!before.equals(encodeStack(stack)))
                    throw new IllegalStateException("DyeColor Shift tooltip mutated stack data: "
                            + dyeColor.getName() + "/" + itemIdText);

                reports.add(Map.of("item", itemIdText, "dyeColor", dyeColor.getName(),
                        "diffuseRgb", String.format("#%06X", diffuseRgb),
                        "ieColor4Argb", String.format("#%08X", expectedArgb),
                        "codecRoundTripPreserved", true, "collapsedAndExpandedShiftAppended", true,
                        "stackPreserved", true));
            }
        }
        canonicalDyeColorCoverage = Map.of("dyeColorsPerFamily", DyeColor.values().length,
                "families", List.of("immersiveengineering:strip_curtain", "immersiveengineering:earmuffs"),
                "stacksChecked", reports.size(), "cases", reports);
    }

    private static Object ieColorFromDiffuseRgb(int diffuseRgb) {
        try {
            Class<?> color4 = Class.forName("blusunrize.immersiveengineering.api.utils.Color4");
            return color4.getMethod("fromRGB", int.class).invoke(null, diffuseRgb);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot convert DyeColor diffuse RGB through IE Color4", exception);
        }
    }

    private static int color4ToInt(Object color4) {
        try {
            return (int) color4.getClass().getMethod("toInt").invoke(color4);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot read IE Color4 components", exception);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void setComponent(ItemStack stack, DataComponentType<?> type, Object value) {
        stack.set((DataComponentType) type, value);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object getComponent(ItemStack stack, DataComponentType<?> type) {
        return stack.get((DataComponentType) type);
    }

    private Map<ResourceLocation, WTarget> parseExpectedW(JsonObject rows) {
        var parsed = new LinkedHashMap<ResourceLocation, WTarget>();
        rows.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            ResourceLocation item = parseIeItemId(entry.getKey());
            JsonObject value = entry.getValue().getAsJsonObject();
            String lessonId = requiredString(value, "lesson");
            String chapterId = requiredString(value, "chapter");
            parsed.put(item, new WTarget(lessonId, chapterId));
        });
        return Map.copyOf(parsed);
    }

    private Map<String, StackCase> parseStackCases(JsonArray rows,
                                                   Map<ResourceLocation, List<StoryBoardEntry>> ownedEntries) {
        var parsed = new LinkedHashMap<String, StackCase>();
        for (JsonElement element : rows) {
            JsonObject row = element.getAsJsonObject();
            String key = requiredString(row, "id");
            JsonObject serializedStack = row.getAsJsonObject("stack");
            if (serializedStack == null) throw new IllegalStateException("Stack case has no serialized stack: " + key);
            ItemStack stack = decodeStack(serializedStack);
            if (stack.isEmpty() || stack.getCount() != 1)
                throw new IllegalStateException("Stack case must decode to one nonempty item: " + key);
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (!IE_NAMESPACE.equals(itemId.getNamespace()))
                throw new IllegalStateException("Component stack case is outside Immersive Engineering: " + key + "/" + itemId);
            String entrance = requiredString(row, "entrance");
            if (!Set.of("ponder", "shift").contains(entrance))
                throw new IllegalStateException("Unknown component stack entrance: " + key + "/" + entrance);
            String lesson = optionalString(row, "lesson");
            String chapter = optionalString(row, "chapter");
            if ((lesson == null) != (chapter == null))
                throw new IllegalStateException("Stack case must provide both lesson and chapter: " + key);
            if ("ponder".equals(entrance) && !ownedEntries.containsKey(itemId))
                throw new IllegalStateException("Ponder stack case has no ieponder-owned storyboard: " + key + "/" + itemId);
            if ("shift".equals(entrance) && !shiftItemIds.contains(itemId.toString()))
                throw new IllegalStateException("Shift stack case has no actual appended description: " + key + "/" + itemId);
            WTarget expected = expectedW.get(itemId);
            if (lesson == null && expected != null) {
                lesson = expected.lesson();
                chapter = expected.chapter();
            } else if (lesson != null && expected != null
                    && (!lesson.equals(expected.lesson()) || !chapter.equals(expected.chapter()))) {
                throw new IllegalStateException("Component stack lesson/chapter differs from expectedW: " + key);
            }
            if (parsed.putIfAbsent(key, new StackCase(key, itemId, stack.copy(), entrance, lesson, chapter)) != null)
                throw new IllegalStateException("Duplicate component stack case: " + key);
        }
        return Map.copyOf(parsed);
    }

    private void verifyExpectedW(Map<ResourceLocation, List<StoryBoardEntry>> ownedEntries) {
        for (var entry : expectedW.entrySet()) {
            ResourceLocation itemId = entry.getKey();
            WTarget target = entry.getValue();
            GuideBook.Lesson lesson = GuideBook.lessons().stream().filter(candidate -> candidate.id().equals(target.lesson()))
                    .findFirst().orElseThrow(() -> new IllegalStateException("expectedW names unknown lesson: " + target.lesson()));
            if (!lesson.chapter().equals(target.chapter()))
                throw new IllegalStateException("expectedW chapter does not match lesson: " + itemId);
            boolean manual = GuidePlugin.GUIDE_ITEM.equals(itemId);
            boolean lessonUsesItem = manual || itemId.equals(GuideResources.parse(lesson.icon()))
                    || lesson.components().stream().map(GuideResources::parse).anyMatch(itemId::equals);
            if (!lessonUsesItem) throw new IllegalStateException("expectedW item is not a component/icon of its lesson: " + itemId);
            List<StoryBoardEntry> entries = ownedEntries.get(itemId);
            ResourceLocation tagId = GuideResources.of(GuideConstants.MOD_ID, target.chapter());
            if (entries == null || entries.stream().noneMatch(board -> board.getTags().contains(tagId)))
                throw new IllegalStateException("expectedW has no registered owned chapter storyboard: " + itemId + "/" + tagId);
            if (manual && entries.size() != GuideBook.lessons().size())
                throw new IllegalStateException("The manual W entrance does not contain every active lesson");
            var registeredTag = PonderIndex.getTagAccess().getRegisteredTag(tagId);
            // The manual is a global course entrance. Its native scene context displays
            // the current lesson icon, which owns the chapter's component tag.
            ResourceLocation categoryItem = manual ? GuideResources.parse(lesson.icon()) : itemId;
            if (registeredTag == null || !PonderIndex.getTagAccess().getItems(registeredTag).contains(categoryItem))
                throw new IllegalStateException("expectedW is missing from native chapter tag items: " + itemId + "/" + tagId);
            checkedExpectedW++;
        }
    }

    private void verifyIndependentExpectations() {
        Set<String> covered = new LinkedHashSet<>(ieStoryboardItemIds);
        covered.addAll(shiftItemIds);
        for (String excludedId : registryExclusionIds) {
            requireKnownIeItem(excludedId, "registryExclusions");
            if (covered.contains(excludedId))
                throw new IllegalStateException("Registry exclusion is already covered by IE Ponder or Shift: " + excludedId);
            checkedRegistryExclusions++;
        }
        Set<String> unaccounted = new TreeSet<>(allIeItemIds);
        unaccounted.removeAll(covered);
        unaccounted.removeAll(registryExclusionIds);
        if (!unaccounted.isEmpty())
            throw new IllegalStateException("IE registry items lack Ponder, Shift or an explicit exclusion: " + unaccounted);
        for (String value : stringArray(expectations.getAsJsonArray("manualExpectedIds"), "manualExpectedIds")) {
            String item = parseIeItemId(value).toString();
            requireKnownIeItem(item, "manualExpectedIds");
            if (!covered.contains(item)) throw new IllegalStateException("Manual-related item has neither own Ponder nor Shift: " + item);
            checkedManualExpectedIds++;
        }
        for (JsonElement familyElement : expectations.getAsJsonArray("variantFamilies")) {
            JsonObject family = familyElement.getAsJsonObject();
            String familyId = requiredString(family, "id");
            for (String value : stringArray(family.getAsJsonArray("expected_ids"), "variantFamilies." + familyId + ".expected_ids")) {
                String item = parseIeItemId(value).toString();
                requireKnownIeItem(item, "variantFamilies." + familyId);
                if (!covered.contains(item))
                    throw new IllegalStateException("Uncovered item in variant family " + familyId + ": " + item);
            }
            checkedVariantFamilies++;
        }
        for (var entry : expectedW.entrySet()) {
            if (!ieStoryboardItemIds.contains(entry.getKey().toString()))
                throw new IllegalStateException("Expected W item is not registered by ieponder: " + entry.getKey());
        }
    }

    private Set<String> parseRegistryExclusions(JsonArray rows) {
        var parsed = new TreeSet<String>();
        for (JsonElement element : rows) {
            JsonObject exclusion = element.getAsJsonObject();
            String id = parseIeItemId(requiredString(exclusion, "id")).toString();
            requiredString(exclusion, "reason");
            requiredString(exclusion, "evidence");
            if (!parsed.add(id)) throw new IllegalStateException("Duplicate IE registry exclusion: " + id);
        }
        return Set.copyOf(parsed);
    }

    private List<String> accountedForIeItemIds() {
        var accounted = new TreeSet<String>(ieStoryboardItemIds);
        accounted.addAll(shiftItemIds);
        accounted.addAll(registryExclusionIds);
        return accounted.stream().filter(id -> id.startsWith(IE_NAMESPACE + ":")).toList();
    }

    private void verifyStackFactoryCases(Minecraft client, Map<ResourceLocation, List<StoryBoardEntry>> ownedEntries) {
        for (StackCase test : stackCases.values()) {
            if (!"ponder".equals(test.entrance())) continue;
            ResourceLocation chapterTag = test.chapter() == null
                    ? ownedEntries.get(test.itemId()).stream().flatMap(entry -> entry.getTags().stream())
                    .filter(tag -> GuideConstants.MOD_ID.equals(tag.getNamespace())).findFirst().orElse(null)
                    : GuideResources.of(GuideConstants.MOD_ID, test.chapter());
            PonderUI oneArgument = PonderUI.of(test.copyStack());
            PonderUI explicitNullTag = PonderUI.of(test.copyStack(), null);
            verifyNativeFactoryStack(oneArgument, test, chapterTag);
            verifyNativeFactoryStack(explicitNullTag, test, chapterTag);
            verifyNativePonderTooltipHint(client, test);
            if (chapterTag != null) {
                var registeredTag = PonderIndex.getTagAccess().getRegisteredTag(chapterTag);
                if (registeredTag == null || !PonderIndex.getTagAccess().getItems(registeredTag).contains(test.itemId()))
                    throw new IllegalStateException("Component stack W case has no native chapter item: " + test.key());
            }
            checkedFactoryCases += 2;
        }
    }

    private void verifyShiftStackCases() {
        for (StackCase test : stackCases.values()) {
            if (!"shift".equals(test.entrance())) continue;
            ItemStack original = test.copyStack();
            JsonElement before = encodeStack(original);
            var collapsed = new ArrayList<Component>();
            collapsed.add(original.getHoverName());
            GuideItemDescriptions.append(original, collapsed, false);
            var expanded = new ArrayList<Component>();
            expanded.add(original.getHoverName());
            GuideItemDescriptions.append(original, expanded, true);
            if (collapsed.size() < 2 || expanded.size() < 2)
                throw new IllegalStateException("Component Shift stack did not receive its native hint and description: "
                        + test.key());
            if (!before.equals(encodeStack(original)))
                throw new IllegalStateException("Shift description append mutated component stack data: " + test.key());
            checkedComponentShiftStacks++;
        }
    }

    private void verifyNativePonderTooltipHint(Minecraft client, StackCase test) {
        var tooltip = new ArrayList<Component>();
        var previousScreen = client.screen;
        try {
            client.screen = null;
            PonderTooltipHandler.addToTooltip(tooltip, test.copyStack());
        } finally {
            client.screen = previousScreen;
        }
        String keybind = PonderKeybinds.PONDER.message().getString();
        if (tooltip.stream().noneMatch(line -> line.getString().contains(keybind)))
            throw new IllegalStateException("Native Ponder hold-to-open hint did not appear for W stack: " + test.key());
        checkedPonderTooltipHints++;
    }

    private static void verifyNativeFactoryStack(PonderUI ui, StackCase test, ResourceLocation expectedChapterTag) {
        if (ui.getClass() != PonderUI.class)
            throw new IllegalStateException("Native W factory returned a guide-specific screen: " + test.key());
        if (!(ui instanceof NativePonderItemStackContext context)
                || !ItemStack.isSameItemSameComponents(context.ieponder$getSourceStack(), test.stack()))
            throw new IllegalStateException("Native W factory lost source item components: " + test.key());
        var tags = ((PonderUIAccessor) (Object) ui).ieponder$getTags().stream()
                .map(tag -> tag.getId()).collect(java.util.stream.Collectors.toSet());
        if (tags.stream().noneMatch(tag -> GuideConstants.MOD_ID.equals(tag.getNamespace())))
            throw new IllegalStateException("Native W factory has no registered category tags: " + test.key());
        if (expectedChapterTag != null && !tags.contains(expectedChapterTag))
            throw new IllegalStateException("Native W factory is missing its expected chapter tag: " + test.key());
    }

    private void verifyItemCommandAliases() {
        GuideBook.Lesson first = GuideChapter.active(GuideBook.lessons()).stream().findFirst()
                .map(chapter -> GuideBook.lessons().stream().filter(lesson -> lesson.chapter().equals(chapter.id()))
                        .findFirst().orElseThrow())
                .orElseThrow(() -> new IllegalStateException("There are no active lessons for the manual command"));
        requireCommandLesson("manual", first);
        for (String alias : List.of("crusher", "arc_furnace")) {
            ResourceLocation itemId = parseIeItemId(alias);
            GuideBook.Lesson expected = GuideBook.lessons().stream()
                    .filter(lesson -> itemId.equals(GuideResources.parse(lesson.icon())))
                    .findFirst().orElseGet(() -> GuideBook.lessons().stream()
                            .filter(lesson -> lesson.components().stream().map(GuideResources::parse).anyMatch(itemId::equals))
                            .findFirst().orElseThrow(() -> new IllegalStateException("Item command target is not a lesson component: " + alias)));
            requireCommandLesson(alias, expected);
        }
    }

    private static void requireCommandLesson(String query, GuideBook.Lesson expected) {
        GuideBook.Lesson actual = GuideNavigation.findLessonForItem(query);
        if (actual == null || !actual.id().equals(expected.id()))
            throw new IllegalStateException("Item command resolver mismatch for " + query + ": expected "
                    + expected.id() + ", got " + (actual == null ? "<none>" : actual.id()));
    }

    private String verifyReleaseJar(Path jarPath, Map<String, String> entryHashes) throws IOException {
        if (!Files.isRegularFile(jarPath)) throw new IllegalStateException("Completed release JAR is missing: " + jarPath);
        String jarHash = sha256(Files.readAllBytes(jarPath));
        var entries = new ArrayList<String>(List.of(
                "dev/ieponder/ImmersiveEngineeringPonder.class",
                "dev/ieponder/client/GuideBook.class",
                "dev/ieponder/client/GuidePlugin.class",
                "dev/ieponder/client/GuideNavigation.class",
                "dev/ieponder/client/GuideItemDescriptions.class",
                "dev/ieponder/client/GuideTextTones.class",
                "dev/ieponder/client/NativePonderItemStackContext.class",
                "dev/ieponder/mixin/PonderUIContextMixin.class",
                "dev/ieponder/mixin/PonderUIFactoryMixin.class",
                "assets/ieponder/guide/lessons.json",
                "assets/ieponder/guide/processing.json",
                "assets/ieponder/guide/item_descriptions.json",
                "assets/ieponder/guide/text_tones.json"));
        for (String locale : supportedLocales()) entries.add("assets/ieponder/lang/" + locale + ".json");
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            if (jar.stream().anyMatch(entry -> entry.getName().startsWith("dev/ieponder/validation/")))
                throw new IllegalStateException("Validation code leaked into the release JAR");
            for (String entry : entries) {
                var jarEntry = jar.getJarEntry(entry);
                if (jarEntry == null) throw new IllegalStateException("Release JAR is missing entry: " + entry);
                byte[] expected;
                try (InputStream in = jar.getInputStream(jarEntry)) { expected = in.readAllBytes(); }
                byte[] runtime = readRuntimeResource(entry);
                if (!Arrays.equals(expected, runtime))
                    throw new IllegalStateException("Runtime bytes do not match the release JAR entry: " + entry);
                entryHashes.put(entry, sha256(expected));
            }
        }
        return jarHash;
    }

    private static byte[] readRuntimeResource(String entry) throws IOException {
        try (InputStream stream = GuideBook.class.getResourceAsStream("/" + entry)) {
            if (stream == null) throw new IllegalStateException("Runtime validation cannot load resource/class: " + entry);
            return stream.readAllBytes();
        }
    }

    private JsonElement encodeStack(ItemStack stack) {
        return ItemStack.CODEC.encodeStart(registryOps, stack).getOrThrow(
                error -> new IllegalStateException("Cannot encode item stack for mutation check: " + error));
    }

    private ItemStack decodeStack(JsonObject json) {
        return ItemStack.CODEC.parse(registryOps, json).getOrThrow(
                error -> new IllegalStateException("Cannot decode item stack coverage fixture: " + error));
    }

    private static String visualText(FormattedCharSequence sequence) {
        var text = new StringBuilder();
        sequence.accept((index, style, codepoint) -> {
            text.appendCodePoint(codepoint);
            return true;
        });
        return text.toString();
    }

    private record GlyphAudit(int checkedCodepoints, List<String> missingCodepoints) {}

    private static GlyphAudit auditGlyphs(Font font, String text) {
        try {
            var method = Font.class.getDeclaredMethod("getFontSet", ResourceLocation.class);
            method.setAccessible(true);
            var fontSet = (net.minecraft.client.gui.font.FontSet) method.invoke(font,
                    net.minecraft.network.chat.Style.DEFAULT_FONT);
            var codepoints = new TreeSet<Integer>();
            text.codePoints().forEach(codepoints::add);
            int checked = 0;
            var missing = new ArrayList<String>();
            for (int codepoint : codepoints) {
                if (Character.isWhitespace(codepoint) || Character.isSpaceChar(codepoint)
                        || Character.getType(codepoint) == Character.FORMAT) continue;
                if (fontSet.getGlyphInfo(codepoint, false) == net.minecraft.client.gui.font.glyphs.SpecialGlyphs.MISSING)
                    missing.add(String.format("U+%04X", codepoint));
                else checked++;
            }
            return new GlyphAudit(checked, List.copyOf(missing));
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot inspect active tooltip font glyphs", exception);
        }
    }

    private void recordFontProblem(String locale, String key, int unitIndex, String type,
                                   String authoredUnit, String detail) {
        var problem = new LinkedHashMap<String, Object>();
        problem.put("locale", locale);
        if (key != null && !key.isBlank()) problem.put("key", key);
        if (unitIndex >= 0) problem.put("unitIndex", unitIndex);
        problem.put("type", type);
        if (authoredUnit != null) problem.put("authoredUnit", authoredUnit);
        problem.put("detail", detail);
        fontProblems.add(problem);
    }

    private static List<String> supportedLocales() {
        return List.of("ja_jp", "en_us", "hi_in", "zh_hk", "zh_tw", "zh_cn", "ru_ru", "pt_br", "pt_pt",
                "es_es", "fr_fr", "de_de", "cs_cz", "pl_pl", "ms_my", "ro_ro", "ar_sa", "id_id",
                "ko_kr", "vi_vn", "tr_tr", "it_it");
    }

    private static ResourceLocation parseIeItemId(String value) {
        String normalized = value.contains(":") ? value : IE_NAMESPACE + ":" + value;
        ResourceLocation id = ResourceLocation.tryParse(normalized);
        if (id == null || !IE_NAMESPACE.equals(id.getNamespace()))
            throw new IllegalStateException("Expected an Immersive Engineering item ID: " + value);
        return id;
    }

    private static void requireKnownIeItem(String id, String source) {
        if (!BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(id)))
            throw new IllegalStateException(source + " references an unknown IE item: " + id);
    }

    private static JsonArray requireArray(JsonObject json, String key) {
        JsonArray value = json.getAsJsonArray(key);
        if (value == null) throw new IllegalStateException("Coverage fixture needs array " + key);
        return value;
    }

    private static List<String> stringArray(JsonArray rows, String field) {
        if (rows == null) throw new IllegalStateException("Coverage fixture needs array " + field);
        var result = new ArrayList<String>();
        for (JsonElement value : rows) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString() || value.getAsString().isBlank())
                throw new IllegalStateException("Expected nonempty string in " + field);
            result.add(value.getAsString());
        }
        return List.copyOf(result);
    }

    private static String requiredString(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive() || !json.getAsJsonPrimitive(key).isString()
                || json.get(key).getAsString().isBlank())
            throw new IllegalStateException("Coverage fixture needs nonempty string " + key);
        return json.get(key).getAsString();
    }

    private static String optionalString(JsonObject json, String key) {
        return json.has(key) && json.get(key).isJsonPrimitive() && json.getAsJsonPrimitive(key).isString()
                && !json.get(key).getAsString().isBlank() ? json.get(key).getAsString() : null;
    }

    private static String requiredSystemProperty(String key) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing system property: " + key);
        return value;
    }

    private static byte[] readResource(Class<?> owner, String path) throws IOException {
        try (InputStream stream = owner.getResourceAsStream(path)) {
            if (stream == null) throw new IllegalStateException("Missing loaded validation class resource: " + path);
            return stream.readAllBytes();
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }

    private List<Map<String, Object>> expectedWReport() {
        return expectedW.entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                .map(entry -> Map.<String, Object>of("item", entry.getKey().toString(),
                        "lesson", entry.getValue().lesson(), "chapter", entry.getValue().chapter())).toList();
    }

    private List<Map<String, Object>> stackCaseReport() {
        return stackCases.values().stream().sorted(Comparator.comparing(StackCase::key))
                .map(test -> Map.<String, Object>of("id", test.key(),
                "item", test.itemId().toString(), "entrance", test.entrance(), "lesson", test.lesson() == null ? "" : test.lesson(),
                "chapter", test.chapter() == null ? "" : test.chapter(),
                "components", test.stack().getComponentsPatch().toString(),
                "serializedStack", encodeStack(test.stack()))).toList();
    }

}
