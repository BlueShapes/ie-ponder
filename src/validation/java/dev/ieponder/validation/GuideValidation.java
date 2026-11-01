package dev.ieponder.validation;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import dev.ieponder.GuideConstants;
import dev.ieponder.GuideResources;
import dev.ieponder.client.GuideBook;
import dev.ieponder.client.CaptionUnits;
import dev.ieponder.client.GuideCaptionElement;
import dev.ieponder.client.GuideCaptions;
import dev.ieponder.client.GuideItemDescriptions;
import dev.ieponder.client.GuidePonderScreen;
import dev.ieponder.client.GuideNavigation;
import dev.ieponder.client.GuidePlugin;
import dev.ieponder.client.GuideScenes;
import dev.ieponder.client.GuideTextTones;
import dev.ieponder.client.NativePonderItemStackContext;
import net.createmod.ponder.api.element.EntityElement;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.PonderTag;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.element.InputWindowElement;
import net.createmod.ponder.foundation.element.TextWindowElement;
import net.createmod.ponder.foundation.ui.PonderButton;
import net.createmod.ponder.foundation.ui.PonderTagIndexScreen;
import net.createmod.ponder.foundation.ui.PonderTagScreen;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.neoforged.neoforge.client.ClientCommandHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Opt-in client integration harness, compiled only in the validation source set. */
public final class GuideValidation {
    private static final long TIMEOUT_NANOS = java.util.concurrent.TimeUnit.MINUTES.toNanos(10);
    private static final List<String> LOCALES = List.of("ja_jp", "en_us", "hi_in", "zh_hk", "zh_tw", "zh_cn",
            "ru_ru", "pt_br", "pt_pt", "es_es", "fr_fr", "de_de", "cs_cz", "pl_pl", "ms_my", "ro_ro",
            "ar_sa", "id_id", "ko_kr", "vi_vn", "tr_tr", "it_it");
    private static final String JAPANESE = "ja_jp";
    private static final String ENGLISH = "en_us";
    private static final String[] DEFAULT_VALIDATION_LESSONS = {"fluid_pipes", "refinery", "metal_press"};
    private static final List<PonderScene> SCENES = new ArrayList<>();
    private static final List<PonderScene> VISUAL_SCENES = new ArrayList<>();
    private static final List<String> TITLE_OVERFLOWS = new ArrayList<>();
    private static final List<Map<String, Object>> CAPTION_OVERFLOWS = new ArrayList<>();
    private static final boolean CAPTIONS_ONLY = Boolean.getBoolean("ieponder.captionValidation");
    private static final boolean LAYOUT_ONLY = Boolean.getBoolean("ieponder.layoutValidation");
    private static final boolean PRESENTATION_ONLY = Boolean.getBoolean("ieponder.presentationValidation");
    private static final boolean COVERAGE_ONLY = Boolean.getBoolean("ieponder.coverageValidation");
    private static final java.util.Set<String> VALIDATED_LOCALES = new java.util.HashSet<>();
    private static final Map<String, Map<String, String>> LANGUAGE_RESOURCES = new HashMap<>();
    private static final Map<String, Map<Integer, Float>> FONT_ADVANCES = new LinkedHashMap<>();
    private static final Map<String, String> NATIVE_UI_ENGLISH = new LinkedHashMap<>();
    private static final List<CommandCase> COMMAND_CASES = List.of(
            new CommandCase("ieponder metal_press", "metal_press", false),
            new CommandCase("ponder-ie 2", "basic_wiring", false),
            new CommandCase("ponder-ie logistics", "conveyors", false),
            new CommandCase("ponder-ie metal_press", "metal_press", false),
            new CommandCase("ponder-ie search 硝石の粉", "refinery", false),
            new CommandCase("ponder-ie item hammer", "capacitors", false),
            new CommandCase("ponder-ie item crusher", "crusher", false),
            new CommandCase("ponder-ie item minecraft:air", null, true));

    private static boolean initialized;
    private static boolean openingWorld;
    private static boolean finished;
    private static boolean optionsToNativeTagIndex;
    private static boolean powerCapacitorStartsCapacitors;
    private static boolean nativeTagIndexToLogistics;
    private static boolean nativeLogisticsItemToFullCourse;
    private static boolean nativeNextPrevious;
    private static boolean nativePonderBackToTag;
    private static boolean nativeTagBackToIndex;
    private static boolean nativeIndexBackToOptions;
    private static boolean courseTagSelectionChangedCourse;
    private static boolean processingFluidPipeStartsRefinery;
    private static boolean wPublicFactoryPreserved;
    private static boolean manualCourseContainsAllLessons;
    private static boolean commandsOpenFullCourse;
    private static boolean unknownChapterIconRejected;
    private static boolean turkishUppercaseChapterCommand;
    private static int verifiedFullCourseCommands;
    private static int commandCaseIndex;
    private static int localeValidationIndex;
    private static boolean optionsButtonless;
    private static boolean presentationOptionsFixturePreserved;
    private static boolean presentationCommandStartedCourse;
    private static boolean presentationCourseTagOpened;
    private static boolean presentationSameChapterLessonSelected;
    private static boolean presentationNativeBackPath;
    private static boolean presentationWirecoilWFactory;
    private static boolean presentationFluidPipeWFactory;
    private static boolean presentationFluidPipeCategoryMove;
    private static boolean presentationFluidPipeCategoryBack;
    private static boolean presentationDirectTagTick;
    private static boolean presentationForeignContextRestored;
    private static boolean presentationForeignOverridePreserved;
    private static boolean presentationNativeGuideHistoryDistinct;
    private static boolean presentationGuideHistoryExact;
    private static boolean presentationArabicCaptionFontChecked;
    private static boolean presentationJapaneseRestored;
    private static boolean presentationTooltipToneColors;
    private static final java.util.EnumSet<GuideTextTones.Tone> PRESENTATION_CAPTION_TONES =
            java.util.EnumSet.noneOf(GuideTextTones.Tone.class);
    private static final java.util.EnumSet<GuideTextTones.Tone> PRESENTATION_TOOLTIP_TONES =
            java.util.EnumSet.noneOf(GuideTextTones.Tone.class);
    private static final Map<String, Integer> PRESENTATION_TONE_COUNTS = new LinkedHashMap<>();
    private static int presentationNativeOptionWidgets;
    private static final Map<PonderUI, Integer> PRESENTATION_NATIVE_CONTROL_BUTTON_COUNTS = new java.util.IdentityHashMap<>();
    private static int presentationCrossChapterMoves;
    private static int presentationFluidPipeScrolls;
    private static int presentationToneScreenshots;
    private static int presentationArabicCaptionPages;
    private static int presentationArabicMaximumCaptionLines;
    private static int presentationToneIndex;
    private static PonderScene presentationToneScene;
    private static List<GuideTextTones.Unit> presentationToneUnits = List.of();
    private static List<Integer> presentationToneTimes = List.of();
    private static List<Map<String, Object>> presentationArabicCaptionOverflows = List.of();
    private static long startedAt;
    private static long renderedFrames;
    private static long consumedRenderedFrames;
    private static int phaseFrame;
    private static int visualIndex;
    private static int visualFrame;
    private static int verifiedCallouts;
    private static int verifiedNodes;
    private static int verifiedSteps;
    private static int verifiedFlows;
    private static int verifiedEntities;
    private static int verifiedActorReplays;
    private static int verifiedTooltipItems;
    private static int verifiedCaptionPages;
    private static int verifiedGlyphCodepoints;
    private static int maximumTitleLines;
    private static int maximumCaptionLines;
    private static int mostRecentLocaleMaximumCaptionLines;
    private static int twoLineCaptionUnits;
    private static int captionLayoutChecks;
    private static int verifiedNativeUiLabels;
    private static boolean englishFallback;
    private static int verifiedLocalizedChapters;
    private static int verifiedLocalizedItems;
    private static int verifiedLanguageResources;
    private static int screenshotCount;
    private static CompletableFuture<Void> languageReload;
    private static String currentLocale;
    private static String originalLocale;
    private static List<String> localeValidationOrder = List.of();
    private static String stage = "initialize";
    private static ItemCoverageAudit coverageAudit;
    private static int coverageLocaleIndex;
    private static ItemCoverageAudit.StackCase coveragePonderCase;
    private static PonderUI coverageOriginUi;
    private static String coverageOriginLesson;
    private static String coverageOriginChapter;
    private static String coverageTargetItem;
    private static String coverageTargetLesson;
    private static int coverageCommandIndex;
    private static int coverageCommandCount;
    private static boolean coverageRealWToTag;
    private static boolean coverageAnotherLesson;
    private static boolean coverageBackPreservedStack;
    private static boolean coverageForeignContextRestored;
    private static boolean coverageForeignOverridePreserved;
    private static boolean coverageComponentHistoryDistinct;
    private static boolean coverageNativeHistoryPreserved;

    private GuideValidation() {}

    static boolean initialize() {
        if (!Boolean.getBoolean("ieponder.validate")) return false;
        var client = Minecraft.getInstance();
        initialized = true;
        startedAt = System.nanoTime();
        originalLocale = client.getLanguageManager().getSelected();
        ValidationPlatform.hideWindow(client);
        try {
            if (PRESENTATION_ONLY && (CAPTIONS_ONLY || LAYOUT_ONLY))
                throw new IllegalStateException("Presentation validation cannot be combined with caption/layout validation");
            if (COVERAGE_ONLY && (PRESENTATION_ONLY || CAPTIONS_ONLY || LAYOUT_ONLY))
                throw new IllegalStateException("Item coverage validation is a focused scope and cannot be combined with other focused validations");
            var directory = client.gameDirectory.toPath();
            Files.createDirectories(directory);
            Files.deleteIfExists(directory.resolve("validation-result.json"));
            Files.deleteIfExists(directory.resolve("validation-failure.txt"));
            if (COVERAGE_ONLY) Files.deleteIfExists(directory.resolve("coverage-font-problems.json"));
            transitionTo("waiting_for_title");
            LogUtils.getLogger().info("IEPONDER_VALIDATION_INITIALIZED: screen={}, locale={}",
                    screenName(client), client.getLanguageManager().getSelected());
        } catch (Throwable exception) {
            failValidation(client, exception);
        }
        return true;
    }

