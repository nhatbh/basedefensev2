package com.nhatbh.basedefensev2.boss.impl.testboss;

import com.nhatbh.basedefensev2.boss.skills.ActiveSequence;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

public class VoidSunderSkill {
    public static ActiveSequence create() {
        return ActiveSequence.builder("void_sunder")
                // Phase 1: Wide Horizontal Slash
                .step("horizontal_slash", 10)
                .onStart(ctx -> {
                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                            SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 2.0f, 0.7f);
                    BossSkillHelper.stopMovement(ctx);
                    
                    LivingEntity target = BossSkillHelper.getClosestTarget(ctx);
                    Vec3 dir = ctx.boss().getLookAngle().multiply(1, 0, 1).normalize();
                    if (target != null) {
                        dir = target.position().subtract(ctx.boss().position()).multiply(1, 0, 1).normalize();
                    }
                    ctx.data().put("sunder_dir", dir);
                    ctx.data().put("sweep_tick", 0);
                    ctx.data().put("sweep_hits", new java.util.ArrayList<java.util.UUID>());
                })
                .onTick(ctx -> {
                    BossSkillHelper.stopMovement(ctx);
                    Vec3 dir = (Vec3) ctx.data().get("sunder_dir");
                    BossSkillHelper.performDynamicSweep(ctx, dir, -90.0, 90.0, 7.0, 6.0f, 12.0f, 10, true, ParticleTypes.SWEEP_ATTACK);
                })

                // Phase 2: Reverse Spin
                .step("reverse_spin", 15)
                .onStart(ctx -> {
                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                            SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 2.0f, 0.4f);
                    ctx.data().put("sweep_tick", 0);
                    ctx.data().put("sweep_hits", new java.util.ArrayList<java.util.UUID>());
                })
                .onTick(ctx -> {
                    BossSkillHelper.stopMovement(ctx);
                    Vec3 dir = (Vec3) ctx.data().get("sunder_dir");
                    // Spin in reverse (90 down to -270)
                    BossSkillHelper.performDynamicSweep(ctx, dir, 90.0, -270.0, 6.0, 6.0f, 12.0f, 15, true, ParticleTypes.SWEEP_ATTACK);
                })

                // Phase 3: Massive Frontal Shockwave
                .step("shockwave", 25)
                .onStart(ctx -> {
                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                            SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 2.0f, 0.2f);
                })
                .onTick(ctx -> {
                    BossSkillHelper.stopMovement(ctx);
                    if (ctx.getTicks() % 2 == 0) {
                        Vec3 dir = (Vec3) ctx.data().get("sunder_dir");
                        if (dir != null && ctx.boss().level() instanceof ServerLevel level) {
                            double dist = ctx.getTicks() * 1.5;
                            Vec3 pos = ctx.boss().position().add(dir.scale(dist));
                            
                            level.sendParticles(ParticleTypes.SONIC_BOOM, pos.x, pos.y + 0.1, pos.z, 1, 0, 0, 0, 0);
                            level.sendParticles(ParticleTypes.SOUL, pos.x, pos.y + 0.1, pos.z, 10, 0.5, 0.2, 0.5, 0.05);

                            level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(pos.subtract(2, 1, 2), pos.add(2, 3, 2))).forEach(e -> {
                                if (e != ctx.boss()) {
                                    float damage = BossSkillHelper.calculateMixedDamage(ctx, e, 10.0f, 25.0f);
                                    e.hurt(ctx.boss().damageSources().magic(), damage);
                                    e.setDeltaMovement(e.getDeltaMovement().add(0, 0.3, 0));
                                }
                            });
                        }
                    }
                })
                .build();
    }
}
