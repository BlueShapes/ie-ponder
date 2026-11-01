package dev.ieponder.client;

import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.element.TextWindowElement;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.Vec3;

/** Moves only this guide's anchor; all wrapping, borders and animation remain native. */
public final class GuideCaptionElement extends TextWindowElement {
    @Override
    public void render(PonderScene scene, PonderUI ui, GuiGraphics graphics, float partialTicks, float fade) {
        // Native near-target placement reserves a full 180px even at the 320x240 minimum.
        // Recompute through the public transform so camera rotation and resizing stay stable.
        builder(scene).pointAt(anchor(scene, ui, partialTicks)).placeNearTarget();
        super.render(scene, ui, graphics, partialTicks, fade);
    }

    public static Vec3 anchor(PonderScene scene, PonderUI ui, float partialTicks) {
        // Clear ingredient bubbles and the footer; bound native curvature at tall GUI sizes.
        return scene.getTransform().screenToScene(ui.width - 240, anchorY(ui.height), 0, partialTicks);
    }

    public static float anchorY(int height) { return Math.min(height - 100, height / 2f + 80); }
}
