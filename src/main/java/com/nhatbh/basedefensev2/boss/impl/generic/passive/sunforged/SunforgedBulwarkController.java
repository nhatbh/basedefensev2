package com.nhatbh.basedefensev2.boss.impl.generic.passive.sunforged;

import com.nhatbh.basedefensev2.boss.client.BossResourceBarRegistry;
import com.nhatbh.basedefensev2.boss.utils.HitboxUtils;
import com.nhatbh.basedefensev2.effects.PetrificationEffect;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SunforgedBulwarkController {

    private final LivingEntity boss;

    // Stance & Radiance Meter States
    private float radiance = 0.0f; // 0 to 100
    private boolean isShieldActive = false;
    private int shieldActiveTimer = 0;
    private int maxShieldActiveTimer = 60; // 60 ticks = 3.0s (80 in P2)

    private float lockedYaw = 0.0f;
    private float lockedHeadYaw = 0.0f;

    private boolean phase2Active = false;

    // Player Gaze exposure accumulator
    private final Map<UUID, Integer> gazeExposureTicks = new HashMap<>();

    public SunforgedBulwarkController(LivingEntity boss) {
        this.boss = boss;
        BossResourceBarRegistry.registerBar(
                boss,
                () -> isShieldActive ? "SOLAR RADIANCE [PETRIFYING GAZE]" : "Charging Solar Radiance...",
                () -> isShieldActive ? (((float) shieldActiveTimer / (float) maxShieldActiveTimer) * 100.0f) : radiance,
                () -> 100.0f,
                () -> isShieldActive ? 0xFFFFD700 : 0xFFFFA500,
                () -> isShieldActive ? 0xFFFF8C00 : 0xFF8B4500
        );
    }

    public void tick() {
        if (boss == null || !boss.isAlive() || boss.level().isClientSide())
            return;
        if (com.nhatbh.basedefensev2.api.PoiseAPI.isExhausted(boss)) {
            if (isShieldActive) {
                isShieldActive = false;
                radiance = 0.0f;
                gazeExposureTicks.clear();
            }
            return;
        }
        ServerLevel level = (ServerLevel) boss.level();

        // Check Phase 2 (<= 25% HP Desperation)
        float hpPercent = boss.getHealth() / boss.getMaxHealth();
        if (hpPercent <= 0.25f && !phase2Active) {
            phase2Active = true;
        }

        maxShieldActiveTimer = phase2Active ? 40 : 30; // 2.0s in P2, 1.5s in P1 (lessened from 4s/3s)

        if (isShieldActive) {
            shieldActiveTimer--;
            radiance = ((float) shieldActiveTimer / (float) maxShieldActiveTimer) * 100.0f;

            // A. Direction Lock: Lock boss facing direction while shield is active!
            boss.setYRot(lockedYaw);
            boss.yRotO = lockedYaw;
            boss.setYHeadRot(lockedHeadYaw);
            boss.yHeadRotO = lockedHeadYaw;

            // B. Movement Speed Reduction: Boss is heavily slowed while shield is active
            boss.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, 2, false, false));

            // C. Visuals: Render frontal golden solar shield arc
            renderFrontalShieldParticles(level);

            // D. Gaze Check: Staring directly at the active frontal shield petrifies players
            tickPetrifyingGaze(level);

            if (shieldActiveTimer <= 0) {
                isShieldActive = false;
                radiance = 0.0f;
                gazeExposureTicks.clear();
                level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 1.5f, 0.6f);
            }
        } else {
            // Charge Radiance up to 100 (45s in P1, 30s in P2)
            float gainPerTick = phase2Active ? (100.0f / 600.0f) : (100.0f / 900.0f);
            radiance = Math.min(100.0f, radiance + gainPerTick);

            if (radiance >= 100.0f) {
                // Activate Sunforged Bulwark Stance!
                isShieldActive = true;
                shieldActiveTimer = maxShieldActiveTimer;
                lockedYaw = boss.getYRot();
                lockedHeadYaw = boss.getYHeadRot();

                level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 2.0f, 1.2f);
                level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.ANVIL_PLACE, SoundSource.HOSTILE, 1.5f, 1.5f);
            }
        }
    }

    private void renderFrontalShieldParticles(ServerLevel level) {
        Vec3 eyePos = boss.getEyePosition();
        double radYaw = Math.toRadians(-lockedYaw);
        Vec3 forward = new Vec3(Math.sin(radYaw), 0, Math.cos(radYaw)).normalize();
        Vec3 right = new Vec3(-forward.z, 0, forward.x);

        Vec3 shieldCenter = eyePos.add(forward.scale(1.2));

        for (int i = -4; i <= 4; i++) {
            double offset = i * 0.3;
            Vec3 pos = shieldCenter.add(right.scale(offset));
            level.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 1, 0, 0.1, 0, 0.01);
            level.sendParticles(ParticleTypes.GLOW, pos.x, pos.y - 0.5, pos.z, 1, 0.05, 0.2, 0.05, 0.01);
        }
    }

    private void tickPetrifyingGaze(ServerLevel level) {
        List<Player> players = HitboxUtils.getEntitiesInCircle(level, Player.class, boss.position(), 25.0, Player::isAlive);

        double radYaw = Math.toRadians(-lockedYaw);
        Vec3 bossFacing = new Vec3(Math.sin(radYaw), 0, Math.cos(radYaw)).normalize();

        for (Player player : players) {
            Vec3 toBoss = boss.getEyePosition().subtract(player.getEyePosition()).normalize();

            // Check 1: Player is in front of boss (frontal cone)
            Vec3 bossToPlayer = player.position().subtract(boss.position()).normalize();
            boolean inFrontalCone = bossFacing.dot(bossToPlayer) > 0.2; // ~75 degree frontal cone

            // Check 2: Player is looking directly at the boss
            boolean lookingAtBoss = player.getLookAngle().normalize().dot(toBoss) > 0.5;

            if (inFrontalCone && lookingAtBoss) {
                int exp = gazeExposureTicks.getOrDefault(player.getUUID(), 0) + 1;
                gazeExposureTicks.put(player.getUUID(), exp);

                // Add 1 Petrification stage every 10 ticks (0.5s) of direct gaze
                if (exp % 10 == 0) {
                    PetrificationEffect.addStage(player, 100, "Staring at Sunforged Bulwark");
                }
            } else {
                gazeExposureTicks.put(player.getUUID(), 0);
            }
        }
    }

    public void onBossDamaged(LivingHurtEvent event) {
        if (boss.level().isClientSide() || !isShieldActive || com.nhatbh.basedefensev2.api.PoiseAPI.isExhausted(boss))
            return;
        ServerLevel level = (ServerLevel) boss.level();

        Entity attacker = event.getSource().getDirectEntity();
        if (attacker == null)
            attacker = event.getSource().getEntity();

        if (attacker != null) {
            double radYaw = Math.toRadians(-lockedYaw);
            Vec3 bossFacing = new Vec3(Math.sin(radYaw), 0, Math.cos(radYaw)).normalize();
            Vec3 bossToAttacker = attacker.position().subtract(boss.position()).normalize();

            // Frontal attack -> Reduced by Sunforged Bulwark (40% damage reduction instead of 100% block)
            if (bossFacing.dot(bossToAttacker) > 0.2) {
                event.setAmount(event.getAmount() * 0.60f);

                level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 2.0f, 1.2f);
                level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.ANVIL_PLACE, SoundSource.HOSTILE, 1.5f, 1.5f);
                level.sendParticles(ParticleTypes.FLASH, boss.getX(), boss.getY() + 1.2, boss.getZ(), 2, 0.2, 0.2, 0.2, 0);
                level.sendParticles(ParticleTypes.CRIT, boss.getX(), boss.getY() + 1.2, boss.getZ(), 15, 0.3, 0.3, 0.3, 0.1);

                if (attacker instanceof Player p) {
                    p.displayClientMessage(Component.literal("§e[!] Frontal Attack Mitigated (-40%) by Sunforged Bulwark! Flank to the side/back!"), true);
                }
            }
        }
    }

    public boolean isShieldActive() {
        return isShieldActive;
    }
}
