package com.nhatbh.basedefensev2.boss.impl.testboss;

import com.nhatbh.basedefensev2.boss.skills.ActiveSequence;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

public class PartingShotSkill {
    public static ActiveSequence create() {
        return ActiveSequence.builder("parting_shot")
            // Step 1: Dash backward
            .step("dash_back", 15)
                .onStart(ctx -> {
                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                            SoundEvents.HORSE_GALLOP, SoundSource.HOSTILE, 2.0f, 1.5f);
                    LivingEntity target = BossSkillHelper.getClosestTarget(ctx);
                    if (target != null) {
                        Vec3 dir = target.position().subtract(ctx.boss().position()).normalize().scale(-1.5);
                        BossSkillHelper.getMovementEntity(ctx).setDeltaMovement(dir.x, ctx.boss().getDeltaMovement().y, dir.z);
                    }
                })
            
            // Step 2: Drop traps
            .step("drop_traps", 10)
                .onStart(ctx -> {
                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                            SoundEvents.CHICKEN_EGG, SoundSource.HOSTILE, 1.5f, 0.5f);
                    LivingEntity target = BossSkillHelper.getClosestTarget(ctx);
                    if (target != null) {
                        for (int i = 0; i < 3; i++) {
                            AreaEffectCloud trap = new AreaEffectCloud(ctx.boss().level(), target.getX() + (Math.random() - 0.5) * 4, target.getY(), target.getZ() + (Math.random() - 0.5) * 4);
                            trap.setRadius(2.5f);
                            trap.setDuration(120); // 6 seconds
                            trap.setParticle(ParticleTypes.SQUID_INK);
                            trap.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.BLINDNESS, 60, 1));
                            trap.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WITHER, 100, 1));
                            trap.addTag(com.nhatbh.basedefensev2.stage.ArenaConstants.ARENA_AFFILIATED_TAG);
                            ctx.boss().level().addFreshEntity(trap);
                        }
                    }
                })

            // Step 2.5: Aiming (Counter Window)
            .parryStep("aiming_prepare", 30)
                .counter(ActiveSequence.CounterType.NORMAL, 20, 30)
                .onCountered((ctx, event) -> BossSkillHelper.depletePoise(ctx, 10f))
                .onStart(ctx -> {
                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                            SoundEvents.CROSSBOW_LOADING_MIDDLE, SoundSource.HOSTILE, 1.0f, 0.5f);
                    BossSkillHelper.stopMovement(ctx);
                })

            // Step 3: Heavy tracking arrow
            .step("heavy_shot", 20)
                .onStart(ctx -> {
                    LivingEntity target = BossSkillHelper.getClosestTarget(ctx);
                    if (target != null) {
                        fireHeavyArrowVolley(ctx.boss(), target);
                    }
                })
            .build();
    }

    private static void fireHeavyArrowVolley(LivingEntity shooter, LivingEntity target) {
        Vec3 baseDir = target.position().add(0, target.getEyeHeight() * 0.5, 0).subtract(shooter.position().add(0, shooter.getEyeHeight(), 0)).normalize();
        shooter.level().playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(),
                SoundEvents.ARROW_SHOOT, SoundSource.HOSTILE, 1.5f, 0.2f);
        for (int i = -2; i <= 2; i++) {
            double rad = Math.toRadians(i * 10);
            double cos = Math.cos(rad);
            double sin = Math.sin(rad);
            double nx = baseDir.x * cos - baseDir.z * sin;
            double nz = baseDir.x * sin + baseDir.z * cos;
            Vec3 dir = new Vec3(nx, baseDir.y, nz);
            BossSkillHelper.fireFastArrow(shooter, shooter.position().add(0, shooter.getEyeHeight(), 0), dir, 4.0f, 10.0);
        }
    }
}
