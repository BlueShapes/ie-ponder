package dev.ieponder.client;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.ieponder.GuideResources;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

public final class GuideScenes {
    private static final int FLOW_TICKS = 36;
    private static final int CALLOUT_SLOT_TICKS = 72; // 60 readable ticks + fades and a short gap.
    private GuideScenes() {}

    public static void build(GuideBook.Lesson lesson, SceneBuilder scene, SceneBuildingUtil util) {
        scene.title(lesson.id(), lesson.title().en());
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(0.85f);
        for (var node : lesson.nodes()) {
            scene.world().setBlock(node.position(), state(node), false);
            applyNbt(scene, util, node, node.nbt());
        }
        scene.showBasePlate();
        scene.idle(15);
        var actors = new java.util.HashMap<String, net.createmod.ponder.api.element.ElementLink<net.createmod.ponder.api.element.EntityElement>>();
        for (int i = 0; i < lesson.steps().size(); i++) {
            buildStep(lesson, i, scene, util, actors);
        }
        scene.markAsFinished();
    }

    private static void buildStep(GuideBook.Lesson lesson, int index, SceneBuilder scene, SceneBuildingUtil util,
            java.util.Map<String, net.createmod.ponder.api.element.ElementLink<net.createmod.ponder.api.element.EntityElement>> actors) {
        var step = lesson.steps().get(index);
        scene.addKeyframe();
        step.hide().forEach(id -> scene.world().hideSection(util.select().position(lesson.node(id).position()), Direction.UP));
        step.show().forEach(id -> scene.world().showSection(util.select().position(lesson.node(id).position()), Direction.DOWN));
        for (var actor : lesson.entities()) {
            if (step.hide().contains(actor.showAt()) && actors.containsKey(actor.id()))
                scene.world().modifyEntity(actors.get(actor.id()), entity -> entity.discard());
            if (!step.show().contains(actor.showAt())) continue;
            var id = GuideResources.parse(actor.type());
            if (!BuiltInRegistries.ENTITY_TYPE.containsKey(id))
                throw new IllegalArgumentException("Unknown scene entity: " + id);
            actors.put(actor.id(), scene.world().createEntity(level -> {
                var entity = BuiltInRegistries.ENTITY_TYPE.get(id).create(level);
                if (entity == null) throw new IllegalArgumentException("Cannot create scene entity: " + id);
                entity.setPos(actor.pos().get(0), actor.pos().get(1), actor.pos().get(2));
                entity.setNoGravity(true);
                entity.setInvulnerable(true);
                entity.setSilent(true);
                if (entity instanceof Mob mob) mob.setNoAi(true);
                return entity;
            }));
        }
        step.nbt().forEach((id, nbt) -> applyNbt(scene, util, lesson.node(id), nbt));
        scene.idle(12);
        // Give longer translations additional reading time; Ponder also provides pauses/keyframes.
        int duration = captionDuration(lesson, index);
        var outlineColor = step.warning() ? PonderPalette.RED : PonderPalette.BLUE;
        for (var id : step.focus()) {
            scene.overlay().showOutline(outlineColor, "focus_" + id, util.select().position(lesson.node(id).position()), duration);
        }
        var pages = GuideCaptions.pages(captionText(lesson, index));
        var units = GuideTextTones.units("ieponder.ponder.shared." + lesson.id() + "_" + index,
                captionText(lesson, index));
        var pageStarts = new int[pages.size() + 1];
        for (int page = 0; page < pages.size(); page++)
            pageStarts[page + 1] = pageStarts[page] + GuideCaptions.duration(pages.get(page)) + 12;
        pageStarts[pages.size()] = duration;
        // Caption and ingredient schedules share the same authored step and keyframe.
        // Independent native text placement has a predictable width at every GUI scale;
        // the outlines and item bubbles keep each page connected to its scene target.
        int slots = Math.max(1, step.callouts().size());
        int slotDuration = duration / slots;
        var events = new java.util.TreeSet<Integer>();
        events.add(duration);
        for (int page = 0; page < pages.size(); page++) events.add(pageStarts[page]);
        for (int slot = 0; slot < slots; slot++) events.add(slot * slotDuration);
        int elapsed = 0;
        for (int time : events) {
            scene.idle(time - elapsed);
            elapsed = time;
            for (int page = 0; page < pages.size(); page++) {
                if (pageStarts[page] != time) continue;
                int end = pageStarts[page + 1];
                GuideCaptions.show(scene, pages.get(page), end - time - 12).colored(units.get(page).tone().palette());
            }
            for (int slot = 0; slot < slots; slot++)
                if (slot * slotDuration == time && !step.callouts().isEmpty())
                    callout(lesson, step.callouts().get(slot), scene, slotDuration);
            if (time == 0 && step.flow() != null) {
                flow(lesson, step.flow(), scene);
                elapsed = FLOW_TICKS;
            }
        }
        scene.idle(12);
    }

