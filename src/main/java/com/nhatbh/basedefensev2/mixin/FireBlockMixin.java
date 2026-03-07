package com.nhatbh.basedefensev2.mixin;

import com.nhatbh.basedefensev2.stage.ModDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FireBlock.class)
public class FireBlockMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void stopFireSpreadInArenaDimension(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        if (level.dimension().equals(ModDimensions.ARENA)) {
            // Canceling the tick stops the fire from calculating spread.
            // Note: This also stops the fire from naturally burning out.
            ci.cancel();
        }
    }
}
