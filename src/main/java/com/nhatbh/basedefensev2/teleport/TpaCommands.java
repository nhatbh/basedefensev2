package com.nhatbh.basedefensev2.teleport;

import com.mojang.brigadier.CommandDispatcher;
import com.nhatbh.basedefensev2.sanctity.data.AltarSavedData;
import com.nhatbh.basedefensev2.sanctity.network.SanctitySyncPacket;
import com.nhatbh.basedefensev2.strength.network.NetworkManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TpaCommands {

    public enum TpaType {
        TPA,     // Requester teleports to Target
        TPAHERE  // Target teleports to Requester
    }

    public static class TpaRequest {
        public final UUID requesterId;
        public final UUID targetId;
        public final TpaType type;
        public int ticksRemaining;

        public TpaRequest(UUID requesterId, UUID targetId, TpaType type) {
            this.requesterId = requesterId;
            this.targetId = targetId;
            this.type = type;
            this.ticksRemaining = 1200; // 60 seconds expiration
        }
    }

    // Key: Target Player UUID, Value: TpaRequest sent to them
    private static final Map<UUID, TpaRequest> PENDING_REQUESTS = new ConcurrentHashMap<>();

    private static final double ENERGY_COST = 30.0;

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        // /tpa <player>
        dispatcher.register(Commands.literal("tpa")
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(context -> {
                            ServerPlayer requester = context.getSource().getPlayerOrException();
                            ServerPlayer target = EntityArgument.getPlayer(context, "target");
                            return createRequest(requester, target, TpaType.TPA);
                        })));

        // /tpahere <player>
        dispatcher.register(Commands.literal("tpahere")
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(context -> {
                            ServerPlayer requester = context.getSource().getPlayerOrException();
                            ServerPlayer target = EntityArgument.getPlayer(context, "target");
                            return createRequest(requester, target, TpaType.TPAHERE);
                        })));

        // /tpaccept
        dispatcher.register(Commands.literal("tpaccept")
                .executes(context -> {
                            ServerPlayer target = context.getSource().getPlayerOrException();
                            return acceptRequest(target);
                        }));

        // /tpdeny
        dispatcher.register(Commands.literal("tpdeny")
                .executes(context -> {
                            ServerPlayer target = context.getSource().getPlayerOrException();
                            return denyRequest(target);
                        }));
    }

    private static int createRequest(ServerPlayer requester, ServerPlayer target, TpaType type) {
        if (requester.getUUID().equals(target.getUUID())) {
            requester.sendSystemMessage(Component.literal("You cannot send a teleport request to yourself!")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        TpaRequest req = new TpaRequest(requester.getUUID(), target.getUUID(), type);
        PENDING_REQUESTS.put(target.getUUID(), req);

        // Message to Requester
        String reqTypeMsg = (type == TpaType.TPA) ? "teleport to them" : "teleport them to you";
        requester.sendSystemMessage(Component.literal("Teleport request sent to ")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(target.getScoreboardName()).withStyle(ChatFormatting.GREEN))
                .append(Component.literal(" to " + reqTypeMsg + ". (Expires in 60s)")));

        // Message to Target with interactive buttons
        Component acceptBtn = Component.literal(" [ACCEPT] ")
                .withStyle(Style.EMPTY
                        .withColor(ChatFormatting.GREEN)
                        .withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpaccept")));

        Component denyBtn = Component.literal(" [DENY] ")
                .withStyle(Style.EMPTY
                        .withColor(ChatFormatting.RED)
                        .withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpdeny")));

        Component requestMsg;
        if (type == TpaType.TPA) {
            requestMsg = Component.literal(requester.getScoreboardName()).withStyle(ChatFormatting.GREEN)
                    .append(Component.literal(" has requested to teleport to you.\n").withStyle(ChatFormatting.YELLOW))
                    .append(acceptBtn)
                    .append(Component.literal("  "))
                    .append(denyBtn);
        } else {
            requestMsg = Component.literal(requester.getScoreboardName()).withStyle(ChatFormatting.GREEN)
                    .append(Component.literal(" has requested you to teleport to them.\n").withStyle(ChatFormatting.YELLOW))
                    .append(acceptBtn)
                    .append(Component.literal("  "))
                    .append(denyBtn);
        }

        target.sendSystemMessage(requestMsg);
        return 1;
    }

    private static int acceptRequest(ServerPlayer target) {
        TpaRequest req = PENDING_REQUESTS.remove(target.getUUID());
        if (req == null) {
            target.sendSystemMessage(Component.literal("You have no pending teleport requests!")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        ServerLevel level = target.serverLevel();
        ServerPlayer requester = level.getServer().getPlayerList().getPlayer(req.requesterId);

        if (requester == null) {
            target.sendSystemMessage(Component.literal("The requesting player is no longer online.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        // Determine energy cost: standard = 30.0, knocked down player = 60.0
        boolean isKnockedDown = false;
        var reqCap = requester.getCapability(com.nhatbh.basedefensev2.sanctity.data.ReviveStateProvider.REVIVE_STATE).orElse(null);
        var targetCap = target.getCapability(com.nhatbh.basedefensev2.sanctity.data.ReviveStateProvider.REVIVE_STATE).orElse(null);
        if ((reqCap != null && reqCap.isKnockedDown()) || (targetCap != null && targetCap.isKnockedDown())) {
            isKnockedDown = true;
        }

        double energyCost = isKnockedDown ? 60.0 : ENERGY_COST;

        // Check base energy (Grace)
        AltarSavedData altarData = AltarSavedData.get(level);
        if (altarData.getGrace() < energyCost) {
            Component lowEnergyMsg = Component.literal("Teleportation failed: Base Energy (Grace) is below " + (int) energyCost + "!" + (isKnockedDown ? " (Knocked down player requires 60 Base Energy)" : ""))
                    .withStyle(ChatFormatting.RED);
            target.sendSystemMessage(lowEnergyMsg);
            requester.sendSystemMessage(lowEnergyMsg);
            return 0;
        }

        // Consume Base Energy
        altarData.setGrace(altarData.getGrace() - energyCost);
        syncSanctityToAll(level, altarData);

        // Perform Teleport
        if (req.type == TpaType.TPA) {
            // Requester teleports to Target
            requester.teleportTo(target.serverLevel(), target.getX(), target.getY(), target.getZ(), target.getYRot(), target.getXRot());
            requester.sendSystemMessage(Component.literal("Teleported to " + target.getScoreboardName() + "! (Consumed " + (int) energyCost + " Base Energy)")
                    .withStyle(ChatFormatting.GREEN));
            target.sendSystemMessage(Component.literal(requester.getScoreboardName() + " has teleported to you.")
                    .withStyle(ChatFormatting.GREEN));
        } else {
            // Target teleports to Requester
            target.teleportTo(requester.serverLevel(), requester.getX(), requester.getY(), requester.getZ(), requester.getYRot(), requester.getXRot());
            target.sendSystemMessage(Component.literal("Teleported to " + requester.getScoreboardName() + "! (Consumed " + (int) energyCost + " Base Energy)")
                    .withStyle(ChatFormatting.GREEN));
            requester.sendSystemMessage(Component.literal(target.getScoreboardName() + " has accepted your teleport request.")
                    .withStyle(ChatFormatting.GREEN));
        }

        return 1;
    }

    private static int denyRequest(ServerPlayer target) {
        TpaRequest req = PENDING_REQUESTS.remove(target.getUUID());
        if (req == null) {
            target.sendSystemMessage(Component.literal("You have no pending teleport requests!")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        target.sendSystemMessage(Component.literal("Teleport request denied.")
                .withStyle(ChatFormatting.YELLOW));

        ServerPlayer requester = target.serverLevel().getServer().getPlayerList().getPlayer(req.requesterId);
        if (requester != null) {
            requester.sendSystemMessage(Component.literal(target.getScoreboardName() + " denied your teleport request.")
                    .withStyle(ChatFormatting.RED));
        }

        return 1;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            Iterator<Map.Entry<UUID, TpaRequest>> iterator = PENDING_REQUESTS.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<UUID, TpaRequest> entry = iterator.next();
                TpaRequest req = entry.getValue();
                req.ticksRemaining--;
                if (req.ticksRemaining <= 0) {
                    iterator.remove();
                }
            }
        }
    }

    private static void syncSanctityToAll(ServerLevel level, AltarSavedData data) {
        var config = com.nhatbh.basedefensev2.config.SanctityConfig.data;
        SanctitySyncPacket packet = new SanctitySyncPacket(data.getSanctity(), data.getGrace(), config.maxSanctity, config.maxGrace, data.getRetriesUsed(), config.maxWorldRetries);
        level.getServer().getPlayerList().getPlayers().forEach(player -> {
            NetworkManager.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), packet);
        });
    }
}
