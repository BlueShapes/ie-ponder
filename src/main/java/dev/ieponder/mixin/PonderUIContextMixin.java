package dev.ieponder.mixin;

import dev.ieponder.GuideConstants;
import dev.ieponder.GuideResources;
import dev.ieponder.client.GuideBook;
import dev.ieponder.client.GuidePlugin;
import dev.ieponder.client.GuidePonderScreen;
import dev.ieponder.client.NativePonderItemStackContext;
import net.createmod.catnip.gui.NavigatableSimiScreen;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.PonderTag;
import net.createmod.ponder.foundation.ui.AbstractPonderScreen;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** Synchronizes native subject/tag widgets only while an IE guide scene is active. */
@Mixin(value = PonderUI.class, remap = false)
public abstract class PonderUIContextMixin extends AbstractPonderScreen implements NativePonderItemStackContext {
    @Unique private ItemStack ieponder$sourceStack = ItemStack.EMPTY;
    @Unique private ResourceLocation ieponder$lastScene;
    @Unique private ItemStack ieponder$originalSubject;
    @Unique private List<PonderTag> ieponder$originalTags;
    @Unique private ItemStack ieponder$assignedSubject;
    @Unique private List<PonderTag> ieponder$assignedTags;

    @Inject(method = "init", at = @At("HEAD"), remap = false)
    private void ieponder$beforeWidgets(CallbackInfo ci) { ieponder$syncContext(false); }

    @Inject(method = "scroll", at = @At("RETURN"), remap = false)
    private void ieponder$afterScroll(boolean forward, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) ieponder$syncContext(true);
    }

    @Inject(method = "tick", at = @At("TAIL"), remap = false)
    private void ieponder$afterNativeTagSelection(CallbackInfo ci) { ieponder$syncContext(true); }

    @Inject(method = "isEquivalentTo", at = @At("HEAD"), cancellable = true, remap = false)
    private void ieponder$keepCourseHistoryDistinct(NavigatableSimiScreen other,
                                                    CallbackInfoReturnable<Boolean> cir) {
        // Native equivalence compares only the item, even before a tag selects its first scene.
        // This type boundary is needed in both directions, independent of the current namespace.
        if (((Object) this instanceof GuidePonderScreen) != (other instanceof GuidePonderScreen)) {
            cir.setReturnValue(false);
            return;
        }

        // Native Ponder compares only item identity. Keep component variants distinct only when
        // both screens currently show this guide's own scenes; foreign screen history stays native.
        if (other instanceof GuidePonderScreen
                || !(((Object) this) instanceof PonderUI current)
                || !(other instanceof PonderUI next)
                || !ieponder$isOwnScene(current) || !ieponder$isOwnScene(next)) return;
        if (!ItemStack.isSameItemSameComponents(ieponder$comparisonStack(current), ieponder$comparisonStack(next)))
            cir.setReturnValue(false);
    }

    @Unique @Override public ItemStack ieponder$getSourceStack() {
        return ieponder$sourceStack.isEmpty() ? ItemStack.EMPTY : ieponder$sourceStack.copy();
    }

    @Unique @Override public void ieponder$setSourceStack(ItemStack stack) {
        ieponder$sourceStack = stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
    }

    @Unique private void ieponder$syncContext(boolean rebuild) {
        var ui = (PonderUI) (Object) this;
        var scene = ui.getActiveScene();
        var id = scene.getId();
        if (id.equals(ieponder$lastScene)) return;
        ieponder$lastScene = id;
        var access = (PonderUIAccessor) (Object) this;
        var tags = access.ieponder$getTags();
        var lesson = GuideConstants.MOD_ID.equals(id.getNamespace()) ? GuideBook.lessons().stream()
                .filter(candidate -> candidate.id().equals(id.getPath())).findFirst().orElse(null) : null;
        List<PonderTag> nextTags;
        if (lesson != null) {
            if (ieponder$originalSubject == null) {
                ieponder$originalSubject = ui.getSubject().copy();
                ieponder$originalTags = List.copyOf(tags);
            }
            // Native Ponder factories retain the item but discard its data components.
            // Keep those components for the matching component scene; the manual uses the
            // lesson icon and other guide scenes use their registered machine or icon.
            var sourceStack = ieponder$getSourceStack();
            var sceneLocation = scene.getLocation();
            var sourceItem = sourceStack.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(sourceStack.getItem());
            ieponder$assignedSubject = !sceneLocation.equals(GuidePlugin.GUIDE_ITEM)
                    && sceneLocation.equals(sourceItem)
                    ? sourceStack.copy()
                    : new ItemStack(BuiltInRegistries.ITEM.get(sceneLocation.equals(GuidePlugin.GUIDE_ITEM)
                            ? GuideResources.parse(lesson.icon()) : sceneLocation));
            access.ieponder$setStack(ieponder$assignedSubject);
            nextTags = List.of(PonderIndex.getTagAccess().getRegisteredTag(
                    GuideResources.of(GuideConstants.MOD_ID, lesson.chapter())));
            ieponder$assignedTags = nextTags;
        } else {
            if (ieponder$originalSubject == null) return;
            // Restore our own changes; another guide may already have installed its context.
            if (ui.getSubject() == ieponder$assignedSubject)
                access.ieponder$setStack(ieponder$originalSubject);
            nextTags = tags.equals(ieponder$assignedTags) ? ieponder$originalTags : List.copyOf(tags);
            ieponder$originalSubject = null;
            ieponder$originalTags = null;
            ieponder$assignedSubject = null;
            ieponder$assignedTags = null;
        }
        if (tags.equals(nextTags)) return;
        tags.clear();
        tags.addAll(nextTags);
        // Screen clears widgets before init; scene time, camera and Catnip history are retained.
        if (rebuild) rebuildWidgets();
    }

    @Unique private static boolean ieponder$isOwnScene(PonderUI ui) {
        try {
            return GuideConstants.MOD_ID.equals(ui.getActiveScene().getId().getNamespace());
        } catch (IndexOutOfBoundsException ignored) {
            return false;
        }
    }

    @Unique private static ItemStack ieponder$comparisonStack(PonderUI ui) {
        if (ui instanceof NativePonderItemStackContext context) {
            ItemStack source = context.ieponder$getSourceStack();
            if (!source.isEmpty()) return source;
        }
        return ui.getSubject();
    }
}
