package com.nhatbh.basedefensev2.boss.impl.testboss;

import com.nhatbh.basedefensev2.boss.skills.ActiveSequence;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

public class RainOfArrowsSkill {
    public static ActiveSequence create() {
        return ActiveSequence.builder("rain_of_arrows")
                // Step 1: Aiming
                .step("aim_skyward", 60)
                .counter(ActiveSequence.CounterType.NORMAL, 40, 60)
                .onCountered((ctx, event) -> BossSkillHelper.depletePoise(ctx, 10f))
                .onStart(ctx -> {
                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                            SoundEvents.ARROW_SHOOT, SoundSource.HOSTILE, 1.0f, 0.5f);
                    BossSkillHelper.stopMovement(ctx);

                    // Select up to 3 random targets
                    java.util.List<LivingEntity> targets = BossSkillHelper.getRandomTargets(ctx, 100.0, 3);
                    ctx.data().put("rain_targets", targets);

                    if (!targets.isEmpty()) {
                        BossSkillHelper.broadcastMessage(ctx.boss(), "Let the sky fall!");
                    }
                })
                .onTick(ctx -> {
                    BossSkillHelper.stopMovement(ctx);
                    if (ctx.boss().level() instanceof ServerLevel serverLevel) {
                        serverLevel.sendParticles(ParticleTypes.ENCHANTED_HIT, ctx.boss().getX(), ctx.boss().getY() + 3,
                                ctx.boss().getZ(), 5, 0.5, 0.5, 0.5, 0.1);
                    }
                })

                // Step 2-4: Volleys
                .step("volley_1", 20).onStart(ctx -> fireVolley(ctx, 0))
                .step("volley_2", 20).onStart(ctx -> fireVolley(ctx, 1))
                .step("volley_3", 20).onStart(ctx -> fireVolley(ctx, 2))
                .build();
    }

    private static void fireVolley(com.nhatbh.basedefensev2.boss.skills.SkillContext ctx, int index) {
        @SuppressWarnings("unchecked")
        java.util.List<LivingEntity> targets = (java.util.List<LivingEntity>) ctx.data().get("rain_targets");
        if (targets == null || index >= targets.size())
            return;

        LivingEntity target = targets.get(index);
        if (target == null || !target.isAlive())
            return;

        Vec3 targetPos = target.position();
        if (ctx.boss().level() instanceof ServerLevel level) {
            // Warning particles
            for (int i = 0; i < 20; i++) {
                double angle = i * Math.PI * 2 / 20;
                level.sendParticles(ParticleTypes.SMALL_FLAME, targetPos.x + Math.cos(angle) * 3, targetPos.y + 0.1,
                        targetPos.z + Math.sin(angle) * 3, 1, 0, 0, 0, 0);
            }

            // Fire 5 arrows from above
            for (int i = 0; i < 5; i++) {
                double offsetX = (Math.random() - 0.5) * 4.0;
                double offsetZ = (Math.random() - 0.5) * 4.0;
                Vec3 spawnPos = targetPos.add(offsetX, 20.0, offsetZ);
                Vec3 dir = new Vec3(0, -1, 0); // Straight down
                BossSkillHelper.fireFastArrow(ctx.boss(), spawnPos, dir, 3.0f, 8.0);
                ctx.boss().level().playSound(null, spawnPos.x, spawnPos.y, spawnPos.z,
                        SoundEvents.ARROW_SHOOT, SoundSource.HOSTILE, 1.0f, 0.8f);
            }
        }
    }
}
