package com.nhatbh.basedefensev2.boss.impl.generic;

import com.nhatbh.basedefensev2.boss.utils.HitboxUtils;
import com.nhatbh.basedefensev2.boss.utils.ParticleUtils;
import com.nhatbh.basedefensev2.effects.PetrificationEffect;
import com.nhatbh.basedefensev2.registry.ModEffects;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class SunforgedBulwarkController {

    private static final UUID ATTACK_SPEED_MOD_UUID = UUID.fromString("b8923a10-2e4d-4a8d-9b1b-7a8e2f01a910");
    private static final UUID MOVE_SPEED_MOD_UUID = UUID.fromString("c9934b21-3f5e-5b9e-0c2c-8b9f3012b021");

    private final LivingEntity boss;
    private final DaybreakSentinelManager sentinelManager;

    // State & Meters
    private float radiance = 0.0f; // 0 - 100
    private long lastDamagedTimeMs = 0L;
    private float unbankedHealingHp = 0.0f;

    // Threshold One-Shot Flags
    private boolean threshold75Passed = false;
    private boolean threshold50Passed = false;
    private boolean threshold25Passed = false;

    // Halo State Tracking
    private boolean isHaloActive = false;
    private boolean isHalo2 = false;
    private int haloDurationTicks = 0;
    private int haloMaxTicks = 400; // 20s for Halo I
    private boolean haloDrainedByHits = false;
    private final Map<UUID, Long> playerDrainIcd = new HashMap<>();

    // Solar Flare State
    private boolean isSolarFlareWindup = false;
    private int flareWindupTicks = 0;
    private float flareConsumedRadiance = 0.0f;

    // Sun Lance Timer
    private int lanceTimerTicks = 0;

    // Zenith Gaze Timer (Phase 4)
    private int zenithGazeTimer = 0;
    private boolean isZenithGazeWindup = false;
    private int zenithGazeWindupTicks = 0;

    // Gaze exposure accumulator per player
    private final Map<UUID, Integer> gazeExposureTicks = new HashMap<>();

    public SunforgedBulwarkController(LivingEntity boss) {
        this.boss = boss;
        this.sentinelManager = new DaybreakSentinelManager(boss);
    }

    public void tick() {
        if (boss == null || !boss.isAlive() || boss.level().isClientSide())
            return;
        ServerLevel level = (ServerLevel) boss.level();

        float hpPercent = boss.getHealth() / boss.getMaxHealth();

        // Check HP Thresholds
        if (!threshold75Passed && hpPercent <= 0.75f) {
            threshold75Passed = true;
            triggerSolarFlare();
        }
        if (!threshold50Passed && hpPercent <= 0.50f) {
            threshold50Passed = true;
            sentinelManager.spawnSentinels();
            triggerSolarFlare();
        }
        if (!threshold25Passed && hpPercent <= 0.25f) {
            threshold25Passed = true;
            enterPhase4Zenith();
            triggerSolarFlare();
        }

        // Sentinel tick & radiance feed
        int sentinelBonus = sentinelManager.tickAndGetRadianceBonus();
        if (sentinelBonus > 0) {
            addRadiance(sentinelBonus);
        }

        // Ticking Radiance Generation (+5/s base, +2.5/s if damaged in last 2s -
        // Accelerated accumulation)
        if (!isHaloActive && !isSolarFlareWindup && !boss.hasEffect(ModEffects.HALO.get())) {
            long now = System.currentTimeMillis();
            boolean recentlyDamaged = (now - lastDamagedTimeMs) < 2000L;
            float gainPerTick = (recentlyDamaged ? 2.5f : 5.0f) / 20.0f;
            addRadiance(gainPerTick);

            if (radiance >= 100.0f) {
                activateHalo(false); // Halo I
            }
        }

        // Solar Flare Wind-up tick
        if (isSolarFlareWindup) {
            tickSolarFlare(level);
        }

        // Halo State tick
        if (isHaloActive) {
            tickHalo(level);
        }

        // Boss Pulsing Gleam interval tick (75% HP and lower)
        tickPulsingGleam(level);

        // Phase 4 Zenith Gaze tick
        if (threshold25Passed) {
            tickZenithGaze(level);
        }

        // Corona particles & Sun Ward maintenance
        renderCorona(level);
        maintainSunWard(level);
    }

    private void addRadiance(float amount) {
        radiance = Math.min(100.0f, radiance + amount);
    }

    public void addHealingRedirect(float hp) {
        unbankedHealingHp += hp;
        while (unbankedHealingHp >= 4.0f) {
            addRadiance(1.0f);
            unbankedHealingHp -= 4.0f;
        }
    }

    public void onBossDamaged() {
        this.lastDamagedTimeMs = System.currentTimeMillis();
    }

    // --- Halo System ---
    public void activateHalo(boolean phase4Halo2) {
        if (isHaloActive)
            return;
        isHaloActive = true;
        isHalo2 = phase4Halo2;
        haloDurationTicks = phase4Halo2 ? 300 : 400; // Halo II: 15s pool, Halo I: 20s
        haloMaxTicks = haloDurationTicks;
        haloDrainedByHits = false;

        boss.addEffect(
                new MobEffectInstance(ModEffects.HALO.get(), haloDurationTicks, phase4Halo2 ? 1 : 0, false, true));
        radiance = 100.0f; // Radiance stays at 100 during Halo, resets to 0 only when Solar Flare triggers

        if (boss.level() instanceof ServerLevel level) {
            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.BEACON_POWER_SELECT,
                    SoundSource.HOSTILE, 2.0f, 1.2f);
            level.sendParticles(ParticleTypes.FLASH, boss.getX(), boss.getY() + 1.0, boss.getZ(), 2, 0, 0, 0, 0);
        }
    }

    private void tickHalo(ServerLevel level) {
        // Warning shimmer particles
        if (haloDurationTicks > haloMaxTicks - 30) {
            ParticleUtils.renderCircle(level, ParticleTypes.GLOW, boss.position(), 2.0, 16, 0.05);
        }

        if (!isHalo2) {
            haloDurationTicks--;
            if (haloDurationTicks <= 0) {
                endHalo(haloDrainedByHits);
            }
        }
    }

    private void endHalo(boolean drainedByHits) {
        isHaloActive = false;
        boss.removeEffect(ModEffects.HALO.get());

        // Remove sun_ward from all players
        List<Player> players = HitboxUtils.getEntitiesInCircle(boss.level(), Player.class, boss.position(), 100.0,
                p -> true);
        for (Player p : players) {
            p.removeEffect(ModEffects.SUN_WARD.get());
        }

        // Always trigger Solar Flare whenever Halo ends (regardless of natural
        // expiration or breaking via hits)
        triggerSolarFlare();

        if (drainedByHits) {
            // Sunless burn window (8s / 160 ticks)
            boss.addEffect(new MobEffectInstance(ModEffects.SUNLESS.get(), 160, 0, false, true));
            if (boss.level() instanceof ServerLevel level) {
                level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.WITHER_DEATH,
                        SoundSource.HOSTILE, 1.5f, 1.5f);
            }
        }

        // In Phase 4, Halo II auto-reapplies after Sunless ends
        if (threshold25Passed && drainedByHits) {
            // Scheduled auto-reapply handled in tick when sunless expires
        }
    }

    public boolean handleBossMeleeHit(Player attacker, float damageAmount, boolean isParry,
            boolean isElementalAdvantage) {
        long now = System.currentTimeMillis();
        long lastDrain = playerDrainIcd.getOrDefault(attacker.getUUID(), 0L);

        if (isParry) {
            // Warrior parry counter = -5s (100 ticks) to Halo if active, Backlash-exempt!
            if (isHaloActive) {
                haloDurationTicks -= 100;
            }
            if (boss.level() instanceof ServerLevel level) {
                level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.SHIELD_BLOCK,
                        SoundSource.HOSTILE, 2.0f, 0.8f);
                level.sendParticles(ParticleTypes.FLASH, boss.getX(), boss.getY() + 1.0, boss.getZ(), 3, 0.2, 0.2, 0.2,
                        0.0);
                level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, boss.getX(), boss.getY() + 1.0, boss.getZ(), 20,
                        0.4, 0.4, 0.4, 0.15);
            }
        } else if ((now - lastDrain >= 1500L) || (isHaloActive && isHalo2 && isElementalAdvantage)) {
            playerDrainIcd.put(attacker.getUUID(), now);

            // Halo duration drain if Halo is active
            if (isHaloActive) {
                int drainTicks = (isHalo2 && isElementalAdvantage) ? 100 : 30;
                haloDurationTicks -= drainTicks;
            }

            // Backlash on all levels (Level 1: 2s, Level 2: 3s, Level 3: 4s, Level 4: 6s)
            if (attacker.getLookAngle().normalize()
                    .dot(boss.getEyePosition().subtract(attacker.getEyePosition()).normalize()) > 0.5) {
                int backlashTicks;
                if (isHaloActive || radiance >= 75.0f) {
                    backlashTicks = 120; // 6 seconds (Level 4)
                } else if (radiance >= 50.0f) {
                    backlashTicks = 80; // 4 seconds (Level 3)
                } else if (radiance >= 25.0f) {
                    backlashTicks = 60; // 3 seconds (Level 2)
                } else {
                    backlashTicks = 40; // 2 seconds (Level 1)
                }

                PetrificationEffect.addStage(attacker, backlashTicks, "Melee Hitting Boss (Solar Backlash)");
                triggerBacklashFlashingEffect(attacker);
            }
        }

        if (isHaloActive && haloDurationTicks <= 0) {
            haloDrainedByHits = true;
            endHalo(true);
        }

        return true;
    }

    private void triggerBacklashFlashingEffect(Player attacker) {
        if (attacker.level() instanceof ServerLevel level) {
            Vec3 pos = attacker.getEyePosition();
            level.sendParticles(ParticleTypes.FLASH, pos.x, pos.y, pos.z, 2, 0.1, 0.1, 0.1, 0);
            level.sendParticles(ParticleTypes.GLOW, pos.x, pos.y, pos.z, 15, 0.3, 0.3, 0.3, 0.1);
            level.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 8, 0.2, 0.2, 0.2, 0.05);
            level.playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.PLAYERS, 2.0f, 1.6f);
        }
    }

    // --- Solar Flare ---
    public void triggerSolarFlare() {
        if (isSolarFlareWindup)
            return;
        isSolarFlareWindup = true;
        flareWindupTicks = 0;
        flareConsumedRadiance = radiance;
        radiance = 0.0f;

        if (boss.level() instanceof ServerLevel level) {
            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.BEACON_DEACTIVATE,
                    SoundSource.HOSTILE, 2.5f, 0.5f);
        }
    }

    private void tickSolarFlare(ServerLevel level) {
        flareWindupTicks++;

        // Inward light particles telegraph (1.5s / 30 ticks)
        Vec3 core = boss.getEyePosition();
        for (int i = 0; i < 8; i++) {
            double angle = (flareWindupTicks * 0.2) + (i * Math.PI / 4.0);
            double r = 4.0 * (1.0 - flareWindupTicks / 30.0);
            level.sendParticles(ParticleTypes.END_ROD, core.x + Math.cos(angle) * r, core.y + Math.sin(angle) * r,
                    core.z + Math.sin(angle) * r, 1, 0, 0, 0, 0);
        }

        // Facing gaze check during windup (ticks 0 - 30)
        checkPetrifyingGaze(level);

        if (flareWindupTicks >= 30) {
            isSolarFlareWindup = false;

            // Flashing Solar Flare Detonation!
            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.GENERIC_EXPLODE,
                    SoundSource.HOSTILE, 3.0f, 0.6f);
            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.TOTEM_USE, SoundSource.HOSTILE,
                    2.0f, 1.2f);

            level.sendParticles(ParticleTypes.FLASH, core.x, core.y, core.z, 10, 0.5, 0.5, 0.5, 0);
            level.sendParticles(ParticleTypes.LAVA, core.x, core.y, core.z, 25, 1.5, 1.5, 1.5, 0.2);
            level.sendParticles(ParticleTypes.GLOW, core.x, core.y, core.z, 40, 2.0, 2.0, 2.0, 0.15);
            level.sendParticles(ParticleTypes.END_ROD, core.x, core.y, core.z, 30, 1.5, 1.5, 1.5, 0.1);

            float totalDamagePercent = 0.10f + (0.004f * flareConsumedRadiance);

            List<Player> players = HitboxUtils.getEntitiesInCircle(level, Player.class, boss.position(), 60.0,
                    Player::isAlive);
            for (Player p : players) {
                if (hasSolidBlockLineOfSight(level, core, p.getEyePosition())) {
                    // Blocked entirely by solid block cover!
                    level.sendParticles(ParticleTypes.CRIT, p.getX(), p.getY() + 1.0, p.getZ(), 5, 0.2, 0.2, 0.2, 0.05);
                    continue;
                }

                // Flash on hit player
                level.sendParticles(ParticleTypes.FLASH, p.getX(), p.getY() + 1.0, p.getZ(), 3, 0.1, 0.1, 0.1, 0);
                level.sendParticles(ParticleTypes.GLOW, p.getX(), p.getY() + 1.0, p.getZ(), 12, 0.3, 0.3, 0.3, 0.05);

                // Deal damage: half max HP % + half flat damage on 30 HP baseline
                float rawDamage = p.getMaxHealth() * totalDamagePercent;
                float halfHpPortion = (p.getMaxHealth() * (totalDamagePercent / 2.0f));
                float halfFlatPortion = (30.0f * (totalDamagePercent / 2.0f));
                float finalDamage = halfHpPortion + halfFlatPortion;

                p.hurt(level.damageSources().mobAttack(boss), finalDamage);
            }
        }
    }

    private boolean hasSolidBlockLineOfSight(ServerLevel level, Vec3 start, Vec3 end) {
        BlockHitResult result = level
                .clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, boss));
        return result.getType() == HitResult.Type.BLOCK;
    }

    private void checkPetrifyingGaze(ServerLevel level) {
        Vec3 bossEye = boss.getEyePosition();
        List<Player> players = HitboxUtils.getEntitiesInCircle(level, Player.class, boss.position(), 40.0,
                Player::isAlive);

        for (Player p : players) {
            Vec3 pLook = p.getLookAngle().normalize();
            Vec3 toBoss = bossEye.subtract(p.getEyePosition()).normalize();

            // Facing boss (dot product > 0.5) & Line of sight clear
            if (pLook.dot(toBoss) > 0.5 && !hasSolidBlockLineOfSight(level, p.getEyePosition(), bossEye)) {
                int exp = gazeExposureTicks.getOrDefault(p.getUUID(), 0) + 1;
                gazeExposureTicks.put(p.getUUID(), exp);

                // +1 stage per 0.5s (10 ticks) of exposure
                if (exp % 10 == 0) {
                    PetrificationEffect.addStage(p, 300, "Facing Boss during Petrifying Gaze / Solar Flare");
                }
            } else {
                gazeExposureTicks.put(p.getUUID(), 0);
            }
        }
    }

    // --- Boss Pulsing Gleam (75% HP and lower) ---
    private int gleamTimerTicks = 0;

    private void tickPulsingGleam(ServerLevel level) {
        if (!threshold75Passed)
            return;

        gleamTimerTicks++;
        int cycleTick = gleamTimerTicks % 160; // 8s cycle

        // Telegraph ring at 7s (ticks 140 to 160)
        if (cycleTick >= 140 && cycleTick < 160) {
            ParticleUtils.renderCircle(level, ParticleTypes.FLAME, boss.position(), 8.0, 32, 0.03);
        }

        if (cycleTick == 0) {
            // Detonate 8-block Pulsing Gleam!
            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER,
                    SoundSource.HOSTILE, 1.2f, 1.8f);
            level.sendParticles(ParticleTypes.FLASH, boss.getX(), boss.getY() + 1.0, boss.getZ(), 3, 0.5, 0.5, 0.5,
                    0.0);
            level.sendParticles(ParticleTypes.GLOW, boss.getX(), boss.getY() + 1.0, boss.getZ(), 25, 2.0, 0.5, 2.0,
                    0.1);

            List<Player> hitPlayers = HitboxUtils.getEntitiesInCircle(level, Player.class, boss.position(), 8.0,
                    Player::isAlive);
            for (Player p : hitPlayers) {
                Vec3 pLook = p.getLookAngle().normalize();
                Vec3 toBoss = boss.getEyePosition().subtract(p.getEyePosition()).normalize();
                if (pLook.dot(toBoss) > 0.5) {
                    PetrificationEffect.addStage(p, 300, "Hit by Boss Pulsing Gleam while Facing Boss");
                }
            }
        }
    }

    // --- Phase 4 Eternal Zenith ---
    private int phase4FlareTimer = 0;

    private void enterPhase4Zenith() {
        activateHalo(true); // Permanent Halo II

        // Additive attribute modifiers (+25% attack speed, +25% move speed)
        var attSpeed = boss.getAttribute(Attributes.ATTACK_SPEED);
        if (attSpeed != null && attSpeed.getModifier(ATTACK_SPEED_MOD_UUID) == null) {
            attSpeed.addTransientModifier(new AttributeModifier(ATTACK_SPEED_MOD_UUID, "Zenith Attack Speed", 0.25,
                    AttributeModifier.Operation.MULTIPLY_BASE));
        }

        var moveSpeed = boss.getAttribute(Attributes.MOVEMENT_SPEED);
        if (moveSpeed != null && moveSpeed.getModifier(MOVE_SPEED_MOD_UUID) == null) {
            moveSpeed.addTransientModifier(new AttributeModifier(MOVE_SPEED_MOD_UUID, "Zenith Move Speed", 0.25,
                    AttributeModifier.Operation.MULTIPLY_BASE));
        }
    }

    private void tickZenithGaze(ServerLevel level) {
        // Periodical Solar Flares every 15 seconds (300 ticks) in Phase 4
        phase4FlareTimer++;
        if (phase4FlareTimer >= 300) {
            phase4FlareTimer = 0;
            if (!isSolarFlareWindup) {
                triggerSolarFlare();
            }
        }

        zenithGazeTimer++;
        if (zenithGazeTimer >= 300) { // Every 15s
            if (!isZenithGazeWindup) {
                isZenithGazeWindup = true;
                zenithGazeWindupTicks = 0;
                level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.EVOKER_PREPARE_ATTACK,
                        SoundSource.HOSTILE, 2.0f, 1.8f);
            }
        }

        if (isZenithGazeWindup) {
            zenithGazeWindupTicks++;
            checkPetrifyingGaze(level);

            if (zenithGazeWindupTicks >= 20) { // 1s windup
                isZenithGazeWindup = false;
                zenithGazeTimer = 0;
                level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.BEACON_POWER_SELECT,
                        SoundSource.HOSTILE, 1.5f, 1.0f);
            }
        }

        // Auto-reapply Halo II after Sunless ends in Phase 4
        if (!isHaloActive && !boss.hasEffect(ModEffects.SUNLESS.get())) {
            activateHalo(true);
        }
    }

    // --- Visual Corona & Sun Ward ---
    private void renderCorona(ServerLevel level) {
        if (boss.hasEffect(ModEffects.SUNLESS.get()))
            return;
        if (radiance <= 0.0f && !isHaloActive)
            return;

        Vec3 head = boss.getEyePosition().add(0, 0.4, 0);
        long tick = boss.tickCount;

        net.minecraft.core.particles.ParticleOptions levelParticle;
        double crownRadius = 1.6;
        int particleCount = 10;

        if (isHaloActive) {
            // Dynamic dimming as halo duration decreases (ratio 1.0 -> 0.0)
            float durationRatio = Math.max(0.0f, (float) haloDurationTicks / (float) Math.max(1, haloMaxTicks));

            // Particle type degrades as duration drops: 4 (END_ROD) -> 3 (SOUL_FIRE_FLAME)
            // -> 2 (FLAME) -> 1 (CRIT)
            if (durationRatio > 0.75f) {
                levelParticle = ParticleTypes.END_ROD; // Level 4
            } else if (durationRatio > 0.50f) {
                levelParticle = ParticleTypes.SOUL_FIRE_FLAME; // Level 3
            } else if (durationRatio > 0.25f) {
                levelParticle = ParticleTypes.FLAME; // Level 2
            } else {
                levelParticle = ParticleTypes.CRIT; // Level 1 (Dimming out)
            }

            // Radius & Density dim as duration decreases
            crownRadius = 1.0 + 0.6 * durationRatio;
            particleCount = Math.max(3, (int) (10 * durationRatio));

            // Light rays dim and vanish as duration drops below 40%
            if (durationRatio > 0.4f && tick % 4 == 0) {
                level.sendParticles(ParticleTypes.END_ROD, boss.getX(), boss.getY() + 1.2, boss.getZ(),
                        (int) (4 * durationRatio), 0.3, 0.8, 0.3, 0.02);
            }
        } else {
            // Charging Golden Corona based on Radiance meter (0 - 100)
            if (radiance >= 75.0f) {
                levelParticle = ParticleTypes.END_ROD; // Level 4
            } else if (radiance >= 50.0f) {
                levelParticle = ParticleTypes.SOUL_FIRE_FLAME; // Level 3
            } else if (radiance >= 25.0f) {
                levelParticle = ParticleTypes.FLAME; // Level 2
            } else {
                levelParticle = ParticleTypes.CRIT; // Level 1
            }
        }

        // Full 360-degree crown circle
        double angleStep = 360.0 / particleCount;
        for (int i = 0; i < particleCount; i++) {
            double angleRad = Math.toRadians((i * angleStep) + (tick * 4));
            double dx = Math.cos(angleRad) * crownRadius;
            double dz = Math.sin(angleRad) * crownRadius;
            level.sendParticles(levelParticle, head.x + dx, head.y, head.z + dz, 1, 0, 0, 0, 0.01);
        }
    }

    private void maintainSunWard(ServerLevel level) {
        boolean shouldApply = (isHaloActive || (threshold25Passed && !boss.hasEffect(ModEffects.SUNLESS.get())));

        if (shouldApply) {
            List<Player> players = HitboxUtils.getEntitiesInCircle(level, Player.class, boss.position(), 100.0,
                    p -> true);
            for (Player p : players) {
                if (!p.hasEffect(ModEffects.SUNWARD_IMMUNITY.get())) {
                    p.addEffect(new MobEffectInstance(ModEffects.SUN_WARD.get(), 30, 0, false, true));
                }
            }
        }
    }

    public DaybreakSentinelManager getSentinelManager() {
        return sentinelManager;
    }

    public boolean isHaloActive() {
        return isHaloActive;
    }

    public boolean isHalo2() {
        return isHalo2;
    }
}
