package dev.ieponder.mixin;

import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.ponder.foundation.ui.PonderUI;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Sets the initial lesson without animating through the lessons before it. */
@Mixin(value = PonderUI.class, remap = false)
public interface PonderUIAccessor {
    @Accessor("index") void ieponder$setIndex(int index);
    @Accessor("lazyIndex") LerpedFloat ieponder$getLazyIndex();
    @Accessor("identifyMode") void ieponder$setIdentifyMode(boolean identifyMode);
    @Accessor("stack") void ieponder$setStack(net.minecraft.world.item.ItemStack stack);
    @Accessor("tags") java.util.List<net.createmod.ponder.foundation.PonderTag> ieponder$getTags();
}
