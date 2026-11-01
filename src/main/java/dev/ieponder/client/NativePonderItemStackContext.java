package dev.ieponder.client;

import net.minecraft.world.item.ItemStack;

/** Carries the original item components through native Ponder factories that discard them. */
public interface NativePonderItemStackContext {
    ItemStack ieponder$getSourceStack();

    void ieponder$setSourceStack(ItemStack stack);
}
