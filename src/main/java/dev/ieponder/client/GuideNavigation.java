package dev.ieponder.client;

import dev.ieponder.GuideConstants;
import dev.ieponder.GuideResources;
import net.createmod.catnip.gui.NavigatableSimiScreen;
import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.ponder.foundation.PonderTag;
import net.createmod.ponder.foundation.ui.PonderTagIndexScreen;
import net.createmod.ponder.foundation.ui.PonderTagScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Small navigation adapter around Ponder's native index, tag and course screens. */
public final class GuideNavigation {
    private GuideNavigation() {}

    /** Opens Ponder's native tag index and records the current screen for its back button. */
    public static void openIndex() {
        ScreenOpener.open(new PonderTagIndexScreen());
    }

    /** Opens Ponder's native item list for one of this guide's chapter tags. */
    public static void openTag(PonderTag tag) {
        if (tag == null || !GuideConstants.MOD_ID.equals(tag.getId().getNamespace())) return;
        var screen = new PonderTagScreen(tag);
        if (Minecraft.getInstance().screen instanceof NavigatableSimiScreen) ScreenOpener.transitionTo(screen);
        else ScreenOpener.open(screen);
    }

    /** Opens a lesson at its position in the complete, ordered guide course. */
    public static void openLesson(GuideBook.Lesson lesson) {
        GuidePonderScreen.open(lesson);
    }

    /** Finds the first chapter lesson represented by an item in that chapter's native tag list. */
    public static GuideBook.Lesson findLessonForTagAndItem(PonderTag tag, ItemStack stack) {
        if (tag == null || stack == null || stack.isEmpty()
                || !GuideConstants.MOD_ID.equals(tag.getId().getNamespace())) return null;

        GuideChapter chapter = GuideChapter.byId(tag.getId().getPath());
        if (chapter == null) return null;

        List<GuideBook.Lesson> lessons = GuideBook.lessons().stream()
                .filter(lesson -> lesson.chapter().equals(chapter.id())).toList();
        if (lessons.isEmpty()) return null;

        ResourceLocation clickedItem = BuiltInRegistries.ITEM.getKey(stack.getItem());
        var exactIcon = lessons.stream()
                .filter(lesson -> GuideResources.parse(lesson.icon()).equals(clickedItem))
                .findFirst().orElse(null);
        if (exactIcon != null) return exactIcon;
        return lessons.stream().filter(lesson -> lesson.components().stream()
                        .map(GuideResources::parse).anyMatch(clickedItem::equals))
                .findFirst().orElse(null);
    }

    /** Resolves a chapter number, id, English name, or currently localized tag name. */
    public static GuideBook.Lesson findLessonForChapter(String query) {
        if (query == null || query.isBlank()) return null;
        String value = normalized(query);
        List<GuideChapter> active = GuideChapter.active(GuideBook.lessons());

        if (value.chars().allMatch(Character::isDigit)) {
            try {
                int number = Integer.parseInt(value);
                if (number > 0 && number <= GuideChapter.values().length)
                    return firstLesson(GuideChapter.values()[number - 1].id());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        for (var chapter : active) {
            // Stable IDs and English aliases keep their case rules when the UI locale is Turkish.
            if (query.trim().equalsIgnoreCase(chapter.id()) || query.trim().equalsIgnoreCase(chapter.en())
                    || matches(query, chapter.label().getString())
                    || matches(query, withoutNumber(chapter.label().getString())))
                return firstLesson(chapter.id());
        }
        return null;
    }

    /** Resolves an authored lesson id. */
    public static GuideBook.Lesson findLessonForId(String id) {
        if (id == null) return null;
        return GuideBook.lessons().stream().filter(lesson -> lesson.id().equalsIgnoreCase(id.trim()))
                .findFirst().orElse(null);
    }

    /** Searches logical text in the selected language; the first hit starts the native course. */
    public static GuideBook.Lesson findLessonForSearch(String query) {
        if (query == null || query.isBlank()) return null;
        String wanted = normalized(query);
        var lessons = GuideBook.lessons();
        for (var lesson : lessons)
            if (normalized(translatedTitle(lesson)).equals(wanted)) return lesson;
        for (var lesson : lessons)
            if (normalized(translatedTitle(lesson)).contains(wanted)) return lesson;
        for (var lesson : lessons) {
            if (normalized(Component.translatable("ieponder.lesson." + lesson.id() + ".summary").getString()).contains(wanted))
                return lesson;
            for (int index = 0; index < lesson.steps().size(); index++)
                if (normalized(Component.translatable("ieponder.ponder.shared." + lesson.id() + "_" + index).getString()).contains(wanted))
                    return lesson;
        }
        return null;
    }

    private static String translatedTitle(GuideBook.Lesson lesson) {
        return Component.translatable("ieponder.ponder." + lesson.id() + ".header").getString();
    }

    /** Resolves an IE item path, namespaced registry id, or selected-language display name. */
    public static GuideBook.Lesson findLessonForItem(String query) {
        if (query == null || query.isBlank()) return null;
        Map<ResourceLocation, GuideBook.Lesson> candidates = itemCandidates();

        ResourceLocation itemId = parseItemId(query);
        if (itemId != null) {
            GuideBook.Lesson byId = candidates.get(itemId);
            if (byId != null) return byId;
        }

        String wanted = normalized(query);
        for (var entry : candidates.entrySet()) {
            var item = BuiltInRegistries.ITEM.getOptional(entry.getKey()).orElse(null);
            if (item != null && normalized(item.getDescription().getString()).equals(wanted))
                return entry.getValue();
        }
        return null;
    }

    private static GuideBook.Lesson firstLesson(String chapterId) {
        return GuideBook.lessons().stream().filter(lesson -> lesson.chapter().equals(chapterId))
                .findFirst().orElse(null);
    }

    private static Map<ResourceLocation, GuideBook.Lesson> itemCandidates() {
        Map<ResourceLocation, GuideBook.Lesson> result = new LinkedHashMap<>();
        GuideChapter.active(GuideBook.lessons()).stream().findFirst()
                .map(chapter -> firstLesson(chapter.id()))
                .ifPresent(lesson -> result.put(GuidePlugin.GUIDE_ITEM, lesson));
        for (var lesson : GuideBook.lessons())
            result.putIfAbsent(GuideResources.parse(lesson.icon()), lesson);
        for (var lesson : GuideBook.lessons())
            for (var component : lesson.components())
                result.putIfAbsent(GuideResources.parse(component), lesson);
        return result;
    }

    private static ResourceLocation parseItemId(String query) {
        String value = query.trim().toLowerCase(Locale.ROOT);
        if (value.matches("[a-z0-9_.-]+")) value = "immersiveengineering:" + value;
        return ResourceLocation.tryParse(value);
    }

    private static boolean matches(String query, String candidate) {
        return normalized(query).equals(normalized(candidate));
    }

    private static String normalized(String value) {
        String selected = Minecraft.getInstance().getLanguageManager().getSelected();
        Locale locale = Locale.forLanguageTag(selected.replace('_', '-'));
        return SearchText.normalize(value, locale);
    }

    private static String withoutNumber(String value) {
        return value.replaceFirst("^\\s*\\d+\\s*\\.\\s*", "").trim();
    }
}
