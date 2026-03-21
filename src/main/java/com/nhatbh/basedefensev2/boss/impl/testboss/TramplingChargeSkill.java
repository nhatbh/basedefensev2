package com.nhatbh.basedefensev2.boss.impl.testboss;

import com.nhatbh.basedefensev2.boss.skills.ActiveSequence;
import com.nhatbh.basedefensev2.boss.utils.HitboxUtils;
import com.nhatbh.basedefensev2.boss.utils.ParticleUtils;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TramplingChargeSkill {
    public static ActiveSequence create() {
        return ActiveSequence.builder("trampling_charge")
                // Phase 1: Rear Up (Telegraph)
                .parryStep("rear_up", 60)
                .counter(ActiveSequence.CounterType.NORMAL, 30, 60)
                .punishment((ctx, event) -> {
                    if (event.getSource().getEntity() instanceof LivingEntity attacker) {
                        attacker.hurt(ctx.boss().damageSources().mobAttack(ctx.boss()), 10.0f);
                        ctx.jumpToStep("charge"); // Advance to next phase instantly
                    }
                })
                .onStart(ctx -> {
                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                            SoundEvents.HORSE_ANGRY, SoundSource.HOSTILE, 2.0f, 1.0f);
                    BossSkillHelper.stopMovement(ctx);
                    BossSkillHelper.updateTracking(ctx);

                    // Capture direction for the whole charge
                    Vec3 dir = ctx.boss().getLookAngle().normalize();
                    ctx.data().put("vanguard_dir", dir);

                    // Slight upward burst to simulate rearing up
                    BossSkillHelper.getMovementEntity(ctx).setDeltaMovement(0, 0.3, 0);
                })
                .onTick(ctx -> {
                    // Lock rotation during telegraph
                    BossSkillHelper.stopMovement(ctx);
                    BossSkillHelper.updateTracking(ctx);
                })

                // Phase 2: Charge (The Dash)
                .step("charge", 25)
                .onStart(ctx -> {
                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                            SoundEvents.HORSE_GALLOP, SoundSource.HOSTILE, 2.0f, 1.5f);
                    ctx.data().put("charge_hits", new ArrayList<UUID>());
                })
                .onTick(ctx -> {
                    Vec3 dir = (Vec3) ctx.data().get("vanguard_dir");
                    if (dir != null) {
                        Entity mover = BossSkillHelper.getMovementEntity(ctx);
                        mover.setDeltaMovement(dir.scale(1.8)); // Slightly slower than thrust but longer

                        @SuppressWarnings("unchecked")
                        List<UUID> hitTargets = (List<UUID>) ctx.data().get("charge_hits");
                        List<LivingEntity> targets = HitboxUtils.getEntitiesInCircle(ctx.boss().level(),
                                LivingEntity.class, ctx.boss().position(), 2.5,
                                e -> e.isAlive() && e != ctx.boss() && e != ctx.boss().getVehicle()
                                        && !hitTargets.contains(e.getUUID()));

                        for (LivingEntity target : targets) {
                            hitTargets.add(target.getUUID());
                            target.hurt(ctx.boss().damageSources().mobAttack(ctx.boss()), 10f);
                            // Knock the target aside and up
                            Vec3 side = new Vec3(-dir.z, 0, dir.x).normalize().scale(0.5);
                            if (target.position().subtract(ctx.boss().position()).dot(side) < 0) {
                                side = side.scale(-1);
                            }
                            target.setDeltaMovement(target.getDeltaMovement().add(side).add(0, 0.4, 0));

                            ctx.boss().level().playSound(null, target.getX(), target.getY(), target.getZ(),
                                    SoundEvents.HORSE_STEP, SoundSource.HOSTILE, 1.5f, 1.0f);
                        }

                        if (ctx.boss().level() instanceof ServerLevel level) {
                            level.sendParticles(ParticleTypes.CRIT, ctx.boss().getX(), ctx.boss().getY(),
                                    ctx.boss().getZ(), 5, 0.5, 0.2, 0.5, 0.1);
                        }
                    }
                })

                // Phase 3: Final Spear Strike
                .step("spear_strike", 15)
                .onStart(ctx -> {
                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                            SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 2.0f, 0.5f);
                    BossSkillHelper.stopMovement(ctx);

                    Vec3 dir = (Vec3) ctx.data().get("vanguard_dir");
                    if (dir != null && ctx.boss().level() instanceof ServerLevel level) {
                        double radius = 5.0;
                        double angle = 140.0;
                        Vec3 pos = BossSkillHelper.getMovementEntity(ctx).position();

                        ParticleUtils.renderArc(level, ParticleTypes.SWEEP_ATTACK, pos.add(0, 1, 0), dir, radius, angle,
                                12, 0.05);

                        List<LivingEntity> targets = HitboxUtils.getEntitiesInArc(level, LivingEntity.class, pos, dir,
                                radius, angle,
                                e -> e.isAlive() && e != ctx.boss() && e != ctx.boss().getVehicle());

                        for (LivingEntity target : targets) {
                            target.hurt(ctx.boss().damageSources().mobAttack(ctx.boss()), 15f);
                            target.setDeltaMovement(target.getDeltaMovement().add(dir.scale(1.2).add(0, 0.3, 0)));
                            level.sendParticles(ParticleTypes.ENCHANTED_HIT, target.getX(), target.getY() + 1,
                                    target.getZ(), 10, 0.2, 0.2, 0.2, 0.2);
                        }
                    }
                })
                .onTick(BossSkillHelper::stopMovement)
                .build();
    }
}
