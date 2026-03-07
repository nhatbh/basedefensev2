package com.nhatbh.basedefensev2.boss.impl.testboss;

import com.nhatbh.basedefensev2.boss.skills.SkillContext;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;

public class BossSkillHelper {
    public static Entity getMovementEntity(SkillContext ctx) {
        Entity vehicle = ctx.boss().getVehicle();
        return vehicle != null ? vehicle : ctx.boss();
    }

    public static LivingEntity getClosestTarget(SkillContext ctx) {
        LivingEntity target = null;
        if (ctx.boss() instanceof Mob mob) {
            target = mob.getTarget();
        }
        if (target == null) {
            List<Player> players = ctx.boss().level().getEntitiesOfClass(Player.class, 
                ctx.boss().getBoundingBox().inflate(32));
            target = players.stream()
                .min(Comparator.comparingDouble(p -> p.distanceTo(ctx.boss())))
                .orElse(null);
        }
        return target;
    }

    public static void stopMovement(SkillContext ctx) {
        Entity mover = getMovementEntity(ctx);
        mover.setDeltaMovement(0, mover.getDeltaMovement().y, 0);
        if (mover.onGround()) {
             mover.setDeltaMovement(0, -0.01, 0); // Keep it grounded
        }
    }

    public static void updateTracking(SkillContext ctx) {
        LivingEntity target = getClosestTarget(ctx);
        if (target != null) {
            Entity mover = getMovementEntity(ctx);
            Vec3 dir = target.position().subtract(mover.position()).normalize();
            ctx.data().put("vanguard_dir", dir);
            float yaw = (float) (Math.atan2(-dir.x, dir.z) * (180 / Math.PI));
            mover.setYRot(yaw);
            if (mover instanceof LivingEntity livingMover) {
                livingMover.setYHeadRot(yaw);
                livingMover.setYBodyRot(yaw);
            }
            ctx.boss().setYRot(yaw);
            ctx.boss().setYHeadRot(yaw);
            ctx.boss().setYBodyRot(yaw);
        }
    }
}
