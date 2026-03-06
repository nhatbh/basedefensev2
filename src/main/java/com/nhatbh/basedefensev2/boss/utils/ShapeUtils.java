package com.nhatbh.basedefensev2.boss.utils;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class ShapeUtils {

    public static ShapeResult circle(Entity origin, double radius) {
        Level level = origin.level();
        AABB box = new AABB(
            origin.getX() - radius, origin.getY() - 1, origin.getZ() - radius,
            origin.getX() + radius, origin.getY() + 3, origin.getZ() + radius
        );
        
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, box, entity -> {
            if (entity == origin) return false;
            double distanceSq = entity.distanceToSqr(origin.getX(), entity.getY(), origin.getZ());
            return distanceSq <= radius * radius;
        });

        return new ShapeResult(origin, targets);
    }

    public static ShapeResult ring(Entity origin, double innerRadius, double outerRadius) {
        Level level = origin.level();
        AABB box = new AABB(
            origin.getX() - outerRadius, origin.getY() - 1, origin.getZ() - outerRadius,
            origin.getX() + outerRadius, origin.getY() + 3, origin.getZ() + outerRadius
        );
        
        double innerSq = innerRadius * innerRadius;
        double outerSq = outerRadius * outerRadius;

        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, box, entity -> {
            if (entity == origin) return false;
            double distanceSq = entity.distanceToSqr(origin.getX(), entity.getY(), origin.getZ());
            return distanceSq >= innerSq && distanceSq <= outerSq;
        });

        return new ShapeResult(origin, targets);
    }

    public static ShapeResult cone(Entity origin, double length, double fovRadians) {
        Level level = origin.level();
        AABB box = origin.getBoundingBox().inflate(length);

        Vec3 look = origin.getLookAngle();
        Vec3 originPos = origin.position();

        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, box, entity -> {
            if (entity == origin) return false;
            Vec3 dir = entity.position().subtract(originPos).normalize();
            double angle = Math.acos(look.dot(dir));
            if (angle > fovRadians / 2.0) return false;
            
            return entity.distanceToSqr(origin) <= length * length;
        });

        return new ShapeResult(origin, targets);
    }

    public static class ShapeResult {
        private final Entity origin;
        private final List<LivingEntity> targets;

        public ShapeResult(Entity origin, List<LivingEntity> targets) {
            this.origin = origin;
            this.targets = targets;
        }

        public ShapeResult dealDamage(float amount) {
            for (LivingEntity target : targets) {
                target.hurt(origin.damageSources().mobAttack((LivingEntity) origin), amount);
            }
            return this;
        }

        public ShapeResult knockback(double strength) {
            return this.applyKnockback(strength, 1.0);
        }

        public ShapeResult heavyKnockback(double strength) {
            return this.applyKnockback(strength, 2.0);
        }

        private ShapeResult applyKnockback(double strength, double vertical) {
            for (LivingEntity target : targets) {
                Vec3 dir = target.position().subtract(origin.position()).normalize();
                target.knockback(strength, -dir.x, -dir.z); // Note standard knockback signs
                target.setDeltaMovement(target.getDeltaMovement().add(0, vertical * 0.2, 0));
            }
            return this;
        }
        
        public List<LivingEntity> getTargets() {
            return targets;
        }
    }
}
