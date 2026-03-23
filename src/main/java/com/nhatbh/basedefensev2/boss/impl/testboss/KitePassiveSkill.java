package com.nhatbh.basedefensev2.boss.impl.testboss;

import com.nhatbh.basedefensev2.boss.skills.PassiveSkill;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

public class KitePassiveSkill implements PassiveSkill {
    private final float minDistance;
    private final float maxDistance;
    private final float speed;

    public KitePassiveSkill(float minDistance, float maxDistance, float speed) {
        this.minDistance = minDistance;
        this.maxDistance = maxDistance;
        this.speed = speed;
    }

    @Override
    public void tick(LivingEntity boss) {
        if (boss.level().isClientSide) return;

        LivingEntity target = null;
        if (boss instanceof Mob mob) {
            target = mob.getTarget();
        }

        if (target == null) return;

        double distSq = boss.distanceToSqr(target);
        double minDistSq = minDistance * minDistance;
        double maxDistSq = maxDistance * maxDistance;

        Entity mover = boss.getVehicle() != null ? boss.getVehicle() : boss;
        if (mover == null) return;
        Vec3 toTarget = target.position().subtract(boss.position()).normalize();

        if (distSq < minDistSq) {
            // Too close, move away
            Vec3 moveDir = toTarget.scale(-speed);
            mover.setDeltaMovement(moveDir.x, mover.getDeltaMovement().y, moveDir.z);
            updateRotation(boss, mover, toTarget.scale(-1));
        } else if (distSq > maxDistSq) {
            // Too far, move closer
            Vec3 moveDir = toTarget.scale(speed);
            mover.setDeltaMovement(moveDir.x, mover.getDeltaMovement().y, moveDir.z);
            updateRotation(boss, mover, toTarget);
        } else {
            // In range, stop or slow down (optional: circle the target)
            mover.setDeltaMovement(mover.getDeltaMovement().scale(0.8));
            updateRotation(boss, mover, toTarget);
        }
    }

    private void updateRotation(LivingEntity boss, Entity mover, Vec3 dir) {
        float yaw = (float) (Math.atan2(-dir.x, dir.z) * (180 / Math.PI));
        mover.setYRot(yaw);
        if (mover instanceof LivingEntity livingMover) {
            livingMover.setYHeadRot(yaw);
            livingMover.setYBodyRot(yaw);
        }
        boss.setYRot(yaw);
        boss.setYHeadRot(yaw);
        boss.setYBodyRot(yaw);
    }
}
