package com.nhatbh.basedefensev2.boss.impl.generic;

import com.nhatbh.basedefensev2.boss.utils.ParticleUtils;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class VengeanceActive {

    private final LivingEntity boss;
    private int ticksRemaining = 140; // 7 seconds wind-up (7s * 20 ticks)
    private static final int TOTAL_WINDUP_TICKS = 140;
    private boolean active = true;

    public static com.nhatbh.basedefensev2.boss.skills.ActiveSequence create() {
        return com.nhatbh.basedefensev2.boss.skills.ActiveSequence.builder("vengeance_active")
                .step("charge", 140)
                .onStart(ctx -> new VengeanceActive(ctx.boss()))
                .onTick(ctx -> {
                    // Visual/audio ticks handled in VengeanceActive instance if desired
                })
                .build();
    }

    public VengeanceActive(LivingEntity boss) {
        this.boss = boss;
        if (boss.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 2.5f, 0.6f);
            serverLevel.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, 2.0f, 0.5f);
        }
    }

    public boolean tick() {
        if (!active || !boss.isAlive())
            return false;

        // Apply Slowness 255 effect to render boss immobile while charging Vengeance
        boss.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 255, false, false));

        if (boss.level() instanceof ServerLevel serverLevel) {
            Vec3 center = boss.position().add(0, boss.getBbHeight() * 0.5, 0);

            // Calculate progress (0.0 at start, 1.0 near finish)
            float progress = 1.0f - ((float) ticksRemaining / TOTAL_WINDUP_TICKS);

            // Inward-sucking smoke particles (subtle, non-obscuring density)
            if (ticksRemaining % 2 == 0) {
                int particleCount = 2 + (int) (progress * 3);
                double spawnRadius = 4.0 + (1.0 - progress) * 2.0;

                for (int i = 0; i < particleCount; i++) {
                    double angle = serverLevel.random.nextDouble() * 2 * Math.PI;
                    double verticalOffset = (serverLevel.random.nextDouble() - 0.5) * 1.5;

                    double px = center.x + spawnRadius * Math.cos(angle);
                    double py = center.y + verticalOffset;
                    double pz = center.z + spawnRadius * Math.sin(angle);

                    Vec3 dir = center.subtract(px, py, pz).normalize();
                    double speed = 0.25 + progress * 0.2;

                    serverLevel.sendParticles(ParticleTypes.SMOKE, px, py, pz, 0, dir.x, dir.y, dir.z, speed);
                    if (serverLevel.random.nextInt(3) == 0) {
                        serverLevel.sendParticles(ParticleTypes.FLAME, px, py, pz, 0, dir.x, dir.y, dir.z, speed * 0.6);
                    }
                }
            }

            // Periodic buildup sounds
            if (ticksRemaining % 20 == 0) {
                float pitch = 0.5f + (progress * 0.8f);
                serverLevel.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                        SoundEvents.BLAZE_AMBIENT, SoundSource.HOSTILE, 1.5f, pitch);
            }
        }

        ticksRemaining--;
        if (ticksRemaining <= 0) {
            executeShockwave();
            active = false;
            return false;
        }

        return true;
    }

    private void executeShockwave() {
        if (!(boss.level() instanceof ServerLevel serverLevel))
            return;

        Vec3 center = boss.position().add(0, boss.getBbHeight() * 0.5, 0);

        // Explosion sounds
        serverLevel.playSound(null, center.x, center.y, center.z,
                SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 4.0f, 0.6f);
        serverLevel.playSound(null, center.x, center.y, center.z,
                SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.HOSTILE, 3.0f, 0.8f);

        // Huge explosion emitter at epicenter
        serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y, center.z, 2, 0.5, 0.5, 0.5, 0.0);
        serverLevel.sendParticles(ParticleTypes.FLASH, center.x, center.y, center.z, 4, 0.5, 0.5, 0.5, 0.0);

        // Particle outburst explosion expanding outward using velocity
        int burstCount = 60;
        for (int i = 0; i < burstCount; i++) {
            double angle = serverLevel.random.nextDouble() * 2 * Math.PI;
            double elevation = (serverLevel.random.nextDouble() - 0.2) * Math.PI * 0.5;
            double speed = 0.6 + serverLevel.random.nextDouble() * 0.9;

            double vx = Math.cos(angle) * Math.cos(elevation);
            double vy = Math.sin(elevation);
            double vz = Math.sin(angle) * Math.cos(elevation);

            serverLevel.sendParticles(ParticleTypes.FLAME, center.x, center.y, center.z, 0, vx, vy, vz, speed);
            serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, center.x, center.y, center.z, 0, vx, vy, vz,
                    speed * 0.7);
            if (i % 2 == 0) {
                serverLevel.sendParticles(ParticleTypes.LAVA, center.x, center.y, center.z, 0, vx, vy, vz, speed * 0.5);
            }
        }

        // Visual expansion ring on the ground
        Vec3 groundCenter = boss.position().add(0, 0.2, 0);
        for (double r = 1.0; r <= 14.0; r += 2.0) {
            ParticleUtils.renderCircle(serverLevel, ParticleTypes.FLAME, groundCenter, r, (int) (r * 6), 0.1);
        }

        AABB box = new AABB(center.x - 20, center.y - 5, center.z - 20, center.x + 20, center.y + 5, center.z + 20);
        List<Player> players = serverLevel.getEntitiesOfClass(Player.class, box);

        DamageSource damageSource = serverLevel.damageSources().mobAttack(boss);

        for (Player player : players) {
            if (!com.nhatbh.basedefensev2.boss.impl.testboss.BossSkillHelper.canBeHitBySkill(player))
                continue;

            double dist = player.position().distanceTo(boss.position());
            float maxHp = player.getMaxHealth();

            if (dist <= 4.0) {
                // 0-4m: Lethal (9999 true damage)
                player.hurt(serverLevel.damageSources().genericKill(), 9999.0f);
            } else if (dist <= 8.0) {
                // 4-8m: 60% max HP damage
                player.hurt(damageSource, maxHp * 0.60f);
            } else if (dist <= 12.0) {
                // 8-12m: 25% max HP damage
                player.hurt(damageSource, maxHp * 0.25f);
            } else if (dist <= 16.0) {
                // 12-16m: 5% max HP chip
                player.hurt(damageSource, maxHp * 0.05f);
            }

            if (dist <= 16.0) {
                // Knockback applied at all distances, strongest at center
                double knockbackScale = Math.max(0.8, 3.0 - (dist / 16.0) * 2.2);
                Vec3 dir = player.position().subtract(boss.position());
                if (dir.lengthSqr() < 0.0001) {
                    dir = new Vec3(0, 1, 0);
                } else {
                    dir = dir.normalize();
                }
                Vec3 knockback = dir.scale(knockbackScale).add(0, 0.4 + (knockbackScale * 0.1), 0);
                player.setDeltaMovement(player.getDeltaMovement().add(knockback));
                player.hurtMarked = true;
            }
        }
    }
}
