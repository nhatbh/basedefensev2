package com.nhatbh.basedefensev2.boss.impl.testboss;

import com.nhatbh.basedefensev2.boss.skills.ActiveSequence;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

public class SweepingPhalanxSkill {
    public static ActiveSequence create() {
        return ActiveSequence.builder("sweeping_phalanx")
                // Phase 1: Telegraph & Lock
                .step("lock", 80)
                .onStart(ctx -> {
                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                            SoundEvents.ARMOR_EQUIP_IRON, SoundSource.HOSTILE, 2.0f, 0.5f);
                    BossSkillHelper.stopMovement(ctx);
                    BossSkillHelper.updateTracking(ctx);

                    // Capture direction for the whole charge
                    Vec3 dir = ctx.boss().getLookAngle().normalize();
                    ctx.data().put("vanguard_dir", dir);
                })
                .onTick(ctx -> {
                    BossSkillHelper.stopMovement(ctx);
                    BossSkillHelper.updateTracking(ctx);
                })

                // Phase 2: Left-to-Right Cleave
                .step("sweep_left", 10)
                .onStart(ctx -> {
                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                            SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 2.0f, 0.7f);
                    ctx.data().put("sweep_tick", 0);
                    ctx.data().put("sweep_hits", new java.util.ArrayList<java.util.UUID>());
                })
                .onTick(ctx -> {
                    BossSkillHelper.stopMovement(ctx);
                    Vec3 dir = (Vec3) ctx.data().get("vanguard_dir");
                    BossSkillHelper.performDynamicSweep(ctx, dir, -60.0, 60.0, 6.0, 5.0f, 15.0f, 10, false, ParticleTypes.SWEEP_ATTACK);
                })

                // Inter-sweep delay
                .step("delay_1", 10)
                .onTick(BossSkillHelper::stopMovement)

                // Phase 3: Right-to-Left Cleave
                .step("sweep_right", 10)
                .onStart(ctx -> {
                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                            SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 2.0f, 0.6f);
                    ctx.data().put("sweep_tick", 0);
                    ctx.data().put("sweep_hits", new java.util.ArrayList<java.util.UUID>());
                })
                .onTick(ctx -> {
                    BossSkillHelper.stopMovement(ctx);
                    Vec3 dir = (Vec3) ctx.data().get("vanguard_dir");
                    BossSkillHelper.performDynamicSweep(ctx, dir, 60.0, -60.0, 6.0, 5.0f, 15.0f, 10, false, ParticleTypes.SWEEP_ATTACK);
                })

                // Inter-sweep delay
                .step("delay_2", 10)
                .onTick(BossSkillHelper::stopMovement)

                // Phase 3.5: Spin Prepare (Counter Window)
                .parryStep("spin_prepare", 30)
                .counter(ActiveSequence.CounterType.NORMAL, 20, 30)
                .onCountered((ctx, event) -> BossSkillHelper.depletePoise(ctx, 10f))
                .onStart(ctx -> {
                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                            SoundEvents.IRON_GOLEM_ATTACK, SoundSource.HOSTILE, 1.0f, 0.5f);
                    BossSkillHelper.stopMovement(ctx);
                })
                .onTick(ctx -> {
                    BossSkillHelper.stopMovement(ctx);
                    if (ctx.getTicks() % 5 == 0 && ctx.boss().level() instanceof ServerLevel level) {
                        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, ctx.boss().getX(), ctx.boss().getY() + 1.5,
                                ctx.boss().getZ(), 5, 0.2, 0.2, 0.2, 0.05);
                    }
                })

                // Phase 4: 360-Degree Spin
                .step("spin", 15)
                .onStart(ctx -> {
                    Vec3 dir = ctx.boss().getLookAngle();
                    ctx.data().put("counter_dir", dir);

                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                            SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 2.0f, 0.4f);
                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                            SoundEvents.IRON_GOLEM_ATTACK, SoundSource.HOSTILE, 1.5f, 0.5f);
                    ctx.data().put("sweep_tick", 0);
                    ctx.data().put("sweep_hits", new java.util.ArrayList<java.util.UUID>());
                })
                .onTick(ctx -> {
                    BossSkillHelper.stopMovement(ctx);
                    Vec3 dir = ctx.boss().getLookAngle();
                    BossSkillHelper.performDynamicSweep(ctx, dir, 0.0, 360.0, 5.0, 8.0f, 20.0f, 15, true, ParticleTypes.SWEEP_ATTACK);
                })
                .build();
    }
}
