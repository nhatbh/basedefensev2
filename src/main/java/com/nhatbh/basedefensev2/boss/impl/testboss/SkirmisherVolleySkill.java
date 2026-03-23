package com.nhatbh.basedefensev2.boss.impl.testboss;

import com.nhatbh.basedefensev2.boss.skills.ActiveSequence;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

public class SkirmisherVolleySkill {
    public static ActiveSequence create() {
        return ActiveSequence.builder("skirmisher_volley")
                // Stage 1: Fast single arrow
                .step("single_shot", 10)
                .onStart(ctx -> {
                    LivingEntity target = BossSkillHelper.getClosestTarget(ctx);
                    if (target != null) {
                        fireFastVolleyArrow(ctx.boss(), target, 0);
                    }
                })

                // Stage 2: Twin shot
                .step("twin_shot", 15)
                .onStart(ctx -> {
                    LivingEntity target = BossSkillHelper.getClosestTarget(ctx);
                    if (target != null) {
                        fireFastVolleyArrow(ctx.boss(), target, -5);
                        fireFastVolleyArrow(ctx.boss(), target, 5);
                    }
                })

                // Stage 3: Fan spread (Counterable)
                .step("fan_windup", 30)
                .counter(ActiveSequence.CounterType.NORMAL, 20, 30)
                .onCountered((ctx, event) -> BossSkillHelper.depletePoise(ctx, 10f))
                .onStart(ctx -> {
                    BossSkillHelper.stopMovement(ctx);
                })

                .step("fan_shot", 10)
                .onStart(ctx -> {
                    LivingEntity target = BossSkillHelper.getClosestTarget(ctx);
                    if (target != null) {
                        for (int i = -2; i <= 2; i++) {
                            fireFastVolleyArrow(ctx.boss(), target, i * 15);
                        }
                    }
                })
                .build();
    }

    private static void fireFastVolleyArrow(LivingEntity shooter, LivingEntity target, float angleOffset) {
        Vec3 dir = target.position().add(0, target.getEyeHeight() * 0.5, 0)
                .subtract(shooter.position().add(0, shooter.getEyeHeight(), 0)).normalize();

        // Apply angle offset
        double rad = Math.toRadians(angleOffset);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        double nx = dir.x * cos - dir.z * sin;
        double nz = dir.x * sin + dir.z * cos;
        dir = new Vec3(nx, dir.y, nz);

        BossSkillHelper.fireFastArrow(shooter, shooter.position().add(0, shooter.getEyeHeight(), 0), dir, 3.5f, 7.0);
        shooter.level().playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(),
                SoundEvents.ARROW_SHOOT, SoundSource.HOSTILE, 1.0f, 1.0f);
    }
}
