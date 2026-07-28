package com.nhatbh.basedefensev2.effects;

import com.nhatbh.basedefensev2.registry.ModEffects;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

public class HeavyFootingEffect extends MobEffect {

    private static final UUID SPEED_MODIFIER_UUID = UUID.fromString("d8721a20-3e5f-4a9d-8b2b-6a9e1f01c801");

    public HeavyFootingEffect() {
        super(MobEffectCategory.HARMFUL, 0x8B5A2B); // Earthy brown color
        // Stage 1 (amp 0): -20% speed; Stage 2 (amp 1): -40% speed
        addAttributeModifier(Attributes.MOVEMENT_SPEED, SPEED_MODIFIER_UUID.toString(), -0.20, AttributeModifier.Operation.MULTIPLY_BASE);
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level().isClientSide()) return;

        // Disable sprinting for Stage 2+ (amplifier >= 1)
        if (amplifier >= 1 && entity instanceof Player player && player.isSprinting()) {
            player.setSprinting(false);
        }

        // Conversion to Petrified on Stage 3+ (amplifier >= 2)
        if (amplifier >= 2) {
            entity.removeEffect(this);
            // Apply 15s (300 ticks) of Petrified (Stone Encasement)
            entity.addEffect(new MobEffectInstance(ModEffects.PETRIFIED.get(), 300, 0, false, true, true));
        }
    }

    public static void addStage(LivingEntity entity, int durationTicks, String reason) {
        if (entity.hasEffect(ModEffects.PETRIFIED.get())) {
            return; // Already petrified
        }

        int newStage = 1;
        MobEffectInstance existing = entity.getEffect(ModEffects.HEAVY_FOOTING.get());
        if (existing != null) {
            newStage = Math.min(3, existing.getAmplifier() + 2);
        }

        if (newStage >= 3) {
            entity.removeEffect(ModEffects.HEAVY_FOOTING.get());
            entity.addEffect(new MobEffectInstance(ModEffects.PETRIFIED.get(), 300, 0, false, true, true));

            if (entity instanceof Player player) {
                player.sendSystemMessage(Component.literal("§c[!] Heavy Footing Reached Stage 3/3! Encased in Stone for 15s! §7(Reason: " + reason + ")"));
            }
        } else {
            int amplifier = newStage - 1; // 0 for Stage 1, 1 for Stage 2
            entity.addEffect(new MobEffectInstance(ModEffects.HEAVY_FOOTING.get(), durationTicks, amplifier, false, true, true));

            if (entity instanceof Player player) {
                player.sendSystemMessage(Component.literal("§e[!] Heavy Footing Increased! Reason: " + reason + " (" + newStage + "/3 Stacks)"));
            }
        }
    }
}
