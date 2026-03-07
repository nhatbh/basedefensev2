package com.nhatbh.basedefensev2.boss.utils;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class HitboxUtils {

    /**
     * Gets all entities of the specified class within a circle (cylinder) around the given position.
     */
    public static <T extends Entity> List<T> getEntitiesInCircle(Level level, Class<T> entityClass, Vec3 center, double radius, Predicate<T> filter) {
        AABB box = new AABB(center.x - radius, center.y - 2, center.z - radius, center.x + radius, center.y + 4, center.z + radius);
        List<T> entities = level.getEntitiesOfClass(entityClass, box, filter);
        List<T> result = new ArrayList<>();
        
        double radiusSqr = radius * radius;
        for (T entity : entities) {
            double dx = entity.getX() - center.x;
            double dz = entity.getZ() - center.z;
            if (dx * dx + dz * dz <= radiusSqr) {
                result.add(entity);
            }
        }
        return result;
    }

    /**
     * Gets all entities of the specified class within an arc (cone) originating from pos, facing dir.
     */
    public static <T extends Entity> List<T> getEntitiesInArc(Level level, Class<T> entityClass, Vec3 pos, Vec3 dir, double radius, double angleDegrees, Predicate<T> filter) {
        List<T> inCircle = getEntitiesInCircle(level, entityClass, pos, radius, filter);
        List<T> result = new ArrayList<>();
        
        if (dir.lengthSqr() == 0) return result;
        Vec3 normalizedDir = dir.normalize();
        double cosHalfAngle = Math.cos(Math.toRadians(angleDegrees / 2.0));
        
        for (T entity : inCircle) {
            Vec3 toEntity = entity.position().subtract(pos).normalize();
            if (normalizedDir.dot(toEntity) >= cosHalfAngle) {
                result.add(entity);
            }
        }
        return result;
    }

    /**
     * Gets all entities of the specified class within a rectangular line segment from start to end with the given width.
     */
    public static <T extends Entity> List<T> getEntitiesInLine(Level level, Class<T> entityClass, Vec3 start, Vec3 end, double width, Predicate<T> filter) {
        Vec3 dir = end.subtract(start);
        double length = dir.length();
        if (length == 0) return getEntitiesInCircle(level, entityClass, start, width / 2.0, filter);
        
        Vec3 normalizedDir = dir.normalize();
        
        AABB box = new AABB(Math.min(start.x, end.x), Math.min(start.y, end.y), Math.min(start.z, end.z),
                            Math.max(start.x, end.x), Math.max(start.y, end.y), Math.max(start.z, end.z))
                .inflate(width, 2.0, width);
                
        List<T> entities = level.getEntitiesOfClass(entityClass, box, filter);
        List<T> result = new ArrayList<>();
        
        double radiusSqr = (width / 2.0) * (width / 2.0);
        for (T entity : entities) {
            Vec3 p = entity.position();
            Vec3 toP = p.subtract(start);
            // Project toP onto normalizedDir
            double t = toP.dot(normalizedDir);
            
            if (t >= 0 && t <= length) {
                // Point on line segment between start and end
                Vec3 proj = start.add(normalizedDir.scale(t));
                double distanceSqr = proj.distanceToSqr(p);
                if (distanceSqr <= radiusSqr) {
                    result.add(entity);
                }
            } else {
                // Check distance to endpoints if desired (capsule collision)
                if (p.distanceToSqr(start) <= radiusSqr || p.distanceToSqr(end) <= radiusSqr) {
                    result.add(entity);
                }
            }
        }
        return result;
    }
}
