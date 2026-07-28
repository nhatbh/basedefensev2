package com.nhatbh.basedefensev2.boss.impl.generic;

import com.nhatbh.basedefensev2.registry.ModEffects;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber
public class SunforgedEventHandler {

    private static final Map<LivingEntity, SunforgedBulwarkController> activeControllers = new HashMap<>();

    public static void registerController(LivingEntity boss, SunforgedBulwarkController controller) {
        activeControllers.put(boss, controller);
    }

    public static void unregisterController(LivingEntity boss) {
        activeControllers.remove(boss);
    }

    public static SunforgedBulwarkController getController(LivingEntity boss) {
        return activeControllers.get(boss);
    }

    @SubscribeEvent
    public static void onPlayerHeal(LivingHealEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (player.hasEffect(ModEffects.SUN_WARD.get()) && !player.hasEffect(ModEffects.SUNWARD_IMMUNITY.get())) {
                float rawAmount = event.getAmount();
                float stolenAmount = rawAmount * 0.50f;
                event.setAmount(rawAmount * 0.50f); // Cut player healing by 50%

                float playerMaxHp = player.getMaxHealth() > 0 ? player.getMaxHealth() : 30.0f;
                float stolenRatio = stolenAmount / playerMaxHp;
                float cappedRatio = Math.min(0.02f, stolenRatio); // Cap at max 2% boss max HP per heal instance

                // Find active bosses and heal based on boss max HP + feed Radiance
                for (Map.Entry<LivingEntity, SunforgedBulwarkController> entry : activeControllers.entrySet()) {
                    LivingEntity boss = entry.getKey();
                    SunforgedBulwarkController controller = entry.getValue();
                    if (boss != null && boss.isAlive()) {
                        float bossHeal = boss.getMaxHealth() * cappedRatio;
                        boss.heal(bossHeal);
                        controller.addHealingRedirect(stolenAmount);
                    }
                }

                if (player.level() instanceof ServerLevel level) {
                    level.sendParticles(ParticleTypes.ANGRY_VILLAGER, player.getX(), player.getY() + 1.5, player.getZ(),
                            3, 0.2, 0.2, 0.2, 0.05);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();

        // Check if victim is a Boss with an active controller
        SunforgedBulwarkController controller = activeControllers.get(victim);
        if (controller != null) {
            controller.onBossDamaged();

            // Sunless burn window: +30% damage taken
            if (victim.hasEffect(ModEffects.SUNLESS.get())) {
                event.setAmount(event.getAmount() * 1.30f);
            }

            boolean isDirectMelee = event.getSource().getDirectEntity() instanceof Player
                    && !event.getSource().isIndirect();

            // Halo State 80% non-melee DR
            if (victim.hasEffect(ModEffects.HALO.get())) {
                if (!isDirectMelee) {
                    // 80% DR against non-melee damage!
                    event.setAmount(event.getAmount() * 0.20f);
                }
            }

            // Direct melee hit processing (Solar Backlash on all levels + Halo duration
            // drain)
            if (isDirectMelee && event.getSource().getEntity() instanceof Player attacker) {
                boolean isParry = false; // Triggered via warrior parry callback
                boolean isElemental = false; // Triggered via elemental attack
                controller.handleBossMeleeHit(attacker, event.getAmount(), isParry, isElemental);
            }
        }
    }

    @SubscribeEvent
    public static void onProjectileSpawn(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof Projectile projectile && !event.getLevel().isClientSide()) {
            for (Map.Entry<LivingEntity, SunforgedBulwarkController> entry : activeControllers.entrySet()) {
                SunforgedBulwarkController controller = entry.getValue();
                LivingEntity boss = entry.getKey();
                if (controller.isHaloActive() && boss != null && boss.isAlive()) {
                    if (projectile.distanceTo(boss) < 4.0) {
                        // Reflect projectile back along reverse vector!
                        Vec3 vel = projectile.getDeltaMovement();
                        projectile.setDeltaMovement(vel.scale(-1.5));
                        projectile.setOwner(boss);

                        if (event.getLevel() instanceof ServerLevel level) {
                            level.playSound(null, projectile.getX(), projectile.getY(), projectile.getZ(),
                                    SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 1.5f, 1.5f);
                            level.sendParticles(ParticleTypes.FLASH, projectile.getX(), projectile.getY(),
                                    projectile.getZ(), 1, 0, 0, 0, 0);
                        }
                    }
                }
            }
        }
    }
}
