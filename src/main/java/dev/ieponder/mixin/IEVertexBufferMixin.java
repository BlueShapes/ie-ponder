package dev.ieponder.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ieponder.GuideConstants;
import net.createmod.catnip.render.SuperRenderTypeBuffer;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Uses IE's immediate-render fallback while this guide is drawing a Ponder scene. */
@Pseudo
@Mixin(targets = "blusunrize.immersiveengineering.client.utils.VertexBufferHolder", remap = false)
public abstract class IEVertexBufferMixin {
    @Shadow
    private void renderToBuilder(VertexConsumer builder, PoseStack transform, int light, int overlay, boolean inverted) {
        throw new AssertionError();
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true, remap = false)
    private void ieponder$renderInPonder(RenderType type, int light, int overlay, MultiBufferSource directOut,
                                         PoseStack transform, boolean inverted, CallbackInfo ci) {
        if (!(directOut instanceof SuperRenderTypeBuffer ponderBuffer)) return;
        if (!(Minecraft.getInstance().screen instanceof PonderUI ui)) return;
        if (ui.getActiveScene() == null || !GuideConstants.MOD_ID.equals(ui.getActiveScene().getNamespace())) return;

        renderToBuilder(ponderBuffer.getBuffer(type), transform, light, overlay, inverted);
        ci.cancel();
    }
}