    public static int captionDuration(GuideBook.Step step) {
        return Math.max(Math.max(120, step.callouts().size() * CALLOUT_SLOT_TICKS),
                Math.max(step.text().ja().length() * 3, step.text().en().length()));
    }

    public static int captionDuration(GuideBook.Lesson lesson, int index) {
        var pages = GuideCaptions.pages(captionText(lesson, index));
        int readingTime = pages.stream().mapToInt(page -> GuideCaptions.duration(page) + 12).sum();
        return Math.max(captionDuration(lesson.steps().get(index)), readingTime);
    }

    private static String captionText(GuideBook.Lesson lesson, int index) {
        return Component.translatable("ieponder.ponder.shared." + lesson.id() + "_" + index).getString();
    }

    private static void callout(GuideBook.Lesson lesson, GuideBook.Callout callout, SceneBuilder scene, int duration) {
        var node = lesson.node(callout.anchor());
        var offset = callout.offset();
        var position = Vec3.atCenterOf(node.position()).add(offset.get(0), 0.5 + offset.get(1), offset.get(2));
        var id = GuideResources.parse(callout.item());
        if (!BuiltInRegistries.ITEM.containsKey(id)) throw new IllegalArgumentException("Unknown callout item: " + id);
        // Ponder adds five ticks each for fade-in/out; leave two ticks before the following bubble.
        var bubble = scene.overlay().showControls(position, Pointing.valueOf(callout.pointing().toUpperCase(Locale.ROOT)), duration - 12)
                .withItem(new ItemStack(BuiltInRegistries.ITEM.get(id)));
        switch (callout.action()) {
            case "right_click" -> bubble.rightClick();
            case "left_click" -> bubble.leftClick();
            case "scroll" -> bubble.scroll();
            default -> { }
        }
        if (callout.sneak()) bubble.whileSneaking();
    }

    private static void flow(GuideBook.Lesson lesson, GuideBook.Flow flow, SceneBuilder scene) {
        var from = Vec3.atCenterOf(lesson.node(flow.from()).position()).add(0, 0.65, 0);
        var to = Vec3.atCenterOf(lesson.node(flow.to()).position()).add(0, 0.65, 0);
        scene.overlay().showBigLine(PonderPalette.GREEN, from, to, FLOW_TICKS + 12);
        var item = BuiltInRegistries.ITEM.get(GuideResources.parse(flow.item()));
        var link = scene.world().createItemEntity(from, Vec3.ZERO, new ItemStack(item));
        scene.world().modifyEntity(link, entity -> { entity.setNoGravity(true); entity.noPhysics = true; });
        for (int tick = 1; tick <= FLOW_TICKS; tick++) {
            var position = from.lerp(to, tick / (double) FLOW_TICKS);
            scene.world().modifyEntity(link, entity -> entity.setPos(position));
            scene.idle(1);
        }
        scene.world().modifyEntity(link, entity -> entity.discard());
    }

    private static void applyNbt(SceneBuilder scene, SceneBuildingUtil util, GuideBook.Node node, String snbt) {
        if (snbt.equals("{}")) return;
        var data = parseNbt(snbt);
        scene.world().modifyBlockEntityNBT(util.select().position(node.position()), BlockEntity.class,
                tag -> tag.merge(data.copy()), true);
    }

    public static CompoundTag parseNbt(String snbt) {
        try { return TagParser.parseTag(snbt); }
        catch (CommandSyntaxException exception) { throw new IllegalArgumentException("Invalid guide visual NBT: " + snbt, exception); }
    }

    public static BlockState state(GuideBook.Node node) {
        var id = GuideResources.parse(node.block());
        if (!BuiltInRegistries.BLOCK.containsKey(id)) throw new IllegalArgumentException("Unknown guide block: " + id);
        var state = BuiltInRegistries.BLOCK.get(id).defaultBlockState();
        for (var entry : node.state().entrySet()) {
            var property = state.getBlock().getStateDefinition().getProperty(entry.getKey());
            if (property == null) throw new IllegalArgumentException("Unknown state property: " + node.block() + "/" + entry.getKey());
            state = setProperty(state, property, entry.getValue());
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState setProperty(BlockState state, Property<T> property, String value) {
        return state.setValue(property, property.getValue(value)
                .orElseThrow(() -> new IllegalArgumentException("Invalid block state value: " + value)));
    }
}
