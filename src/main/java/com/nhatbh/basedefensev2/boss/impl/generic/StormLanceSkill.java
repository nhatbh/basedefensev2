package com.nhatbh.basedefensev2.boss.impl.generic;

import com.nhatbh.basedefensev2.boss.impl.testboss.BossSkillHelper;
import com.nhatbh.basedefensev2.boss.skills.ActiveSequence;
import com.nhatbh.basedefensev2.boss.utils.HitboxUtils;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
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

public class StormLanceSkill {

    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor();

    public static ActiveSequence create() {
        AtomicReference<Player> targetRef = new AtomicReference<>();

        return ActiveSequence.builder("storm_lance")
                // Phase 1: 1.0-Second Wind-Up & Target Lock (20 Ticks)
                .step("wind_up", 20)
                .onStart(ctx -> {
                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                            SoundEvents.TRIDENT_THUNDER, SoundSource.HOSTILE, 2.0f, 0.8f);
                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                            SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 1.8f, 1.5f);

                    // Lock onto a random player in range (100 blocks)
                    List<Player> nearbyPlayers = HitboxUtils.getEntitiesInCircle(
                            ctx.boss().level(), Player.class, ctx.boss().position(), 100.0, Player::isAlive);

                    if (!nearbyPlayers.isEmpty()) {
                        Collections.shuffle(nearbyPlayers);
                        targetRef.set(nearbyPlayers.get(0));
                    }
                })
                .onTick(ctx -> {
                    // Immobile while winding up
                    ctx.boss().addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 255, false, false));
                    BossSkillHelper.stopMovement(ctx);

                    Player target = targetRef.get();
                    if (target != null) {
                        ctx.boss().lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
                    }

                    if (ctx.boss().level() instanceof ServerLevel level) {
                        Vec3 origin = ctx.boss().getEyePosition();
                        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, origin.x, origin.y, origin.z, 10, 0.5, 0.5, 0.5, 0.1);
                        level.sendParticles(ParticleTypes.FLASH, origin.x, origin.y, origin.z, 1, 0, 0, 0, 0);
                    }
                })

                // Phase 2: Launch Storm Lance (1 Tick Execution)
                .step("launch", 1)
                .onStart(ctx -> {
                    if (ctx.boss().level() instanceof ServerLevel level) {
                        Vec3 startPos = ctx.boss().getEyePosition();
                        Vec3 targetPos;

                        Player target = targetRef.get();
                        if (target != null && target.isAlive()) {
                            targetPos = target.getEyePosition();
                        } else {
                            targetPos = startPos.add(ctx.boss().getLookAngle().scale(10.0));
                        }

                        Vec3 dir = targetPos.subtract(startPos);
                        if (dir.lengthSqr() < 0.001) {
                            dir = ctx.boss().getLookAngle();
                        } else {
                            dir = dir.normalize();
                        }

                        // Play throw sound
                        level.playSound(null, startPos.x, startPos.y, startPos.z,
                                SoundEvents.TRIDENT_THROW, SoundSource.HOSTILE, 2.0f, 0.6f);

                        // Fire 50 blocks/sec trajectory
                        fireStormLance(level, ctx.boss(), startPos, dir);
                    }
                })
                .build();
    }

    private static void fireStormLance(ServerLevel level, LivingEntity sourceBoss, Vec3 startPos, Vec3 dir) {
        double speed = 1.0; // 1.0 block per step
        int maxSteps = 100; // 100 blocks max range
        long stepDelayMs = 20L; // 20ms per step = 50 blocks/sec travel speed!

        List<UUID> hitEntities = new ArrayList<>();

        for (int i = 1; i <= maxSteps; i++) {
            final int step = i;

            SCHEDULER.schedule(() -> {
                level.getServer().execute(() -> {
                    Vec3 currentPos = startPos.add(dir.scale(step * speed));

                    // Dense electric, glowing, and lightning particles along trajectory
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, currentPos.x, currentPos.y, currentPos.z, 4, 0.1, 0.1, 0.1, 0.05);
                    level.sendParticles(ParticleTypes.END_ROD, currentPos.x, currentPos.y, currentPos.z, 2, 0.05, 0.05, 0.05, 0.01);

                    // Spawn lightning strikes every 3 steps along the way!
                    if (step % 3 == 0) {
                        LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(level);
                        if (lightning != null) {
                            lightning.moveTo(currentPos);
                            lightning.setVisualOnly(true);
                            level.addFreshEntity(lightning);
                        }

                        level.playSound(null, currentPos.x, currentPos.y, currentPos.z,
                                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 1.2f, 1.5f);
                    }

                    // Check player collisions along the lance path (passes through walls & entities)
                    AABB hitBox = new AABB(currentPos.x - 2.0, currentPos.y - 2.0, currentPos.z - 2.0,
                                           currentPos.x + 2.0, currentPos.y + 2.0, currentPos.z + 2.0);

                    level.getEntitiesOfClass(Player.class, hitBox).forEach(player -> {
                        if (player.isAlive() && !player.isCreative() && !player.isSpectator() && !hitEntities.contains(player.getUUID())) {
                            hitEntities.add(player.getUUID()); // Pass through players while dealing 1 hit per player

                            // Clear beneficial effects before applying damage
                            com.nhatbh.basedefensev2.boss.impl.testboss.BossSkillHelper.clearBeneficialEffects(player);

                            // Deal damage: half max HP (15%) + half flat (4.5) = 9.0 total damage on 30 HP player
                            float damage = (player.getMaxHealth() * 0.15f) + 4.5f;
                            player.hurt(level.damageSources().mobAttack(sourceBoss != null ? sourceBoss : player), damage);

                            // Lightning impact visuals on hit
                            LightningBolt hitLightning = EntityType.LIGHTNING_BOLT.create(level);
                            if (hitLightning != null) {
                                hitLightning.moveTo(player.position());
                                hitLightning.setVisualOnly(true);
                                level.addFreshEntity(hitLightning);
                            }

                            level.sendParticles(ParticleTypes.FLASH, player.getX(), player.getY() + 1.0, player.getZ(), 1, 0, 0, 0, 0);
                            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.4, 0.4, 0.4, 0.2);
                        }
                    });
                });
            }, step * stepDelayMs, TimeUnit.MILLISECONDS);
        }
    }
}
