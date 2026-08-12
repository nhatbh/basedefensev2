package com.nhatbh.basedefensev2.boss.impl.testboss;

import com.nhatbh.basedefensev2.boss.skills.ActiveSequence;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import com.nhatbh.basedefensev2.boss.core.BossManager;
import com.nhatbh.basedefensev2.boss.core.BossComponent;

public class CullingBladeSkill {
    public static ActiveSequence create() {
        return ActiveSequence.builder("culling_blade")
                // Step 1: Windup & Leap
                .step("strike_windup", 45)
                .counter(ActiveSequence.CounterType.NORMAL, 30, 45)
                .onCountered((ctx, event) -> BossSkillHelper.depletePoise(ctx, 10f))
                .onStart(ctx -> {
                    BossSkillHelper.broadcastMessage(ctx.boss(), "Your time is ending.");
                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                            SoundEvents.IRON_GOLEM_ATTACK, SoundSource.HOSTILE, 2.0f, 0.5f);

                    LivingEntity target = BossSkillHelper.getClosestTarget(ctx);
                    if (target != null) {
                        Vec3 jumpDir = target.position().subtract(ctx.boss().position()).normalize().scale(1.2);
                        ctx.boss().setDeltaMovement(jumpDir.add(0, 0.5, 0));
                    }
                })
                .onTick(ctx -> {
                    if (ctx.getTicks() % 5 == 0 && ctx.boss().level() instanceof ServerLevel level) {
                        level.sendParticles(ParticleTypes.CRIT, ctx.boss().getX(), ctx.boss().getY() + 2,
                                ctx.boss().getZ(), 5, 0.2, 0.2, 0.2, 0.1);
                        level.sendParticles(ParticleTypes.WITCH, ctx.boss().getX(), ctx.boss().getY() + 1,
                                ctx.boss().getZ(), 5, 0.3, 0.3, 0.3, 0.05);
                    }
                })

                // Step 2: Strike
                .step("strike_execution", 10)
                .onStart(ctx -> {
                    BossSkillHelper.broadcastMessage(ctx.boss(), "Your life is forfeit!");
                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                            SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 2.0f, 0.5f);

                    LivingEntity target = BossSkillHelper.getClosestTarget(ctx);
                    if (target != null && target.isAlive()) {
                        float damage = BossSkillHelper.calculateMixedDamage(ctx, target, 30.0f, 50.0f);
                        target.hurt(ctx.boss().damageSources().mobAttack(ctx.boss()), damage);

                        if (ctx.boss().level() instanceof ServerLevel level) {
                            level.sendParticles(ParticleTypes.ENCHANTED_HIT, target.getX(), target.getY() + 1,
                                    target.getZ(), 30, 0.5, 0.5, 0.5, 0.3);
                            level.sendParticles(ParticleTypes.SOUL, target.getX(), target.getY() + 1, target.getZ(), 20,
                                    0.3, 0.3, 0.3, 0.1);
                        }

                        // Reset cooldown if killed
                        if (!target.isAlive()) {
                            BossComponent comp = BossManager.get(ctx.boss());
                            if (comp != null)
                                comp.setSkillCooldown("culling_blade", 0);
                            ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                                    SoundEvents.WITHER_DEATH, SoundSource.HOSTILE, 2.0f, 1.5f);
                        }
                    }
                })
                .build();
    }
}
