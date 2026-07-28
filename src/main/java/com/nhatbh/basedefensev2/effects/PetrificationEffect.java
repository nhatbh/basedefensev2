package com.nhatbh.basedefensev2.effects;

import com.nhatbh.basedefensev2.registry.ModEffects;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

public class PetrificationEffect extends MobEffect {

    public PetrificationEffect() {
        super(MobEffectCategory.HARMFUL, 0x808080); // Gray stone color
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        // Stage 5 check (amplifier >= 4 means 5th stage, since amplifiers are 0-indexed)
        if (amplifier >= 4) {
            MobEffectInstance currentInstance = entity.getEffect(this);
            int remainingDuration = currentInstance != null ? currentInstance.getDuration() : 200;

            // Consume/Remove Petrification
            entity.removeEffect(this);

            // Apply Petrified for remaining duration
            entity.addEffect(new MobEffectInstance(ModEffects.PETRIFIED.get(), remainingDuration, 0, false, true, true));
        }
    }

    /**
     * Helper method to add a Petrification stage to an entity.
     * On stage 5 (amplifier 4), converts effect into Petrified.
     *
     * @param entity The living entity
     * @param durationTicks Duration of the effect in ticks
     */
    public static void addStage(LivingEntity entity, int durationTicks) {
        addStage(entity, durationTicks, "Solar Energy");
    }

    /**
     * Helper method to add a Petrification stage to an entity with a chat warning notification.
     *
     * @param entity The living entity
     * @param durationTicks Duration of the effect in ticks
     * @param reason Description of the action that caused petrification
     */
    public static void addStage(LivingEntity entity, int durationTicks, String reason) {
        if (entity.hasEffect(ModEffects.PETRIFIED.get())) {
            return; // Already petrified
        }

        int newStage = 1;
        MobEffectInstance existing = entity.getEffect(ModEffects.PETRIFICATION.get());
        if (existing != null) {
            int newAmp = existing.getAmplifier() + 1;
            if (newAmp >= 4) {
                // Stage 5 reached: consume effect & convert to Petrified for remaining duration
                entity.removeEffect(ModEffects.PETRIFICATION.get());
                entity.addEffect(new MobEffectInstance(ModEffects.PETRIFIED.get(), durationTicks, 0, false, true, true));
                newStage = 4;
                if (entity instanceof net.minecraft.world.entity.player.Player player) {
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal("§4§l[!] PETRIFIED! §cYou have been turned to stone by: §e" + reason), false);
                }
                return;
            } else {
                // Increment stage
                entity.addEffect(new MobEffectInstance(ModEffects.PETRIFICATION.get(), durationTicks, newAmp, false, true, true));
                newStage = newAmp + 1;
            }
        } else {
            // Stage 1 (amplifier 0)
            entity.addEffect(new MobEffectInstance(ModEffects.PETRIFICATION.get(), durationTicks, 0, false, true, true));
            newStage = 1;
        }

        if (entity instanceof net.minecraft.world.entity.player.Player player) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("§c[!] Petrification Increased! §fReason: §e" + reason + " §c(" + newStage + "/4 Stacks)"), false);
        }
    }
}
