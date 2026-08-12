package com.nhatbh.basedefensev2.boss.impl.testboss;

import com.nhatbh.basedefensev2.boss.skills.ActiveSequence;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;

public class SwarmBeaconSkill {
    public static ActiveSequence create() {
        return ActiveSequence.builder("swarm_beacon")
                // Step 1: Fire tracking arrow
                .step("fire_beacon", 20)
                .onStart(ctx -> {
                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                            SoundEvents.BEE_LOOP_AGGRESSIVE, SoundSource.HOSTILE, 1.0f, 0.5f);
                    BossSkillHelper.broadcastMessage(ctx.boss(), "Feast, my children!");
                    LivingEntity target = getLowestArmorPlayer(ctx);
                    if (target != null) {
                        fireBeaconArrow(ctx.boss(), target);
                        ctx.data().put("swarm_target", target);
                    }
                })

                // Step 2: Continuous Spawning
                .step("swarm_active", 200) // 10 seconds
                .onTick(ctx -> {
                    if (ctx.getTicks() % 40 == 0) { // Every 2s
                        LivingEntity target = (LivingEntity) ctx.data().get("swarm_target");
                        if (target != null && target.isAlive()) {
                            ctx.boss().level().playSound(null, target.getX(), target.getY(), target.getZ(),
                                    SoundEvents.BEE_LOOP_AGGRESSIVE, SoundSource.HOSTILE, 1.0f, 1.5f);
                            spawnLeaper(ctx.boss(), target);
                        }
                    }
                })
                .build();
    }

    private static LivingEntity getLowestArmorPlayer(com.nhatbh.basedefensev2.boss.skills.SkillContext ctx) {
        return ctx.boss().level()
                .getEntitiesOfClass(net.minecraft.world.entity.player.Player.class,
                        ctx.boss().getBoundingBox().inflate(64))
                .stream()
                .min(java.util.Comparator.comparingDouble(p -> p.getArmorValue()))
                .orElse(null);
    }

    private static void fireBeaconArrow(LivingEntity shooter, LivingEntity target) {
        Vec3 dir = target.position().add(0, target.getEyeHeight() * 0.5, 0)
                .subtract(shooter.position().add(0, shooter.getEyeHeight(), 0)).normalize();
        BossSkillHelper.fireFastArrow(shooter, shooter.position().add(0, shooter.getEyeHeight(), 0), dir, 2.5f, 4.0);
    }

    private static void spawnLeaper(LivingEntity boss, LivingEntity target) {
        if (boss.level() instanceof ServerLevel level) {
            Zombie leaper = new Zombie(level); // Placeholder for Leaper
            leaper.setPos(target.getX() + (Math.random() - 0.5) * 6, target.getY() + 2,
                    target.getZ() + (Math.random() - 0.5) * 6);
            leaper.setTarget(target);
            leaper.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).setCount(0); // No helmet
            var speedAttr = leaper.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
            if (speedAttr != null)
                speedAttr.setBaseValue(0.45f);
            var damageAttr = leaper.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
            if (damageAttr != null)
                damageAttr.setBaseValue(2f);
            leaper.addTag(com.nhatbh.basedefensev2.stage.ArenaConstants.ARENA_AFFILIATED_TAG);
            level.addFreshEntity(leaper);
            level.sendParticles(ParticleTypes.POOF, leaper.getX(), leaper.getY(), leaper.getZ(), 5, 0, 0, 0, 0);
        }
    }
}
