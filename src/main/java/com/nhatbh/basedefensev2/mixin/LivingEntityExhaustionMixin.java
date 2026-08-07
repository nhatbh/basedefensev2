package com.nhatbh.basedefensev2.mixin;

import com.nhatbh.basedefensev2.api.PoiseAPI;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LivingEntity.class)
public abstract class LivingEntityExhaustionMixin {

    @Redirect(
        method = "getDamageAfterMagicAbsorb",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;hasEffect(Lnet/minecraft/world/effect/MobEffect;)Z")
    )
    private boolean redirectHasResistanceEffect(LivingEntity entity, MobEffect effect) {
        if (effect == MobEffects.DAMAGE_RESISTANCE && PoiseAPI.isExhausted(entity)) {
            return false;
        }
        return entity.hasEffect(effect);
    }
}
