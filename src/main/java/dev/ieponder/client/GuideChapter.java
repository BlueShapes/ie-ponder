package dev.ieponder.client;

import net.minecraft.network.chat.Component;

public enum GuideChapter {
    START("start", "Getting started", "Core materials and your first machines", "immersiveengineering:hammer"),
    POWER("power", "Power", "Generation, storage and wiring", "immersiveengineering:capacitor_lv"),
    METALLURGY("metallurgy", "Metallurgy", "Ore processing and metalworking", "immersiveengineering:blast_furnace"),
    LOGISTICS("logistics", "Logistics", "Moving items and fluids", "immersiveengineering:conveyor_basic"),
    PROCESSING("processing", "Processing", "Multiblock machine operation", "immersiveengineering:crusher"),
    TROUBLESHOOTING("troubleshooting", "Troubleshooting", "Power, setup, inputs and outputs", "immersiveengineering:maintenance_kit");

    private final String id, en, description, icon;

    GuideChapter(String id, String en, String description, String icon) {
        this.id = id;
        this.en = en;
        this.description = description;
        this.icon = icon;
    }

    public String id() { return id; }
    public String en() { return en; }
    public String description() { return description; }
    public String icon() { return icon; }
    public String indexTitle() { return (ordinal() + 1) + ". " + en; }
    public Component label() { return Component.translatable("ieponder.ponder.tag." + id); }

    public static GuideChapter byId(String id) {
        for (var chapter : values()) if (chapter.id.equals(id)) return chapter;
        return null;
    }

    public static int orderOf(String id) {
        for (int i = 0; i < values().length; i++)
            if (values()[i].id.equals(id)) return i;
        throw new IllegalArgumentException("Unknown guide chapter: " + id);
    }

    public static java.util.List<GuideChapter> active(java.util.List<GuideBook.Lesson> lessons) {
        return java.util.Arrays.stream(values())
                .filter(chapter -> lessons.stream().anyMatch(lesson -> lesson.chapter().equals(chapter.id)))
                .toList();
    }
}
