package com.nhatbh.basedefensev2.boss.impl.generic;

import com.nhatbh.basedefensev2.boss.impl.testboss.BossSkillHelper;
import com.nhatbh.basedefensev2.boss.skills.ActiveSequence;
import com.nhatbh.basedefensev2.boss.utils.HitboxUtils;
import com.nhatbh.basedefensev2.boss.utils.ParticleUtils;
import com.nhatbh.basedefensev2.boss.utils.ShockwaveEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class ExplosiveDropkickSkill {

    public static ActiveSequence create() {
        AtomicReference<Vec3> targetDirRef = new AtomicReference<>(new Vec3(1, 0, 0));

        return ActiveSequence.builder("explosive_dropkick")
                // Phase 1: 1.0-Second Charge Wind-Up (20 Ticks) with Magic Counter Window
                .parryStep("wind_up", 20)
                .counter(ActiveSequence.CounterType.MAGIC, 0, 20)
                .onCountered((ctx, event) -> {
                    // Magic counter success: cancel sequence and drop boss back to ground
                    ctx.boss().removeEffect(MobEffects.LEVITATION);
                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                            SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 2.0f, 0.5f);
                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                            SoundEvents.TRIDENT_THUNDER, SoundSource.HOSTILE, 1.5f, 1.5f);
                    ctx.stopSequence();
                })
                .onStart(ctx -> {
                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                            SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.HOSTILE, 2.0f, 0.8f);
                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                            SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 1.8f, 0.6f);

                    // Find closest player within 35 blocks to aim the dropkick line
                    List<Player> nearbyPlayers = HitboxUtils.getEntitiesInCircle(
                            ctx.boss().level(), Player.class, ctx.boss().position(), 35.0, Player::isAlive);

                    Vec3 travelDir;
                    if (!nearbyPlayers.isEmpty()) {
                        Player closest = nearbyPlayers.get(0);
                        double minDist = ctx.boss().distanceToSqr(closest);
                        for (Player p : nearbyPlayers) {
                            double dist = ctx.boss().distanceToSqr(p);
                            if (dist < minDist) {
                                minDist = dist;
                                closest = p;
                            }
                        }
                        travelDir = closest.position().subtract(ctx.boss().position());
                    } else {
                        travelDir = ctx.boss().getLookAngle();
                    }

                    targetDirRef.set(new Vec3(travelDir.x, 0, travelDir.z).normalize());
                })
                .onTick(ctx -> {
                    // Boss hovers slightly and stays immobile horizontally while charging
                    ctx.boss().addEffect(new MobEffectInstance(MobEffects.LEVITATION, 10, 0, false, false));
                    ctx.boss().addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 255, false, false));
                    BossSkillHelper.stopMovement(ctx);

                    if (ctx.boss().level() instanceof ServerLevel level) {
                        Vec3 dir = targetDirRef.get();
                        // Render line indicator charging particles along the path
                        for (int i = 1; i <= 15; i += 2) {
                            Vec3 p = ctx.boss().position().add(dir.scale(i));
                            level.sendParticles(ParticleTypes.CRIT, p.x, p.y + 0.5, p.z, 2, 0.2, 0.2, 0.2, 0.02);
                        }

                        // Magic counter cyan/flame aura around boss
                        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, ctx.boss().getX(), ctx.boss().getY() + 0.5,
                                ctx.boss().getZ(), 4, 0.5, 0.5, 0.5, 0.02);
                        level.sendParticles(ParticleTypes.EXPLOSION, ctx.boss().getX(), ctx.boss().getY() + 0.5,
                                ctx.boss().getZ(), 2, 0.3, 0.3, 0.3, 0.05);
                    }
                })

                // Phase 2: Leap & Explosive Strike Down (15 Ticks)
                .step("dropkick_slam", 15)
                .onStart(ctx -> {
                    ctx.boss().removeEffect(MobEffects.LEVITATION);

                    // Launch boss forward and downward at target
                    Vec3 dir = targetDirRef.get();
                    ctx.boss().setDeltaMovement(dir.x * 1.6, -2.0, dir.z * 1.6);
                    ctx.boss().hurtMarked = true;

                    if (ctx.boss().level() instanceof ServerLevel level) {
                        BlockPos bossPos = ctx.boss().blockPosition();

                        // Heavy slam sounds
                        level.playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                                SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 2.0f, 0.8f);
                        level.playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                                SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.8f, 0.5f);

                        // Slam explosion particles
                        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, ctx.boss().getX(), ctx.boss().getY(),
                                ctx.boss().getZ(), 1, 0, 0, 0, 0);

                        // Trigger straight 5-block wide, 40-block long expanding shockwave line!
                        // Drags hit players along the line and inflicts damage per second!
                        ShockwaveEffect.createLineRipple(level, bossPos, dir, 40, 5, 40L, ctx.boss());
                    }
                })
                .build();
    }
}