    static void tick() {
        if (!initialized || finished) return;
        var client = Minecraft.getInstance();
        try {
            if (System.nanoTime() - startedAt > TIMEOUT_NANOS)
                throw new IllegalStateException("Validation timed out after ten minutes at stage=" + stage
                        + (visualIndex < SCENES.size() ? ", lesson=" + SCENES.get(visualIndex).getId() : ""));

            if (languageReload != null) {
                if (!languageReload.isDone()) return;
                languageReload.join();
                languageReload = null;
                LogUtils.getLogger().info("IEPONDER_VALIDATION_LANGUAGE_READY: {}", currentLocale);
                if (stage.equals("japanese_reload")) {
                    if (client.screen instanceof TitleScreen && client.getOverlay() == null) {
                        ValidationPlatform.createFreshLevel(client);
                        openingWorld = true;
                        transitionTo("opening_fresh_world");
                    } else {
                        transitionTo("waiting_for_title");
                    }
                } else if (stage.equals("english_reload")) {
                    GuideNavigation.openIndex();
                    transitionTo("english_index");
                } else if (stage.equals("caption_locale_reload")) {
                    validateActiveLocale(client, currentLocale);
                    if (currentLocale.equals("tr_tr")) transitionTo("turkish_command_wait");
                    else advanceLocaleValidation(client);
                } else if (stage.equals("coverage_locale_reload")) {
                    coverageAudit.validateDescriptionLocale(client, currentLocale);
                    coverageLocaleIndex++;
                    if (coverageLocaleIndex < LOCALES.size())
                        requestLocale(client, LOCALES.get(coverageLocaleIndex), "coverage_locale_reload");
                    else requestLocale(client, originalLocale, "coverage_restore_locale_reload");
                } else if (stage.equals("coverage_restore_locale_reload")) {
                    var fontProblems = coverageAudit.writeFontProblems(
                            client.gameDirectory.toPath().resolve("coverage-font-problems.json"));
                    if (coverageLocaleIndex != LOCALES.size() || !coverageAudit.allFontsChecked(LOCALES))
                        throw new IllegalStateException("Coverage font audit did not process all 22 locales: "
                                + fontProblems);
                    if (!client.getLanguageManager().getSelected().equals(originalLocale))
                        throw new IllegalStateException("Original language was not restored after item coverage font checks");
                    if (coverageAudit.hasFontProblems())
                        throw new IllegalStateException("Coverage font audit found "
                                + fontProblems.get("fontProblemCount")
                                + " problem(s); see coverage-font-problems.json");
                    startCoverageNavigation(client);
                } else if (stage.equals("fallback_locale_reload")) {
                    for (var entry : LANGUAGE_RESOURCES.get(ENGLISH).entrySet())
                        if (!Language.getInstance().getOrDefault(entry.getKey()).equals(entry.getValue()))
                            throw new IllegalStateException("Unsupported locale did not fall back to English: " + entry.getKey());
                    englishFallback = true;
                    requestLocale(client, JAPANESE, "restore_locale_reload");
                } else if (stage.equals("restore_locale_reload")) {
                    startVisualScenes(client);
                } else if (stage.equals("presentation_arabic_reload")) {
                    int pageCountBefore = verifiedCaptionPages;
                    int overflowCountBefore = CAPTION_OVERFLOWS.size();
                    validateCaptionPages(client, "ar_sa");
                    presentationArabicCaptionPages = verifiedCaptionPages - pageCountBefore;
                    presentationArabicMaximumCaptionLines = mostRecentLocaleMaximumCaptionLines;
                    presentationArabicCaptionOverflows = List.copyOf(
                            CAPTION_OVERFLOWS.subList(overflowCountBefore, CAPTION_OVERFLOWS.size()));
                    if (presentationArabicCaptionOverflows.size() > 0)
                        throw new IllegalStateException("Arabic authored caption units exceed three lines: "
                                + presentationArabicCaptionOverflows.size());
                    presentationArabicCaptionFontChecked = true;
                    requestLocale(client, JAPANESE, "presentation_restore_japanese_reload");
                } else if (stage.equals("presentation_restore_japanese_reload")) {
                    if (!client.getLanguageManager().getSelected().equals(JAPANESE))
                        throw new IllegalStateException("Japanese was not restored after focused Arabic font checks");
                    presentationJapaneseRestored = true;
                    client.setScreen(ValidationPlatform.createOptionsScreen(client));
                    transitionTo("presentation_options");
                } else if (stage.equals("final_locale_reload")) {
                    writeSuccess(client);
                }
                return;
            }

            if (stage.equals("waiting_for_title")) {
                if (client.getOverlay() != null || !(client.screen instanceof TitleScreen)) {
                    ValidationPlatform.recoverInitialScreen(client);
                    return;
                }
                if (client.getLanguageManager().getSelected().equals(JAPANESE)) {
                    ValidationPlatform.createFreshLevel(client);
                    openingWorld = true;
                    transitionTo("opening_fresh_world");
                } else {
                    // The initial reload is normally already complete by the time the title screen is ready.
                    requestLocale(client, JAPANESE, "japanese_reload");
                }
                return;
            }

            if (openingWorld) {
                if (client.level == null || client.player == null || client.getOverlay() != null) return;
                // A level/player can exist while the terrain loading screen still covers the framebuffer.
                if (client.screen != null) return;
                openingWorld = false;
                if (COVERAGE_ONLY) {
                    coverageAudit = ItemCoverageAudit.begin(client);
                    validateCoverageHistory(client);
                    coverageLocaleIndex = 0;
                    requestLocale(client, LOCALES.get(coverageLocaleIndex), "coverage_locale_reload");
                    return;
                }
                validateLanguageResources(client);
                validateScenes();
                if (PRESENTATION_ONLY) {
                    validatePresentationToneMappings();
                    validatePresentationRegistrations();
                    validatePublicPonderFactories(client);
                    requestLocale(client, "ar_sa", "presentation_arabic_reload");
                    return;
                }
                if (CAPTIONS_ONLY) {
                    client.options.guiScale().set(3);
                    client.resizeDisplay();
                    if (client.getWindow().getGuiScaledWidth() != 320 || client.getWindow().getGuiScaledHeight() != 240)
                        throw new IllegalStateException("Caption validation needs a 320x240 minimum GUI");
                    if (LAYOUT_ONLY) {
                        startVisualScenes(client);
                        return;
                    }
                    localeValidationOrder = new ArrayList<>(LOCALES);
                    localeValidationIndex = 0;
                    requestLocale(client, localeValidationOrder.get(0), "caption_locale_reload");
                    return;
                }
                validatePublicPonderFactories(client);
                client.setScreen(PonderUI.of(new ItemStack(BuiltInRegistries.ITEM.get(GuidePlugin.GUIDE_ITEM))));
                transitionTo("manual_factory_first");
                return;
            }

            // UI changes and screenshots are advanced only after a distinct completed render.
            // Reload completion precedes the Mojang overlay's fade-out.
            if (client.getOverlay() != null) return;
            if (renderedFrames == consumedRenderedFrames) return;
            consumedRenderedFrames = renderedFrames;
            phaseFrame++;
            if (phaseFrame < 3) return;

            if (PRESENTATION_ONLY) {
                tickPresentation(client);
                return;
            }
            if (COVERAGE_ONLY) {
                tickCoverage(client);
                return;
            }

            switch (stage) {
                case "turkish_command_wait" -> {
                    if (phaseFrame < 30) return;
                    var ui = requireGuidePonder(client, "conveyors");
                    requireFullCourse(ui, "ponder-ie LOGISTICS");
                    turkishUppercaseChapterCommand = true;
                    advanceLocaleValidation(client);
                }
                case "manual_factory_first" -> {
                    requirePonder(client, GuideBook.lessons().get(0).id());
                    if (client.screen.getClass() != PonderUI.class)
                        throw new IllegalStateException("The public one-argument Ponder factory was redirected");
                    screenshot("manual-w-factory.png");
                    clickArrow(true);
                    transitionTo("manual_factory_next");
                }
                case "manual_factory_next" -> {
                    if (phaseFrame >= 2) {
                        requirePonder(client, GuideBook.lessons().get(1).id());
                        client.setScreen(ValidationPlatform.createOptionsScreen(client));
                        transitionTo("japanese_options");
                    }
                }
                case "japanese_options" -> {
                    if (!ValidationPlatform.isOptionsScreen(client.screen))
                        throw new IllegalStateException("Options screen did not open for guide command validation");
                    optionsButtonless = optionsGuideButtonCount(client.screen) == 0;
                    if (!optionsButtonless)
                        throw new IllegalStateException("A guide-owned button remains on the Options screen");
                    screenshot("options-no-guide-button.png");
                    executeIndexCommand();
                    transitionTo("japanese_tag_index");
                }
                case "japanese_tag_index" -> {
                    if (phaseFrame < 12) break;
                    requireNativeTagIndex(client, JAPANESE);
                    screenshot("ponder-tag-index-ja.png");
                    optionsToNativeTagIndex = true;
                    clickTag("power");
                    transitionTo("japanese_power_tag");
                }
                case "japanese_power_tag" -> {
                    if (phaseFrame < 12) break;
                    requireNativeTagScreen(client, "power", JAPANESE);
                    screenshot("ponder-tag-power-ja.png");
                    clickItem("immersiveengineering:capacitor_lv");
                    transitionTo("japanese_capacitor_ponder");
                }
                case "japanese_capacitor_ponder" -> {
                    if (phaseFrame < 6) break;
                    var ui = requireGuidePonder(client, "capacitors");
                    powerCapacitorStartsCapacitors = true;
                    ui.seekToTime(earlyCaptionTime(ui.getActiveScene()));
                    screenshot("ponder-capacitors-ja.png");
                    transitionTo("native_controls_wait");
                }
                case "native_controls_wait" -> {
                    if (phaseFrame >= 30) {
                        clickArrow(true);
                        transitionTo("native_next_check");
                    }
                }
                case "native_next_check" -> {
                    if (phaseFrame >= 2) {
                        requireAdjacentPonder(client, "capacitors", 1);
                        transitionTo("native_previous_wait");
                    }
                }
                case "native_previous_wait" -> {
                    if (phaseFrame >= 9) {
                        clickArrow(false);
                        transitionTo("native_previous_check");
                    }
                }
                case "native_previous_check" -> {
                    if (phaseFrame >= 30) {
                        requireGuidePonder(client, "capacitors");
                        nativeNextPrevious = true;
                        clickTagFromCourse("power");
                        transitionTo("course_power_tag");
                    }
                }
                case "course_power_tag" -> {
                    if (phaseFrame >= 12) {
                        requireNativeTagScreen(client, "power", JAPANESE);
                        clickItem("immersiveengineering:connector_lv");
                        transitionTo("power_connector_ponder");
                    }
                }
                case "power_connector_ponder" -> {
                    if (phaseFrame < 6) break;
                    requireGuidePonder(client, "basic_wiring");
                    courseTagSelectionChangedCourse = true;
                    clickBack();
                    transitionTo("connector_ponder_back_check");
                }
                case "connector_ponder_back_check" -> {
                    if (phaseFrame >= 30) {
                        requireNativeTagScreen(client, "power", JAPANESE);
                        nativePonderBackToTag = true;
                        clickBack();
                        transitionTo("connector_tag_back_check");
                    }
                }
                case "connector_tag_back_check" -> {
                    if (phaseFrame >= 30) {
                        // Native ScreenOpener reuses the previous Power tag when the
                        // course selects that same registered tag; no duplicate is pushed.
                        requireNativeTagIndex(client, JAPANESE);
                        nativeTagBackToIndex = true;
                        clickBack();
                        transitionTo("power_index_back_check");
                    }
                }
                case "power_index_back_check" -> {
                    if (phaseFrame >= 2) {
                        if (!ValidationPlatform.isOptionsScreen(client.screen))
                            throw new IllegalStateException("Native tag index did not return to the options screen");
                        nativeIndexBackToOptions = true;
                        executeIndexCommand();
                        transitionTo("logistics_tag_index");
                    }
                }
                case "logistics_tag_index" -> {
                    if (phaseFrame < 12) break;
                    requireNativeTagIndex(client, JAPANESE);
                    clickTag("logistics");
                    transitionTo("logistics_tag");
                }
                case "logistics_tag" -> {
                    if (phaseFrame < 12) break;
                    requireNativeTagScreen(client, "logistics", JAPANESE);
                    screenshot("ponder-tag-logistics-ja.png");
                    nativeTagIndexToLogistics = true;
                    clickItem("immersiveengineering:fluid_pipe");
                    transitionTo("logistics_pipe_ponder");
                }
                case "logistics_pipe_ponder" -> {
                    if (phaseFrame < 6) break;
                    requireGuidePonder(client, "fluid_pipes");
                    nativeLogisticsItemToFullCourse = true;
                    clickBack();
                    transitionTo("logistics_ponder_back_check");
                }
                case "logistics_ponder_back_check" -> {
                    if (phaseFrame >= 30) {
                        requireNativeTagScreen(client, "logistics", JAPANESE);
                        clickBack();
                        transitionTo("logistics_tag_back_check");
                    }
                }
                case "logistics_tag_back_check" -> {
                    if (phaseFrame >= 30) {
                        requireNativeTagIndex(client, JAPANESE);
                        clickBack();
                        transitionTo("logistics_index_back_check");
                    }
                }
                case "logistics_index_back_check" -> {
                    if (phaseFrame >= 2) {
                        if (!ValidationPlatform.isOptionsScreen(client.screen))
                            throw new IllegalStateException("Logistics index did not return to options");
                        executeIndexCommand();
                        transitionTo("processing_tag_index");
                    }
                }
                case "processing_tag_index" -> {
                    if (phaseFrame < 12) break;
                    requireNativeTagIndex(client, JAPANESE);
                    clickTag("processing");
                    transitionTo("processing_tag");
                }
                case "processing_tag" -> {
                    if (phaseFrame < 12) break;
                    requireNativeTagScreen(client, "processing", JAPANESE);
                    clickItem("immersiveengineering:fluid_pipe");
                    transitionTo("processing_refinery_ponder");
                }
                case "processing_refinery_ponder" -> {
                    if (phaseFrame < 6) break;
                    requireGuidePonder(client, "refinery");
                    processingFluidPipeStartsRefinery = true;
                    clickBack();
                    transitionTo("processing_ponder_back_check");
                }
                case "processing_ponder_back_check" -> {
                    if (phaseFrame >= 30) {
                        requireNativeTagScreen(client, "processing", JAPANESE);
                        clickBack();
                        transitionTo("processing_tag_back_check");
                    }
                }
                case "processing_tag_back_check" -> {
                    if (phaseFrame >= 30) {
                        requireNativeTagIndex(client, JAPANESE);
                        clickBack();
                        transitionTo("processing_index_back_check");
                    }
                }
                case "processing_index_back_check" -> {
                    if (phaseFrame >= 2) {
                        if (!ValidationPlatform.isOptionsScreen(client.screen))
                            throw new IllegalStateException("Processing index did not return to options");
                        commandCaseIndex = 0;
                        executeCommandCase(client);
                        transitionTo("command_open");
                    }
                }
                case "command_open" -> {
                    if (phaseFrame >= 2) {
                        var commandCase = COMMAND_CASES.get(commandCaseIndex);
                        var ui = requireGuidePonder(client, commandCase.lessonId());
                        requireFullCourse(ui, commandCase.command());
                        if (commandCaseIndex == 0) screenshot("command-metal-press.png");
                        commandsOpenFullCourse = true;
                        verifiedFullCourseCommands++;
                        clickBack();
                        transitionTo("command_back_check");
                    }
                }
                case "command_back_check" -> {
                    if (phaseFrame >= 30) {
                        if (!ValidationPlatform.isOptionsScreen(client.screen))
                            throw new IllegalStateException("Command guide did not return to options: "
                                    + COMMAND_CASES.get(commandCaseIndex).command());
                        commandCaseIndex++;
                        if (commandCaseIndex < COMMAND_CASES.size()) {
                            executeCommandCase(client);
                            if (commandCaseIndex < COMMAND_CASES.size()) transitionTo("command_open");
                            else requestLocale(client, ENGLISH, "english_reload");
                        } else {
                            requestLocale(client, ENGLISH, "english_reload");
                        }
                    }
                }
                case "english_index" -> {
                    if (phaseFrame < 12) break;
                    requireNativeTagIndex(client, ENGLISH);
                    screenshot("ponder-tag-index-en.png");
                    clickTag("logistics");
                    transitionTo("english_logistics_tag");
                }
                case "english_logistics_tag" -> {
                    if (phaseFrame < 12) break;
                    requireNativeTagScreen(client, "logistics", ENGLISH);
                    screenshot("ponder-tag-logistics-en.png");
                    clickItem("immersiveengineering:fluid_pipe");
                    transitionTo("english_logistics_ponder");
                }
                case "english_logistics_ponder" -> {
                    var ui = requireGuidePonder(client, "fluid_pipes");
                    ui.seekToTime(earlyCaptionTime(ui.getActiveScene()));
                    screenshot("ponder-fluid-pipes-en.png");
                    localeValidationOrder = LOCALES.stream().filter(locale -> !locale.equals(originalLocale))
                            .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
                    if (LOCALES.contains(originalLocale)) localeValidationOrder.add(originalLocale);
                    localeValidationIndex = 0;
                    requestLocale(client, localeValidationOrder.get(0), "caption_locale_reload");
                }
                case "visual_open" -> openVisualScene(client);
                case "visual_render" -> renderVisualScene(client);
                default -> throw new IllegalStateException("Unexpected validation stage: " + stage);
            }
        } catch (Throwable exception) {
            failValidation(client, exception);
        }
    }

    static void rendered() {
        if (initialized && !finished) renderedFrames++;
    }

    private static void validatePresentationToneMappings() {
        var toneType = new com.google.gson.reflect.TypeToken<Map<String, Map<String, List<GuideTextTones.Tone>>>>() {}.getType();
        Map<String, Map<String, List<GuideTextTones.Tone>>> authored;
        try (var stream = GuideValidation.class.getResourceAsStream("/assets/ieponder/guide/text_tones.json")) {
            if (stream == null) throw new IllegalStateException("Missing authored text tones");
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                authored = new com.google.gson.Gson().fromJson(reader, toneType);
            }
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Unable to read authored text tones", error);
        }
        if (authored == null || !authored.keySet().equals(java.util.Set.copyOf(LOCALES)))
            throw new IllegalStateException("Text tones must explicitly cover all 22 guide locales");

        var contentKeys = new java.util.HashSet<String>();
        for (var lesson : GuideBook.lessons()) for (int index = 0; index < lesson.steps().size(); index++)
            contentKeys.add("ieponder.ponder.shared." + lesson.id() + "_" + index);
        GuideItemDescriptions.descriptions().forEach(description -> contentKeys.add(description.translationKey()));
        if (contentKeys.isEmpty()) throw new IllegalStateException("No authored guide display units were found");

        for (var locale : LOCALES) {
            var authoredLocale = authored.get(locale);
            if (!authoredLocale.keySet().equals(contentKeys))
                throw new IllegalStateException("Wrong authored tone keys for " + locale + ": expected="
                        + contentKeys.size() + ", actual=" + authoredLocale.size());
            var translations = LANGUAGE_RESOURCES.get(locale);
            if (translations == null) throw new IllegalStateException("Guide translations were not loaded for " + locale);
            var seen = java.util.EnumSet.noneOf(GuideTextTones.Tone.class);
            int unitsValidated = 0;
            for (var key : contentKeys) {
                String text = translations.get(key);
                if (text == null) throw new IllegalStateException("Missing display text for tone mapping: " + locale + "/" + key);
                var boundaryProblems = CaptionUnits.problems(text, locale);
                if (!boundaryProblems.isEmpty())
                    throw new IllegalStateException("Invalid authored display units: " + locale + "/" + key + boundaryProblems);
                var units = GuideTextTones.units(locale, key, text);
                if (units.size() != authoredLocale.get(key).size())
                    throw new IllegalStateException("Tone/unit count changed after resolution: " + locale + "/" + key);
                units.forEach(unit -> seen.add(unit.tone()));
                unitsValidated += units.size();
            }
            if (!seen.equals(java.util.EnumSet.allOf(GuideTextTones.Tone.class)))
                throw new IllegalStateException("Each locale needs NORMAL, WARNING and HINT tone examples: " + locale + " => " + seen);
            PRESENTATION_TONE_COUNTS.put(locale, unitsValidated);
        }

        String sampleKey = "ieponder.ponder.shared.coke_oven_4";
        String sampleText = LANGUAGE_RESOURCES.get(JAPANESE).get(sampleKey);
        presentationToneUnits = GuideTextTones.units(JAPANESE, sampleKey, sampleText);
        var expected = List.of(GuideTextTones.Tone.WARNING, GuideTextTones.Tone.HINT, GuideTextTones.Tone.NORMAL);
        if (!presentationToneUnits.stream().map(GuideTextTones.Unit::tone).toList().equals(expected))
            throw new IllegalStateException("Japanese coke_oven_4 must demonstrate WARNING, HINT, then NORMAL units");

