package com.nhatbh.basedefensev2.events;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

/**
 * Periodically clears dropped ground items every 5 minutes (6000 ticks).
 * Items dropped within 64 blocks of a player are stored in their personal Lost
 * & Found chest (/lostitems).
 * On each new cycle, unclaimed items from the previous cycle in the chest are
 * wiped.
 */
@Mod.EventBusSubscriber(modid = "basedefensev2")
public class ItemClearHandler {

    private static final int CLEAR_INTERVAL_TICKS = 6000; // 5 minutes (300 seconds)
    private static final double NEARBY_PLAYER_RADIUS_SQR = 64.0 * 64.0; // 64 blocks radius
    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        MinecraftServer server = event.getServer();
        if (server == null) {
            return;
        }

        // When a stage is happening, suspend regular periodic item clearing
        ServerLevel arenaLevel = server.getLevel(com.nhatbh.basedefensev2.stage.ModDimensions.ARENA);
        if (arenaLevel != null) {
            com.nhatbh.basedefensev2.stage.core.StageContext stageCtx = com.nhatbh.basedefensev2.stage.core.StageContext.getOrCreate(arenaLevel);
            if (stageCtx.isActive() && stageCtx.getStageState() != null && stageCtx.getStageState() != com.nhatbh.basedefensev2.stage.core.StageState.ENDED) {
                return;
            }
        }

        tickCounter++;
        int remainingTicks = CLEAR_INTERVAL_TICKS - tickCounter;

