package dev.ieponder.client;

import dev.ieponder.mixin.TextWindowElementAccessor;
import net.createmod.ponder.api.element.TextElementBuilder;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.foundation.instruction.TextInstruction;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Style;

import java.util.List;

/** Fits this guide's logical-order text to the native Ponder caption renderer. */
public final class GuideCaptions {
    public static final int TARGET_LINES = 2;
    public static final int MAX_LINES = 3;
    public static final int PAGE_WIDTH = 180;

    private GuideCaptions() {}

    public static int width() {
        return PAGE_WIDTH;
    }

    public static List<String> pages(String text) { return pages(text, width()); }

    public static List<String> pages(String text, int width) {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("Empty guide caption");
        if (width < 1) throw new IllegalArgumentException("Caption width must be positive");
        return CaptionUnits.split(text);
    }

    public static List<String> lines(String text, int width) {
        var font = Minecraft.getInstance().font;
        int logicalWidth = width;
        while (true) {
            var lines = font.getSplitter().splitLines(text, logicalWidth, Style.EMPTY).stream()
                    .map(net.minecraft.network.chat.FormattedText::getString).toList();
            // Native Ponder draws each logical line through Font's bidi-aware String path.
            // Measure that same shaped line, but retain logical text for commands/resources
            // and let the renderer shape it exactly once when drawing.
            if (!font.isBidirectional() || logicalWidth == 1
                    || lines.stream().allMatch(line -> renderedWidth(line) <= width)) return lines;
            logicalWidth--;
        }
    }

    public static int renderedWidth(String logicalLine) {
        var font = Minecraft.getInstance().font;
        return font.width(font.isBidirectional() ? font.bidirectionalShaping(logicalLine) : logicalLine);
    }

    public static int duration(String text) {
        return Math.max(90, text.codePointCount(0, text.length()) * 2);
    }

    /** Uses the native element and animation, without registering locale-dependent numbered text. */
    static TextElementBuilder show(SceneBuilder scene, String text, int duration) {
        var element = new GuideCaptionElement();
        // Natural line wrapping stays inside this one authored bubble, including bidi shaping.
        ((TextWindowElementAccessor) (Object) element).ieponder$setTextGetter(() -> String.join("\n", lines(text, PAGE_WIDTH)));
        scene.addInstruction(new TextInstruction(element, duration));
        return element.builder(scene.getScene()).placeNearTarget();
    }
}