        var expectedColors = Map.of(
                GuideTextTones.Tone.NORMAL, 0xF6F6E5,
                GuideTextTones.Tone.WARNING, 0xFFAEA4,
                GuideTextTones.Tone.HINT, 0xC5DC97);
        expectedColors.forEach((tone, expectedColor) -> {
            int actualColor = tone.textColor() & 0xFFFFFF;
            if (actualColor != expectedColor)
                throw new IllegalStateException("Native bright text color changed for " + tone + ": expected=#"
                        + String.format("%06X", expectedColor) + ", actual=#" + String.format("%06X", actualColor));
        });
    }

    private static void validatePresentationRegistrations() {
        verifyNavigationResolvers();
        var tagAccess = PonderIndex.getTagAccess();
        for (var chapter : dev.ieponder.client.GuideChapter.active(GuideBook.lessons())) {
            var tag = tagAccess.getRegisteredTag(GuideResources.of("ieponder", chapter.id()));
            var expectedItems = new java.util.HashSet<ResourceLocation>();
            for (var lesson : GuideBook.lessons()) {
                if (!lesson.chapter().equals(chapter.id())) continue;
                expectedItems.add(GuideResources.parse(lesson.icon()));
                lesson.components().stream().map(GuideResources::parse).forEach(expectedItems::add);
            }
            var actualItems = tagAccess.getItems(tag);
            if (!actualItems.equals(expectedItems))
                throw new IllegalStateException("Ponder tag contains missing or unrelated items: " + chapter.id()
                        + ", expected=" + expectedItems + ", actual=" + actualItems);
            for (var item : expectedItems) {
                if (!tagAccess.getTags(item).contains(tag))
                    throw new IllegalStateException("Guide component is missing its actual Ponder tag: " + item + " -> " + tag.getId());
                if (!PonderIndex.getSceneAccess().doScenesExistForId(item))
                    throw new IllegalStateException("Tagged guide item has no registered Ponder course: " + item);
            }
        }
        var wirecoil = GuideResources.parse("immersiveengineering:wirecoil_copper");
        var power = tagAccess.getRegisteredTag(GuideResources.of("ieponder", "power"));
        if (!tagAccess.getTags(wirecoil).contains(power))
            throw new IllegalStateException("wirecoil_copper is missing its real power tag registration");
        var fluidPipe = GuideResources.parse("immersiveengineering:fluid_pipe");
        for (String chapter : List.of("logistics", "processing", "power"))
            if (!tagAccess.getTags(fluidPipe).contains(tagAccess.getRegisteredTag(GuideResources.of("ieponder", chapter))))
                throw new IllegalStateException("fluid_pipe is missing a real chapter tag: " + chapter);
    }

    private static void tickPresentation(Minecraft client) {
        switch (stage) {
            case "presentation_options" -> {
                validateOptionsScreen(client.screen, false);
                presentationOptionsFixturePreserved = true;
                executeClientCommand("ponder-ie metal_press");
                transitionTo("presentation_command_open");
            }
            case "presentation_command_open" -> {
                var ui = requireGuidePonder(client, "metal_press");
                requireFullCourse(ui, "ponder-ie metal_press");
                requireSceneContext(ui, "metal_press", "immersiveengineering:heavy_engineering", "processing");
                presentationCommandStartedCourse = true;
                clickTagFromCourse("processing");
                transitionTo("presentation_command_tag");
            }
            case "presentation_command_tag" -> {
                requirePresentationTagScreen(client, "processing");
                screenshot("presentation-processing-category.png");
                clickItem("immersiveengineering:light_engineering");
                presentationCourseTagOpened = true;
                transitionTo("presentation_command_other_lesson");
            }
            case "presentation_command_other_lesson" -> {
                var ui = requireGuidePonder(client, "crusher");
                requireSceneContext(ui, "crusher", "immersiveengineering:light_engineering", "processing");
                presentationSameChapterLessonSelected = true;
                clickBack();
                transitionTo("presentation_command_tag_back");
            }
            case "presentation_command_tag_back" -> {
                requirePresentationTagScreen(client, "processing");
                clickBack();
                transitionTo("presentation_command_course_back");
            }
            case "presentation_command_course_back" -> {
                requireSceneContext(requireGuidePonder(client, "metal_press"), "metal_press",
                        "immersiveengineering:heavy_engineering", "processing");
                clickBack();
                transitionTo("presentation_options_back");
            }
            case "presentation_options_back" -> {
                validateOptionsScreen(client.screen, true);
                presentationNativeBackPath = true;
                executeClientCommand("ponder-ie basic_wiring");
                transitionTo("presentation_cross_chapter_start");
            }
            case "presentation_cross_chapter_start" -> {
                var ui = requireGuidePonder(client, "basic_wiring");
                requireSceneContext(ui, "basic_wiring", "immersiveengineering:connector_lv", "power");
                verifyNativeCourseWidgets(ui);
                clickArrow(false);
                transitionTo("presentation_cross_chapter_previous");
            }
            case "presentation_cross_chapter_previous" -> {
                var ui = requireGuidePonder(client, "alloy_kiln");
                requireSceneContext(ui, "alloy_kiln", "immersiveengineering:alloybrick", "start");
                verifyNativeCourseWidgets(ui);
                presentationCrossChapterMoves++;
                clickArrow(true);
                transitionTo("presentation_cross_chapter_next");
            }
            case "presentation_cross_chapter_next" -> {
                var ui = requireGuidePonder(client, "basic_wiring");
                requireSceneContext(ui, "basic_wiring", "immersiveengineering:connector_lv", "power");
                verifyNativeCourseWidgets(ui);
                presentationCrossChapterMoves++;
                var stack = new ItemStack(BuiltInRegistries.ITEM.get(GuideResources.parse("immersiveengineering:wirecoil_copper")));
                PonderUI wirecoil = PonderUI.of(stack);
                if (wirecoil.getClass() != PonderUI.class)
                    throw new IllegalStateException("The public W factory for wirecoil_copper stopped using native PonderUI");
                client.setScreen(wirecoil);
                transitionTo("presentation_wirecoil_w");
            }
            case "presentation_wirecoil_w" -> {
                var ui = requirePonder(client, "basic_wiring");
                if (client.screen.getClass() != PonderUI.class)
                    throw new IllegalStateException("wirecoil_copper W entry was redirected from native PonderUI");
                requireSceneContext(ui, "basic_wiring", "immersiveengineering:wirecoil_copper", "power");
                verifyNativeCourseWidgets(ui);
                presentationWirecoilWFactory = true;
                var pipe = new ItemStack(BuiltInRegistries.ITEM.get(GuideResources.parse("immersiveengineering:fluid_pipe")));
                PonderUI pipeUi = PonderUI.of(pipe);
                if (pipeUi.getClass() != PonderUI.class)
                    throw new IllegalStateException("The public W factory for fluid_pipe stopped using native PonderUI");
                var pipeScenes = ponderScenes(pipeUi);
                int logisticsIndex = java.util.stream.IntStream.range(0, pipeScenes.size())
                        .filter(index -> pipeScenes.get(index).getId().getPath().equals("fluid_pipes"))
                        .findFirst().orElseThrow(() -> new IllegalStateException("fluid_pipe W course has no logistics lesson"));
                var pipeAccess = (dev.ieponder.mixin.PonderUIAccessor) (Object) pipeUi;
                pipeAccess.ieponder$setIndex(logisticsIndex);
                pipeAccess.ieponder$getLazyIndex().startWithValue(logisticsIndex);
                client.setScreen(pipeUi);
                presentationFluidPipeWFactory = true;
                presentationFluidPipeScrolls = 0;
                transitionTo("presentation_pipe_scroll");
            }
            case "presentation_pipe_scroll" -> {
                var ui = requirePonder(client, activeLesson(client));
                if (client.screen.getClass() != PonderUI.class)
                    throw new IllegalStateException("fluid_pipe W course stopped using native PonderUI");
                String chapter = activeLessonData(ui).chapter();
                String subject = "immersiveengineering:fluid_pipe";
                if (chapter.equals("processing")) {
                    requireSceneContext(ui, activeLesson(client), subject, "processing");
                    verifyNativeCourseWidgets(ui);
                    presentationFluidPipeCategoryMove = true;
                    clickTagFromCourse("processing");
                    transitionTo("presentation_pipe_tag");
                } else {
                    if (!chapter.equals("logistics"))
                        throw new IllegalStateException("fluid_pipe W course passed through an unexpected chapter before processing: " + chapter);
                    requireSceneContext(ui, activeLesson(client), subject, "logistics");
                    verifyNativeCourseWidgets(ui);
                    if (++presentationFluidPipeScrolls > sceneCount(ui))
                        throw new IllegalStateException("fluid_pipe W course never reached its processing chapter");
                    clickArrow(true);
                    transitionTo("presentation_pipe_scroll");
                }
            }
            case "presentation_pipe_tag" -> {
                requirePresentationTagScreen(client, "processing");
                clickItem("immersiveengineering:cloche");
                transitionTo("presentation_pipe_other_lesson");
            }
            case "presentation_pipe_other_lesson" -> {
                var ui = requireGuidePonder(client, "garden_cloche");
                requireSceneContext(ui, "garden_cloche", "immersiveengineering:cloche", "processing");
                clickBack();
                transitionTo("presentation_pipe_selected_back");
            }
            case "presentation_pipe_selected_back" -> {
                requirePresentationTagScreen(client, "processing");
                clickBack();
                transitionTo("presentation_pipe_course_back");
            }
            case "presentation_pipe_course_back" -> {
                var ui = requirePonder(client, activeLesson(client));
                if (client.screen.getClass() != PonderUI.class)
                    throw new IllegalStateException("Returning from a processing tag did not restore the native fluid_pipe W course");
                requireSceneContext(ui, activeLesson(client), "immersiveengineering:fluid_pipe", "processing");
                verifyNativeCourseWidgets(ui);
                presentationFluidPipeCategoryBack = true;
                openNativeTagTickProbe(client);
                transitionTo("presentation_direct_tag_tick");
            }
            case "presentation_direct_tag_tick" -> {
                var ui = requirePonder(client, activeLesson(client));
                String active = activeLesson(client);
                if (!activeLessonData(ui).chapter().equals("processing") || active.equals("fluid_pipes"))
                    throw new IllegalStateException("The native referredToByTag tick did not select a processing W scene: " + active);
                requireSceneContext(ui, active, "immersiveengineering:fluid_pipe", "processing");
                verifyNativeCourseWidgets(ui);
                presentationDirectTagTick = true;
                openMixedContextProbe(client);
                transitionTo("presentation_foreign_context_move");
            }
            case "presentation_foreign_context_move" -> {
                if (!(client.screen instanceof ValidationContextProbeScreen ui)
                        || !ui.getActiveScene().getId().equals(GuideResources.of("ieponder", "fluid_pipes")))
                    throw new IllegalStateException("The mixed-course context probe did not start in the IE logistics scene");
                requireSceneContext(ui, "fluid_pipes", "immersiveengineering:fluid_pipe", "logistics");
                verifyNativeCourseWidgets(ui);
                clickArrow(true);
                transitionTo("presentation_foreign_context_restore");
            }
            case "presentation_foreign_context_restore" -> {
                if (!(client.screen instanceof ValidationContextProbeScreen ui)
                        || !ui.getActiveScene().getId().equals(GuideResources.of("validationfixture", "foreign_machine")))
                    throw new IllegalStateException("Native scroll did not reach the foreign fixture scene");
                String subject = BuiltInRegistries.ITEM.getKey(ui.getSubject().getItem()).toString();
                var actualTags = ((dev.ieponder.mixin.PonderUIAccessor) (Object) ui).ieponder$getTags().stream()
                        .map(tag -> tag.getId()).collect(java.util.stream.Collectors.toSet());
                var originalTags = PonderIndex.getTagAccess().getTags(GuideResources.parse("immersiveengineering:fluid_pipe"))
                        .stream().map(PonderTag::getId).collect(java.util.stream.Collectors.toSet());
                if (!subject.equals("immersiveengineering:fluid_pipe") || !actualTags.equals(originalTags))
                    throw new IllegalStateException("Leaving IE guide scenes did not restore the synthetic native subject/tags: "
                            + subject + " / " + actualTags);
                verifyNativeCourseWidgets(ui);
                presentationForeignContextRestored = true;
                openMixedContextProbe(client);
                transitionTo("presentation_foreign_override_setup");
            }
            case "presentation_foreign_override_setup" -> {
                if (!(client.screen instanceof ValidationContextProbeScreen ui)
                        || !ui.getActiveScene().getId().equals(GuideResources.of("ieponder", "fluid_pipes")))
                    throw new IllegalStateException("The override fixture did not initialize in an IE guide scene");
                requireSceneContext(ui, "fluid_pipes", "immersiveengineering:fluid_pipe", "logistics");
                var access = (dev.ieponder.mixin.PonderUIAccessor) (Object) ui;
                access.ieponder$setStack(new ItemStack(BuiltInRegistries.ITEM.get(
                        GuideResources.parse("minecraft:hopper"))));
                var tags = access.ieponder$getTags();
                tags.clear();
                tags.add(PonderIndex.getTagAccess().getRegisteredTag(GuideResources.of("ieponder", "power")));
                transitionTo("presentation_foreign_override_tick");
            }
            case "presentation_foreign_override_tick" -> {
                if (!(client.screen instanceof ValidationContextProbeScreen ui)
                        || !ui.getActiveScene().getId().equals(GuideResources.of("ieponder", "fluid_pipes")))
                    throw new IllegalStateException("The synthetic foreign override left its IE scene before the probe tick");
                requireSyntheticForeignOverride(ui);
                clickArrow(true);
                transitionTo("presentation_foreign_override_restore");
            }
            case "presentation_foreign_override_restore" -> {
                if (!(client.screen instanceof ValidationContextProbeScreen ui)
                        || !ui.getActiveScene().getId().equals(GuideResources.of("validationfixture", "foreign_machine")))
                    throw new IllegalStateException("Native scroll did not reach the override fixture's foreign scene");
                requireSyntheticForeignOverride(ui);
                verifyNativeCourseWidgets(ui);
                presentationForeignOverridePreserved = true;
                executeClientCommand("ponder-ie coke_oven");
                transitionTo("presentation_tone_open");
            }
            case "presentation_tone_open" -> {
                var ui = requireGuidePonder(client, "coke_oven");
                requireSceneContext(ui, "coke_oven", "immersiveengineering:cokebrick", "start");
                presentationToneScene = ui.getActiveScene();
                presentationToneTimes = presentationToneSampleTimes(presentationToneScene);
                presentationToneIndex = 0;
                ui.seekToTime(presentationToneTimes.get(presentationToneIndex));
                transitionTo("presentation_tone_sample");
            }
            case "presentation_tone_sample" -> {
                var ui = requireGuidePonder(client, "coke_oven");
                var expected = presentationToneUnits.get(presentationToneIndex).tone();
                var captions = presentationToneScene.getElements().stream()
                        .filter(GuideCaptionElement.class::isInstance).map(GuideCaptionElement.class::cast)
                        .filter(TextWindowElement::isVisible).toList();
                if (captions.size() != 1 || captions.get(0).getPalette() != expected.palette()
                        || captions.get(0).getFade(1) < .95f)
                    throw new IllegalStateException("Caption palette did not match " + expected + " at authored unit "
                            + presentationToneIndex + ": visible=" + captions.size());
                PRESENTATION_CAPTION_TONES.add(expected);
                screenshot("caption-tone-" + expected.name().toLowerCase(java.util.Locale.ROOT) + ".png");
                presentationToneScreenshots++;
                presentationToneIndex++;
                if (presentationToneIndex < presentationToneUnits.size()) {
                    ui.seekToTime(presentationToneTimes.get(presentationToneIndex));
                    transitionTo("presentation_tone_sample");
                } else {
                    validateTooltipToneColors();
                    writeSuccess(client);
                }
            }
            default -> throw new IllegalStateException("Unknown presentation validation stage: " + stage);
        }
    }

    private static void validateCoverageHistory(Minecraft client) {
        var test = coverageAudit.firstPonderCase();
        ItemStack covered = test.copyStack();
        PonderUI original = PonderUI.of(covered.copy());
        PonderUI sameComponents = PonderUI.of(covered.copy());
        ItemStack different = covered.copy();
        var marker = new net.minecraft.nbt.CompoundTag();
        marker.putString("ieponder_coverage_probe", "different-components");
        different.set(DataComponents.CUSTOM_DATA, CustomData.of(marker));
        if (ItemStack.isSameItemSameComponents(covered, different))
            throw new IllegalStateException("Synthetic component history fixture did not change stack components");
        PonderUI differentComponents = PonderUI.of(different);
        if (!original.isEquivalentTo(sameComponents) || !sameComponents.isEquivalentTo(original)
                || original.isEquivalentTo(differentComponents) || differentComponents.isEquivalentTo(original))
            throw new IllegalStateException("Native Ponder history did not distinguish same-item component variants symmetrically");
        GuideBook.Lesson lesson = test.lesson() == null
                ? GuideBook.lessons().stream().filter(candidate -> test.itemId().equals(GuideResources.parse(candidate.icon()))
                        || candidate.components().stream().map(GuideResources::parse).anyMatch(test.itemId()::equals))
                .findFirst().orElseThrow(() -> new IllegalStateException("No lesson uses coverage stack " + test.itemId()))
                : GuideBook.lessons().stream().filter(candidate -> candidate.id().equals(test.lesson())).findFirst()
                .orElseThrow(() -> new IllegalStateException("Unknown coverage lesson: " + test.lesson()));
        var manualCourse = presentationGuideScreen(lesson.id(), covered);
        if (original.isEquivalentTo(manualCourse) || manualCourse.isEquivalentTo(original))
            throw new IllegalStateException("Native W and manual-course history crossed their screen boundary");

        var pipe = new ItemStack(BuiltInRegistries.ITEM.get(GuideResources.parse("immersiveengineering:fluid_pipe")));
        PonderUI pipeUi = PonderUI.of(pipe.copy());
        var foreign = presentationForeignInitialScreen(pipe);
        if (!pipeUi.isEquivalentTo(foreign) || !foreign.isEquivalentTo(pipeUi))
            throw new IllegalStateException("The component guard changed native history equivalence for a foreign scene");
        coverageComponentHistoryDistinct = true;
        coverageNativeHistoryPreserved = true;
    }

    private static void startCoverageNavigation(Minecraft client) {
        coveragePonderCase = coverageAudit.firstPonderCase();
        ItemStack source = coveragePonderCase.copyStack();
        PonderUI ui = PonderUI.of(source.copy());
        if (ui.getClass() != PonderUI.class || !(ui instanceof NativePonderItemStackContext context)
                || !ItemStack.isSameItemSameComponents(context.ieponder$getSourceStack(), source))
            throw new IllegalStateException("Component coverage W factory lost its source stack before opening");
        var scenes = ponderScenes(ui);
        int index = java.util.stream.IntStream.range(0, scenes.size())
                .filter(candidate -> GuideConstants.MOD_ID.equals(scenes.get(candidate).getId().getNamespace())
                        && scenes.get(candidate).getLocation().equals(coveragePonderCase.itemId())
                        && (coveragePonderCase.lesson() == null
                        || scenes.get(candidate).getId().getPath().equals(coveragePonderCase.lesson())))
                .findFirst().orElseThrow(() -> new IllegalStateException("No owned W scene matches component case: "
                        + coveragePonderCase.key()));
        var access = (dev.ieponder.mixin.PonderUIAccessor) (Object) ui;
        access.ieponder$setIndex(index);
        access.ieponder$getLazyIndex().startWithValue(index);
        var lesson = GuideBook.lessons().stream().filter(candidate ->
                        candidate.id().equals(scenes.get(index).getId().getPath())).findFirst()
                .orElseThrow(() -> new IllegalStateException("Coverage W factory selected an unknown lesson"));
        if (coveragePonderCase.chapter() != null && !coveragePonderCase.chapter().equals(lesson.chapter()))
            throw new IllegalStateException("Coverage W scene is outside its expected chapter: " + coveragePonderCase.key());
        coverageOriginLesson = lesson.id();
        coverageOriginChapter = lesson.chapter();
        coverageOriginUi = ui;
        client.setScreen(ui);
        transitionTo("coverage_w_open");
    }

    private static void tickCoverage(Minecraft client) {
        switch (stage) {
            case "coverage_w_open" -> {
                if (phaseFrame < 3) return;
                if (client.screen != coverageOriginUi)
                    throw new IllegalStateException("Native component W factory did not open its PonderUI instance");
                requireSceneContext(coverageOriginUi, coverageOriginLesson,
                        coveragePonderCase.itemId().toString(), coverageOriginChapter);
                if (!(coverageOriginUi instanceof NativePonderItemStackContext context)
                        || !ItemStack.isSameItemSameComponents(context.ieponder$getSourceStack(), coveragePonderCase.stack())
                        || !ItemStack.isSameItemSameComponents(coverageOriginUi.getSubject(), coveragePonderCase.stack()))
                    throw new IllegalStateException("Opening native W did not preserve the exact component stack");
                verifyNativeCourseWidgets(coverageOriginUi);
                coverageRealWToTag = true;
                clickTagFromCourse(coverageOriginChapter);
                transitionTo("coverage_w_tag");
            }
            case "coverage_w_tag" -> {
                if (phaseFrame < 12) return;
                if (!(client.screen instanceof PonderTagScreen tagScreen)
                        || !tagScreen.getTag().getId().equals(GuideResources.of(GuideConstants.MOD_ID, coverageOriginChapter)))
                    throw new IllegalStateException("Native W category button did not open its matching Ponder tag");
                var target = findAnotherCoverageLesson(tagScreen, coverageOriginLesson);
                coverageTargetItem = target.getKey();
                coverageTargetLesson = target.getValue().id();
                clickItem(coverageTargetItem);
                transitionTo("coverage_tag_lesson");
            }
            case "coverage_tag_lesson" -> {
                if (phaseFrame < 3) return;
                var lesson = requireGuidePonder(client, coverageTargetLesson);
                var lessonData = GuideBook.lessons().stream().filter(candidate -> candidate.id().equals(coverageTargetLesson))
                        .findFirst().orElseThrow();
                requireSceneContext(lesson, lessonData.id(), GuideResources.parse(lessonData.icon()).toString(), lessonData.chapter());
                coverageAnotherLesson = true;
                clickBack();
                transitionTo("coverage_tag_back");
            }
            case "coverage_tag_back" -> {
                if (phaseFrame < 30) return;
                if (!(client.screen instanceof PonderTagScreen tagScreen)
                        || !tagScreen.getTag().getId().equals(GuideResources.of(GuideConstants.MOD_ID, coverageOriginChapter)))
                    throw new IllegalStateException("Back from the selected lesson did not restore the Ponder tag screen");
                clickBack();
                transitionTo("coverage_w_back");
            }
            case "coverage_w_back" -> {
                if (phaseFrame < 30) return;
                if (!(client.screen instanceof PonderUI ui) || ui.getClass() != PonderUI.class)
                    throw new IllegalStateException("Back from the Ponder tag did not restore the native W screen");
                requireSceneContext(ui, coverageOriginLesson, coveragePonderCase.itemId().toString(), coverageOriginChapter);
                if (!(ui instanceof NativePonderItemStackContext context)
                        || !ItemStack.isSameItemSameComponents(context.ieponder$getSourceStack(), coveragePonderCase.stack())
                        || !ItemStack.isSameItemSameComponents(ui.getSubject(), coveragePonderCase.stack()))
                    throw new IllegalStateException("Returning through native Ponder history lost covered stack components");
                coverageBackPreservedStack = true;
                coverageCommandIndex = 0;
                executeCoverageCommand(client);
            }
            case "coverage_foreign_move" -> {
                if (phaseFrame < 3) return;
                if (!(client.screen instanceof ValidationContextProbeScreen ui)
                        || !ui.getActiveScene().getId().equals(GuideResources.of(GuideConstants.MOD_ID, "fluid_pipes")))
                    throw new IllegalStateException("Foreign-context probe did not start on IE logistics scene");
                requireSceneContext(ui, "fluid_pipes", "immersiveengineering:fluid_pipe", "logistics");
                verifyNativeCourseWidgets(ui);
                clickArrow(true);
                transitionTo("coverage_foreign_restore");
            }
            case "coverage_foreign_restore" -> {
                if (phaseFrame < 3) return;
                if (!(client.screen instanceof ValidationContextProbeScreen ui)
                        || !ui.getActiveScene().getId().equals(GuideResources.of("validationfixture", "foreign_machine")))
                    throw new IllegalStateException("Native scroll did not reach the foreign context fixture");
                verifyRestoredCoverageContext(ui);
                coverageForeignContextRestored = true;
                openMixedContextProbe(client);
                transitionTo("coverage_foreign_override_setup");
            }
            case "coverage_foreign_override_setup" -> {
                if (phaseFrame < 3) return;
                if (!(client.screen instanceof ValidationContextProbeScreen ui)
                        || !ui.getActiveScene().getId().equals(GuideResources.of(GuideConstants.MOD_ID, "fluid_pipes")))
                    throw new IllegalStateException("Foreign-override fixture did not start on an IE scene");
                requireSceneContext(ui, "fluid_pipes", "immersiveengineering:fluid_pipe", "logistics");
                var access = (dev.ieponder.mixin.PonderUIAccessor) (Object) ui;
                access.ieponder$setStack(new ItemStack(BuiltInRegistries.ITEM.get(GuideResources.parse("minecraft:hopper"))));
                var tags = access.ieponder$getTags();
                tags.clear();
                tags.add(PonderIndex.getTagAccess().getRegisteredTag(GuideResources.of(GuideConstants.MOD_ID, "power")));
                transitionTo("coverage_foreign_override_tick");
            }
            case "coverage_foreign_override_tick" -> {
                if (phaseFrame < 3) return;
                if (!(client.screen instanceof ValidationContextProbeScreen ui)
                        || !ui.getActiveScene().getId().equals(GuideResources.of(GuideConstants.MOD_ID, "fluid_pipes")))
                    throw new IllegalStateException("Foreign override left its IE scene before the probe tick");
                requireSyntheticForeignOverride(ui);
                clickArrow(true);
                transitionTo("coverage_foreign_override_restore");
            }
            case "coverage_foreign_override_restore" -> {
                if (phaseFrame < 3) return;
                if (!(client.screen instanceof ValidationContextProbeScreen ui)
                        || !ui.getActiveScene().getId().equals(GuideResources.of("validationfixture", "foreign_machine")))
                    throw new IllegalStateException("Native scroll did not reach the overridden foreign fixture");
                requireSyntheticForeignOverride(ui);
                verifyNativeCourseWidgets(ui);
                coverageForeignOverridePreserved = true;
                writeSuccess(client);
            }
            case "coverage_command_open" -> {
                if (phaseFrame < 3) return;
                requireGuidePonder(client, coverageTargetLesson);
                coverageCommandCount++;
                clickBack();
                transitionTo("coverage_command_back");
            }
            case "coverage_command_back" -> {
                if (phaseFrame < 30) return;
                if (client.screen != coverageOriginUi)
                    throw new IllegalStateException("Item command back path did not restore the originating native W screen");
                if (coverageCommandIndex + 1 < coverageAudit.itemCommands().size()) {
                    coverageCommandIndex++;
                    executeCoverageCommand(client);
                } else {
                    openMixedContextProbe(client);
                    transitionTo("coverage_foreign_move");
                }
            }
            default -> throw new IllegalStateException("Unknown item coverage validation stage: " + stage);
        }
    }

    private static Map.Entry<String, GuideBook.Lesson> findAnotherCoverageLesson(PonderTagScreen screen,
                                                                                   String excludedLesson) {
        for (ResourceLocation itemId : PonderIndex.getTagAccess().getItems(screen.getTag()).stream()
                .sorted(Comparator.comparing(ResourceLocation::toString)).toList()) {
            GuideBook.Lesson lesson = GuideNavigation.findLessonForTagAndItem(screen.getTag(),
                    new ItemStack(BuiltInRegistries.ITEM.get(itemId)));
            if (lesson != null && !lesson.id().equals(excludedLesson))
                return Map.entry(itemId.toString(), lesson);
        }
        throw new IllegalStateException("Coverage chapter has no distinct lesson reachable from its native item list: "
                + screen.getTag().getId());
    }

    private static void verifyRestoredCoverageContext(PonderUI ui) {
        String subject = BuiltInRegistries.ITEM.getKey(ui.getSubject().getItem()).toString();
        var actualTags = ((dev.ieponder.mixin.PonderUIAccessor) (Object) ui).ieponder$getTags().stream()
                .map(PonderTag::getId).collect(java.util.stream.Collectors.toSet());
        var originalTags = PonderIndex.getTagAccess().getTags(GuideResources.parse("immersiveengineering:fluid_pipe"))
                .stream().map(PonderTag::getId).collect(java.util.stream.Collectors.toSet());
        if (!subject.equals("immersiveengineering:fluid_pipe") || !actualTags.equals(originalTags))
            throw new IllegalStateException("Foreign scene did not restore the native IE subject/category context: "
                    + subject + " / " + actualTags);
        verifyNativeCourseWidgets(ui);
    }

    private static void executeCoverageCommand(Minecraft client) {
        String query = coverageAudit.itemCommands().get(coverageCommandIndex);
        GuideBook.Lesson expected = coverageAudit.commandLesson(query);
        if (expected == null) throw new IllegalStateException("Coverage item command has no authored lesson: " + query);
        coverageTargetLesson = expected.id();
        executeClientCommand("ponder-ie item " + query);
        transitionTo("coverage_command_open");
    }

    private static void validateOptionsScreen(net.minecraft.client.gui.screens.Screen screen, boolean verifyReturn) {
        if (!ValidationPlatform.isOptionsScreen(screen))
            throw new IllegalStateException("Expected the vanilla Options screen; actual="
                    + (screen == null ? "<none>" : screen.getClass().getName()));
        int guideButtons = optionsGuideButtonCount(screen);
        if (guideButtons != 0)
            throw new IllegalStateException("Options screen contains " + guideButtons + " ieponder-owned guide widgets");
        int fixtureButtons = ValidationPlatform.fixtureButtonCount(screen);
        int nativeWidgets = ValidationPlatform.minecraftWidgetCount(screen);
        if (fixtureButtons != 1 || nativeWidgets == 0)
            throw new IllegalStateException("Options fixture/vanilla widgets were removed: fixture=" + fixtureButtons
                    + ", vanilla=" + nativeWidgets);
        if (verifyReturn && nativeWidgets < presentationNativeOptionWidgets)
            throw new IllegalStateException("Returning through native Ponder history removed vanilla Options widgets");
        optionsButtonless = true;
        if (verifyReturn) presentationOptionsFixturePreserved = true;
        presentationNativeOptionWidgets = Math.max(presentationNativeOptionWidgets, nativeWidgets);
    }

    private static int optionsGuideButtonCount(net.minecraft.client.gui.screens.Screen screen) {
        return (int) screen.children().stream()
                .filter(net.minecraft.client.gui.components.AbstractWidget.class::isInstance)
                .map(net.minecraft.client.gui.components.AbstractWidget.class::cast)
                .filter(widget -> widget.getClass().getName().startsWith("dev.ieponder.client."))
                .count();
    }

    private static void executeIndexCommand() throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        executeClientCommand("ponder-ie");
    }

    private static void executeClientCommand(String command) {
        try {
            int result = ClientCommandHandler.getDispatcher().execute(command, ClientCommandHandler.getSource());
            if (result <= 0) throw new IllegalStateException("Client command returned no result: " + command);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
            throw new IllegalStateException("Client command failed: " + command, exception);
        }
    }

    private static PonderTagScreen requirePresentationTagScreen(Minecraft client, String chapterId) {
        requireNativeTagScreen(client, chapterId, JAPANESE);
        var screen = (PonderTagScreen) client.screen;
        var expectedItems = PonderIndex.getTagAccess().getItems(
                PonderIndex.getTagAccess().getRegisteredTag(GuideResources.of("ieponder", chapterId)));
        var renderedItems = screen.children().stream().filter(PonderButton.class::isInstance).map(PonderButton.class::cast)
                .filter(button -> button.getItem() != null)
                .map(button -> BuiltInRegistries.ITEM.getKey(button.getItem().getItem()))
                .collect(java.util.stream.Collectors.toSet());
        if (!renderedItems.equals(expectedItems))
            throw new IllegalStateException("Native tag screen does not contain exactly its registered chapter items: "
                    + chapterId + ", expected=" + expectedItems + ", actual=" + renderedItems);
        return screen;
    }

    private static void requireSceneContext(PonderUI ui, String lessonId, String subjectId, String chapterId) {
        if (!ui.getActiveScene().getId().getPath().equals(lessonId))
            throw new IllegalStateException("Wrong active Ponder lesson: expected=" + lessonId + ", actual="
                    + ui.getActiveScene().getId());
        var actualSubject = BuiltInRegistries.ITEM.getKey(ui.getSubject().getItem());
        if (actualSubject == null || !actualSubject.toString().equals(subjectId))
            throw new IllegalStateException("Ponder subject is not synchronized with its lesson: expected="
                    + subjectId + ", actual=" + actualSubject);
        var tags = ((dev.ieponder.mixin.PonderUIAccessor) (Object) ui).ieponder$getTags();
        if (tags.size() != 1 || !tags.get(0).getId().equals(GuideResources.of("ieponder", chapterId)))
            throw new IllegalStateException("Ponder course must expose exactly its active chapter: expected="
                    + chapterId + ", actual=" + tags.stream().map(tag -> tag.getId().toString()).toList());
    }

    private static void requireSyntheticForeignOverride(PonderUI ui) {
        String subject = BuiltInRegistries.ITEM.getKey(ui.getSubject().getItem()).toString();
        var expectedTag = GuideResources.of("ieponder", "power");
        var actualTags = ((dev.ieponder.mixin.PonderUIAccessor) (Object) ui).ieponder$getTags().stream()
                .map(PonderTag::getId).toList();
        if (!subject.equals("minecraft:hopper") || !actualTags.equals(List.of(expectedTag)))
            throw new IllegalStateException("The synthetic foreign context override was overwritten: "
                    + subject + " / " + actualTags);
    }

    private static int verifyNativeCourseWidgets(PonderUI ui) {
        var tags = ((dev.ieponder.mixin.PonderUIAccessor) (Object) ui).ieponder$getTags();
        try {
            var field = PonderUI.class.getDeclaredField("tagButtons");
            field.setAccessible(true);
            var tagButtons = (List<?>) field.get(ui);
            if (tagButtons.size() != tags.size())
                throw new IllegalStateException("Native course chapter widgets do not match the current tag list");
            for (var button : tagButtons) {
                long copies = ui.children().stream().filter(child -> child == button).count();
                if (copies != 1)
                    throw new IllegalStateException("Native course chapter widget is missing or duplicated: copies=" + copies);
            }
            int childButtons = (int) ui.children().stream().filter(PonderButton.class::isInstance).count();
            int nonTagControls = childButtons - tagButtons.size();
            var previous = PRESENTATION_NATIVE_CONTROL_BUTTON_COUNTS.putIfAbsent(ui, nonTagControls);
            if (previous != null && previous != nonTagControls)
                throw new IllegalStateException("Native non-tag control count changed after lesson/category movement: "
                        + previous + " -> " + nonTagControls);
            return childButtons;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot inspect native Ponder tag widgets", exception);
        }
    }

    private static String activeLesson(Minecraft client) {
        if (!(client.screen instanceof PonderUI ui))
            throw new IllegalStateException("No Ponder scene is active for presentation validation");
        return ui.getActiveScene().getId().getPath();
    }

    private static GuideBook.Lesson activeLessonData(PonderUI ui) {
        String lessonId = ui.getActiveScene().getId().getPath();
        return GuideBook.lessons().stream().filter(lesson -> lesson.id().equals(lessonId)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Active scene is not an IE guide lesson: " + lessonId));
    }

    private static List<Integer> presentationToneSampleTimes(PonderScene scene) {
        if (scene.getKeyframeCount() <= 4 || presentationToneUnits.size() != 3)
            throw new IllegalStateException("The mixed-tone coke oven sample does not match the authored scene");
        var result = new ArrayList<Integer>();
        int nextPageOffset = 12;
        for (var unit : presentationToneUnits) {
            int sampleTime = scene.getKeyframeTime(4) + nextPageOffset + 30;
            if (sampleTime >= scene.getTotalTime())
                throw new IllegalStateException("Authored text unit is outside coke_oven_4 scene time");
            result.add(sampleTime);
            nextPageOffset += GuideCaptions.duration(unit.text()) + 12;
        }
        return List.copyOf(result);
    }

    private static void openNativeTagTickProbe(Minecraft client) {
        var pipe = new ItemStack(BuiltInRegistries.ITEM.get(GuideResources.parse("immersiveengineering:fluid_pipe")));
        PonderUI ui = PonderUI.of(pipe);
        if (ui.getClass() != PonderUI.class)
            throw new IllegalStateException("The referredToByTag probe must use the public native Ponder factory");
        var scenes = ponderScenes(ui);
        int logisticsIndex = java.util.stream.IntStream.range(0, scenes.size())
                .filter(index -> scenes.get(index).getId().getPath().equals("fluid_pipes"))
                .findFirst().orElseThrow(() -> new IllegalStateException("fluid_pipe W course has no logistics lesson"));
        var access = (dev.ieponder.mixin.PonderUIAccessor) (Object) ui;
        access.ieponder$setIndex(logisticsIndex);
        access.ieponder$getLazyIndex().startWithValue(logisticsIndex);
        client.setScreen(ui);
        try {
            var field = PonderUI.class.getDeclaredField("referredToByTag");
            field.setAccessible(true);
            field.set(ui, PonderIndex.getTagAccess().getRegisteredTag(GuideResources.of("ieponder", "processing")));
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot configure the native referredToByTag tick probe", exception);
        }
    }

    private static List<PonderScene> ponderScenes(PonderUI ui) {
        try {
            var field = PonderUI.class.getDeclaredField("scenes");
            field.setAccessible(true);
            @SuppressWarnings("unchecked") var scenes = (List<PonderScene>) field.get(ui);
            return scenes;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot inspect the native Ponder scene list", exception);
        }
    }

    private static void openMixedContextProbe(Minecraft client) {
        var fluidPipe = GuideResources.parse("immersiveengineering:fluid_pipe");
        var scenes = PonderIndex.getSceneAccess().compile(fluidPipe);
        var ieScene = scenes.stream().filter(scene -> scene.getId().getPath().equals("fluid_pipes")).findFirst()
                .orElseThrow(() -> new IllegalStateException("No IE logistics scene for the synthetic context probe"));
        var foreignScene = scenes.stream().filter(scene -> !scene.getId().getPath().equals("fluid_pipes"))
                .findFirst().orElseThrow(() -> new IllegalStateException("No second real scene for the synthetic context probe"));
        try {
            var id = PonderScene.class.getDeclaredField("sceneId");
            id.setAccessible(true);
            id.set(foreignScene, GuideResources.of("validationfixture", "foreign_machine"));
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot mark a fresh scene as a foreign context fixture", exception);
        }
        client.setScreen(new ValidationContextProbeScreen(List.of(ieScene, foreignScene)));
    }

    private static void validateTooltipToneColors() {
        boolean collapsedHint = false;
        for (var description : GuideItemDescriptions.descriptions()) {
            var stack = new ItemStack(BuiltInRegistries.ITEM.get(GuideResources.parse(description.items().get(0))));
            var collapsed = new ArrayList<Component>();
            collapsed.add(stack.getHoverName());
            GuideItemDescriptions.append(stack, collapsed, false);
            if (collapsed.size() < 2 || componentColor(collapsed.get(1)) != GuideTextTones.Tone.HINT.textColor())
                throw new IllegalStateException("Collapsed tooltip hint is not rendered in native bright green: " + description.id());
            collapsedHint = true;

            var expanded = new ArrayList<Component>();
            expanded.add(stack.getHoverName());
            GuideItemDescriptions.append(stack, expanded, true);
            for (var component : expanded.subList(1, expanded.size())) {
                int color = componentColor(component);
                for (var tone : GuideTextTones.Tone.values())
                    if (color == tone.textColor()) PRESENTATION_TOOLTIP_TONES.add(tone);
            }
        }
        if (!collapsedHint || !PRESENTATION_TOOLTIP_TONES.equals(java.util.EnumSet.allOf(GuideTextTones.Tone.class)))
            throw new IllegalStateException("Item tooltip output must exercise native white, pale red and pale green colors: "
                    + PRESENTATION_TOOLTIP_TONES);
        presentationTooltipToneColors = true;
    }

    private static int componentColor(Component component) {
        var color = component.getStyle().getColor();
        if (color == null) throw new IllegalStateException("Guide tooltip component has no authored tone color");
        return color.getValue();
    }

    private static void validateScenes() {
        var lessons = GuideBook.lessons();
        if (lessons.isEmpty()) throw new IllegalStateException("No Immersive Engineering lessons are available");

        for (var lesson : lessons) {
            requireItem(lesson.icon());
            requireTooltipEntry(lesson.icon());
            for (var component : lesson.components()) {
                requireItem(component);
                requireTooltipEntry(component);
            }
        }

        var configured = java.util.Arrays.stream(System.getProperty("ieponder.validationLessons", "").split(","))
                .map(String::strip).filter(id -> !id.isEmpty()).toList();
        var selectedIds = configured.isEmpty() ? List.of(DEFAULT_VALIDATION_LESSONS) : configured;
        if (selectedIds.size() > 3 || selectedIds.stream().distinct().count() != selectedIds.size())
            throw new IllegalArgumentException("Select one to three distinct lessons for client validation: " + selectedIds);
        var selectedLessons = selectedIds.stream().map(id -> lessons.stream()
                        .filter(lesson -> lesson.id().equals(id)).findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Unknown validation lesson: " + id)))
                .toList();

        // The shared caption scheduler affects every lesson. Compile/seek them all
        // cheaply, while limiting actual framebuffer rendering to the selected three.
        for (var lesson : lessons) {
            for (var step : lesson.steps()) {
                if (step.flow() != null) {
                    requireItem(step.flow().item());
                    verifiedFlows++;
                }
                for (var callout : step.callouts()) requireItem(callout.item());
                verifiedSteps++;
                step.nbt().values().forEach(GuideScenes::parseNbt);
            }
            for (var node : lesson.nodes()) {
                GuideScenes.state(node);
                GuideScenes.parseNbt(node.nbt());
                verifiedNodes++;
            }
            for (var actor : lesson.entities()) {
                var type = GuideResources.parse(actor.type());
                if (!BuiltInRegistries.ENTITY_TYPE.containsKey(type))
                    throw new IllegalStateException("Unknown lesson entity type: " + lesson.id() + "/" + type);
                verifiedEntities++;
            }

            var entry = GuidePlugin.entry(lesson.id());
            var scene = PonderIndex.getSceneAccess().compile(List.of(entry)).get(0);
            if (scene.getKeyframeCount() != lesson.steps().size())
                throw new IllegalStateException("Ponder keyframe count differs from lesson steps: " + lesson.id());
            scene.seekToTime(scene.getTotalTime());
            checkNodeStates(lesson, scene);
            var firstActorTypes = checkVisibleActorTypes(lesson, scene);
            scene.begin();
            scene.seekToTime(scene.getTotalTime());
            checkNodeStates(lesson, scene);
            var replayedActorTypes = checkVisibleActorTypes(lesson, scene);
            if (!firstActorTypes.equals(replayedActorTypes))
                throw new IllegalStateException("Lesson actors changed after replay: " + lesson.id()
                        + ", first=" + firstActorTypes + ", replay=" + replayedActorTypes);
            if (!lesson.entities().isEmpty()) verifiedActorReplays++;
            verifiedCallouts += checkCallouts(lesson, scene);
            scene.begin();
            scene.seekToTime(scene.getTotalTime());
            checkNodeStates(lesson, scene);
            if (!replayedActorTypes.equals(checkVisibleActorTypes(lesson, scene)))
                throw new IllegalStateException("Lesson actors changed after callout inspection replay: " + lesson.id());
            SCENES.add(scene);
            LogUtils.getLogger().info("IEPONDER_VALIDATION_SCENE_PASS: {} ({} nodes, {} steps)",
                    lesson.id(), lesson.nodes().size(), lesson.steps().size());
        }

        if (SCENES.size() != lessons.size())
            throw new IllegalStateException("The authored lesson scenes did not all compile");
        for (var lesson : selectedLessons)
            VISUAL_SCENES.add(SCENES.stream().filter(scene -> scene.getId().getPath().equals(lesson.id())).findFirst().orElseThrow());
    }

    private static void requireTooltipEntry(String item) {
        ResourceLocation id = GuideResources.parse(item);
        if (!PonderIndex.getSceneAccess().doScenesExistForId(id))
            throw new IllegalStateException("Item has no tooltip Ponder entry: " + id);
    }

    private static int checkCallouts(GuideBook.Lesson lesson, PonderScene scene) {
        var expectedCallouts = lesson.steps().stream().mapToInt(step -> step.callouts().size()).sum();
        if (expectedCallouts == 0) return 0;
        // Node/replay checks above leave the scene at its end; seeks only move forward.
        scene.begin();
        try {
            Field itemField = InputWindowElement.class.getDeclaredField("item");
            itemField.setAccessible(true);
            int checked = 0;
            for (int stepIndex = 0; stepIndex < lesson.steps().size(); stepIndex++) {
                var step = lesson.steps().get(stepIndex);
                int slots = step.callouts().size();
                if (slots == 0) continue;
                int slotDuration = GuideScenes.captionDuration(lesson, stepIndex) / slots;
                for (int slot = 0; slot < slots; slot++) {
                    int time = scene.getKeyframeTime(stepIndex) + 12 + slot * slotDuration + 15;
                    scene.seekToTime(time);
                    var visible = scene.getElements().stream().filter(InputWindowElement.class::isInstance)
                            .map(InputWindowElement.class::cast).filter(InputWindowElement::isVisible).toList();
                    if (visible.size() != 1)
                        throw new IllegalStateException("Missing or overlapping item callout: " + lesson.id()
                                + "/step=" + stepIndex + "/slot=" + slot + ", visible=" + visible.size());
                    var bubble = visible.get(0);
                    var stack = (ItemStack) itemField.get(bubble);
                    String actual = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                    String expected = step.callouts().get(slot).item();
                    if (bubble.getFade(1) < .99f || !actual.equals(expected))
                        throw new IllegalStateException("Wrong or invisible item callout: " + lesson.id()
                                + "/step=" + stepIndex + "/slot=" + slot + ", expected=" + expected + ", actual=" + actual);
                    checked++;
                }
            }
            return checked;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot inspect rendered Ponder item callouts for " + lesson.id(), exception);
        }
    }

    private static void checkNodeStates(GuideBook.Lesson lesson, PonderScene scene) {
        for (var node : lesson.nodes()) {
            var expected = GuideScenes.state(node);
            var actual = scene.getWorld().getBlockState(node.position());
            if (!actual.equals(expected))
                throw new IllegalStateException("Lesson node state changed: " + lesson.id() + "/" + node.id()
                        + ", expected=" + expected + ", actual=" + actual);
        }
    }

    private static Map<String, Integer> checkVisibleActorTypes(GuideBook.Lesson lesson, PonderScene scene) {
        var expected = new HashMap<String, Integer>();
        var visible = new HashMap<String, Boolean>();
        lesson.entities().forEach(actor -> visible.put(actor.id(), false));
        for (var step : lesson.steps()) {
            for (var actor : lesson.entities())
                if (step.hide().contains(actor.showAt())) visible.put(actor.id(), false);
            for (var actor : lesson.entities())
                if (step.show().contains(actor.showAt())) visible.put(actor.id(), true);
        }
        for (var actor : lesson.entities()) {
            String type = GuideResources.parse(actor.type()).toString();
            if (visible.getOrDefault(actor.id(), false) && !type.equals("minecraft:item"))
                expected.merge(type, 1, Integer::sum);
        }

        var actual = new HashMap<String, Integer>();
        for (var element : scene.getElements()) {
            if (!(element instanceof EntityElement actorElement) || !actorElement.isVisible()) continue;
            actorElement.ifPresent(entity -> {
                if (!entity.isAlive() || entity instanceof ItemEntity) return;
                String type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
                actual.merge(type, 1, Integer::sum);
            });
        }
        if (!expected.equals(actual))
            throw new IllegalStateException("Visible lesson actors do not match the final scene: " + lesson.id()
                    + ", expected=" + expected + ", actual=" + actual);
        return Map.copyOf(actual);
    }

    private static void requireItem(String item) {
        var id = GuideResources.parse(item);
        if (!BuiltInRegistries.ITEM.containsKey(id)) throw new IllegalStateException("Unknown guide item: " + id);
    }

    private static void requireNativeTagIndex(Minecraft client, String locale) {
        if (client.screen == null || client.screen.getClass() != PonderTagIndexScreen.class)
            throw new IllegalStateException("Expected Ponder's native tag index for " + locale + "; actual=" + screenName(client));
        requireLocalized("ieponder.title", locale);
        for (var chapter : dev.ieponder.client.GuideChapter.active(GuideBook.lessons())) {
            var button = findTagButton((PonderTagIndexScreen) client.screen, chapter.id());
            requireTagTranslation(button.getTag(), locale);
        }
        if (VALIDATED_LOCALES.add(locale)) validateLocalizedGuideContent(locale);
    }

    private static void validateLocalizedGuideContent(String locale) {
        for (var lesson : GuideBook.lessons()) {
            String titleKey = "ieponder.ponder." + lesson.id() + ".header";
            String summaryKey = "ieponder.lesson." + lesson.id() + ".summary";
            if (locale.equals(JAPANESE) || locale.equals(ENGLISH)) {
                requireExactText(titleKey, lesson.title(), locale);
                requireExactText(summaryKey, lesson.summary(), locale);
            } else {
                requireLocalized(titleKey, locale);
                requireLocalized(summaryKey, locale);
            }
            for (int i = 0; i < lesson.steps().size(); i++) {
                String key = "ieponder.ponder.shared." + lesson.id() + "_" + i;
                if (locale.equals(JAPANESE) || locale.equals(ENGLISH))
                    requireExactText(key, lesson.steps().get(i).text(), locale);
                else requireLocalized(key, locale);
            }
        }
        verifyDescriptionTooltips(locale);
    }

    private static void validateLanguageResources(Minecraft client) {
        var nativeId = GuideResources.of("ponder", "lang/en_us.json");
        for (var resource : client.getResourceManager().getResourceStack(nativeId)) {
            try (var reader = new InputStreamReader(resource.open(), StandardCharsets.UTF_8)) {
                for (var entry : JsonParser.parseReader(reader).getAsJsonObject().entrySet()) {
                    String key = entry.getKey();
                    if (key.startsWith("ponder.ui.") || key.startsWith("ponder.ponder.shared.") || key.equals("catnip.ui.go_back_button"))
                        NATIVE_UI_ENGLISH.put(key, entry.getValue().getAsString());
                }
            } catch (Exception exception) {
                throw new IllegalStateException("Cannot read native Ponder UI originals", exception);
            }
        }
        if (NATIVE_UI_ENGLISH.size() != 22) throw new IllegalStateException("Native Ponder UI originals changed");
        var english = readLanguageResource(client, ENGLISH);
        var storyKeys = new java.util.HashSet<String>();
        for (var lesson : GuideBook.lessons()) {
            storyKeys.add("ieponder.ponder." + lesson.id() + ".header");
            storyKeys.add("ieponder.lesson." + lesson.id() + ".summary");
            for (int i = 0; i < lesson.steps().size(); i++)
                storyKeys.add("ieponder.ponder.shared." + lesson.id() + "_" + i);
        }
        GuideItemDescriptions.descriptions().forEach(description -> storyKeys.add(description.translationKey()));
        if (!english.keySet().containsAll(storyKeys))
            throw new IllegalStateException("English guide language resource is missing authored content keys");

        for (var locale : LOCALES) {
            var values = locale.equals(ENGLISH) ? english : readLanguageResource(client, locale);
            if (!values.keySet().equals(english.keySet()))
                throw new IllegalStateException("Wrong guide translation keys for " + locale + ": expected="
                        + english.keySet().size() + ", actual=" + values.keySet().size());
            var rawValues = new LinkedHashMap<String, String>();
            for (var entry : values.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue().getAsString();
                if (value.isBlank() || value.equals(key) || value.contains("ieponder."))
                    throw new IllegalStateException("Empty or unresolved guide translation: " + locale + "/" + key);
                String englishValue = english.get(key).getAsString();
                if (!locale.equals(JAPANESE) && !locale.equals(ENGLISH) && value.equals(englishValue))
                    throw new IllegalStateException("English fallback remains untranslated: " + locale + "/" + key);
                if (!placeholderSignature(englishValue).equals(placeholderSignature(value)))
                    throw new IllegalStateException("Placeholder mismatch in guide translation: " + locale + "/" + key);
                rawValues.put(key, value);
            }
            LANGUAGE_RESOURCES.put(locale, Map.copyOf(rawValues));
            verifiedLanguageResources++;
        }
    }

    private static JsonObject readLanguageResource(Minecraft client, String locale) {
        var id = GuideResources.of("ieponder", "lang/" + locale + ".json");
        var resource = client.getResourceManager().getResource(id)
                .orElseThrow(() -> new IllegalStateException("Missing guide language resource: " + id));
        try (var reader = new InputStreamReader(resource.open(), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot read guide language resource: " + id, exception);
        }
    }

    private static List<String> placeholderSignature(String value) {
        var matcher = java.util.regex.Pattern.compile("%(?:\\d+\\$)?[sd]").matcher(value);
        var result = new ArrayList<String>();
        while (matcher.find()) result.add(matcher.group());
        result.sort(String::compareTo);
        return result;
    }

    private static void validateActiveLocale(Minecraft client, String locale) {
        if (!LOCALES.contains(locale))
            throw new IllegalStateException("Unsupported locale in validation cycle: " + locale);
        var languageResource = LANGUAGE_RESOURCES.get(locale);
        if (languageResource == null)
            throw new IllegalStateException("Guide language resource was not loaded for " + locale);
        for (var entry : languageResource.entrySet()) {
            if (!Language.getInstance().getOrDefault(entry.getKey()).equals(entry.getValue()))
                throw new IllegalStateException("Active language does not match its guide resource: " + locale + "/" + entry.getKey());
        }
        var displayedText = new ArrayList<>(languageResource.values());
        for (var entry : NATIVE_UI_ENGLISH.entrySet()) {
            String key = entry.getKey();
            String value = Language.getInstance().getOrDefault(key);
            if (value.isBlank() || value.equals(key) || (!locale.equals(ENGLISH) && value.equals(entry.getValue())
                    && !key.equals("ponder.ponder.shared.ctrl_and")))
                throw new IllegalStateException("Native Ponder UI remains untranslated: " + locale + "/" + key);
            if (!placeholderSignature(value).equals(placeholderSignature(entry.getValue())))
                throw new IllegalStateException("Native Ponder UI placeholder mismatch: " + locale + "/" + key);
            displayedText.add(value);
            verifiedNativeUiLabels++;
        }
        validateGlyphCoverage(client, locale, displayedText);
        validateLocalizedGuideContent(locale);
        for (var lesson : GuideBook.lessons()) {
            var title = Component.translatable("ieponder.ponder." + lesson.id() + ".header").getString();
            int titleLines = client.font.split(Component.literal(title), 180).size();
            maximumTitleLines = Math.max(maximumTitleLines, titleLines);
            if (titleLines > 3) {
                TITLE_OVERFLOWS.add(locale + "/" + lesson.id() + "=" + titleLines);
                LogUtils.getLogger().warn("IEPONDER_TITLE_OVERFLOW {}/{}={} title={}", locale, lesson.id(), titleLines, title);
            }
            requireLesson(GuideNavigation.findLessonForSearch(title), lesson.id(), "localized title search: " + locale);
            var summary = Component.translatable("ieponder.lesson." + lesson.id() + ".summary").getString();
            requireLesson(GuideNavigation.findLessonForSearch(summary), lesson.id(), "localized summary search: " + locale);
        }
        requireLesson(GuideNavigation.findLessonForSearch(Component.translatable(
                "ieponder.ponder.shared.refinery_6").getString()), "refinery", "localized caption search: " + locale);
        for (var chapter : dev.ieponder.client.GuideChapter.active(GuideBook.lessons())) {
            var tag = PonderIndex.getTagAccess().getRegisteredTag(GuideResources.of("ieponder", chapter.id()));
            requireTagTranslation(tag, locale);
            verifiedLocalizedChapters++;
        }
        var itemIds = new java.util.LinkedHashSet<String>();
        for (var lesson : GuideBook.lessons()) {
            itemIds.add(lesson.icon());
            itemIds.addAll(lesson.components());
        }
        for (var itemId : itemIds) {
            var item = BuiltInRegistries.ITEM.get(GuideResources.parse(itemId));
            String key = item.getDescriptionId();
            String name = new ItemStack(item).getHoverName().getString();
            if (name.isBlank() || name.equals(key) || name.contains("immersiveengineering:"))
                throw new IllegalStateException("Item name is not localized for " + locale + ": " + itemId + " => " + name);
            verifiedLocalizedItems++;
        }
        validateCaptionPages(client, locale);
        VALIDATED_LOCALES.add(locale);
        LogUtils.getLogger().info("IEPONDER_LOCALE_PASS {}", locale);
    }

    private static void validateGlyphCoverage(Minecraft client, String locale, java.util.Collection<String> texts) {
        try {
            var getter = net.minecraft.client.gui.Font.class.getDeclaredMethod("getFontSet", ResourceLocation.class);
            getter.setAccessible(true);
            var fontSet = (net.minecraft.client.gui.font.FontSet) getter.invoke(client.font, net.minecraft.network.chat.Style.DEFAULT_FONT);
            var codepoints = new java.util.HashSet<Integer>();
            // Include spaces and ASCII for inexpensive draft checks after this actual-font pass.
            java.util.stream.IntStream.rangeClosed(32, 126).forEach(codepoints::add);
            for (var text : texts) {
                text.codePoints().forEach(codepoints::add);
                if (client.font.isBidirectional())
                    client.font.bidirectionalShaping(text).codePoints().forEach(codepoints::add);
            }
            for (int codepoint : codepoints) {
                if (Character.isWhitespace(codepoint) || Character.isSpaceChar(codepoint)
                        || Character.getType(codepoint) == Character.FORMAT) continue;
                if (fontSet.getGlyphInfo(codepoint, false) == net.minecraft.client.gui.font.glyphs.SpecialGlyphs.MISSING)
                    throw new IllegalStateException("Missing font glyph for " + locale + ": U+" + Integer.toHexString(codepoint));
                verifiedGlyphCodepoints++;
            }
            var advances = new java.util.TreeMap<Integer, Float>();
            for (int codepoint : codepoints) {
                if (codepoint == '\n' || codepoint == '\r') continue;
                advances.put(codepoint, Character.getType(codepoint) == Character.FORMAT
                        ? 0f : fontSet.getGlyphInfo(codepoint, false).getAdvance(false));
            }
            FONT_ADVANCES.put(locale, advances);
            Files.writeString(client.gameDirectory.toPath().resolve("caption-font-metrics.json"),
                    new GsonBuilder().setPrettyPrinting().create().toJson(FONT_ADVANCES));
        } catch (ReflectiveOperationException | java.io.IOException exception) {
            throw new IllegalStateException("Cannot inspect the active Minecraft font stack", exception);
        }
    }

    private static void requireTagTranslation(PonderTag tag, String locale) {
        String chapterId = tag.getId().getPath();
        String title = requireLocalized("ieponder.ponder.tag." + chapterId, locale);
        String description = requireLocalized("ieponder.ponder.tag." + chapterId + ".description", locale);
        if (!tag.getTitle().equals(title) || !tag.getDescription().equals(description))
            throw new IllegalStateException("Native chapter text differs from the " + locale + " translation: " + chapterId);
    }

    private static void validateCaptionPages(Minecraft client, String locale) {
        mostRecentLocaleMaximumCaptionLines = 0;
        for (var lesson : GuideBook.lessons()) {
            for (int i = 0; i < lesson.steps().size(); i++) {
                String key = "ieponder.ponder.shared." + lesson.id() + "_" + i;
                String caption = Component.translatable(key).getString();
                if (caption.isBlank() || caption.equals(key))
                    throw new IllegalStateException("Caption translation is missing for " + locale + "/" + key);
                if (GuideCaptions.duration(caption) < 1)
                    throw new IllegalStateException("Caption duration is invalid for " + locale + "/" + key);
                var problems = CaptionUnits.problems(caption, locale);
                if (!problems.isEmpty()) throw new IllegalStateException("Invalid authored unit: " + locale + "/" + key + problems);
                var pages = GuideCaptions.pages(caption);
                if (!pages.equals(CaptionUnits.split(caption)) || !pages.equals(GuideCaptions.pages(caption, 80))
                        || !pages.equals(GuideCaptions.pages(caption, 1000)))
                    throw new IllegalStateException("Font width changed an authored sentence boundary: " + locale + "/" + key);
                if (!compact(String.join("", pages)).equals(compact(caption)))
                    throw new IllegalStateException("Authored units changed content/order: " + locale + "/" + key);
                for (int pageIndex = 0; pageIndex < pages.size(); pageIndex++) {
                    var page = pages.get(pageIndex);
                    var lines = GuideCaptions.lines(page, GuideCaptions.PAGE_WIDTH);
                    if (!compact(String.join("", lines)).equals(compact(page)))
                        throw new IllegalStateException("Natural wrapping changed sentence content/order: " + locale + "/" + key);
                    maximumCaptionLines = Math.max(maximumCaptionLines, lines.size());
                    mostRecentLocaleMaximumCaptionLines = Math.max(mostRecentLocaleMaximumCaptionLines, lines.size());
                    if (lines.size() <= GuideCaptions.TARGET_LINES) twoLineCaptionUnits++;
                    if (lines.size() > GuideCaptions.MAX_LINES) {
                        CAPTION_OVERFLOWS.add(Map.of("locale", locale, "key", key, "unit", pageIndex,
                                "lines", lines.size(), "text", page));
                        LogUtils.getLogger().warn("IEPONDER_CAPTION_OVERFLOW {}/{}/{}={} text={}",
                                locale, key, pageIndex, lines.size(), page);
                    }
                    for (var line : lines)
                        if (GuideCaptions.renderedWidth(line) > GuideCaptions.PAGE_WIDTH)
                            throw new IllegalStateException("Shaped caption exceeds native width: " + locale + "/" + key);
                    verifiedCaptionPages++;
                }
            }
        }
        if (locale.equals("tr_tr")) {
            int result;
            try {
                result = ClientCommandHandler.getDispatcher().execute("ponder-ie LOGISTICS", ClientCommandHandler.getSource());
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
                throw new IllegalStateException("Turkish-locale uppercase chapter command failed", exception);
            }
            if (result <= 0) throw new IllegalStateException("Turkish-locale uppercase chapter command returned no result");
            // ScreenOpener queues Minecraft.tell; assert after later completed renders.
        }
    }

    private static void requireNativeTagScreen(Minecraft client, String chapterId, String locale) {
        if (client.screen == null || client.screen.getClass() != PonderTagScreen.class)
            throw new IllegalStateException("Expected Ponder's native tag screen for " + chapterId + "; actual=" + screenName(client));
        var tagScreen = (PonderTagScreen) client.screen;
        if (!tagScreen.getTag().getId().equals(GuideResources.of("ieponder", chapterId)))
            throw new IllegalStateException("Native tag screen opened a different chapter: expected=" + chapterId
                    + ", actual=" + tagScreen.getTag().getId());
        requireTagTranslation(tagScreen.getTag(), locale);
    }

    private static PonderButton findTagButton(PonderTagIndexScreen screen, String chapterId) {
        return screen.children().stream().filter(PonderButton.class::isInstance).map(PonderButton.class::cast)
                .filter(button -> button.getTag() != null
                        && button.getTag().getId().equals(GuideResources.of("ieponder", chapterId)))
                .findFirst().orElseThrow(() -> new IllegalStateException("Native Ponder tag index has no chapter: " + chapterId));
    }

    private static void clickTag(String chapterId) {
        if (!(Minecraft.getInstance().screen instanceof PonderTagIndexScreen screen))
            throw new IllegalStateException("Cannot select a chapter outside Ponder's native tag index");
        clickButton(screen, findTagButton(screen, chapterId), "chapter " + chapterId);
    }

    private static void clickTagFromCourse(String chapterId) {
        if (!(Minecraft.getInstance().screen instanceof PonderUI screen))
            throw new IllegalStateException("Cannot select a chapter outside the guide course");
        // Ponder's course strip uses showing(tag), not showingTag(tag), so getTag()
        // is null. Its private widget list is parallel to the public-accessor tag list.
        var tags = ((dev.ieponder.mixin.PonderUIAccessor) (Object) screen).ieponder$getTags();
        int index = java.util.stream.IntStream.range(0, tags.size())
                .filter(i -> tags.get(i).getId().equals(GuideResources.of("ieponder", chapterId)))
                .findFirst().orElseThrow(() -> new IllegalStateException("Course has no chapter tag: " + chapterId));
        try {
            var field = PonderUI.class.getDeclaredField("tagButtons");
            field.setAccessible(true);
            var buttons = (List<?>) field.get(screen);
            if (!(buttons.get(index) instanceof PonderButton button) || !screen.children().contains(button))
                throw new IllegalStateException("Course chapter widget is missing: " + chapterId);
            clickButton(screen, button, "course chapter " + chapterId);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot inspect the native course chapter strip", exception);
        }
    }

    private static void clickItem(String itemId) {
        if (!(Minecraft.getInstance().screen instanceof PonderTagScreen screen))
            throw new IllegalStateException("Cannot select an item outside Ponder's native tag screen");
        var item = GuideResources.parse(itemId);
        var button = screen.children().stream().filter(PonderButton.class::isInstance).map(PonderButton.class::cast)
                .filter(candidate -> candidate.getItem() != null
                        && BuiltInRegistries.ITEM.getKey(candidate.getItem().getItem()).equals(item))
                .findFirst().orElseThrow(() -> new IllegalStateException("Native Ponder tag has no item icon: " + itemId));
        clickButton(screen, button, "item " + itemId + " in " + screen.getTag().getId());
    }

    private static void clickButton(net.minecraft.client.gui.screens.Screen screen, PonderButton button, String label) {
        if (!button.isVisible()) throw new IllegalStateException("Native Ponder button has not finished fading in: " + label);
        double x = button.getX() + button.getWidth() / 2d;
        double y = button.getY() + button.getHeight() / 2d;
        if (!screen.mouseClicked(x, y, 0)) throw new IllegalStateException("Native Ponder button did not handle click: " + label);
    }

    private static void executeCommandCase(Minecraft client) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        while (commandCaseIndex < COMMAND_CASES.size()) {
            var commandCase = COMMAND_CASES.get(commandCaseIndex);
            var previousScreen = client.screen;
            try {
                int result = ClientCommandHandler.getDispatcher().execute(commandCase.command(), ClientCommandHandler.getSource());
                if (commandCase.expectedFailure())
                    throw new IllegalStateException("Expected this client command to be rejected: " + commandCase.command());
                if (result <= 0)
                    throw new IllegalStateException("Client command returned no result: " + commandCase.command());
                return;
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
                if (!commandCase.expectedFailure()) throw exception;
                if (client.screen != previousScreen)
                    throw new IllegalStateException("Rejected item command changed the current screen: " + commandCase.command());
                unknownChapterIconRejected = true;
                commandCaseIndex++;
            }
        }
    }

    private static void validatePublicPonderFactories(Minecraft client) {
        verifyNavigationResolvers();
        if (PRESENTATION_ONLY) validatePresentationHistoryEquivalence(client);
        var manual = new ItemStack(BuiltInRegistries.ITEM.get(GuidePlugin.GUIDE_ITEM));
        var manualUi = PonderUI.of(manual);
        if (manualUi.getClass() != PonderUI.class)
            throw new IllegalStateException("The native one-argument factory for the IE manual was replaced");
        int manualScenes = sceneCount(manualUi);
        if (manualScenes != GuideBook.lessons().size())
            throw new IllegalStateException("The standard manual factory does not expose the complete course: "
                    + manualScenes + "/" + GuideBook.lessons().size());
        var pipeUi = PonderUI.of(new ItemStack(BuiltInRegistries.ITEM.get(
                GuideResources.parse("immersiveengineering:fluid_pipe"))));
        if (pipeUi.getClass() != PonderUI.class)
            throw new IllegalStateException("The null-tag W-entry factory was redirected to a guide screen");
        var nullTagPipeUi = PonderUI.of(new ItemStack(BuiltInRegistries.ITEM.get(
                GuideResources.parse("immersiveengineering:fluid_pipe"))), null);
        if (nullTagPipeUi.getClass() != PonderUI.class)
            throw new IllegalStateException("PonderUI.of(stack, null) no longer uses the native W-entry screen");
        requireTooltipEntry("immersiveengineering:manual");
        requireTooltipEntry("immersiveengineering:fluid_pipe");
        wPublicFactoryPreserved = true;
        manualCourseContainsAllLessons = true;
    }

    private static void validatePresentationHistoryEquivalence(Minecraft client) {
        var pipe = new ItemStack(BuiltInRegistries.ITEM.get(GuideResources.parse("immersiveengineering:fluid_pipe")));
        var nativeUi = PonderUI.of(pipe.copy());
        var nativeTwin = PonderUI.of(pipe.copy());
        var wirecoilUi = PonderUI.of(new ItemStack(
                BuiltInRegistries.ITEM.get(GuideResources.parse("immersiveengineering:wirecoil_copper"))));
        if (nativeUi.getClass() != PonderUI.class || nativeTwin.getClass() != PonderUI.class
                || wirecoilUi.getClass() != PonderUI.class)
            throw new IllegalStateException("History checks require unredirected native PonderUI factory results");
        if (!nativeUi.isEquivalentTo(nativeTwin) || !nativeTwin.isEquivalentTo(nativeUi)
                || nativeUi.isEquivalentTo(wirecoilUi) || wirecoilUi.isEquivalentTo(nativeUi))
            throw new IllegalStateException("Native PonderUI equivalence changed for same/different item subjects");

        var scenes = ponderScenes(nativeUi);
        int logisticsIndex = java.util.stream.IntStream.range(0, scenes.size())
                .filter(index -> scenes.get(index).getId().getPath().equals("fluid_pipes"))
                .findFirst().orElseThrow(() -> new IllegalStateException("fluid_pipe W course has no logistics lesson"));
        var nativeAccess = (dev.ieponder.mixin.PonderUIAccessor) (Object) nativeUi;
        nativeAccess.ieponder$setIndex(logisticsIndex);
        nativeAccess.ieponder$getLazyIndex().startWithValue(logisticsIndex);
        var guide = presentationGuideScreen("fluid_pipes", pipe);

        if (nativeUi.isEquivalentTo(guide) || guide.isEquivalentTo(nativeUi))
            throw new IllegalStateException("A native item course and manual course with the same subject reused history");
        if (readReferredToByTag(nativeUi) != null)
            throw new IllegalStateException("The pre-tick W factory probe unexpectedly selected a tag");

        // A standard W course is also distinct before its first tick consumes referredToByTag.
        setReferredToByTag(nativeUi, PonderIndex.getTagAccess().getRegisteredTag(
                GuideResources.of("ieponder", "processing")));
        if (nativeUi.isEquivalentTo(guide) || guide.isEquivalentTo(nativeUi))
            throw new IllegalStateException("A pending native referredToByTag selection reused manual-course history");

        var foreign = presentationForeignInitialScreen(pipe);
        if (!foreign.getActiveScene().getId().getNamespace().equals("validationfixture"))
            throw new IllegalStateException("The history fixture did not start on its synthetic foreign scene");
        if (foreign.isEquivalentTo(guide) || guide.isEquivalentTo(foreign))
            throw new IllegalStateException("A foreign initial scene and manual course reused history by subject alone");
        if (!nativeUi.isEquivalentTo(foreign) || !foreign.isEquivalentTo(nativeUi))
            throw new IllegalStateException("Native-to-native Ponder history equivalence was changed");
        presentationNativeGuideHistoryDistinct = true;

        var sameLesson = presentationGuideScreen("fluid_pipes", pipe);
        var sameLessonAgain = presentationGuideScreen("fluid_pipes", pipe);
        var otherLesson = presentationGuideScreen("refinery", pipe);
        if (!sameLesson.isEquivalentTo(sameLessonAgain) || !sameLessonAgain.isEquivalentTo(sameLesson)
                || sameLesson.isEquivalentTo(otherLesson) || otherLesson.isEquivalentTo(sameLesson))
            throw new IllegalStateException("Manual-course history must match the active lesson scene exactly");

        var otherLocale = presentationGuideScreen("fluid_pipes", pipe);
        setGuideCompiledLocaleForValidation(otherLocale, ENGLISH);
        if (sameLesson.isEquivalentTo(otherLocale) || otherLocale.isEquivalentTo(sameLesson))
            throw new IllegalStateException("Manual-course history reused a screen compiled under another locale");
        presentationGuideHistoryExact = true;
    }

    private static void setGuideCompiledLocaleForValidation(GuidePonderScreen screen, String locale) {
        try {
            var field = GuidePonderScreen.class.getDeclaredField("compiledLocale");
            field.setAccessible(true);
            field.set(screen, locale);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot configure the synthetic compiled-locale history fixture", exception);
        }
    }

    private static GuidePonderScreen presentationGuideScreen(String lessonId, ItemStack subject) {
        var lesson = GuideBook.lessons().stream().filter(candidate -> candidate.id().equals(lessonId)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Unknown manual lesson for history validation: " + lessonId));
        var screen = new GuidePonderScreen(lesson);
        ((dev.ieponder.mixin.PonderUIAccessor) (Object) screen).ieponder$setStack(subject.copy());
        return screen;
    }

    private static ValidationContextProbeScreen presentationForeignInitialScreen(ItemStack subject) {
        var scenes = new ArrayList<>(PonderIndex.getSceneAccess().compile(
                GuideResources.parse("immersiveengineering:fluid_pipe")));
        int index = java.util.stream.IntStream.range(0, scenes.size())
                .filter(candidate -> scenes.get(candidate).getId().getPath().equals("fluid_pipes"))
                .findFirst().orElseThrow(() -> new IllegalStateException("No logistics scene for foreign history fixture"));
        var scene = scenes.get(index);
        try {
            var id = PonderScene.class.getDeclaredField("sceneId");
            id.setAccessible(true);
            id.set(scene, GuideResources.of("validationfixture", "foreign_machine"));
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot mark the initial scene as a synthetic foreign history fixture", exception);
        }
        var screen = new ValidationContextProbeScreen(scenes);
        var access = (dev.ieponder.mixin.PonderUIAccessor) (Object) screen;
        access.ieponder$setIndex(index);
        access.ieponder$getLazyIndex().startWithValue(index);
        access.ieponder$setStack(subject.copy());
        return screen;
    }

    private static PonderTag readReferredToByTag(PonderUI ui) {
        try {
            var field = PonderUI.class.getDeclaredField("referredToByTag");
            field.setAccessible(true);
            return (PonderTag) field.get(ui);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot inspect the native referredToByTag history probe", exception);
        }
    }

    private static void setReferredToByTag(PonderUI ui, PonderTag tag) {
        try {
            var field = PonderUI.class.getDeclaredField("referredToByTag");
            field.setAccessible(true);
            field.set(ui, tag);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot configure the native referredToByTag history probe", exception);
        }
    }

    private static void verifyNavigationResolvers() {
        var tags = PonderIndex.getTagAccess();
        var power = tags.getRegisteredTag(GuideResources.of("ieponder", "power"));
        var logistics = tags.getRegisteredTag(GuideResources.of("ieponder", "logistics"));
        var processing = tags.getRegisteredTag(GuideResources.of("ieponder", "processing"));
        requireLesson(GuideNavigation.findLessonForTagAndItem(power,
                new ItemStack(BuiltInRegistries.ITEM.get(GuideResources.parse("immersiveengineering:capacitor_lv")))),
                "capacitors", "power/capacitor_lv");
        requireLesson(GuideNavigation.findLessonForTagAndItem(logistics,
                new ItemStack(BuiltInRegistries.ITEM.get(GuideResources.parse("immersiveengineering:fluid_pipe")))),
                "fluid_pipes", "logistics/fluid_pipe");
        requireLesson(GuideNavigation.findLessonForTagAndItem(processing,
                new ItemStack(BuiltInRegistries.ITEM.get(GuideResources.parse("immersiveengineering:fluid_pipe")))),
                "refinery", "processing/fluid_pipe");
        requireLesson(GuideNavigation.findLessonForItem("hammer"), "capacitors", "hammer component");
        requireLesson(GuideNavigation.findLessonForItem("crusher"), "crusher", "formed crusher alias");
        requireLesson(GuideNavigation.findLessonForItem("manual"), "coke_oven", "manual alias");
        if (GuideNavigation.findLessonForItem("minecraft:air") != null)
            throw new IllegalStateException("An unrelated item resolved to an IE lesson");
    }

    private static void requireLesson(GuideBook.Lesson lesson, String expected, String selection) {
        if (lesson == null || !lesson.id().equals(expected))
            throw new IllegalStateException("Wrong lesson for " + selection + ": expected=" + expected
                    + ", actual=" + (lesson == null ? "<none>" : lesson.id()));
    }

    private static int sceneCount(PonderUI ui) {
        try {
            Field scenesField = PonderUI.class.getDeclaredField("scenes");
            scenesField.setAccessible(true);
            return ((List<?>) scenesField.get(ui)).size();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot inspect native Ponder course size", exception);
        }
    }

    private static void requireExactText(String key, GuideBook.Localized text, String locale) {
        String expected = locale.equals("ja_jp") ? text.ja() : text.en();
        if (!requireLocalized(key, locale).equals(expected))
            throw new IllegalStateException("Wrong language text: " + locale + "/" + key);
    }

    private static void verifyDescriptionTooltips(String locale) {
        if (GuideItemDescriptions.descriptions().isEmpty())
            throw new IllegalStateException("No item description tooltips are available for locale validation");
        requireLocalized("ieponder.tooltip.hold_for_description", locale);
        for (var description : GuideItemDescriptions.descriptions()) {
            description.items().forEach(GuideValidation::requireItem);
            String key = description.translationKey();
            String expected = requireLocalized(key, locale);
            var item = description.items().get(0);
            var stack = new ItemStack(BuiltInRegistries.ITEM.get(GuideResources.parse(item)));
            var collapsed = new ArrayList<Component>();
            collapsed.add(stack.getHoverName());
            GuideItemDescriptions.append(stack, collapsed, false);
            if (collapsed.size() < 2 || collapsed.get(1).getString().contains("ieponder.tooltip."))
                throw new IllegalStateException("Collapsed tooltip hint is missing or leaked a key: " + locale);
            var tooltip = new ArrayList<Component>();
            tooltip.add(stack.getHoverName());
            GuideItemDescriptions.append(stack, tooltip, true);
            if (tooltip.size() < 2)
                throw new IllegalStateException("Item description did not append tooltip text: " + description.id());
            String rendered = tooltip.subList(1, tooltip.size()).stream().map(Component::getString)
                    .collect(java.util.stream.Collectors.joining());
            if (!compact(rendered).contains(compact(expected)))
                throw new IllegalStateException("Localized item tooltip did not contain its text: " + locale + "/" + description.id());
            verifiedTooltipItems++;
        }
    }

    private static String requireLocalized(String key, String locale) {
        String value = Language.getInstance().getOrDefault(key);
        if (value == null || value.isBlank() || value.equals(key) || value.contains("ieponder."))
            throw new IllegalStateException("Missing or leaked localization key for " + locale + ": " + key + " => " + value);
        return value;
    }

    private static String compact(String value) {
        var result = new StringBuilder();
        value.codePoints().filter(codePoint -> !Character.isWhitespace(codePoint) && !Character.isSpaceChar(codePoint))
                .forEach(result::appendCodePoint);
        return result.toString();
    }

    private static void requestLocale(Minecraft client, String locale, String nextStage) {
        currentLocale = locale;
        transitionTo(nextStage);
        client.getLanguageManager().setSelected(locale);
        client.options.languageCode = locale;
        languageReload = client.reloadResourcePacks();
    }

    private static void transitionTo(String nextStage) {
        stage = nextStage;
        phaseFrame = 0;
    }

    private static void advanceLocaleValidation(Minecraft client) {
        localeValidationIndex++;
        if (localeValidationIndex < localeValidationOrder.size())
            requestLocale(client, localeValidationOrder.get(localeValidationIndex), "caption_locale_reload");
        else requestLocale(client, "en_gb", "fallback_locale_reload");
    }

    private static void startVisualScenes(Minecraft client) {
        if (!CAPTION_OVERFLOWS.isEmpty()) {
            try {
                Files.writeString(client.gameDirectory.toPath().resolve("caption-overflows.json"),
                        new GsonBuilder().setPrettyPrinting().create().toJson(CAPTION_OVERFLOWS));
            } catch (java.io.IOException error) { throw new IllegalStateException(error); }
            throw new IllegalStateException(CAPTION_OVERFLOWS.size() + " complete caption units exceed three font lines; see caption-overflows.json");
        }
        if (!TITLE_OVERFLOWS.isEmpty())
            throw new IllegalStateException("Scene headers exceed three native font lines: " + TITLE_OVERFLOWS);
        visualIndex = 0;
        visualFrame = 0;
        transitionTo("visual_open");
    }

    private static void openVisualScene(Minecraft client) {
        if (visualIndex >= VISUAL_SCENES.size()) {
            requestLocale(client, originalLocale, "final_locale_reload");
            return;
        }
        client.options.guiScale().set(CAPTIONS_ONLY ? 3 : 2);
        client.resizeDisplay();
        var scene = VISUAL_SCENES.get(visualIndex);
        client.setScreen(new ValidationPonderScreen(scene, earlyCaptionTime(scene)));
        visualFrame = 0;
        transitionTo("visual_render");
    }

    private static void renderVisualScene(Minecraft client) {
        var expectedScene = VISUAL_SCENES.get(visualIndex);
        var ui = requirePonder(client, expectedScene.getId().getPath());
        visualFrame++;
        var projected = expectedScene.getTransform().sceneToScreen(
                GuideCaptionElement.anchor(expectedScene, ui, PonderUI.getPartialTicks()), PonderUI.getPartialTicks());
        if (Math.abs(projected.x - (ui.width - 240)) > .05
                || Math.abs(projected.y - GuideCaptionElement.anchorY(ui.height)) > .05)
            throw new IllegalStateException("Native caption anchor moved after camera/GUI transform");
        captionLayoutChecks++;
        if (visualFrame == 2) screenshot("ponder-" + expectedScene.getId().getPath() + "-minimum.png");
        if (visualFrame == 4) {
            client.options.guiScale().set(2);
            client.resizeDisplay();
            ui.seekToTime(earlyCaptionTime(expectedScene));
        }
        if (visualFrame == 7) screenshot("ponder-" + expectedScene.getId().getPath() + ".png");
        if (visualFrame == 8) {
            client.options.guiScale().set(1);
            client.resizeDisplay();
            ui.seekToTime(earlyCaptionTime(expectedScene));
        }
        if (visualFrame == 11) screenshot("ponder-" + expectedScene.getId().getPath() + "-tall.png");
        if (visualFrame == 12) ui.seekToTime(expectedScene.getTotalTime());
        if (visualFrame == 14) screenshot("ponder-" + expectedScene.getId().getPath() + "-complete.png");
        if (visualFrame >= 15) {
            visualIndex++;
            transitionTo("visual_open");
        }
    }

    private static void clickArrow(boolean forward) {
        var screen = Minecraft.getInstance().screen;
        if (screen == null || !screen.mouseClicked(screen.width / 2 + (forward ? 28 : -28), screen.height - 41, 0))
            throw new IllegalStateException("Native Ponder course arrow did not handle click: forward=" + forward);
    }

    private static void clickBack() {
        var screen = Minecraft.getInstance().screen;
        if (screen == null || !screen.mouseClicked(41, screen.height - 41, 0))
            throw new IllegalStateException("Native Ponder back button did not handle click: " + screenName(Minecraft.getInstance()));
    }

    private static int earlyCaptionTime(PonderScene scene) {
        return Math.min(Math.max(30, scene.getKeyframeTime(0) + 30), Math.max(0, scene.getTotalTime() - 1));
    }

    private static PonderUI requirePonder(Minecraft client, String lessonId) {
        if (!(client.screen instanceof PonderUI ui))
            throw new IllegalStateException("Expected Ponder for " + lessonId + "; actual=" + screenName(client));
        String active = ui.getActiveScene().getId().getPath();
        if (!active.equals(lessonId)) throw new IllegalStateException("Opened the wrong lesson: expected="
                + lessonId + ", actual=" + active);
        return ui;
    }

    private static GuidePonderScreen requireGuidePonder(Minecraft client, String lessonId) {
        if (client.screen == null || client.screen.getClass() != GuidePonderScreen.class)
            throw new IllegalStateException("Expected the full guide course for " + lessonId + "; actual=" + screenName(client));
        var ui = (GuidePonderScreen) client.screen;
        String active = ui.getActiveScene().getId().getPath();
        if (!active.equals(lessonId))
            throw new IllegalStateException("Guide course started at the wrong lesson: expected=" + lessonId + ", actual=" + active);
        return ui;
    }

    private static void requireAdjacentPonder(Minecraft client, String lessonId, int offset) {
        var lessons = GuideBook.lessons();
        int start = java.util.stream.IntStream.range(0, lessons.size())
                .filter(index -> lessons.get(index).id().equals(lessonId)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Unknown expected lesson: " + lessonId));
        int target = start + offset;
        if (target < 0 || target >= lessons.size())
            throw new IllegalStateException("No full-course lesson at offset " + offset + " from " + lessonId);
        requireGuidePonder(client, lessons.get(target).id());
    }

    private static void requireFullCourse(PonderUI ui, String command) {
        int count = sceneCount(ui);
        if (count != GuideBook.lessons().size())
            throw new IllegalStateException("Command did not open the full course: " + command + " -> "
                    + count + "/" + GuideBook.lessons().size());
    }

    private static void screenshot(String name) {
        var client = Minecraft.getInstance();
        Screenshot.grab(client.gameDirectory, name, client.getMainRenderTarget(),
                message -> LogUtils.getLogger().info("IEPONDER_VALIDATION_SCREENSHOT: {}", message.getString()));
        screenshotCount++;
    }

    private static void writeSuccess(Minecraft client) {
        try {
            if (COVERAGE_ONLY) {
                if (!coverageRealWToTag || !coverageAnotherLesson || !coverageBackPreservedStack
                        || coverageCommandCount != coverageAudit.itemCommands().size()
                        || !coverageForeignContextRestored || !coverageForeignOverridePreserved
                        || !coverageComponentHistoryDistinct || !coverageNativeHistoryPreserved)
                    throw new IllegalStateException("Item coverage UI or history evidence is incomplete");
                var result = new LinkedHashMap<>(coverageAudit.result(LOCALES));
                result.put("passed", true);
                result.put("scope", "ie-item-coverage-and-component-ui");
                result.put("freshFlatWorld", true);
                result.put("fontLocaleReloads", LOCALES.size() + 1);
                result.put("uiAssertions", Map.of(
                        "nativeComponentWOpened", coverageRealWToTag,
                        "nativeChapterTagOpened", coverageRealWToTag,
                        "anotherLessonOpenedFromTag", coverageAnotherLesson,
                        "backRestoredExactComponentStack", coverageBackPreservedStack,
                        "manualCrusherArcFurnaceCommandsOpened", coverageCommandCount,
                        "manualCrusherArcFurnaceCommandCount", coverageAudit.itemCommands().size(),
                        "foreignSceneRestoredNativeContext", coverageForeignContextRestored,
                        "foreignContextOverridePreserved", coverageForeignOverridePreserved,
                        "componentHistoryGuardScopedToOwnScenes", coverageComponentHistoryDistinct,
                        "nativeHistoryBoundaryPreserved", coverageNativeHistoryPreserved));
                result.put("elapsedMillis", java.util.concurrent.TimeUnit.NANOSECONDS
                        .toMillis(System.nanoTime() - startedAt));
                Files.writeString(client.gameDirectory.toPath().resolve("validation-result.json"),
                        new GsonBuilder().setPrettyPrinting().create().toJson(result));
                LogUtils.getLogger().info("IEPONDER_VALIDATION_PASS: scope={}, {} IE items checked",
                        result.get("scope"), result.get("ieRegistryItemCount"));
                finished = true;
                client.stop();
                return;
            }
            if (PRESENTATION_ONLY) {
                if (!optionsButtonless || !presentationOptionsFixturePreserved || !presentationCommandStartedCourse
                        || !presentationCourseTagOpened || !presentationSameChapterLessonSelected || !presentationNativeBackPath
                        || presentationCrossChapterMoves != 2 || !presentationWirecoilWFactory || !presentationFluidPipeWFactory
                        || !presentationFluidPipeCategoryMove || !presentationFluidPipeCategoryBack || presentationFluidPipeScrolls == 0
                        || !presentationDirectTagTick || !presentationForeignContextRestored || !presentationForeignOverridePreserved
                        || !presentationTooltipToneColors
                        || !presentationNativeGuideHistoryDistinct || !presentationGuideHistoryExact
                        || !presentationArabicCaptionFontChecked || !presentationJapaneseRestored
                        || presentationArabicCaptionPages == 0 || presentationArabicMaximumCaptionLines > GuideCaptions.MAX_LINES
                        || !presentationArabicCaptionOverflows.isEmpty()
                        || presentationToneScreenshots != 3
                        || !PRESENTATION_CAPTION_TONES.equals(java.util.EnumSet.allOf(GuideTextTones.Tone.class))
                        || !PRESENTATION_TOOLTIP_TONES.equals(java.util.EnumSet.allOf(GuideTextTones.Tone.class))
                        || !PRESENTATION_TONE_COUNTS.keySet().equals(java.util.Set.copyOf(LOCALES))
                        || verifiedLanguageResources != LOCALES.size() || verifiedCallouts != 151)
                    throw new IllegalStateException("Focused presentation, category, tone or content evidence is incomplete");
            } else if (!LAYOUT_ONLY && ((!CAPTIONS_ONLY && (!optionsToNativeTagIndex || !powerCapacitorStartsCapacitors || !nativeNextPrevious || !nativeIndexBackToOptions
                    || !courseTagSelectionChangedCourse || !nativePonderBackToTag || !nativeTagBackToIndex
                    || !nativeTagIndexToLogistics || !nativeLogisticsItemToFullCourse
                    || !processingFluidPipeStartsRefinery || !wPublicFactoryPreserved
                    || !manualCourseContainsAllLessons || !commandsOpenFullCourse
                    || !unknownChapterIconRejected || !optionsButtonless
                    || verifiedFullCourseCommands != COMMAND_CASES.size() - 1))
                    || !VALIDATED_LOCALES.containsAll(LOCALES)
                    || verifiedLanguageResources != LOCALES.size()
                    || !turkishUppercaseChapterCommand || !englishFallback))
                throw new IllegalStateException("UI navigation evidence is incomplete");
            if (SCENES.size() != GuideBook.lessons().size())
                throw new IllegalStateException("Not every authored lesson was compiled and replayed");
            if (!PRESENTATION_ONLY && (visualIndex != VISUAL_SCENES.size()
                    || captionLayoutChecks < VISUAL_SCENES.size() * 15))
                throw new IllegalStateException("Caption scene/layout evidence is incomplete");
            var result = new LinkedHashMap<String, Object>();
            result.put("passed", true);
            result.put("scope", PRESENTATION_ONLY ? "presentation-categories-and-tones"
                    : LAYOUT_ONLY ? "caption-layout-only" : CAPTIONS_ONLY ? "caption-units-font-layout-and-search" : "full-navigation");
            result.put("maximumCaptionLines", maximumCaptionLines);
            result.put("captionUnitsWithinTwoLines", twoLineCaptionUnits);
            result.put("captionWidth", GuideCaptions.PAGE_WIDTH);
            result.put("captionLayoutChecks", captionLayoutChecks);
            result.put("lessons", GuideBook.lessons().size());
            result.put("compiled", SCENES.size());
            result.put("compiledLessons", SCENES.stream().map(scene -> scene.getId().getPath()).toList());
            result.put("rendered", PRESENTATION_ONLY ? 1 : visualIndex);
            result.put("renderedLessons", PRESENTATION_ONLY ? List.of("coke_oven")
                    : VISUAL_SCENES.stream().map(scene -> scene.getId().getPath()).toList());
            result.put("optionsGuideButtons", optionsButtonless ? 0 : null);
            result.put("nodes", verifiedNodes);
            result.put("steps", verifiedSteps);
            result.put("flowItems", verifiedFlows);
            result.put("entityTypes", verifiedEntities);
            result.put("actorReplays", verifiedActorReplays);
            result.put("callouts", verifiedCallouts);
            result.put("localizedTooltipItems", verifiedTooltipItems);
            result.put("locales", LOCALES);
            result.put("validatedLocales", LOCALES.stream().filter(VALIDATED_LOCALES::contains).toList());
            result.put("languageResourcesValidated", verifiedLanguageResources);
            result.put("localizedChapterNames", verifiedLocalizedChapters);
            result.put("localizedItemNames", verifiedLocalizedItems);
            result.put("captionPagesFontChecked", verifiedCaptionPages);
            result.put("screenshots", screenshotCount);
            result.put("glyphCodepoints", verifiedGlyphCodepoints);
            result.put("fontAdvancesLocales", LOCALES.stream().filter(FONT_ADVANCES::containsKey).toList());
            result.put("maximumTitleLines", maximumTitleLines);
            result.put("nativeUiLabels", verifiedNativeUiLabels);
            result.put("englishFallbackFromEnGb", englishFallback);
            result.put("coexistingMods", net.neoforged.fml.ModList.get().getMods().stream()
                    .filter(mod -> java.util.Set.of("ae2ponder", "ae2", "guideme").contains(mod.getModId()))
                    .collect(java.util.stream.Collectors.toMap(mod -> mod.getModId(), mod -> mod.getVersion().toString())));
            result.put("freshFlatWorld", true);
            result.put("wFactoryContract", Map.of("nullTagRemainsNative", wPublicFactoryPreserved,
                    "manualCourseScenes", sceneCount(PonderUI.of(new ItemStack(
                            BuiltInRegistries.ITEM.get(GuidePlugin.GUIDE_ITEM))))));
            if (PRESENTATION_ONLY) {
                result.put("fontLocaleReloads", 2);
                result.put("fontLocalesChecked", List.of("ar_sa"));
                result.put("presentation", presentationEvidence());
            } else {
                var navigation = new LinkedHashMap<String, Object>();
                navigation.put("optionsToNativeTagIndex", optionsToNativeTagIndex);
                navigation.put("powerCapacitorStartsCapacitors", powerCapacitorStartsCapacitors);
                navigation.put("courseTagSelectionStartsBasicWiring", courseTagSelectionChangedCourse);
                navigation.put("nativeArrows", nativeNextPrevious);
                navigation.put("ponderBackToTag", nativePonderBackToTag);
                navigation.put("tagBackToIndex", nativeTagBackToIndex);
                navigation.put("indexBackToOptions", nativeIndexBackToOptions);
                navigation.put("indexToLogisticsTag", nativeTagIndexToLogistics);
                navigation.put("logisticsFluidPipeStartsFluidPipes", nativeLogisticsItemToFullCourse);
                navigation.put("processingFluidPipeStartsRefinery", processingFluidPipeStartsRefinery);
                navigation.put("fullCourseCommands", verifiedFullCourseCommands);
                navigation.put("chapterOnlyCrusherRejected", unknownChapterIconRejected);
                navigation.put("turkishUppercaseChapterCommand", turkishUppercaseChapterCommand);
                result.put("navigation", navigation);
            }
            result.put("elapsedMillis", java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
            Files.writeString(client.gameDirectory.toPath().resolve("validation-result.json"),
                    new GsonBuilder().setPrettyPrinting().create().toJson(result));
            LogUtils.getLogger().info("IEPONDER_VALIDATION_PASS: scope={}, {} of {} lessons compiled, {} callouts checked",
                    result.get("scope"), SCENES.size(), GuideBook.lessons().size(), verifiedCallouts);
        } catch (Throwable exception) {
            failValidation(client, exception);
            return;
        }
        finished = true;
        client.stop();
    }

    private static Map<String, Object> presentationEvidence() {
        var palettes = new LinkedHashMap<String, Object>();
        for (var tone : GuideTextTones.Tone.values())
            palettes.put(tone.name().toLowerCase(java.util.Locale.ROOT), Map.of(
                    "palette", tone.palette().name(), "textColor", String.format("#%06X", tone.textColor() & 0xFFFFFF)));
        var result = new LinkedHashMap<String, Object>();
        result.put("guideButtonsOnOptions", optionsButtonless ? 0 : -1);
        result.put("vanillaOptionWidgetsPreserved", presentationNativeOptionWidgets);
        result.put("unrelatedFixturePreserved", presentationOptionsFixturePreserved);
        result.put("commandCourseAndSubject", presentationCommandStartedCourse);
        result.put("courseTagToSameCategoryLesson", presentationCourseTagOpened && presentationSameChapterLessonSelected);
        result.put("nativeBackReturnsToCategoryAndCourse", presentationNativeBackPath);
        result.put("crossChapterMoves", presentationCrossChapterMoves);
        result.put("wirecoilNativeWFactory", presentationWirecoilWFactory);
        result.put("fluidPipeNativeWFactory", presentationFluidPipeWFactory);
        result.put("fluidPipeLogisticsToProcessingScrolls", presentationFluidPipeScrolls);
        result.put("fluidPipeProcessingTagAndReturn", presentationFluidPipeCategoryMove && presentationFluidPipeCategoryBack);
        result.put("nativeReferredToByTagTick", presentationDirectTagTick);
        result.put("syntheticForeignSceneRestoresNativeContext", presentationForeignContextRestored);
        result.put("syntheticForeignOverrideSurvivesTickAndExit", presentationForeignOverridePreserved);
        result.put("nativeAndGuideHistoryEquivalence", presentationNativeGuideHistoryDistinct);
        result.put("guideSceneAndLocaleHistoryEquivalence", presentationGuideHistoryExact);
        result.put("guideLocaleEquivalenceFixture", "synthetic compiledLocale identity; rendered locale remains ja_jp");
        result.put("captionTonePalettes", PRESENTATION_CAPTION_TONES.stream().map(Enum::name).toList());
        result.put("tooltipToneColors", PRESENTATION_TOOLTIP_TONES.stream().map(Enum::name).toList());
        result.put("nativePaletteAndTextColors", palettes);
        result.put("toneUnitCountsByLocale", PRESENTATION_TONE_COUNTS);
        result.put("toneScreenshots", presentationToneScreenshots);
        result.put("arabicCaptionFontCheck", Map.of("locale", "ar_sa", "checkedUnits", presentationArabicCaptionPages,
                "maximumLines", presentationArabicMaximumCaptionLines, "overflows", presentationArabicCaptionOverflows,
                "restoredToJapanese", presentationJapaneseRestored));
        result.put("resourceLocaleReloads", List.of("ar_sa", JAPANESE));
        result.put("fontLocalesChecked", List.of("ar_sa"));
        return result;
    }

    private static void failValidation(Minecraft client, Throwable exception) {
        if (finished) return;
        finished = true;
        LogUtils.getLogger().error("IEPONDER_VALIDATION_FAIL at stage={}", stage, exception);
        var trace = new StringWriter();
        exception.printStackTrace(new PrintWriter(trace));
        try {
            var directory = client.gameDirectory.toPath();
            Files.createDirectories(directory);
            Files.writeString(directory.resolve("validation-failure.txt"), "stage=" + stage + "\n" + trace);
            var result = new LinkedHashMap<String, Object>();
            result.put("passed", false);
            result.put("stage", stage);
            result.put("failure", exception.toString());
            result.put("lessonsCompiled", SCENES.size());
            result.put("lessonsRendered", visualIndex);
            result.put("titleOverflows", TITLE_OVERFLOWS);
            result.put("maximumTitleLines", maximumTitleLines);
            result.put("nativeUiLabels", verifiedNativeUiLabels);
            result.put("captionPages", verifiedCaptionPages);
            result.put("captionOverflows", CAPTION_OVERFLOWS);
            if (COVERAGE_ONLY) {
                result.put("fontLocaleReloadsCompleted", coverageLocaleIndex);
                result.put("coverageFontProblemsFileExpected", "coverage-font-problems.json");
                if (coverageAudit != null) {
                    if (!coverageAudit.fontProblemsWritten()) {
                        try {
                            coverageAudit.writeFontProblems(directory.resolve("coverage-font-problems.json"));
                        } catch (Throwable fontReportFailure) {
                            LogUtils.getLogger().error("Could not write partial coverage font report", fontReportFailure);
                        }
                    }
                    result.putAll(coverageAudit.fontFailureSummary());
                }
            }
            result.put("elapsedMillis", startedAt == 0 ? 0 : java.util.concurrent.TimeUnit.NANOSECONDS
                    .toMillis(System.nanoTime() - startedAt));
            Files.writeString(directory.resolve("validation-result.json"),
                    new GsonBuilder().setPrettyPrinting().create().toJson(result));
        } catch (Throwable writeFailure) {
            LogUtils.getLogger().error("Could not write validation failure files", writeFailure);
        }
        client.stop();
    }

    private static String screenName(Minecraft client) {
        return client.screen == null ? "<none>" : client.screen.getClass().getSimpleName();
    }

    private static final class ValidationPonderScreen extends PonderUI {
        private final int startTime;

        private ValidationPonderScreen(PonderScene scene, int startTime) {
            super(List.of(scene));
            this.startTime = startTime;
        }

        @Override protected void init() {
            super.init();
            seekToTime(startTime);
        }
    }

    private static final class ValidationContextProbeScreen extends PonderUI {
        private ValidationContextProbeScreen(List<PonderScene> scenes) { super(scenes); }
    }

    private record CommandCase(String command, String lessonId, boolean expectedFailure) {}
}
