package com.nhatbh.basedefensev2.boss.impl.generic.active;

import com.nhatbh.basedefensev2.boss.impl.testboss.BossSkillHelper;
import com.nhatbh.basedefensev2.boss.skills.ActiveSequence;
import com.nhatbh.basedefensev2.boss.utils.HitboxUtils;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class ConcentratedLaserSkill {

    public static ActiveSequence create() {
        AtomicReference<Player> targetPlayerRef = new AtomicReference<>(null);
        AtomicReference<Vec3> currentBeamDirRef = new AtomicReference<>(null);

        return ActiveSequence.builder("concentrated_laser")
                // Phase 1: 1.0-Second Laser Concentration Wind-Up (20 Ticks)
                .step("concentrate_windup", 20)
                .onStart(ctx -> {
                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                            SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 2.0f, 1.5f);
                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                            SoundEvents.CONDUIT_ATTACK_TARGET, SoundSource.HOSTILE, 1.8f, 1.2f);

                    // Lock onto a random player within 100 blocks laser range
                    List<Player> nearbyPlayers = HitboxUtils.getEntitiesInCircle(
                            ctx.boss().level(), Player.class, ctx.boss().position(), 100.0, BossSkillHelper::isValidTarget);

                    if (!nearbyPlayers.isEmpty()) {
                        Collections.shuffle(nearbyPlayers);
                        Player target = nearbyPlayers.get(0);
                        targetPlayerRef.set(target);

                        Vec3 origin = ctx.boss().getEyePosition();
                        Vec3 initialDir = target.getEyePosition().subtract(origin).normalize();
                        currentBeamDirRef.set(initialDir);
                    } else {
                        targetPlayerRef.set(null);
                        currentBeamDirRef.set(ctx.boss().getLookAngle().normalize());
                    }
                })
                .onTick(ctx -> {
                    // Immobile during charging
                    ctx.boss().addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 255, false, false));
                    BossSkillHelper.stopMovement(ctx);

                    // Apply Glowing effect to targeted player during windup telegraph
                    Player target = targetPlayerRef.get();
                    if (target != null && target.isAlive()) {
                        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 30, 0, false, false));
                    }

                    if (ctx.boss().level() instanceof ServerLevel level) {
                        Vec3 origin = ctx.boss().getEyePosition();
                        // Concentrating energy glow at boss eye position
                        level.sendParticles(ParticleTypes.END_ROD, origin.x, origin.y, origin.z, 8, 0.3, 0.3, 0.3,
                                0.05);
                        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, origin.x, origin.y, origin.z, 5, 0.2, 0.2,
                                0.2, 0.02);
                    }
                })

                // Phase 2: Fire Continuous Slow-Tracking Laser Beam for 5 Seconds (100 Ticks)
                .step("fire_laser_stream", 100)
                .onStart(ctx -> {
                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                            SoundEvents.GUARDIAN_ATTACK, SoundSource.HOSTILE, 2.0f, 0.8f);
                })
                .onTick(ctx -> {
                    // Slow rotation during firing
                    ctx.boss().addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 2, false, false));

                    Player target = targetPlayerRef.get();
                    if (target != null && target.isAlive()) {
                        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 30, 0, false, false));
                    }

                    if (ctx.boss().level() instanceof ServerLevel level) {
                        Vec3 currentDir = currentBeamDirRef.get();
                        Vec3 origin = ctx.boss().getEyePosition();

                        // Slowly track target player position if alive and in world
                        if (target != null && target.isAlive() && target.level() == ctx.boss().level()) {
                            Vec3 desiredDir = target.getEyePosition().subtract(origin).normalize();
                            // Slow turning rate (lerp 0.06 per tick) allowing players to dodge by running
                            // sideways
                            currentDir = currentDir.scale(0.94).add(desiredDir.scale(0.06)).normalize();
                            currentBeamDirRef.set(currentDir);
                        }

                        // Play ambient beam hum every 5 ticks
                        if (ctx.getTicks() % 5 == 0) {
                            level.playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                                    SoundEvents.BEACON_AMBIENT, SoundSource.HOSTILE, 1.5f, 1.8f);
                        }

                        // Shoot high-velocity particles directly out from boss along laser vector
                        // (count = 0 for velocity mode)
                        for (int p = 0; p < 4; p++) {
                            double speedMult = 1.5 + p * 0.5;
                            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, origin.x, origin.y, origin.z, 0,
                                    currentDir.x * speedMult, currentDir.y * speedMult, currentDir.z * speedMult, 1.0);
                            level.sendParticles(ParticleTypes.CRIT, origin.x, origin.y, origin.z, 0,
                                    currentDir.x * speedMult, currentDir.y * speedMult, currentDir.z * speedMult, 1.0);
                            level.sendParticles(ParticleTypes.END_ROD, origin.x, origin.y, origin.z, 0,
                                    currentDir.x * speedMult, currentDir.y * speedMult, currentDir.z * speedMult, 1.0);
                        }

                        // Shoot continuous particle laser beam out to 100 blocks
                        double range = 100.0;
                        double stepSize = 0.5;

                        for (double d = 0.5; d <= range; d += stepSize) {
                            Vec3 beamPoint = origin.add(currentDir.scale(d));

                            // Denser glowing beam particles
                            level.sendParticles(ParticleTypes.END_ROD, beamPoint.x, beamPoint.y, beamPoint.z, 1, 0.05,
                                    0.05, 0.05, 0.01);
                            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, beamPoint.x, beamPoint.y, beamPoint.z, 1,
                                    0.05, 0.05, 0.05, 0.01);
                            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, beamPoint.x, beamPoint.y, beamPoint.z, 1,
                                    0.02, 0.02, 0.02, 0.01);

                            // Check entity collisions every 1 block step
                            if (d % 1.0 < stepSize) {
                                AABB hurtBox = new AABB(beamPoint.x - 0.75, beamPoint.y - 0.75, beamPoint.z - 0.75,
                                        beamPoint.x + 0.75, beamPoint.y + 0.75, beamPoint.z + 0.75);

                                level.getEntitiesOfClass(Player.class, hurtBox).forEach(entity -> {
                                    if (BossSkillHelper.canBeHitBySkill(entity)) {
                                        if (entity.invulnerableTime > 2) {
                                            entity.invulnerableTime = 0;
                                        }

                                        float damage = (entity.getMaxHealth() * 0.05f) + 1.5f;
                                        entity.hurt(level.damageSources().mobAttack(ctx.boss()), damage);
                                    }
                                });
                            }
                        }
                    }
                })
                .build();
    }
}
