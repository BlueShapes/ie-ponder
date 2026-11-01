package dev.ieponder.mixin;

import dev.ieponder.GuideConstants;
import dev.ieponder.client.GuideNavigation;
import dev.ieponder.client.GuidePonderScreen;
import dev.ieponder.client.NativePonderItemStackContext;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.PonderTag;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps native tag-screen clicks inside the complete IE chapter course. */
@Mixin(value = PonderUI.class, remap = false)
public abstract class PonderUIFactoryMixin {
    @Inject(
            method = "of(Lnet/minecraft/world/item/ItemStack;)Lnet/createmod/ponder/foundation/ui/PonderUI;",
            at = @At("RETURN"), remap = false)
    private static void ieponder$captureSourceStack(ItemStack stack,
                                                     CallbackInfoReturnable<PonderUI> cir) {
        ieponder$preserveSourceStack(stack, cir.getReturnValue());
    }

    @Inject(
            method = "of(Lnet/minecraft/world/item/ItemStack;Lnet/createmod/ponder/foundation/PonderTag;)Lnet/createmod/ponder/foundation/ui/PonderUI;",
            at = @At("RETURN"), remap = false)
    private static void ieponder$captureTaggedSourceStack(ItemStack stack, PonderTag tag,
                                                            CallbackInfoReturnable<PonderUI> cir) {
        ieponder$preserveSourceStack(stack, cir.getReturnValue());
    }

    @Inject(
            method = "of(Lnet/minecraft/world/item/ItemStack;Lnet/createmod/ponder/foundation/PonderTag;)Lnet/createmod/ponder/foundation/ui/PonderUI;",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void ieponder$openCourseAtTagItem(ItemStack stack, PonderTag tag,
                                                       CallbackInfoReturnable<PonderUI> cir) {
        if (tag == null || !"ieponder".equals(tag.getId().getNamespace())) return;
        var lesson = GuideNavigation.findLessonForTagAndItem(tag, stack);
        if (lesson != null) cir.setReturnValue(new GuidePonderScreen(lesson));
    }

    @Unique private static void ieponder$preserveSourceStack(ItemStack stack, PonderUI screen) {
        if (stack == null || stack.isEmpty() || screen == null || screen instanceof GuidePonderScreen)
            return;
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        boolean hasGuideStoryboard = PonderIndex.getSceneAccess().getRegisteredEntries().stream()
                .anyMatch(entry -> itemId.equals(entry.getKey())
                        && GuideConstants.MOD_ID.equals(entry.getValue().getNamespace()));
        if (hasGuideStoryboard && screen instanceof NativePonderItemStackContext context)
            context.ieponder$setSourceStack(stack.copy());
    }
}
