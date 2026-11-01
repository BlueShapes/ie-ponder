package dev.ieponder.client;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.neoforged.fml.ModList;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reads assembly recipes from the separately installed IE mod, without redistributing its assets. */
public final class GuideStructures {
    private GuideStructures() {}

    public record Expanded(List<GuideBook.Node> nodes, List<GuideBook.Step> steps) {}

    public static Expanded expand(GuideBook.Structure structure, List<GuideBook.Node> explicit,
                                  List<GuideBook.Step> originalSteps) {
        var nodes = new ArrayList<>(explicit == null ? List.of() : explicit);
        if (structure == null) return new Expanded(List.copyOf(nodes), originalSteps);
        if (structure.template() == null || !structure.template().matches("[a-z0-9_]+"))
            throw new IllegalArgumentException("Invalid IE structure name: " + structure.template());
        var origin = structure.origin();
        if (origin == null || origin.size() != 3)
            throw new IllegalArgumentException("Structure origin needs three coordinates");
        var groups = new LinkedHashMap<String, List<String>>();
        groups.put("@all", new ArrayList<>());
        var file = ModList.get().getModFileById("immersiveengineering");
        if (file == null) throw new IllegalStateException("Immersive Engineering is required for the guide");
        var path = file.getFile().findResource("data", "immersiveengineering", "structure", "multiblocks",
                structure.template() + ".nbt");
        try (var stream = Files.newInputStream(path)) {
            var root = NbtIo.readCompressed(stream, NbtAccounter.unlimitedHeap());
            var palette = root.getList("palette", Tag.TAG_COMPOUND);
            var blocks = root.getList("blocks", Tag.TAG_COMPOUND);
            if (palette.isEmpty() || blocks.isEmpty())
                throw new IllegalArgumentException("Empty IE structure: " + path);
            for (int index = 0; index < blocks.size(); index++) {
                CompoundTag block = blocks.getCompound(index);
                int paletteIndex = block.getInt("state");
                if (paletteIndex < 0 || paletteIndex >= palette.size())
                    throw new IllegalArgumentException("Invalid palette index in " + path);
                var state = palette.getCompound(paletteIndex);
                String blockId = state.getString("Name");
                if (List.of("minecraft:air", "minecraft:cave_air", "minecraft:void_air").contains(blockId)) continue;
                var pos = block.getList("pos", Tag.TAG_INT);
                if (pos.size() != 3) throw new IllegalArgumentException("Invalid structure position in " + path);
                int x = pos.getInt(0), y = pos.getInt(1), z = pos.getInt(2);
                String id = "mb_" + x + "_" + y + "_" + z;
                var properties = new LinkedHashMap<String, String>();
                var tags = state.getCompound("Properties");
                for (String key : tags.getAllKeys()) properties.put(key, tags.getString(key));
                String nbt = block.contains("nbt", Tag.TAG_COMPOUND) ? block.getCompound("nbt").toString() : "{}";
                nodes.add(new GuideBook.Node(id, blockId,
                        List.of(x + origin.get(0), y + origin.get(1), z + origin.get(2)), properties, nbt));
                groups.get("@all").add(id);
                groups.computeIfAbsent("@layer" + y, unused -> new ArrayList<>()).add(id);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot read installed IE structure " + structure.template(), exception);
        }
        var steps = originalSteps.stream().map(step -> new GuideBook.Step(step.text(),
                references(step.show(), groups), references(step.focus(), groups), references(step.hide(), groups),
                step.flow(), step.warning(), step.nbt(), step.callouts())).toList();
        return new Expanded(List.copyOf(nodes), steps);
    }

    private static List<String> references(List<String> references, Map<String, List<String>> groups) {
        if (references == null) return List.of();
        var expanded = new ArrayList<String>();
        for (String reference : references) {
            if (!reference.startsWith("@")) expanded.add(reference);
            else {
                var group = groups.get(reference);
                if (group == null || group.isEmpty()) throw new IllegalArgumentException("Unknown/empty structure group: " + reference);
                expanded.addAll(group);
            }
        }
        return List.copyOf(expanded);
    }
}
