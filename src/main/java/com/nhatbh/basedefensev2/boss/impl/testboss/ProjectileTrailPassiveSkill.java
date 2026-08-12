package com.nhatbh.basedefensev2.boss.impl.testboss;

import com.nhatbh.basedefensev2.boss.skills.PassiveSkill;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

public class ProjectileTrailPassiveSkill implements PassiveSkill {
    @Override
    public void tick(LivingEntity boss) {
        if (boss.level() instanceof ServerLevel level) {
            // Find all projectiles tagged with boss_projectile in a 64 block radius
            List<Entity> projectiles = level.getEntitiesOfClass(Entity.class, boss.getBoundingBox().inflate(64),
                    e -> e.getTags().contains("boss_projectile"));
            for (Entity p : projectiles) {
                level.sendParticles(ParticleTypes.WITCH, p.getX(), p.getY(), p.getZ(), 2, 0.05, 0.05, 0.05, 0.01);
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, p.getX(), p.getY(), p.getZ(), 1, 0, 0, 0, 0);
            }
        }
    }
}
