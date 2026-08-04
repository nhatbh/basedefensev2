package com.nhatbh.basedefensev2.boss.impl.testboss;

import com.nhatbh.basedefensev2.boss.skills.ActiveSequence;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

public class WarpFlurrySkill {
    public static ActiveSequence create() {
        return ActiveSequence.builder("warp_flurry")
                // Step 1: Warp Behind Target
                .step("warp", 5)
                .onStart(ctx -> {
                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                            SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.5f, 1.0f);

                    LivingEntity target = BossSkillHelper.getClosestTarget(ctx);
                    if (target != null) {
                        // Position precisely behind the target
                        Vec3 backPos = target.position()
                                .subtract(target.getLookAngle().multiply(1, 0, 1).normalize().scale(1.5));
                        ctx.boss().teleportTo(backPos.x, backPos.y, backPos.z);
                        ctx.boss().lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,
                                target.getEyePosition());

                        if (ctx.boss().level() instanceof ServerLevel level) {
                            level.sendParticles(ParticleTypes.REVERSE_PORTAL, ctx.boss().getX(), ctx.boss().getY() + 1,
                                    ctx.boss().getZ(), 20, 0.2, 0.5, 0.2, 0.05);
                        }
                    }
                })

                // Step 2: Three Rapid Stabs
                .step("rapid_stabs", 15)
                .onStart(ctx -> {
                    ctx.data().put("stab_count", 0);
                })
                .onTick(ctx -> {
                    if (ctx.getTicks() % 4 == 0) {
                        int count = (int) ctx.data().get("stab_count");
                        if (count < 3) {
                            ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                                    SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.0f, 1.8f);

                            LivingEntity target = BossSkillHelper.getClosestTarget(ctx);
                            if (target != null && target.distanceTo(ctx.boss()) < 4.0) {
                                float damage = BossSkillHelper.calculateMixedDamage(ctx, target, 5.0f, 10.0f);
                                target.hurt(ctx.boss().damageSources().mobAttack(ctx.boss()), damage);
                                if (ctx.boss().level() instanceof ServerLevel level) {
                                    level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + 1,
                                            target.getZ(), 5, 0.1, 0.1, 0.1, 0.1);
                                }
                            }
                            ctx.data().put("stab_count", count + 1);
                        }
                    }
                })

                // Step 3: Heavy Kick
                .step("heavy_kick", 10)
                .onStart(ctx -> {
                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                            SoundEvents.IRON_GOLEM_ATTACK, SoundSource.HOSTILE, 1.5f, 0.4f);

                    LivingEntity target = BossSkillHelper.getClosestTarget(ctx);
                    if (target != null && target.distanceTo(ctx.boss()) < 4.0) {
                        float damage = BossSkillHelper.calculateMixedDamage(ctx, target, 5.0f, 10.0f);
                        target.hurt(ctx.boss().damageSources().mobAttack(ctx.boss()), damage);
                        Vec3 knockback = target.position().subtract(ctx.boss().position()).multiply(1, 0, 1).normalize()
                                .scale(1.8);
                        target.setDeltaMovement(knockback.x, 0.6, knockback.z);
                        target.hurtMarked = true;

                        if (ctx.boss().level() instanceof ServerLevel level) {
                            level.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY() + 1,
                                    target.getZ(), 1, 0, 0, 0, 0);
                        }
                    }
                })
                .build();
    }
}
