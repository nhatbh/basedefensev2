package com.nhatbh.basedefensev2.boss.impl.generic;

import com.nhatbh.basedefensev2.boss.utils.HitboxUtils;
import com.nhatbh.basedefensev2.boss.utils.ParticleUtils;
import com.nhatbh.basedefensev2.boss.utils.TemporaryBlockManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class StaticConductorManager {

    private static class ConductorObelisk {
        final BlockPos basePos;
        final Vec3 centerPos;
        int ticksRemaining = 600; // 30 seconds

        ConductorObelisk(BlockPos basePos, Vec3 centerPos) {
            this.basePos = basePos;
            this.centerPos = centerPos;
        }
    }

    private final List<ConductorObelisk> activeObelisks = new ArrayList<>();

    public void spawnObelisk(ServerLevel level, Vec3 targetPos) {
        int blockX = (int) Math.floor(targetPos.x);
        int blockZ = (int) Math.floor(targetPos.z);
        int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ);

        BlockPos basePos = new BlockPos(blockX, groundY, blockZ);
        Vec3 centerPos = new Vec3(blockX + 0.5, groundY + 1.5, blockZ + 0.5);

        // Sound effect
        level.playSound(null, centerPos.x, centerPos.y, centerPos.z,
                SoundEvents.COPPER_BREAK, SoundSource.HOSTILE, 2.0f, 0.8f);
        level.playSound(null, centerPos.x, centerPos.y, centerPos.z,
                SoundEvents.TRIDENT_THUNDER, SoundSource.HOSTILE, 1.2f, 1.8f);

        // Place 4-block tall metallic lightning obelisk (30 seconds = 600 ticks)
        BlockPos pos0 = basePos;
        BlockPos pos1 = basePos.above();
        BlockPos pos2 = basePos.above(2);
        BlockPos pos3 = basePos.above(3);

        TemporaryBlockManager.placeTemporaryBlock(level, pos0, Blocks.COPPER_BLOCK.defaultBlockState(), 600);
        TemporaryBlockManager.placeTemporaryBlock(level, pos1, Blocks.CUT_COPPER.defaultBlockState(), 600);
        TemporaryBlockManager.placeTemporaryBlock(level, pos2, Blocks.WAXED_CUT_COPPER.defaultBlockState(), 600);

        // Top block with expiration callback for overload burst
        TemporaryBlockManager.placeTemporaryBlock(level, pos3, Blocks.LIGHTNING_ROD.defaultBlockState(), 600, () -> {
            triggerOverloadBurst(level, centerPos);
        });

        activeObelisks.add(new ConductorObelisk(basePos, centerPos));
    }

    public void tick(ServerLevel level, LivingEntity boss) {
        Iterator<ConductorObelisk> it = activeObelisks.iterator();
        while (it.hasNext()) {
            ConductorObelisk obelisk = it.next();
            obelisk.ticksRemaining--;

            if (obelisk.ticksRemaining <= 0) {
                it.remove();
                continue;
            }

            // Continuous electrical spark particles around the obelisk tip
            Vec3 tipPos = obelisk.centerPos.add(0, 2.0, 0);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, tipPos.x, tipPos.y, tipPos.z, 4, 0.3, 0.4, 0.3, 0.05);

            if (obelisk.ticksRemaining % 20 == 0) {
                // Ticking zap to players standing within 8 blocks (doubled from 4)
                List<Player> nearbyPlayers = HitboxUtils.getEntitiesInCircle(
                        level, Player.class, obelisk.centerPos, 8.0, Player::isAlive);

                for (Player p : nearbyPlayers) {
                    p.hurt(level.damageSources().lightningBolt(), p.getMaxHealth() * 0.02f + 1.0f);
                    Vec3 obeliskTip = obelisk.centerPos.add(0, 1.5, 0);
                    Vec3 playerBody = p.position().add(0, 1.0, 0);
                    ParticleUtils.renderLine(level, ParticleTypes.ELECTRIC_SPARK, obeliskTip, playerBody, 0.3, 0.02);
                }
            }
        }
    }

    public void clear() {
        activeObelisks.clear();
    }

    private static void triggerOverloadBurst(ServerLevel level, Vec3 centerPos) {
        level.playSound(null, centerPos.x, centerPos.y, centerPos.z,
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 2.0f, 1.2f);
        level.playSound(null, centerPos.x, centerPos.y, centerPos.z,
                SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.5f, 1.8f);

        // 8-block radial explosion particles (doubled from 4)
        ParticleUtils.renderCircle(level, ParticleTypes.ELECTRIC_SPARK, centerPos, 8.0, 36, 0.1);
        level.sendParticles(ParticleTypes.EXPLOSION, centerPos.x, centerPos.y + 1.0, centerPos.z, 8, 1.2, 1.2, 1.2, 0.05);

        // Radial burst damage to players within 8 blocks
        List<Player> playersInBurst = HitboxUtils.getEntitiesInCircle(
                level, Player.class, centerPos, 8.0, Player::isAlive);

        for (Player p : playersInBurst) {
            float burstDamage = p.getMaxHealth() * 0.15f + 4.0f;
            p.hurt(level.damageSources().lightningBolt(), burstDamage);
        }
    }
}
