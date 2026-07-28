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
        return getClosestTarget(ctx, 32.0);
    }

    public static boolean isValidTarget(LivingEntity entity) {
        if (entity == null || !entity.isAlive()) return false;
        if (entity instanceof net.minecraft.world.entity.player.Player player) {
            if (player.isCreative() || player.isSpectator()) return false;
            return player.getCapability(com.nhatbh.basedefensev2.sanctity.data.ReviveStateProvider.REVIVE_STATE)
                    .map(state -> !state.isKnockedDown())
                    .orElse(true);
        }
        return true;
    }

    public static LivingEntity getClosestTarget(SkillContext ctx, double radius) {
        List<Player> players = ctx.boss().level().getEntitiesOfClass(Player.class, 
            ctx.boss().getBoundingBox().inflate(radius));
        return players.stream()
            .filter(BossSkillHelper::isValidTarget)
            .min(Comparator.comparingDouble(p -> p.distanceTo(ctx.boss())))
            .orElse(null);
    }

    public static LivingEntity getFurthestTarget(SkillContext ctx, double radius) {
        List<Player> players = ctx.boss().level().getEntitiesOfClass(Player.class, 
            ctx.boss().getBoundingBox().inflate(radius));
        return players.stream()
            .filter(BossSkillHelper::isValidTarget)
            .max(Comparator.comparingDouble(p -> p.distanceTo(ctx.boss())))
            .orElse(null);
    }

    public static LivingEntity getRandomTarget(SkillContext ctx, double radius) {
        List<Player> players = ctx.boss().level().getEntitiesOfClass(Player.class, 
            ctx.boss().getBoundingBox().inflate(radius)).stream()
            .filter(BossSkillHelper::isValidTarget)
            .toList();
        if (players.isEmpty()) return null;
        return players.get(ctx.boss().getRandom().nextInt(players.size()));
    }

    public static java.util.List<LivingEntity> getRandomTargets(SkillContext ctx, double radius, int count) {
        List<Player> players = ctx.boss().level().getEntitiesOfClass(Player.class, 
            ctx.boss().getBoundingBox().inflate(radius)).stream()
            .filter(BossSkillHelper::isValidTarget)
            .collect(java.util.stream.Collectors.toList());
        java.util.Collections.shuffle(players);
        return players.stream().limit(count).map(p -> (LivingEntity)p).collect(java.util.stream.Collectors.toList());
    }

    public static LivingEntity getLowestHealthTarget(SkillContext ctx, double radius) {
        List<Player> players = ctx.boss().level().getEntitiesOfClass(Player.class, 
            ctx.boss().getBoundingBox().inflate(radius));
        return players.stream()
            .filter(BossSkillHelper::isValidTarget)
            .min(Comparator.comparingDouble(p -> p.getHealth()))
            .orElse(null);
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

    public static void fireFastArrow(LivingEntity shooter, Vec3 pos, Vec3 dir, float speed, double damage) {
        net.minecraft.world.entity.projectile.Arrow arrow = new net.minecraft.world.entity.projectile.Arrow(shooter.level(), shooter);
        arrow.setPos(pos.x, pos.y, pos.z);
        arrow.setBaseDamage(damage);
        arrow.shoot(dir.x, dir.y, dir.z, speed, 1.0f);
        arrow.addTag("boss_projectile");
        arrow.addTag(com.nhatbh.basedefensev2.stage.ArenaConstants.ARENA_AFFILIATED_TAG);
        shooter.level().addFreshEntity(arrow);
    }

    public static void depletePoise(SkillContext ctx, float percentage) {
        com.nhatbh.basedefensev2.strength.EntityStrengthData strengthData = com.nhatbh.basedefensev2.strength.EntityStrengthData.get(ctx.boss());
        if (strengthData != null) {
            float depletion = strengthData.maxStrength * (percentage / 100.0f);
            strengthData.currentStrength = Math.max(0, strengthData.currentStrength - depletion);
            strengthData.save(ctx.boss());
            com.nhatbh.basedefensev2.strength.EntityStrengthData.sync(ctx.boss(), strengthData);
        }
    }

    public static void broadcastMessage(LivingEntity boss, String message) {
        if (boss.level().isClientSide) return;
        net.minecraft.network.chat.Component component = net.minecraft.network.chat.Component.literal("§l§c" + message);
        boss.level().players().forEach(p -> p.sendSystemMessage(component));
    }

    public static float calculateMixedDamage(SkillContext ctx, LivingEntity target, float percent, float flat) {
        if (target == null) return flat;
        if (ctx != null && ctx.data().getOrDefault("is_lethal_retaliation", false).equals(true)) {
            // Universal lethal damage: 150% Max HP + 1000 flat
            return (target.getMaxHealth() * 1.5f) + 1000f;
        }
        return (target.getMaxHealth() * (percent / 100.0f)) + flat;
    }

    public static void performDynamicSweep(SkillContext ctx, Vec3 baseDir, double startAngleOffset, double endAngleOffset,
            double radius, float percent, float flat, int duration, boolean applyKnockback, net.minecraft.core.particles.ParticleOptions particle) {
        int tick = (int) ctx.data().getOrDefault("sweep_tick", 0);
        double progress = tick / (double) (duration - 1);
        if (progress > 1.0) progress = 1.0;

        double currentAngleOffset = startAngleOffset + (endAngleOffset - startAngleOffset) * progress;
        if (baseDir != null && ctx.boss().level() instanceof net.minecraft.server.level.ServerLevel level) {
            Vec3 pos = getMovementEntity(ctx).position();

            double angleRad = Math.toRadians(currentAngleOffset);
            double cos = Math.cos(angleRad);
            double sin = Math.sin(angleRad);
            Vec3 rotatedDir = new Vec3(
                    baseDir.x * cos - baseDir.z * sin,
                    0,
                    baseDir.x * sin + baseDir.z * cos).normalize();

            double arcWidth = 35.0;
            com.nhatbh.basedefensev2.boss.utils.ParticleUtils.renderFilledArc(level, particle, pos.add(0, 1, 0), rotatedDir, radius,
                    arcWidth, 3, 3, 0.05);

            @SuppressWarnings("unchecked")
            java.util.List<java.util.UUID> hitTargets = (java.util.List<java.util.UUID>) ctx.data().get("sweep_hits");

            com.nhatbh.basedefensev2.boss.utils.HitboxUtils.getEntitiesInArc(level, LivingEntity.class, pos, rotatedDir,
                    radius, arcWidth, e -> e != ctx.boss() && !hitTargets.contains(e.getUUID())).forEach(target -> {
                hitTargets.add(target.getUUID());
                float damage = calculateMixedDamage(ctx, target, percent, flat);
                target.hurt(ctx.boss().damageSources().mobAttack(ctx.boss()), damage);

                if (applyKnockback) {
                    target.setDeltaMovement(target.getDeltaMovement().add(rotatedDir.scale(2.0).add(0, 0.2, 0)));
                }
            });
        }
        ctx.data().put("sweep_tick", tick + 1);
    }

    public static void clearBeneficialEffects(LivingEntity entity) {
        if (entity == null) return;
        List<net.minecraft.world.effect.MobEffect> beneficial = new java.util.ArrayList<>();
        for (net.minecraft.world.effect.MobEffectInstance instance : entity.getActiveEffects()) {
            if (instance.getEffect().getCategory() == net.minecraft.world.effect.MobEffectCategory.BENEFICIAL) {
                beneficial.add(instance.getEffect());
            }
        }
        for (net.minecraft.world.effect.MobEffect effect : beneficial) {
            entity.removeEffect(effect);
        }
    }
}
