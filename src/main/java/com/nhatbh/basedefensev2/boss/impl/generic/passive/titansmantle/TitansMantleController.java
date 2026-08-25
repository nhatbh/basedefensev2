package com.nhatbh.basedefensev2.boss.impl.generic.passive.titansmantle;

import com.nhatbh.basedefensev2.boss.client.BossResourceBarRegistry;
import com.nhatbh.basedefensev2.boss.impl.generic.active.StoneSpikeManager;
import com.nhatbh.basedefensev2.boss.utils.ParticleUtils;
import com.nhatbh.basedefensev2.registry.ModEffects;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

public class TitansMantleController {

    private final LivingEntity boss;
    private final StoneSpikeManager spikeManager;

    // Meters & Timers
    private float shieldPoints = 100.0f; // 0 to 100
    private int hardenedTimer = 0; // Ticks remaining in Hardened Crust immunity window (30 ticks = 1.5s)
    private int shatteredTimer = 0; // Ticks remaining in Shattered Mantle vulnerability window (160 ticks = 8s)

    private boolean desperationActive = false;

    // Ground Slam State
    private boolean isGroundSlamActive = false;
    private int groundSlamTelegraphTimer = 0; // 30 ticks = 1.5s delay

    public TitansMantleController(LivingEntity boss) {
        this.boss = boss;
        this.spikeManager = new StoneSpikeManager(boss);
        BossResourceBarRegistry.registerBar(
                boss,
                () -> shatteredTimer > 0 ? "MANTLE SHATTERED [+50% DMG]"
                        : (hardenedTimer > 0 ? "HARDENED MANTLE [IMMUNE]" : "Titanic Shield"),
                () -> shatteredTimer > 0 ? (160.0f - (float) shatteredTimer) : shieldPoints,
                () -> shatteredTimer > 0 ? 160.0f : 100.0f,
                () -> shatteredTimer > 0 ? 0xFF9933FF : (hardenedTimer > 0 ? 0xFF00E5FF : 0xFFCD853F),
                () -> shatteredTimer > 0 ? 0xFF4B0082 : (hardenedTimer > 0 ? 0xFF005588 : 0xFF5A3A1A));
    }

