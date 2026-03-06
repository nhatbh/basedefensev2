package com.nhatbh.basedefensev2.stage;

import com.mojang.brigadier.CommandDispatcher;
import com.nhatbh.basedefensev2.stage.core.StageContext;
import com.nhatbh.basedefensev2.stage.core.StageState;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Registers /arena join and /arena leave commands.
 */
public class ArenaCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("arena")
                .then(Commands.literal("join")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            if (player.getServer() == null) return 0;
                            ServerLevel arenaLevel = player.getServer().getLevel(ModDimensions.ARENA);
                            if (arenaLevel == null) return 0;
                            
                            StageContext ctx = StageContext.getOrCreate(arenaLevel);
                            
                            if (!ctx.isActive()) {
                                context.getSource().sendFailure(Component.literal("No stage is currently active!"));
                                return 0;
                            }
                            
                            TeleportManager.requestJoin(player);
                            return 1;
                        }))
                .then(Commands.literal("leave")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            if (player.getServer() == null) return 0;
                            ServerLevel arenaLevel = player.getServer().getLevel(ModDimensions.ARENA);
                            if (arenaLevel == null) return 0;
                            
                            StageContext ctx = StageContext.getOrCreate(arenaLevel);
                            
                            if (ctx.getStageState() != StageState.SCAVENGE && ctx.getStageState() != StageState.ENDED) {
                                context.getSource().sendFailure(Component.literal("You can only leave during the scavenging phase!"));
                                return 0;
                            }
                            
                            TeleportManager.requestLeave(player);
                            return 1;
                        }))
        );
    }
}
