package dev.ieponder.client;

import dev.ieponder.GuideConstants;
import dev.ieponder.GuideResources;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.createmod.ponder.api.registration.SharedTextRegistrationHelper;
import net.createmod.ponder.api.registration.StoryBoardEntry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GuidePlugin implements PonderPlugin {
    public static final ResourceLocation GUIDE_ITEM = GuideResources.of("immersiveengineering", "manual");
    private static final Map<String, StoryBoardEntry> ENTRIES = new LinkedHashMap<>();
    private static final List<StoryBoardEntry> COURSE = new ArrayList<>();

    public static List<StoryBoardEntry> course() { return List.copyOf(COURSE); }

    public static StoryBoardEntry entry(String lessonId) {
        var entry = ENTRIES.get(lessonId);
        if (entry == null) throw new IllegalStateException("Ponder lesson has not been registered: " + lessonId);
        return entry;
    }

    @Override public String getModId() { return GuideConstants.MOD_ID; }

    @Override public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        ENTRIES.clear();
        COURSE.clear();
        for (var lesson : GuideBook.lessons()) {
            var tag = GuideResources.of(getModId(), lesson.chapter());
            var icon = GuideResources.parse(lesson.icon());
            var entry = helper.addStoryBoard(icon, "stage", (scene, util) -> GuideScenes.build(lesson, scene, util), tag);
            ENTRIES.put(lesson.id(), entry);
            COURSE.add(entry);
            if (!icon.equals(GUIDE_ITEM)) helper.addStoryBoard(GUIDE_ITEM, "stage",
                    (scene, util) -> GuideScenes.build(lesson, scene, util), tag);
            for (var component : lesson.components()) {
                var componentId = GuideResources.parse(component);
                if (!componentId.equals(icon) && !componentId.equals(GUIDE_ITEM)) helper.addStoryBoard(componentId, "stage",
                        (scene, util) -> GuideScenes.build(lesson, scene, util), tag);
            }
        }
    }

    @Override public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        for (var chapter : GuideChapter.active(GuideBook.lessons())) {
            helper.registerTag(chapter.id()).title(chapter.indexTitle()).description(chapter.description())
                    .item(BuiltInRegistries.ITEM.get(GuideResources.parse(chapter.icon())), true, false)
                    .addToIndex().register();
        }
        for (var lesson : GuideBook.lessons()) {
            var components = new java.util.LinkedHashSet<>(lesson.components());
            components.add(lesson.icon());
            for (var component : components) helper.addTagToComponent(GuideResources.parse(component),
                    GuideResources.of(getModId(), lesson.chapter()));
        }
    }

    @Override public void registerSharedText(SharedTextRegistrationHelper helper) {
        for (var lesson : GuideBook.lessons()) for (int i = 0; i < lesson.steps().size(); i++)
            helper.registerSharedText(lesson.id() + "_" + i, lesson.steps().get(i).text().en());
    }
}
