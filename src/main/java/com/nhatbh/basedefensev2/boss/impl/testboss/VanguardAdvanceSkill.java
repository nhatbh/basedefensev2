package com.nhatbh.basedefensev2.boss.impl.testboss;

import com.nhatbh.basedefensev2.boss.skills.ActiveSequence;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import com.nhatbh.basedefensev2.boss.utils.HitboxUtils;
import com.nhatbh.basedefensev2.boss.utils.ParticleUtils;
import java.util.List;

public class VanguardAdvanceSkill {
    // Common logic moved to BossSkillHelper


    public static ActiveSequence create() {
        return ActiveSequence.builder("vanguard_advance")
            // Phase 1: Target Lock (Telegraph)
            .step("lock", 15)
                .onStart(ctx -> {
                    ctx.log("§4Preparing Vanguard Advance!§r");
                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 2.0f, 1.0f);
                    BossSkillHelper.stopMovement(ctx);
                    BossSkillHelper.updateTracking(ctx);
                })
                .onTick(ctx -> {
                    BossSkillHelper.stopMovement(ctx);
                    
                    Vec3 dir = (Vec3) ctx.data().get("vanguard_dir");
                    if (dir != null && ctx.boss().level() instanceof ServerLevel serverLevel) {
                        Vec3 start = ctx.boss().position().add(0, 1, 0);
                        ParticleUtils.renderLine(serverLevel, ParticleTypes.FLAME, start, start.add(dir.scale(10.5)), 8, 0.02);
                    }
                })

