package dev.ieponder.client;

import com.google.gson.Gson;
import dev.ieponder.GuideResources;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Data-driven descriptions appended to selected item tooltips. */
public final class GuideItemDescriptions {
    private static final String RESOURCE = "/assets/ieponder/guide/item_descriptions.json";
    private static final String DESCRIPTION_KEY = "ieponder.description.";
    private static final int MAX_WIDTH = 240;

    private GuideItemDescriptions() {}

    private static final class Holder {
        private static final Index INDEX = load();
    }

    private record Index(List<Entry> descriptions, Map<ResourceLocation, Entry> byItem) {}

    public record Entry(String id, List<String> items, GuideBook.Localized text) {
        public Entry {
            if (id == null || !id.matches("[a-z0-9_]+"))
                throw new IllegalArgumentException("Invalid item description ID: " + id);
            items = List.copyOf(Objects.requireNonNull(items, "Item description items"));
            if (items.isEmpty()) throw new IllegalArgumentException("Item description has no items: " + id);
            items.forEach(GuideResources::parse);
            Objects.requireNonNull(text, "Item description text");
        }

        public String translationKey() { return DESCRIPTION_KEY + id; }
    }

    public static List<Entry> descriptions() { return Holder.INDEX.descriptions(); }

    /** Insert a guide hint or translated description after the name, retaining existing tooltip text. */
    public static void append(ItemStack stack, List<Component> tooltip, boolean expanded) {
        if (stack.isEmpty()) return;
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        Entry entry = Holder.INDEX.byItem().get(itemId);
        if (entry == null) return;

        List<Component> additions = expanded ? expandedDescription(entry) : collapsedHint();
        tooltip.addAll(Math.min(1, tooltip.size()), additions);
    }

    private static List<Component> collapsedHint() {
        return List.of(Component.translatable("ieponder.tooltip.hold_for_description",
                Component.translatable("key.keyboard.left.shift")).withColor(GuideTextTones.Tone.HINT.textColor()));
    }

    private static List<Component> expandedDescription(Entry entry) {
        Component text = Component.translatable(entry.translationKey());
        Font font = Minecraft.getInstance().font;
        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int width = Math.max(1, Math.min(MAX_WIDTH, screenWidth - 32));
        return GuideTextTones.units(entry.translationKey(), text.getString()).stream().flatMap(unit ->
                splitLogicalLines(font, unit.text(), width).stream()
                        .<Component>map(line -> Component.literal(line).withColor(unit.tone().textColor())))
                .toList();
    }

    /**
     * Split in logical order, then narrow the split width until each line also fits after
     * Minecraft applies the active language's visual reordering and Arabic shaping.
     * Returned strings remain logical text so the native tooltip renderer shapes them once.
     */
    public static List<String> splitLogicalLines(Font font, String logicalText, int requestedWidth) {
        int targetWidth = Math.max(1, requestedWidth);
        int splitWidth = targetWidth;
        while (true) {
            List<String> lines = font.getSplitter().splitLines(logicalText, splitWidth, Style.EMPTY).stream()
                    .map(FormattedText::getString).toList();
            boolean fitsVisualWidth = lines.stream().allMatch(line -> font.width(
                    Component.literal(line).getVisualOrderText()) <= targetWidth);
            if (fitsVisualWidth || splitWidth == 1) return lines;
            splitWidth--;
        }
    }

    private static Index load() {
        try {
            var stream = Objects.requireNonNull(GuideItemDescriptions.class.getResourceAsStream(RESOURCE),
                    "Missing item descriptions: " + RESOURCE);
            Entry[] entries;
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                entries = new Gson().fromJson(reader, Entry[].class);
            }
            if (entries == null) throw new IllegalStateException("Item descriptions are empty");

            var descriptions = List.copyOf(Arrays.asList(entries));
            var ids = new HashSet<String>();
            var items = new HashSet<ResourceLocation>();
            var byItem = new HashMap<ResourceLocation, Entry>();
            for (var entry : descriptions) {
                if (!ids.add(entry.id())) throw new IllegalStateException("Duplicate item description ID: " + entry.id());
                for (var item : entry.items()) {
                    var itemId = GuideResources.parse(item);
                    if (!items.add(itemId)) throw new IllegalStateException("Duplicate item in descriptions: " + itemId);
                    byItem.put(itemId, entry);
                }
            }
            return new Index(descriptions, Map.copyOf(byItem));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to load Immersive Engineering item descriptions", exception);
        }
    }
}
