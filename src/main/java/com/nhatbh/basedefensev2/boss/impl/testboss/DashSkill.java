package com.nhatbh.basedefensev2.boss.impl.testboss;

import net.minecraft.world.phys.Vec3;
import com.nhatbh.basedefensev2.boss.skills.ActiveSequence;
import com.nhatbh.basedefensev2.elemental.ElementType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.Comparator;
import java.util.List;

public class DashSkill {
    public static ActiveSequence create() {
        return ActiveSequence.builder("dash")
            // Phase 1: Charge
            .step("charge", 80)
                .counter(ActiveSequence.CounterType.MAGIC, 10, 80)
                .magic(ElementType.ICE)
                .punishment((ctx, event) -> {
                    if (event.getSource().getEntity() instanceof LivingEntity attacker) {
                        attacker.hurt(ctx.boss().damageSources().mobAttack(ctx.boss()), 5.0f);
                        attacker.setSecondsOnFire(3);
                        // ctx.log("§cFire Counter!§r " + attacker.getDisplayName().getString() + " was burned while trying to stop the dash!");
                    }
                })
                .onStart(ctx -> {
                    // ctx.log("§cBoss is charging up!§r (Magic counter window: ICE required!)");
                    ctx.boss().setDeltaMovement(Vec3.ZERO);
                })
                .onTick(ctx -> ctx.boss().setDeltaMovement(Vec3.ZERO))

            // Phase 2: Target Lock
            .step("lock", 20)
                .onStart(ctx -> {
                    // ctx.log("§4Locking target...§r");
                    List<Player> players = ctx.boss().level().getEntitiesOfClass(Player.class, 
                        ctx.boss().getBoundingBox().inflate(100));
                    
                    Player target = players.stream()
                        .max(Comparator.comparingDouble(p -> p.distanceTo(ctx.boss())))
                        .orElse(null);

                    if (target != null) {
                        Vec3 targetPos = target.position();
                        ctx.data().put("dash_target", targetPos);
                        
                        // Snapshot direction for rotation lock
                        Vec3 dir = targetPos.subtract(ctx.boss().position()).normalize();
                        ctx.data().put("dash_dir", dir);
                        
                        float yaw = (float) (Math.atan2(-dir.x, dir.z) * (180 / Math.PI));
                        ctx.boss().setYRot(yaw);
                        ctx.boss().setYHeadRot(yaw);
                        ctx.boss().setYBodyRot(yaw);
                    }
                })
                .onTick(ctx -> {
                    ctx.boss().setDeltaMovement(Vec3.ZERO);
                    Vec3 dir = (Vec3) ctx.data().get("dash_dir");
                    if (dir != null) {
                        // Rotation lock
                        float yaw = (float) (Math.atan2(-dir.x, dir.z) * (180 / Math.PI));
                        ctx.boss().setYRot(yaw);
                        ctx.boss().setYHeadRot(yaw);
                        ctx.boss().setYBodyRot(yaw);

                        // Render indicator line
                        if (ctx.boss().level() instanceof ServerLevel serverLevel) {
                            for (int i = 0; i < 10; i++) {
                                Vec3 p = ctx.boss().position().add(0, 1, 0).add(dir.scale(i * 1.5));
                                serverLevel.sendParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 1, 0, 0, 0, 0.02);
                            }
                        }
                    }
                })

            // Phase 3: Dash
            .step("dash", 20)
                .onStart(ctx -> {
                    // ctx.log("§lDASHING!§r");
                })
                .onTick(ctx -> {
                    Vec3 dir = (Vec3) ctx.data().get("dash_dir");
                    if (dir != null) {
                        ctx.boss().setDeltaMovement(dir.scale(2.0));
                        
                        // Impact check
                        ServerLevel level = (ServerLevel) ctx.boss().level();
                        level.getEntitiesOfClass(LivingEntity.class, ctx.boss().getBoundingBox().inflate(1.0)).forEach(e -> {
                            if (e != ctx.boss()) {
                                e.hurt(ctx.boss().damageSources().mobAttack(ctx.boss()), 15f);
                                e.setDeltaMovement(e.getDeltaMovement().add(0, 1.2, 0)); // Great knockup
                                level.sendParticles(ParticleTypes.EXPLOSION, e.getX(), e.getY() + 1, e.getZ(), 1, 0, 0, 0, 0);
                            }
                        });
                        
                        // Trailing particles
                        level.sendParticles(ParticleTypes.LARGE_SMOKE, ctx.boss().getX(), ctx.boss().getY() + 1, ctx.boss().getZ(), 3, 0.2, 0.2, 0.2, 0.05);
                    }
                })

            // Phase 4: Recovery
            .step("recovery", 20)
                .onTick(ctx -> ctx.boss().setDeltaMovement(Vec3.ZERO))
            .build();
    }
}
