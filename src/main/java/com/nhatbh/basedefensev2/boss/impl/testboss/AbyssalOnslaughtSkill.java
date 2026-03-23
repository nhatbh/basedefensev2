package com.nhatbh.basedefensev2.boss.impl.testboss;

import com.nhatbh.basedefensev2.boss.skills.ActiveSequence;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

public class AbyssalOnslaughtSkill {
    public static ActiveSequence create() {
        return ActiveSequence.builder("abyssal_onslaught")
                // Step 1: Rapid Dash-Slash
                .step("dash_slash", 15)
                .onStart(ctx -> {
                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                            SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 2.0f, 0.4f);

                    LivingEntity target = BossSkillHelper.getClosestTarget(ctx);
                    Vec3 dir = ctx.boss().getLookAngle().multiply(1, 0, 1).normalize();
                    if (target != null) {
                        dir = target.position().subtract(ctx.boss().position()).multiply(1, 0, 1).normalize();
                    }
                    ctx.data().put("onslaught_dir", dir);
                    ctx.data().put("hit_targets", new java.util.ArrayList<java.util.UUID>());
                })
                .onTick(ctx -> {
                    Vec3 dir = (Vec3) ctx.data().get("onslaught_dir");
                    if (dir != null) {
                        ctx.boss().setDeltaMovement(dir.scale(1.5));

                        @SuppressWarnings("unchecked")
                        java.util.List<java.util.UUID> hitTargets = (java.util.List<java.util.UUID>) ctx.data()
                                .get("hit_targets");
                        com.nhatbh.basedefensev2.boss.utils.HitboxUtils
                                .getEntitiesInCircle(ctx.boss().level(), LivingEntity.class, ctx.boss().position(), 3.0,
                                        e -> e != ctx.boss() && !hitTargets.contains(e.getUUID()))
                                .forEach(target -> {
                                    hitTargets.add(target.getUUID());
                                    float damage = BossSkillHelper.calculateMixedDamage(ctx, target, 5.0f, 10.0f);
                                    target.hurt(ctx.boss().damageSources().mobAttack(ctx.boss()), damage);
                                });

                        if (ctx.boss().level() instanceof ServerLevel level) {
                            level.sendParticles(ParticleTypes.SWEEP_ATTACK, ctx.boss().getX(), ctx.boss().getY() + 1,
                                    ctx.boss().getZ(), 1, 0, 0, 0, 0);
                        }
                    }
                })

                // Step 2: Rising Uppercut
                .step("uppercut", 10)
                .onStart(ctx -> {
                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                            SoundEvents.IRON_GOLEM_ATTACK, SoundSource.HOSTILE, 2.0f, 0.6f);

                    Vec3 dir = (Vec3) ctx.data().get("onslaught_dir");
                    if (ctx.boss().level() instanceof ServerLevel level) {
                        com.nhatbh.basedefensev2.boss.utils.HitboxUtils.getEntitiesInCircle(level, LivingEntity.class,
                                ctx.boss().position().add(dir.scale(1.5)), 3.0,
                                e -> e != ctx.boss()).forEach(target -> {
                                    float damage = BossSkillHelper.calculateMixedDamage(ctx, target, 5.0f, 10.0f);
                                    target.hurt(ctx.boss().damageSources().mobAttack(ctx.boss()), damage);
                                    target.setDeltaMovement(0, 1.2, 0); // Rising knockback
                                    target.hurtMarked = true;
                                });

                        // Higher pitch sound for uppercut
                        level.playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                                SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.0f, 1.5f);
                    }
                    ctx.boss().setDeltaMovement(0, 1.0, 0); // Boss also rises
                })

                // Step 3: Brutal Leaping Slam
                .step("leaping_slam", 20)
                .onStart(ctx -> {
                    ctx.data().put("slammed", false);
                })
                .onTick(ctx -> {
                    if (!(boolean) ctx.data().get("slammed")) {
                        ctx.boss().setDeltaMovement(ctx.boss().getDeltaMovement().add(0, -0.2, 0));
                        if (ctx.boss().onGround() || ctx.getTicks() > 15) {
                            ctx.data().put("slammed", true);
                            ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                                    SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 3.0f, 0.5f);

                            if (ctx.boss().level() instanceof ServerLevel level) {
                                com.nhatbh.basedefensev2.boss.utils.ParticleUtils.renderCircle(level,
                                        ParticleTypes.SONIC_BOOM, ctx.boss().position(), 6.0, 40, 0.1);
                                level.getEntitiesOfClass(LivingEntity.class, ctx.boss().getBoundingBox().inflate(6.0))
                                        .forEach(e -> {
                                            if (e != ctx.boss()) {
                                                float damage = BossSkillHelper.calculateMixedDamage(ctx, e, 15.0f,
                                                        30.0f);
                                                e.hurt(ctx.boss().damageSources().mobAttack(ctx.boss()), damage);
                                                e.setDeltaMovement(e.getDeltaMovement().add(0, 0.5, 0));
                                            }
                                        });
                            }
                        }
                    }
                })
                .build();
    }
}
