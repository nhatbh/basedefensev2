package com.nhatbh.basedefensev2.boss.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Mod.EventBusSubscriber(modid = "basedefensev2")
public class TemporaryBlockManager {

    private static class TempBlock {
        final ServerLevel level;
        final BlockPos pos;
        final BlockState originalState;
        final BlockState placedState;
        final long expireGameTime;
        final Runnable onExpire;

        TempBlock(ServerLevel level, BlockPos pos, BlockState originalState, BlockState placedState, long expireGameTime, Runnable onExpire) {
            this.level = level;
            this.pos = pos;
            this.originalState = originalState;
            this.placedState = placedState;
            this.expireGameTime = expireGameTime;
            this.onExpire = onExpire;
        }
    }

    private static final List<TempBlock> TEMP_BLOCKS = new ArrayList<>();

    public static void placeTemporaryBlock(ServerLevel level, BlockPos pos, BlockState newState, int durationTicks) {
        placeTemporaryBlock(level, pos, newState, durationTicks, null);
    }

    public static void placeTemporaryBlock(ServerLevel level, BlockPos pos, BlockState newState, int durationTicks, Runnable onExpire) {
        BlockState current = level.getBlockState(pos);
        // Only replace air, water, lava, or grass/replaceable blocks
        if (current.isAir() || current.canBeReplaced()) {
            level.setBlock(pos, newState, 3);
            long expireTime = level.getGameTime() + durationTicks;
            synchronized (TEMP_BLOCKS) {
                TEMP_BLOCKS.add(new TempBlock(level, pos, current, newState, expireTime, onExpire));
            }
        }
    }

    /**
     * If a player mines or breaks a temporary block before it naturally expires,
     * unregister it so it won't force-overwrite player edits or trigger the expiration callback.
     */
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            BlockPos pos = event.getPos();
            synchronized (TEMP_BLOCKS) {
                Iterator<TempBlock> it = TEMP_BLOCKS.iterator();
                while (it.hasNext()) {
                    TempBlock tb = it.next();
                    if (tb.level == serverLevel && tb.pos.equals(pos)) {
                        it.remove();
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.level.isClientSide) return;

        if (event.level instanceof ServerLevel serverLevel) {
            synchronized (TEMP_BLOCKS) {
                long now = serverLevel.getGameTime();
                Iterator<TempBlock> it = TEMP_BLOCKS.iterator();
                while (it.hasNext()) {
                    TempBlock tb = it.next();
                    if (tb.level == serverLevel && now >= tb.expireGameTime) {
                        BlockState current = serverLevel.getBlockState(tb.pos);
                        // Only revert if the temporary block is still present
                        if (current.is(tb.placedState.getBlock())) {
                            serverLevel.setBlock(tb.pos, tb.originalState, 3);
                            serverLevel.sendParticles(ParticleTypes.CLOUD,
                                    tb.pos.getX() + 0.5, tb.pos.getY() + 0.5, tb.pos.getZ() + 0.5,
                                    3, 0.2, 0.2, 0.2, 0.05);

                            if (tb.onExpire != null) {
                                try {
                                    tb.onExpire.run();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                        it.remove();
                    }
                }
            }
        }
    }

    public static void removeTemporaryBlock(ServerLevel level, BlockPos pos) {
        synchronized (TEMP_BLOCKS) {
            Iterator<TempBlock> it = TEMP_BLOCKS.iterator();
            while (it.hasNext()) {
                TempBlock tb = it.next();
                if (tb.level == level && tb.pos.equals(pos)) {
                    BlockState current = level.getBlockState(tb.pos);
                    if (current.is(tb.placedState.getBlock())) {
                        level.setBlock(tb.pos, tb.originalState, 3);
                    }
                    it.remove();
                }
            }
        }
    }
}
