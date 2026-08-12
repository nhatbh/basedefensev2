package com.nhatbh.basedefensev2.sanctity.events;

import com.nhatbh.basedefensev2.stage.ModDimensions;
import com.nhatbh.basedefensev2.stage.core.StageContext;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class ForfeitManager {
    private static final Set<UUID> votedPlayers = new HashSet<>();
    private static boolean voteActive = false;
    private static long voteEndTime = 0;
    private static int activeStageOrder = 0;

    public static boolean isVoteActive() {
        if (voteActive && System.currentTimeMillis() > voteEndTime) {
            voteActive = false;
            votedPlayers.clear();
        }
        return voteActive;
    }

    public static int handleForfeitCommand(ServerPlayer player) {
        if (player.getServer() == null) return 0;

        ServerLevel arenaLevel = player.getServer().getLevel(ModDimensions.ARENA);
        ServerLevel overworld = player.getServer().getLevel(net.minecraft.world.level.Level.OVERWORLD);
        if (arenaLevel == null || overworld == null) return 0;

        StageContext stageCtx = StageContext.getOrCreate(arenaLevel);
        if (!stageCtx.isActive()) {
            player.sendSystemMessage(Component.literal("No stage is currently active to forfeit!").withStyle(ChatFormatting.RED));
            return 0;
        }

        int stageOrder = stageCtx.getActiveConfig() != null 
                ? stageCtx.getActiveConfig().order 
                : (com.nhatbh.basedefensev2.level.WorldLevelSavedData.get(overworld).getWorldLevel() + 1);

        long now = System.currentTimeMillis();
        if (voteActive && (now > voteEndTime || activeStageOrder != stageOrder)) {
            voteActive = false;
            votedPlayers.clear();
        }

        if (votedPlayers.contains(player.getUUID())) {
            player.sendSystemMessage(Component.literal("You have already voted to forfeit!").withStyle(ChatFormatting.RED));
            return 0;
        }

        votedPlayers.add(player.getUUID());

        List<ServerPlayer> allPlayers = player.getServer().getPlayerList().getPlayers();
        int totalPlayers = allPlayers.size();
        int requiredVotes = Math.max(1, totalPlayers); // 100% votes required

        if (!voteActive) {
            voteActive = true;
            voteEndTime = now + 120_000L; // 2 minutes (120 seconds) expiration
            activeStageOrder = stageOrder;

            Component startMsg = Component.literal("[FORFEIT VOTE] " + player.getScoreboardName() 
                    + " initiated a vote to forfeit Stage " + stageOrder + "! Type /ff or /forfeit to vote YES. (" 
                    + votedPlayers.size() + "/" + requiredVotes + " votes - Expires in 2m)").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD);

            for (ServerPlayer p : allPlayers) {
                p.sendSystemMessage(startMsg);
            }
        } else {
            Component voteMsg = Component.literal("[FORFEIT VOTE] " + player.getScoreboardName() 
                    + " voted YES to forfeit. (" + votedPlayers.size() + "/" + requiredVotes + " votes)").withStyle(ChatFormatting.YELLOW);

            for (ServerPlayer p : allPlayers) {
                p.sendSystemMessage(voteMsg);
            }
        }

        if (votedPlayers.size() >= requiredVotes) {
            voteActive = false;
            votedPlayers.clear();

            Component passMsg = Component.literal("[FORFEIT] The team has voted to surrender Stage " + stageOrder + "!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
            for (ServerPlayer p : allPlayers) {
                p.sendSystemMessage(passMsg);
            }

            stageCtx.endStageOnGameOver(arenaLevel);
            SanctityEventHandler.triggerSoftGameOver(overworld, stageOrder);
        }

        return 1;
    }

    public static void tick(MinecraftServer server) {
        if (voteActive && System.currentTimeMillis() > voteEndTime) {
            voteActive = false;
            votedPlayers.clear();
            Component expireMsg = Component.literal("[FORFEIT VOTE] Forfeit vote expired. Surrender failed.").withStyle(ChatFormatting.RED);
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                p.sendSystemMessage(expireMsg);
            }
        }
    }
}
