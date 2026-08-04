package com.nhatbh.basedefensev2.boss.impl.spells;

import com.nhatbh.basedefensev2.boss.skills.ActiveSequence;
import com.nhatbh.basedefensev2.boss.skills.SkillContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Targeted player search helpers for boss skill sequences.
 */
public class TargetUtils {

    public static Player getLowestHpPlayer(LivingEntity boss, double radius) {
        if (boss == null || boss.level().isClientSide) return null;
        List<ServerPlayer> players = boss.level().getEntitiesOfClass(
                ServerPlayer.class, 
                boss.getBoundingBox().inflate(radius), 
                p -> p.isAlive() && !p.isSpectator()
        );

        return players.stream()
                .min(Comparator.comparingDouble(Player::getHealth))
                .orElse(null);
    }

    public static Player getFurthestPlayer(LivingEntity boss, double radius) {
        if (boss == null || boss.level().isClientSide) return null;
        List<ServerPlayer> players = boss.level().getEntitiesOfClass(
                ServerPlayer.class, 
                boss.getBoundingBox().inflate(radius), 
                p -> p.isAlive() && !p.isSpectator()
        );

        return players.stream()
                .max(Comparator.comparingDouble(p -> p.distanceToSqr(boss)))
                .orElse(null);
    }

    public static Player getClosestPlayer(LivingEntity boss, double radius) {
        if (boss == null || boss.level().isClientSide) return null;
        List<ServerPlayer> players = boss.level().getEntitiesOfClass(
                ServerPlayer.class, 
                boss.getBoundingBox().inflate(radius), 
                p -> p.isAlive() && !p.isSpectator()
        );

        return players.stream()
                .min(Comparator.comparingDouble(p -> p.distanceToSqr(boss)))
                .orElse(null);
    }
}
