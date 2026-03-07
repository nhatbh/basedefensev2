package com.nhatbh.basedefensev2.boss.utils;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

public class ParticleUtils {

    /**
     * Renders particles in a circle around the center point.
     * @param count Number of particles to spawn along the circumference.
     */
    public static void renderCircle(ServerLevel level, ParticleOptions particle, Vec3 center, double radius, int count, double speed) {
        double angleStep = 360.0 / count;
        for (int i = 0; i < count; i++) {
            double angleRad = Math.toRadians(i * angleStep);
            double dx = -Math.sin(angleRad) * radius;
            double dz = Math.cos(angleRad) * radius;
            level.sendParticles(particle, center.x + dx, center.y, center.z + dz, 1, 0, 0, 0, speed);
        }
    }

    /**
     * Renders particles in an arc (cone base) facing a specific direction.
     * @param count Number of particles to spawn along the arc.
     */
    public static void renderArc(ServerLevel level, ParticleOptions particle, Vec3 center, Vec3 direction, double radius, double arcAngleDegrees, int count, double speed) {
        if (count <= 1) return;
        
        // Find the base yaw angle from the direction vector
        double baseYaw = Math.toDegrees(Math.atan2(-direction.x, direction.z));
        
        double startAngle = baseYaw - (arcAngleDegrees / 2.0);
        double angleStep = arcAngleDegrees / (count - 1);
        
        for (int i = 0; i < count; i++) {
            double currentAngle = startAngle + (i * angleStep);
            double angleRad = Math.toRadians(currentAngle);
            double dx = -Math.sin(angleRad) * radius;
            double dz = Math.cos(angleRad) * radius;
            level.sendParticles(particle, center.x + dx, center.y, center.z + dz, 1, 0, 0, 0, speed);
        }
    }

    /**
     * Renders a filled arc area with particles.
     */
    public static void renderFilledArc(ServerLevel level, ParticleOptions particle, Vec3 center, Vec3 direction, double radius, double arcAngleDegrees, int arcCounts, int radiusCounts, double speed) {
        if (radiusCounts <= 0) return;
        double radiusStep = radius / radiusCounts;
        for (int r = 1; r <= radiusCounts; r++) {
            renderArc(level, particle, center, direction, r * radiusStep, arcAngleDegrees, arcCounts, speed);
        }
    }

    /**
     * Renders a line of particles from start to end.
     * @param count Number of particles to spawn along the line.
     */
    public static void renderLine(ServerLevel level, ParticleOptions particle, Vec3 start, Vec3 end, int count, double speed) {
        if (count <= 1) {
            level.sendParticles(particle, start.x, start.y, start.z, 1, 0, 0, 0, speed);
            return;
        }
        
        Vec3 delta = end.subtract(start);
        Vec3 step = delta.scale(1.0 / (count - 1));
        
        for (int i = 0; i < count; i++) {
            Vec3 pos = start.add(step.scale(i));
            level.sendParticles(particle, pos.x, pos.y, pos.z, 1, 0, 0, 0, speed);
        }
    }
}
