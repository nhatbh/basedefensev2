package com.nhatbh.basedefensev2.boss.impl.generic.active;

import com.nhatbh.basedefensev2.boss.impl.testboss.BossSkillHelper;
import com.nhatbh.basedefensev2.boss.skills.ActiveSequence;
import com.nhatbh.basedefensev2.boss.utils.HitboxUtils;
import com.nhatbh.basedefensev2.boss.utils.ParticleUtils;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class FlamingDashSkill {

    public static ActiveSequence create() {
        return ActiveSequence.builder("flaming_dash")
                // Phase 1: Charge Up & Lock Target Location (Immobile on Ground)
                .step("charge_up", 25)
                .onStart(ctx -> {
                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                            SoundEvents.ZOMBIE_INFECT, SoundSource.HOSTILE, 2.0f, 0.8f);

                    LivingEntity target = BossSkillHelper.getFurthestTarget(ctx, 64.0);
                    Vec3 lockedTargetPos;
                    if (target != null) {
                        lockedTargetPos = target.position();
                    } else {
                        lockedTargetPos = ctx.boss().position()
                                .add(ctx.boss().getLookAngle().multiply(1, 0, 1).normalize().scale(15.0));
                    }
                    ctx.data().put("locked_target_pos", lockedTargetPos);
                })
                .onTick(ctx -> {
                    // Boss is immobile while charging on ground
                    ctx.boss().addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 255, false, false));

                    Vec3 targetPos = (Vec3) ctx.data().get("locked_target_pos");
                    if (targetPos != null) {
                        // Face locked target position
                        Vec3 delta = targetPos.subtract(ctx.boss().position()).multiply(1, 0, 1);
                        if (delta.lengthSqr() > 0.001) {
                            Vec3 dir = delta.normalize();
                            float yaw = (float) (Math.atan2(-dir.x, dir.z) * (180 / Math.PI));
                            ctx.boss().setYRot(yaw);
                            ctx.boss().setYHeadRot(yaw);
                            ctx.boss().setYBodyRot(yaw);
                        }

                        // Draw line indicator from boss to locked target position
                        if (ctx.boss().level() instanceof ServerLevel level) {
                            Vec3 start = ctx.boss().position().add(0, 0.5, 0);
                            ParticleUtils.renderLine(level, ParticleTypes.FLAME, start, targetPos.add(0, 0.2, 0), 16,
                                    0.05);
                            ParticleUtils.renderCircle(level, ParticleTypes.LAVA, targetPos.add(0, 0.1, 0), 1.5, 12,
                                    0.0);
                        }
                    }
                })

                // Phase 2: Leap Airborne
                .step("leap", 15)
                .onStart(ctx -> {
                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                            SoundEvents.ENDER_DRAGON_FLAP, SoundSource.HOSTILE, 2.0f, 1.2f);
                    Entity mover = BossSkillHelper.getMovementEntity(ctx);
                    mover.setDeltaMovement(0, 0.9, 0); // Launch up into air
                })
                .onTick(ctx -> {
                    Vec3 targetPos = (Vec3) ctx.data().get("locked_target_pos");
                    if (targetPos != null) {
                        // Face locked target position looking down at angle
                        Vec3 delta = targetPos.subtract(ctx.boss().position());
                        if (delta.lengthSqr() > 0.001) {
                            Vec3 dir = delta.normalize();
                            float yaw = (float) (Math.atan2(-dir.x, dir.z) * (180 / Math.PI));
                            float pitch = (float) (-Math.asin(dir.y) * (180 / Math.PI));
                            ctx.boss().setYRot(yaw);
                            ctx.boss().setYHeadRot(yaw);
                            ctx.boss().setYBodyRot(yaw);
                            ctx.boss().setXRot(pitch);
                        }

                        // Continue line indicator from airborne boss down to target position
                        if (ctx.boss().level() instanceof ServerLevel level) {
                            Vec3 start = ctx.boss().position().add(0, 0.5, 0);
                            ParticleUtils.renderLine(level, ParticleTypes.FLAME, start, targetPos.add(0, 0.2, 0), 16,
                                    0.05);
                            level.sendParticles(ParticleTypes.SMOKE, ctx.boss().getX(), ctx.boss().getY(),
                                    ctx.boss().getZ(), 5, 0.3, 0.3, 0.3, 0.02);
                        }
                    }
                })

                // Phase 3: Angular Downward Dash
                .step("dash", 20)
                .onStart(ctx -> {
                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                            SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 2.5f, 0.8f);
                    ctx.data().put("dash_hits", new ArrayList<UUID>());

                    Vec3 targetPos = (Vec3) ctx.data().get("locked_target_pos");
                    if (targetPos != null) {
                        Vec3 dashDir = targetPos.subtract(ctx.boss().position()).normalize();
                        ctx.data().put("dash_dir", dashDir);
                    }
                })
                .onTick(ctx -> {
                    Vec3 dir = (Vec3) ctx.data().get("dash_dir");
                    if (dir != null) {
                        Entity mover = BossSkillHelper.getMovementEntity(ctx);
                        mover.setDeltaMovement(dir.scale(2.5)); // Fast angular downward dash

                        @SuppressWarnings("unchecked")
                        List<UUID> hitTargets = (List<UUID>) ctx.data().get("dash_hits");
                        List<Player> targets = HitboxUtils.getEntitiesInCircle(
                                ctx.boss().level(),
                                Player.class, ctx.boss().position(), 3.0,
                                e -> e.isAlive() && !hitTargets.contains(e.getUUID()));

                        for (Player target : targets) {
                            hitTargets.add(target.getUUID());

                            // Clear beneficial effects before applying damage
                            BossSkillHelper.clearBeneficialEffects(target);

                            // Deal damage: half max HP (40%) + half flat (12.0) = 24.0 total damage on 30 HP player
                            float trueDamage = (target.getMaxHealth() * 0.40f) + 12.0f;
                            target.hurt(ctx.boss().level().damageSources().genericKill(), trueDamage);

                            // Inflict Nausea (Confusion) for 10 seconds (200 ticks)
                            target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200, 0));

                            // Knockback
                            target.setDeltaMovement(dir.scale(1.5).add(0, 0.4, 0));
                            target.hurtMarked = true;

                            ctx.boss().level().playSound(null, target.getX(), target.getY(), target.getZ(),
                                    SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.5f, 1.2f);
                        }

                        if (ctx.boss().level() instanceof ServerLevel level) {
                            // Heavy Flaming Dash Trail
                            level.sendParticles(ParticleTypes.FLAME, ctx.boss().getX(), ctx.boss().getY() + 0.5,
                                    ctx.boss().getZ(), 10, 0.4, 0.4, 0.4, 0.05);
                            level.sendParticles(ParticleTypes.LAVA, ctx.boss().getX(), ctx.boss().getY() + 0.5,
                                    ctx.boss().getZ(), 3, 0.2, 0.2, 0.2, 0.0);
                        }
                    }
                })

                // Phase 4: Ground Impact Recovery
                .step("recovery", 15)
                .onStart(ctx -> {
                    BossSkillHelper.stopMovement(ctx);
                    if (ctx.boss().level() instanceof ServerLevel level) {
                        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, ctx.boss().getX(), ctx.boss().getY(),
                                ctx.boss().getZ(), 1, 0, 0, 0, 0);
                        ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                                SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 2.0f, 0.8f);
                    }
                })
                .onTick(BossSkillHelper::stopMovement)
                .build();
    }
}