        // Warnings at specific thresholds
        if (remainingTicks == 1200) { // 60 seconds remaining
            broadcastWarning(server, "§c[ClearLag] §eAll dropped ground items will be cleared in §c60 seconds§e!");
        } else if (remainingTicks == 600) { // 30 seconds remaining
            broadcastWarning(server, "§c[ClearLag] §eAll dropped ground items will be cleared in §c30 seconds§e!");
        } else if (remainingTicks == 200) { // 10 seconds remaining
            broadcastWarning(server, "§c[ClearLag] §eAll dropped ground items will be cleared in §c10 seconds§e!");
        } else if (remainingTicks == 100) { // 5 seconds remaining
            broadcastWarning(server, "§c[ClearLag] §eAll dropped ground items will be cleared in §c5 seconds§e!");
        } else if (remainingTicks <= 0) {
            performItemSweep(server);
            tickCounter = 0;
        }
    }

    /**
     * Executes the item sweep cycle:
     * 1. Force closes active open recovery chest menus to prevent duplication.
     * 2. Clears previous cycle's unclaimed items in storage.
     * 3. Stores items near players (<= 64 blocks), permanently discarding items far
     * from players.
     * 4. Sends interactive click-to-open chat notifications to players who had
     * items saved.
     *
     * @return total items stored for nearby players
     */
    public static int performItemSweep(MinecraftServer server) {
        // Anti-Dupe 1: Force close any open chest containers across all players
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.containerMenu instanceof ChestMenu) {
                player.closeContainer();
            }
        }

        ServerLevel overworld = server.getLevel(net.minecraft.world.level.Level.OVERWORLD);
        if (overworld == null)
            return 0;

        RecoveredItemSavedData data = RecoveredItemSavedData.get(overworld);

        // Wipe unclaimed items from the previous cycle
        data.clearAll();

        Set<UUID> playersWithStoredItems = new HashSet<>();
        int totalStored = 0;

        for (ServerLevel level : server.getAllLevels()) {
            List<ItemEntity> itemsToClear = new ArrayList<>(level.getEntities(EntityType.ITEM, entity -> true));

            for (ItemEntity itemEntity : itemsToClear) {
                if (!itemEntity.isAlive())
                    continue;

                ItemStack stack = itemEntity.getItem();
                if (stack.isEmpty()) {
                    itemEntity.discard();
                    continue;
                }

                // Find nearest player within 64 blocks in this level
                ServerPlayer nearestPlayer = null;
                double nearestDistSqr = NEARBY_PLAYER_RADIUS_SQR;

                for (ServerPlayer player : level.players()) {
                    double distSqr = player.distanceToSqr(itemEntity);
                    if (distSqr <= nearestDistSqr) {
                        nearestDistSqr = distSqr;
                        nearestPlayer = player;
                    }
                }

                // Only store if an online player is nearby (<= 64 blocks)
                if (nearestPlayer != null) {
                    boolean stored = data.addItem(nearestPlayer.getUUID(), stack);
                    if (stored) {
                        totalStored++;
                        playersWithStoredItems.add(nearestPlayer.getUUID());
                    }
                }

                // Always discard entity from world
                itemEntity.discard();
            }
        }

        // Notify server
        server.getPlayerList().broadcastSystemMessage(
                Component.literal(
                        "§a[ClearLag] §eGround item sweep complete! Unclaimed items from last cycle were cleared."),
                false);

        // Send clickable chat notification to players with recovered items
        for (UUID uuid : playersWithStoredItems) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                MutableComponent clickBtn = Component.literal(" §e[§6§lCLAIM ITEMS§r§e]")
                        .withStyle(style -> style.withBold(true)
                                .withColor(ChatFormatting.GOLD)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/lostitems"))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                        Component.literal("§eClick to open your Lost & Found chest"))));

                Component msg = Component
                        .literal("§a[ClearLag] §eYour nearby dropped items were saved to recovery storage!")
                        .append(clickBtn);

                player.sendSystemMessage(msg);
            }
        }

        return totalStored;
    }

    private static void broadcastWarning(MinecraftServer server, String message) {
        server.getPlayerList().broadcastSystemMessage(Component.literal(message), false);
    }

    public static void openRecoveryChest(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        RecoveredItemSavedData data = RecoveredItemSavedData.get(level);

        if (!data.hasItems(player.getUUID())) {
            player.sendSystemMessage(Component.literal("§c[ClearLag] §eYou have no recovered items in your stash."));
            return;
        }

        // Single authoritative SimpleContainer instance per player UUID
        SimpleContainer container = data.getOrCreateContainer(player.getUUID());

        player.openMenu(new SimpleMenuProvider(
                (containerId, playerInventory, p) -> ChestMenu.sixRows(containerId, playerInventory, container),
                Component.literal("§6[Lost & Found] Recovered Items")));
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("lostitems")
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    openRecoveryChest(player);
                    return 1;
                }));

        dispatcher.register(Commands.literal("recovereditems")
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    openRecoveryChest(player);
                    return 1;
                }));

        dispatcher.register(Commands.literal("clearitems")
                .then(Commands.literal("claim")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            openRecoveryChest(player);
                            return 1;
                        }))
                .then(Commands.literal("now")
                        .requires(s -> s.hasPermission(2))
                        .executes(context -> {
                            MinecraftServer server = context.getSource().getServer();
                            int stored = performItemSweep(server);
                            tickCounter = 0;
                            context.getSource().sendSuccess(() -> Component
                                    .literal("§a[ClearLag] Manually swept nearby items (" + stored + " stored)."),
                                    true);
                            return 1;
                        }))
                .then(Commands.literal("status")
                        .executes(context -> {
                            int remainingSecs = (CLEAR_INTERVAL_TICKS - tickCounter) / 20;
                            int mins = remainingSecs / 60;
                            int secs = remainingSecs % 60;
                            String timeStr = mins > 0 ? mins + "m " + secs + "s" : secs + "s";
                            context.getSource().sendSuccess(() -> Component.literal(
                                    "§c[ClearLag] §eNext item clear in: §c" + timeStr + " §7(Use /lostitems to claim)"),
                                    false);
                            return 1;
                        }))
                .executes(context -> {
                    int remainingSecs = (CLEAR_INTERVAL_TICKS - tickCounter) / 20;
                    int mins = remainingSecs / 60;
                    int secs = remainingSecs % 60;
                    String timeStr = mins > 0 ? mins + "m " + secs + "s" : secs + "s";
                    context.getSource()
                            .sendSuccess(() -> Component.literal(
                                    "§c[ClearLag] §eNext item clear in: §c" + timeStr + " §7(Use /lostitems to claim)"),
                                    false);
                    return 1;
                }));

        dispatcher.register(Commands.literal("clearlag")
                .then(Commands.literal("claim")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            openRecoveryChest(player);
                            return 1;
                        }))
                .then(Commands.literal("now")
                        .requires(s -> s.hasPermission(2))
                        .executes(context -> {
                            MinecraftServer server = context.getSource().getServer();
                            int stored = performItemSweep(server);
                            tickCounter = 0;
                            context.getSource().sendSuccess(() -> Component
                                    .literal("§a[ClearLag] Manually swept nearby items (" + stored + " stored)."),
                                    true);
                            return 1;
                        }))
                .then(Commands.literal("status")
                        .executes(context -> {
                            int remainingSecs = (CLEAR_INTERVAL_TICKS - tickCounter) / 20;
                            int mins = remainingSecs / 60;
                            int secs = remainingSecs % 60;
                            String timeStr = mins > 0 ? mins + "m " + secs + "s" : secs + "s";
                            context.getSource().sendSuccess(
                                    () -> Component.literal("§c[ClearLag] §eNext item clear in: §c" + timeStr), false);
                            return 1;
                        }))
                .executes(context -> {
                    int remainingSecs = (CLEAR_INTERVAL_TICKS - tickCounter) / 20;
                    int mins = remainingSecs / 60;
                    int secs = remainingSecs % 60;
                    String timeStr = mins > 0 ? mins + "m " + secs + "s" : secs + "s";
                    context.getSource().sendSuccess(
                            () -> Component.literal("§c[ClearLag] §eNext item clear in: §c" + timeStr), false);
                    return 1;
                }));
    }
}
