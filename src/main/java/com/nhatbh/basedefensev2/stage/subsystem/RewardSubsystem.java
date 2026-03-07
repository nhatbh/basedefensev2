package com.nhatbh.basedefensev2.stage.subsystem;

import com.mojang.logging.LogUtils;
import com.nhatbh.basedefensev2.stage.config.WaveRewardConfig;
import com.nhatbh.basedefensev2.stage.core.StageContext;
import com.nhatbh.basedefensev2.stage.events.WaveEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

/**
 * Listens to WaveEvents.LootPhaseStarted and:
 * 1. Grants XP to all arena players
 * 2. Runs each configured command via the server command dispatcher
 * 3. Drops configured item rewards at the arena centre
 */
public class RewardSubsystem {

    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public void onLootPhaseStarted(WaveEvents.LootPhaseStarted event) {
        WaveRewardConfig rewards = event.getFinalWave().rewards;
        ServerLevel level = event.getLevel();

        // 1. XP
        if (rewards.xp > 0) {
            for (ServerPlayer player : event.getPlayers()) {
                player.giveExperiencePoints(rewards.xp);
            }
            LOGGER.info("[RewardSubsystem] Granted {} XP to {} player(s)",
                    rewards.xp, event.getPlayers().size());
        }

        // 2. Commands — run as server's command source
        if (!rewards.commands.isEmpty()) {
            var server = level.getServer();
            var cmdSrc = server.createCommandSourceStack();
            for (String cmd : rewards.commands) {
                try {
                    server.getCommands().performPrefixedCommand(cmdSrc, cmd);
                    LOGGER.info("[RewardSubsystem] Ran command: {}", cmd);
                } catch (Exception e) {
                    LOGGER.error("[RewardSubsystem] Failed command '{}': {}", cmd, e.getMessage());
                }
            }
        }

        // 3. Item drops at arena centre
        if (!rewards.items.isEmpty()) {
            double cx = 0, cy = 65, cz = 0;
            StageContext ctx = StageContext.getOrCreate(level);
            if (ctx.getActiveConfig() != null) {
                var area = ctx.getSpawnArea();
                cx = area.x;
                cy = area.y + 1;
                cz = area.z;
            }

            for (WaveRewardConfig.ItemDropEntry drop : rewards.items) {
                String[] parts = drop.item.split(":", 2);
                ResourceLocation loc = parts.length == 2
                        ? ResourceLocation.parse(drop.item)
                        : ResourceLocation.fromNamespaceAndPath("minecraft", drop.item);
                Item item = ForgeRegistries.ITEMS.getValue(loc);
                if (item == null) {
                    LOGGER.warn("[RewardSubsystem] Unknown item: {}", drop.item);
                    continue;
                }
                ItemStack stack = new ItemStack(item, drop.count);
                ItemEntity itemEntity = new ItemEntity(level, cx, cy, cz, stack);
                itemEntity.setPickUpDelay(20);
                level.addFreshEntity(itemEntity);
            }
            LOGGER.info("[RewardSubsystem] Dropped {} item type(s).", rewards.items.size());
        }
    }
}
