package com.nhatbh.basedefensev2.boss.impl.testboss;

import com.nhatbh.basedefensev2.boss.skills.ActiveSequence;
import com.nhatbh.basedefensev2.boss.skills.SkillContext;
import com.nhatbh.basedefensev2.boss.utils.HitboxUtils;
import com.nhatbh.basedefensev2.boss.utils.ParticleUtils;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class SweepingPhalanxSkill {
    public static ActiveSequence create() {
        return ActiveSequence.builder("sweeping_phalanx")
                // Phase 1: Telegraph & Lock
                .parryStep("lock", 80)
                .counter(ActiveSequence.CounterType.NORMAL, 40, 80)
                .punishment((ctx, event) -> {
                    if (event.getSource().getEntity() instanceof LivingEntity attacker) {
                        attacker.hurt(ctx.boss().damageSources().mobAttack(ctx.boss()), 12.0f);
                        ctx.jumpToStep("sweep_left"); // Advance to next phase instantly
                    }
                })
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
                    performDynamicSweep(ctx, -60.0, 60.0, 6.0, 12f, 10, false);
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
                    performDynamicSweep(ctx, 60.0, -60.0, 6.0, 12f, 10, false);
                })

                // Inter-sweep delay
                .step("delay_2", 10)
                .onTick(BossSkillHelper::stopMovement)

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
                    performDynamicSweep(ctx, 0.0, 360.0, 5.0, 15f, 15, true);
                })
                .build();
    }

    private static void performDynamicSweep(SkillContext ctx, double startAngleOffset, double endAngleOffset,
            double radius, float damage, int duration, boolean applyKnockback) {
        int tick = (int) ctx.data().getOrDefault("sweep_tick", 0);
        double progress = tick / (double) (duration - 1);
        if (progress > 1.0)
            progress = 1.0;

        double currentAngleOffset = startAngleOffset + (endAngleOffset - startAngleOffset) * progress;
        Vec3 dir = (Vec3) ctx.data().get("vanguard_dir");
        if (dir != null && ctx.boss().level() instanceof ServerLevel level) {
            Vec3 pos = BossSkillHelper.getMovementEntity(ctx).position();

            // Rotate base direction by current offset
            double angleRad = Math.toRadians(currentAngleOffset);
            double cos = Math.cos(angleRad);
            double sin = Math.sin(angleRad);
            Vec3 rotatedDir = new Vec3(
                    dir.x * cos - dir.z * sin,
                    0,
                    dir.x * sin + dir.z * cos).normalize();

            // Draw visual (filled arc)
            double arcWidth = 35.0;
            ParticleUtils.renderFilledArc(level, ParticleTypes.SWEEP_ATTACK, pos.add(0, 1, 0), rotatedDir, radius,
                    arcWidth, 3, 3, 0.05);

            @SuppressWarnings("unchecked")
            List<java.util.UUID> hitTargets = (List<java.util.UUID>) ctx.data().get("sweep_hits");

            List<LivingEntity> targets = HitboxUtils.getEntitiesInArc(level, LivingEntity.class, pos, rotatedDir,
                    radius, arcWidth, e -> e.isAlive() && e != ctx.boss() && e != ctx.boss().getVehicle()
                            && !hitTargets.contains(e.getUUID()));
            for (LivingEntity target : targets) {
                hitTargets.add(target.getUUID());
                target.hurt(ctx.boss().damageSources().mobAttack(ctx.boss()), damage);

                if (applyKnockback) {
                    // Radial knockback (away)
                    target.setDeltaMovement(target.getDeltaMovement().add(rotatedDir.scale(2.0).add(0, 0.2, 0)));
                }
            }
        }
        ctx.data().put("sweep_tick", tick + 1);
    }
}
