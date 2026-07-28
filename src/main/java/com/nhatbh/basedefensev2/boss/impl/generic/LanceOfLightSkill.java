package com.nhatbh.basedefensev2.boss.impl.generic;

import com.nhatbh.basedefensev2.boss.impl.testboss.BossSkillHelper;
import com.nhatbh.basedefensev2.boss.skills.ActiveSequence;
import com.nhatbh.basedefensev2.boss.utils.HitboxUtils;
import com.nhatbh.basedefensev2.effects.PetrificationEffect;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class LanceOfLightSkill {

    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor();

    public static ActiveSequence create() {
        AtomicReference<Player> targetPlayerRef = new AtomicReference<>(null);

        return ActiveSequence.builder("lance_of_light")
                // Phase 1: 1.0-Second Charge Wind-Up (20 Ticks)
                .step("wind_up", 20)
                .onStart(ctx -> {
                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                            SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 2.0f, 1.5f);
                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                            SoundEvents.TRIDENT_RIPTIDE_1, SoundSource.HOSTILE, 1.8f, 0.8f);

                    // Lock onto a random player within 40 blocks
                    List<Player> nearbyPlayers = HitboxUtils.getEntitiesInCircle(
                            ctx.boss().level(), Player.class, ctx.boss().position(), 40.0, Player::isAlive);

                    if (!nearbyPlayers.isEmpty()) {
                        Collections.shuffle(nearbyPlayers);
                        targetPlayerRef.set(nearbyPlayers.get(0));
                    } else {
                        targetPlayerRef.set(null);
                    }
                })
                .onTick(ctx -> {
                    // Immobile while charging
                    ctx.boss().addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 255, false, false));
                    BossSkillHelper.stopMovement(ctx);

                    if (ctx.boss().level() instanceof ServerLevel level) {
                        Vec3 origin = ctx.boss().getEyePosition();
                        level.sendParticles(ParticleTypes.END_ROD, origin.x, origin.y, origin.z, 6, 0.4, 0.4, 0.4,
                                0.05);
                        level.sendParticles(ParticleTypes.GLOW, origin.x, origin.y, origin.z, 4, 0.3, 0.3, 0.3, 0.02);
                        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, origin.x, origin.y, origin.z, 4, 0.3, 0.3,
                                0.3, 0.05);
                    }
                })

                // Phase 2: Fire 5 Lances of Light (50 Ticks Total, 1 Lance every 10 Ticks)
                .step("fire_lances", 50)
                .onTick(ctx -> {
                    // Slow movement during firing
                    ctx.boss().addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 2, false, false));

                    int tick = ctx.getTicks();
                    // Fire 1 lance every 10 ticks (ticks 0, 10, 20, 30, 40)
                    if (tick % 10 == 0 && ctx.boss().level() instanceof ServerLevel level) {
                        Player target = targetPlayerRef.get();
                        Vec3 startPos = ctx.boss().getEyePosition();
                        Vec3 launchDir;

                        // Retrack player location on each launch!
                        if (target != null && target.isAlive() && target.level() == ctx.boss().level()) {
                            launchDir = target.getEyePosition().subtract(startPos).normalize();
                        } else {
                            launchDir = ctx.boss().getLookAngle().normalize();
                        }

                        level.playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                                SoundEvents.TRIDENT_THROW, SoundSource.HOSTILE, 2.0f, 1.4f);
                        level.playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                                SoundEvents.ITEM_BREAK, SoundSource.HOSTILE, 1.5f, 2.0f);

                        // Launch fast, wall/player-penetrating Lance of Light
                        fireSingleLance(level, ctx.boss(), startPos, launchDir);
                    }
                })
                .build();
    }

    private static void fireSingleLance(ServerLevel level, LivingEntity sourceBoss, Vec3 startPos, Vec3 dir) {
        double speed = 1.0; // 1 block per step
        int maxSteps = 45; // 45 blocks max distance
        long stepDelayMs = 40L; // 40ms per step = 25 blocks/sec traveling speed

        List<UUID> hitEntities = new ArrayList<>();

        for (int step = 1; step <= maxSteps; step++) {
            final int currentStep = step;
            SCHEDULER.schedule(() -> {
                level.getServer().execute(() -> {
                    Vec3 currentPos = startPos.add(dir.scale(currentStep * speed));

                    // Glowing particle stream along lance trajectory
                    level.sendParticles(ParticleTypes.END_ROD, currentPos.x, currentPos.y, currentPos.z, 3, 0.1, 0.1,
                            0.1, 0.02);
                    level.sendParticles(ParticleTypes.GLOW, currentPos.x, currentPos.y, currentPos.z, 2, 0.1, 0.1, 0.1,
                            0.01);
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, currentPos.x, currentPos.y, currentPos.z, 2, 0.1,
                            0.1, 0.1, 0.05);
                    level.sendParticles(ParticleTypes.CRIT, currentPos.x, currentPos.y, currentPos.z, 2, 0.1, 0.1, 0.1,
                            0.05);

                    // Passes through walls & players! Collision check around current lance position
                    AABB hitBox = new AABB(currentPos.x - 1.2, currentPos.y - 1.2, currentPos.z - 1.2,
                            currentPos.x + 1.2, currentPos.y + 1.2, currentPos.z + 1.2);

                    level.getEntitiesOfClass(Player.class, hitBox).forEach(entity -> {
                        if (entity.isAlive() && !entity.isCreative() && !entity.isSpectator()
                                && !hitEntities.contains(entity.getUUID())) {
                            hitEntities.add(entity.getUUID()); // Pass through players while dealing 1 hit per lance

                            // Deal damage: half max HP (7.5%) + half flat (2.25) = 4.5 total damage on 30 HP player
                            float damage = (entity.getMaxHealth() * 0.075f) + 2.25f;
                            entity.hurt(level.damageSources().mobAttack(sourceBoss != null ? sourceBoss : entity),
                                    damage);

                            // Apply Petrification for 15 seconds (300 ticks), advancing stage if already present!
                            PetrificationEffect.addStage(entity, 300, "Hit by Lance of Light Beam");

                            // Impact particle burst on hit
                            level.sendParticles(ParticleTypes.CRIT, entity.getX(), entity.getY() + 1.0, entity.getZ(),
                                    10, 0.3, 0.3, 0.3, 0.1);
                            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                                    SoundEvents.TRIDENT_HIT, SoundSource.HOSTILE, 1.5f, 1.2f);
                        }
                    });
                });
            }, step * stepDelayMs, TimeUnit.MILLISECONDS);
        }
    }
}