    public void tick() {
        if (boss == null || !boss.isAlive() || boss.level().isClientSide() || com.nhatbh.basedefensev2.api.PoiseAPI.isExhausted(boss))
            return;
        ServerLevel level = (ServerLevel) boss.level();

        float hpPercent = boss.getHealth() / boss.getMaxHealth();

        // 1. Check Phase 2 Transition (<= 25% HP Desperation)
        if (!desperationActive && hpPercent <= 0.25f) {
            desperationActive = true;
            triggerGroundSlam();
        }

        // 2. Tick Hardened Crust Window
        if (hardenedTimer > 0) {
            hardenedTimer--;
            if (boss.tickCount % 4 == 0) {
                renderHardenedShieldParticles(level);
            }
        }

        // 3. Tick Shattered Mantle Window
        if (shatteredTimer > 0) {
            shatteredTimer--;
            if (boss.tickCount % 5 == 0) {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.TUFF.defaultBlockState()),
                        boss.getX(), boss.getY() + 1.0, boss.getZ(), 4, 0.4, 0.6, 0.4, 0.1);
            }
            if (shatteredTimer <= 0) {
                // Regenerate Shield back to 100
                shieldPoints = 100.0f;
                boss.removeEffect(ModEffects.SHATTERED_MANTLE.get());
                level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.ANVIL_USE, SoundSource.HOSTILE,
                        1.5f, 0.8f);
            }
        }

        // 4. Tick Ground Slam Telegraph & Impact
        if (isGroundSlamActive) {
            tickGroundSlam(level);
        }

        // 5. Tick Stone Spikes
        spikeManager.tick();
    }

    public void onBossDamaged(LivingHurtEvent event) {
        if (boss.level().isClientSide() || com.nhatbh.basedefensev2.api.PoiseAPI.isExhausted(boss))
            return;
        ServerLevel level = (ServerLevel) boss.level();

        // A. If Shattered Mantle is active -> Take +75% Bonus Damage
        if (shatteredTimer > 0) {
            event.setAmount(event.getAmount() * 1.75f);
            return;
        }

        // B. If Shield is active (> 0 points)
        if (shieldPoints > 0) {
            Entity directAttacker = event.getSource().getDirectEntity();

            // Check if Hardened Crust Window is Active
            if (hardenedTimer > 0) {
                // Apply 40% Damage Reduction while Hardened (lessened from 75%)
                event.setAmount(event.getAmount() * 0.60f);

                if (directAttacker instanceof Projectile proj) {
                    reflectProjectile(level, proj);
                } else {
                    level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.ARMOR_EQUIP_IRON,
                            SoundSource.HOSTILE, 1.5f, 0.6f);
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.TUFF.defaultBlockState()),
                            boss.getX(), boss.getY() + 1.0, boss.getZ(), 8, 0.3, 0.4, 0.3, 0.05);
                }
            } else {
                // First Hit outside Hardened Window -> Takes Full Damage, Drains Shield Points
                float rawDamage = event.getAmount();
                float maxHp = boss.getMaxHealth();
                float hpPercentDealt = maxHp > 0 ? (rawDamage / maxHp) * 100.0f : 1.0f;

                // Dynamic shield damage: 10x multiplier on HP % dealt
                float shieldDamage = Math.min(50.0f, Math.max(5.0f, hpPercentDealt * 10.0f));
                shieldPoints = Math.max(0.0f, shieldPoints - shieldDamage);

                // Trigger Hardened Crust Window (0.75s in Phase 1 / 0.5s in Phase 2)
                hardenedTimer = desperationActive ? 10 : 15;

                level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.ANVIL_PLACE,
                        SoundSource.HOSTILE, 1.8f, 0.7f);
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.TUFF.defaultBlockState()),
                        boss.getX(), boss.getY() + 1.0, boss.getZ(), 20, 0.5, 0.8, 0.5, 0.15);

                if (shieldPoints <= 0.0f) {
                    shatterShield(level);
                }
            }
        }
    }

    public void triggerGroundSlam() {
        if (isGroundSlamActive)
            return;

        isGroundSlamActive = true;
        groundSlamTelegraphTimer = 30; // 1.5 seconds telegraph delay

        if (boss.level() instanceof ServerLevel level) {
            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.EVOKER_PREPARE_ATTACK,
                    SoundSource.HOSTILE, 2.5f, 0.5f);
            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR,
                    SoundSource.HOSTILE, 2.5f, 0.4f);
        }
    }

    private void shatterShield(ServerLevel level) {
        shatteredTimer = 160; // 8 seconds vulnerability
        shieldPoints = 0.0f;

        boss.addEffect(new MobEffectInstance(ModEffects.SHATTERED_MANTLE.get(), 160, 0, false, true));

        level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.ITEM_BREAK, SoundSource.HOSTILE, 2.5f,
                0.5f);
        level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE,
                2.0f, 0.8f);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, boss.getX(), boss.getY() + 1.0, boss.getZ(), 1, 0, 0, 0,
                0);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.TUFF.defaultBlockState()),
                boss.getX(), boss.getY() + 1.0, boss.getZ(), 60, 1.0, 1.0, 1.0, 0.25);

        // In Phase 2, shattering the shield also triggers a Ground Slam punish!
        if (desperationActive) {
            triggerGroundSlam();
        }
    }

    private void reflectProjectile(ServerLevel level, Projectile proj) {
        Entity owner = proj.getOwner();
        Vec3 targetPos;

        if (owner != null && owner.isAlive()) {
            targetPos = owner.position().add(0, owner.getEyeHeight() * 0.5, 0);
        } else {
            // Reflect backward along current trajectory
            targetPos = proj.position().subtract(proj.getDeltaMovement());
        }

        Vec3 reflectDir = targetPos.subtract(proj.position()).normalize();
        double speed = Math.max(1.2, proj.getDeltaMovement().length() * 1.3);

        // Claim ownership for the boss so it harms players!
        proj.setOwner(boss);
        proj.setDeltaMovement(reflectDir.scale(speed));
        proj.hasImpulse = true;

        level.playSound(null, proj.getX(), proj.getY(), proj.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE,
                2.0f, 1.2f);
        level.playSound(null, proj.getX(), proj.getY(), proj.getZ(), SoundEvents.ANVIL_PLACE, SoundSource.HOSTILE, 1.5f,
                1.5f);
        level.sendParticles(ParticleTypes.FLASH, proj.getX(), proj.getY(), proj.getZ(), 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.CRIT, proj.getX(), proj.getY(), proj.getZ(), 15, 0.3, 0.3, 0.3, 0.1);
    }

    private void tickGroundSlam(ServerLevel level) {
        groundSlamTelegraphTimer--;

        // Render 8m expanding dust ring & ground crack particles
        Vec3 bossPos = boss.position();
        ParticleUtils.renderCircle(level, ParticleTypes.CRIT, bossPos, 8.0, 48, 0.05);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.TUFF.defaultBlockState()),
                boss.getX(), boss.getY() + 0.2, boss.getZ(), 20, 2.0, 0.3, 2.0, 0.1);

        if (groundSlamTelegraphTimer <= 0) {
            isGroundSlamActive = false;

            // Execute Slam Impact Audio & Visual Explosion
            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER,
                    SoundSource.HOSTILE, 2.5f, 0.5f);
            level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.GENERIC_EXPLODE,
                    SoundSource.HOSTILE, 2.0f, 0.6f);

            // Trigger 8-block expanding block display shockwave ripple outwards from boss
            // position
            com.nhatbh.basedefensev2.boss.utils.ShockwaveEffect.createRipple(level, boss.blockPosition(), 8, 40, boss);
        }
    }

    private void renderHardenedShieldParticles(ServerLevel level) {
        Vec3 head = boss.getEyePosition().add(0, 0.4, 0);
        long tick = boss.tickCount;
        double radius = 1.4;
        int count = 8;
        double angleStep = 360.0 / count;

        for (int i = 0; i < count; i++) {
            double angleRad = Math.toRadians((i * angleStep) + (tick * 6));
            double dx = Math.cos(angleRad) * radius;
            double dz = Math.sin(angleRad) * radius;
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.TUFF.defaultBlockState()),
                    head.x + dx, head.y, head.z + dz, 1, 0, 0, 0, 0.01);
        }
    }

    public StoneSpikeManager getSpikeManager() {
        return spikeManager;
    }

    public boolean isHardened() {
        return hardenedTimer > 0;
    }

    public boolean isShattered() {
        return shatteredTimer > 0;
    }

    public void clear() {
        spikeManager.clear();
        BossResourceBarRegistry.unregisterBar(boss);
    }
}