            // Phase 2: Forward Lunging Thrust
            .step("thrust", 10)
                .onStart(ctx -> {
                    ctx.log("§lTHRUST!§r");
                    ctx.data().put("thrust_hits", new java.util.ArrayList<java.util.UUID>());
                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 2.0f, 0.5f);
                })
                .onTick(ctx -> {
                    Vec3 dir = (Vec3) ctx.data().get("vanguard_dir");
                    if (dir != null) {
                        BossSkillHelper.getMovementEntity(ctx).setDeltaMovement(dir.scale(2.0));
                        
                        @SuppressWarnings("unchecked")
                        List<java.util.UUID> hitTargets = (List<java.util.UUID>) ctx.data().get("thrust_hits");
                        List<LivingEntity> targets = HitboxUtils.getEntitiesInCircle(ctx.boss().level(), LivingEntity.class, ctx.boss().position(), 2.5, e -> e.isAlive() && e != ctx.boss() && e != ctx.boss().getVehicle() && !hitTargets.contains(e.getUUID()));
                        
                        for (LivingEntity target : targets) {
                            hitTargets.add(target.getUUID());
                            target.hurt(ctx.boss().damageSources().mobAttack(ctx.boss()), 12f);
                            // Knock the target in the thrust direction
                            target.setDeltaMovement(target.getDeltaMovement().add(dir.scale(1.5).add(0, 0.2, 0)));
                            ctx.boss().level().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 1.5f, 1.0f);
                            
                            if (ctx.boss().level() instanceof ServerLevel level) {
                                level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + 1, target.getZ(), 5, 0.2, 0.2, 0.2, 0.1);
                            }
                        }

                        if (ctx.boss().level() instanceof ServerLevel level) {
                            level.sendParticles(ParticleTypes.SWEEP_ATTACK, ctx.boss().getX(), ctx.boss().getY() + 1, ctx.boss().getZ(), 1, 0, 0, 0, 0);
                        }
                    }
                })

            // Phase 2.5: Delay after dash
            .step("thrust_delay", 20)
                .onStart(ctx -> {
                    BossSkillHelper.stopMovement(ctx);
                    BossSkillHelper.updateTracking(ctx);
                })
            // Phase 3: Heavy Shield Bash (Arc hit)
            .step("shield_bash", 20)
                .onStart(ctx -> {
                    ctx.log("§bSHIELD BASH!§r");
                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(), SoundEvents.IRON_GOLEM_ATTACK, SoundSource.HOSTILE, 2.0f, 0.5f);
                    BossSkillHelper.stopMovement(ctx); // Stop dash momentum
                    BossSkillHelper.updateTracking(ctx);
                    
                    Vec3 dir = (Vec3) ctx.data().get("vanguard_dir");
                    if (dir != null && ctx.boss().level() instanceof ServerLevel level) {
                        double arcRadius = 4.0;
                        double arcAngle = 120.0;
                        Vec3 bossPos = BossSkillHelper.getMovementEntity(ctx).position();

                        // Visual indicator for arc
                        ParticleUtils.renderArc(level, ParticleTypes.CLOUD, bossPos.add(0, 1, 0), dir, arcRadius, arcAngle, 13, 0.05);

                        List<LivingEntity> targets = HitboxUtils.getEntitiesInArc(level, LivingEntity.class, bossPos, dir, arcRadius, arcAngle, e -> e.isAlive() && e != ctx.boss() && e != ctx.boss().getVehicle());
                        for (LivingEntity target : targets) {
                            target.hurt(ctx.boss().damageSources().mobAttack(ctx.boss()), 10f);
                            target.setDeltaMovement(target.getDeltaMovement().add(dir.scale(1.2).add(0, 0.4, 0)));
                            ctx.boss().level().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 2.0f, 0.5f);
                            level.sendParticles(ParticleTypes.EXPLOSION, target.getX(), target.getY() + 1, target.getZ(), 1, 0, 0, 0, 0);
                        }
                    }
                })
                .onTick(ctx -> BossSkillHelper.stopMovement(ctx))

            // Phase 4: Plunging Overhead Stab (Circle Ground Stomp)
            .step("overhead_stab_jump", 10)
                .onStart(ctx -> {
                    ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(), SoundEvents.MAGMA_CUBE_JUMP, SoundSource.HOSTILE, 2.0f, 0.5f);
                    BossSkillHelper.updateTracking(ctx);
                    Vec3 dir = (Vec3) ctx.data().get("vanguard_dir");
                    if (dir != null) {
                        BossSkillHelper.getMovementEntity(ctx).setDeltaMovement(dir.scale(1.2).add(0, 0.8, 0));
                    } else {
                        BossSkillHelper.getMovementEntity(ctx).setDeltaMovement(0, 0.8, 0);
                    }
                })
                .onTick(ctx -> BossSkillHelper.updateTracking(ctx))
            .step("overhead_stab_land", 20)
                .onStart(ctx -> {
                    ctx.log("§cOVERHEAD STAB!§r");
                    BossSkillHelper.updateTracking(ctx);
                    BossSkillHelper.getMovementEntity(ctx).setDeltaMovement(0, -1.5, 0);
                })
                .onTick(ctx -> {
                    if (!ctx.data().containsKey("stomp_done") && BossSkillHelper.getMovementEntity(ctx).onGround()) {
                        ctx.data().put("stomp_done", true);
                        ctx.boss().level().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 3.0f, 0.8f);
                        if (ctx.boss().level() instanceof ServerLevel level) {
                            double radius = 5.0;
                            Vec3 center = BossSkillHelper.getMovementEntity(ctx).position();
                            
                            ParticleUtils.renderCircle(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, center.add(0, 0.2, 0), radius, 24, 0.05);
                            ParticleUtils.renderCircle(level, ParticleTypes.LAVA, center.add(0, 0.2, 0), radius * 0.5, 24, 0);

                            List<LivingEntity> targets = HitboxUtils.getEntitiesInCircle(level, LivingEntity.class, center, radius, e -> e.isAlive() && e != ctx.boss() && e != ctx.boss().getVehicle());
                            for (LivingEntity target : targets) {
                                target.hurt(ctx.boss().damageSources().mobAttack(ctx.boss()), 20f);
                                target.setDeltaMovement(target.getDeltaMovement().add(0, 0.8, 0));
                            }
                        }
                    } else if (!ctx.data().containsKey("stomp_done")) {
                         BossSkillHelper.updateTracking(ctx);
                         BossSkillHelper.getMovementEntity(ctx).setDeltaMovement(0, -1.5, 0);
                    }
                })
                
            // Phase 5: Recovery
            .step("recovery", 20)
                .onStart(ctx -> {
                    ctx.data().remove("stomp_done");
                })
                .onTick(ctx -> BossSkillHelper.stopMovement(ctx))
            .build();
    }
}
