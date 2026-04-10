package com.nhatbh.basedefensev2.sanctity.duel;

import com.mojang.brigadier.CommandDispatcher;
import com.nhatbh.basedefensev2.BaseDefenseMod;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = BaseDefenseMod.MODID)
public class DuelCommand {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("duel")
            .then(Commands.argument("target", EntityArgument.player())
                .executes(context -> {
                    ServerPlayer proposer = context.getSource().getPlayerOrException();
                    ServerPlayer target = EntityArgument.getPlayer(context, "target");

                    if (proposer.equals(target)) {
                        context.getSource().sendFailure(Component.literal("You cannot duel yourself!"));
                        return 0;
                    }

                    DuelManager.proposeDuel(proposer, target);
                    
                    proposer.sendSystemMessage(Component.literal("Duel proposal sent to " + target.getScoreboardName()));
                    
                    target.sendSystemMessage(Component.literal(proposer.getScoreboardName() + " has proposed a duel! ")
                        .append(Component.literal("[ACCEPT]")
                            .withStyle(Style.EMPTY.withColor(0x00FF00)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/duel accept " + proposer.getScoreboardName()))))
                        .append(" ")
                        .append(Component.literal("[DECLINE]")
                            .withStyle(Style.EMPTY.withColor(0xFF0000)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/duel decline " + proposer.getScoreboardName())))));
                    
                    return 1;
                }))
            .then(Commands.literal("accept")
                .then(Commands.argument("proposer", EntityArgument.player())
                    .executes(context -> {
                        ServerPlayer acceptor = context.getSource().getPlayerOrException();
                        ServerPlayer proposer = EntityArgument.getPlayer(context, "proposer");

                        if (DuelManager.hasRequest(acceptor, proposer)) {
                            DuelManager.acceptDuel(acceptor, proposer);
                            acceptor.sendSystemMessage(Component.literal("Duel started with " + proposer.getScoreboardName() + "!"));
                            proposer.sendSystemMessage(Component.literal("Duel started with " + acceptor.getScoreboardName() + "!"));
                            return 1;
                        } else {
                            context.getSource().sendFailure(Component.literal("No pending duel request from " + proposer.getScoreboardName()));
                            return 0;
                        }
                    })))
            .then(Commands.literal("decline")
                .then(Commands.argument("proposer", EntityArgument.player())
                    .executes(context -> {
                        ServerPlayer decliner = context.getSource().getPlayerOrException();
                        ServerPlayer proposer = EntityArgument.getPlayer(context, "proposer");

                        if (DuelManager.hasRequest(decliner, proposer)) {
                            DuelManager.declineDuel(decliner, proposer);
                            decliner.sendSystemMessage(Component.literal("Duel request declined."));
                            proposer.sendSystemMessage(Component.literal(decliner.getScoreboardName() + " declined your duel request."));
                            return 1;
                        } else {
                            context.getSource().sendFailure(Component.literal("No pending duel request from " + proposer.getScoreboardName()));
                            return 0;
                        }
                    })))
            .then(Commands.literal("end")
                .then(Commands.argument("opponent", EntityArgument.player())
                    .executes(context -> {
                        ServerPlayer closer = context.getSource().getPlayerOrException();
                        ServerPlayer opponent = EntityArgument.getPlayer(context, "opponent");

                        if (DuelManager.isInDuel(closer.getUUID(), opponent.getUUID())) {
                            DuelManager.endDuel(closer.getUUID(), opponent.getUUID());
                            closer.sendSystemMessage(Component.literal("Duel ended with " + opponent.getScoreboardName()));
                            opponent.sendSystemMessage(Component.literal(closer.getScoreboardName() + " ended the duel."));
                            return 1;
                        } else {
                            context.getSource().sendFailure(Component.literal("You are not in a duel with " + opponent.getScoreboardName()));
                            return 0;
                        }
                    })))
        );
    }
}
