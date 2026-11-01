package dev.ieponder.mixin;

import net.createmod.ponder.foundation.element.TextWindowElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.function.Supplier;

/** Supplies already localized pages only to text elements created by this guide. */
@Mixin(value = TextWindowElement.class, remap = false)
public interface TextWindowElementAccessor {
    @Accessor("textGetter")
    void ieponder$setTextGetter(Supplier<String> text);
}
