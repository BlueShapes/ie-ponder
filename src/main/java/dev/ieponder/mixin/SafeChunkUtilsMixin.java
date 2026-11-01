package dev.ieponder.mixin;

import dev.ieponder.GuideConstants;
import net.createmod.ponder.api.level.PonderLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Makes IE's chunk guards read the Ponder world instead of its wrapped real world. */
@Pseudo
@Mixin(targets = "blusunrize.immersiveengineering.api.utils.SafeChunkUtils", remap = false)
public abstract class SafeChunkUtilsMixin {
    @Inject(method = "isChunkSafe", at = @At("HEAD"), cancellable = true, remap = false)
    private static void ieponder$allowPonderChunk(LevelAccessor level, BlockPos pos,
                                                   CallbackInfoReturnable<Boolean> cir) {
        if (ieponder$isGuideLevel(level)) cir.setReturnValue(true);
    }

    @Inject(method = "getSafeBE", at = @At("HEAD"), cancellable = true, remap = false)
    private static void ieponder$getPonderBlockEntity(LevelAccessor level, BlockPos pos,
                                                       CallbackInfoReturnable<BlockEntity> cir) {
        if (ieponder$isGuideLevel(level)) cir.setReturnValue(level.getBlockEntity(pos));
    }

    @Unique
    private static boolean ieponder$isGuideLevel(LevelAccessor level) {
        return level instanceof PonderLevel ponder
                && ponder.scene != null
                && GuideConstants.MOD_ID.equals(ponder.scene.getNamespace());
    }
}
