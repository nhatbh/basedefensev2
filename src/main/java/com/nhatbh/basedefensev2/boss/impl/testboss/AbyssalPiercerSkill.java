package com.nhatbh.basedefensev2.boss.impl.testboss;

import com.nhatbh.basedefensev2.boss.skills.ActiveSequence;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

public class AbyssalPiercerSkill {
    public static ActiveSequence create() {
        return ActiveSequence.builder("abyssal_piercer")
                // Step 1: Charge
                .step("charge", 40)
                .onStart(ctx -> {
                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                            SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 2.0f, 1.0f);
                    BossSkillHelper.broadcastMessage(ctx.boss(), "Focusing dark energy.");
                    BossSkillHelper.stopMovement(ctx);
                })
                .onTick(ctx -> {
                    LivingEntity target = BossSkillHelper.getFurthestTarget(ctx, 100.0);
                    if (target != null) {
                        Vec3 dir = target.position().add(0, target.getEyeHeight() * 0.5, 0)
                                .subtract(ctx.boss().position().add(0, ctx.boss().getEyeHeight(), 0)).normalize();
                        ctx.data().put("piercer_dir", dir);
                        if (ctx.boss().level() instanceof ServerLevel level) {
                            level.sendParticles(ParticleTypes.WITCH, ctx.boss().getX(),
                                    ctx.boss().getY() + ctx.boss().getEyeHeight(), ctx.boss().getZ(), 5, 0.1, 0.1, 0.1,
                                    0.05);
                        }
                    }
                })

                // Step 2: Fire Piercing Laser
                .step("fire_laser", 60)
                .onStart(ctx -> {
                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                            SoundEvents.WANDERING_TRADER_DISAPPEARED, SoundSource.HOSTILE, 2.0f, 0.5f);
                    BossSkillHelper.broadcastMessage(ctx.boss(), "Light, be gone!");
                    BossSkillHelper.stopMovement(ctx);
                })
                .onTick(ctx -> {
                    BossSkillHelper.stopMovement(ctx);
                    Vec3 dir = (Vec3) ctx.data().get("piercer_dir");
                    if (dir != null && ctx.boss().level() instanceof ServerLevel level) {
                        Vec3 startPos = ctx.boss().position().add(0, ctx.boss().getEyeHeight(), 0);

                        // Render particle beam
                        for (float dist = 0; dist < 100; dist += 1.0f) {
                            Vec3 pos = startPos.add(dir.scale(dist));
                            level.sendParticles(ParticleTypes.SOUL, pos.x, pos.y, pos.z, 2, 0.05, 0.05, 0.05, 0.02);
                            level.sendParticles(ParticleTypes.WITCH, pos.x, pos.y, pos.z, 1, 0.02, 0.02, 0.02, 0.01);

                            // Damage check every tick along the line
                            if (dist % 2 == 0) { // Optimize damage check density
                                level.getEntitiesOfClass(LivingEntity.class,
                                        new net.minecraft.world.phys.AABB(pos.subtract(1, 1, 1), pos.add(1, 1, 1)))
                                        .forEach(e -> {
                                            if (e != ctx.boss()) {
                                                float damage = BossSkillHelper.calculateMixedDamage(ctx, e, 10.0f,
                                                        20.0f);
                                                e.hurt(ctx.boss().damageSources().mobAttack(ctx.boss()), damage);
                                            }
                                        });
                            }
                        }
                    }
                })
                .build();
    }
}
