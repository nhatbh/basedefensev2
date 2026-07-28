package com.nhatbh.basedefensev2.stage;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.nhatbh.basedefensev2.boss.core.BossComponent;
import com.nhatbh.basedefensev2.boss.core.BossDefinition;
import com.nhatbh.basedefensev2.boss.core.BossManager;
import com.nhatbh.basedefensev2.registry.ModBosses;
import com.nhatbh.basedefensev2.stage.core.StageContext;
import com.nhatbh.basedefensev2.stage.core.StageState;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Mod.EventBusSubscriber(modid = "basedefensev2")
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

                            if (player.level().dimension().equals(ModDimensions.ARENA)) {
                                context.getSource().sendFailure(Component.literal("You are already in the arena!"));
                                return 0;
                            }

                            StageContext ctx = StageContext.getOrCreate(arenaLevel);

                            if (!ctx.isActive()) {
                                context.getSource().sendFailure(Component.literal("No stage is currently active!"));
                                return 0;
                            }

                            TeleportManager.requestJoin(player);
                            return 1;
                        }))
                .then(Commands.literal("ready")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            if (player.getServer() == null) return 0;
                            ServerLevel arenaLevel = player.getServer().getLevel(ModDimensions.ARENA);
                            if (arenaLevel == null) return 0;

                            StageContext ctx = StageContext.getOrCreate(arenaLevel);
                            if (ctx.getStageState() != StageState.RETRY_INTERMISSION) {
                                context.getSource().sendFailure(Component.literal("No retry intermission is currently active!"));
                                return 0;
                            }

                            context.getSource().sendSuccess(() -> Component.literal("§a[Borrowed Time] Preparation cut short! Commencing stage retry immediately..."), true);
                            ctx.startRetryStage(arenaLevel);
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
                .then(Commands.literal("status")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            if (player.getServer() == null) return 0;
                            ServerLevel arenaLevel = player.getServer().getLevel(ModDimensions.ARENA);
                            if (arenaLevel == null) return 0;

                            StageContext ctx = StageContext.getOrCreate(arenaLevel);

                            if (ctx.isActive()) {
                                var config = ctx.getActiveConfig();
                                String stageId = config != null ? config.id : "Unknown";
                                int currentWave = ctx.getCurrentWaveIndex() + 1;
                                int totalWaves = config != null && config.waves != null ? config.waves.size() : 0;
                                String stateName = ctx.getStageState() != null ? ctx.getStageState().name() : "NONE";

                                String nextStageId = ctx.getNextStageId();
                                String nextStageStr = nextStageId != null ? nextStageId : "None";
                                context.getSource().sendSuccess(() -> Component.literal("§6[The Rift Status] §eCurrent Stage: §c" + stageId 
                                        + " §7(Assault " + currentWave + "/" + totalWaves + " - " + stateName + ") | §eNext Stage: §c" + nextStageStr), false);
                            } else {
                                int ticksRemaining = ctx.getTicksUntilNextStage(arenaLevel);
                                String nextStageId = ctx.getNextStageId();
                                String nextStageStr = nextStageId != null ? nextStageId : "None";
                                if (ticksRemaining < 0) {
                                    context.getSource().sendSuccess(() -> Component.literal("§6[The Rift Status] §eNo active stage. All trials have been completed!"), false);
                                } else {
                                    int secondsRemaining = ticksRemaining / 20;
                                    int minutes = secondsRemaining / 60;
                                    int seconds = secondsRemaining % 60;
                                    String timeStr = minutes > 0 ? minutes + "m " + seconds + "s" : seconds + "s";

                                    context.getSource().sendSuccess(() -> Component.literal("§6[The Rift Status] §eNo active stage. Next Stage: §c" + nextStageStr + " §7(Arrives in: " + timeStr + ")"), false);
                                }
                            }
                            return 1;
                        }))
                .then(Commands.literal("spawn_boss")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.argument("boss_id", StringArgumentType.string())
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    String bossId = StringArgumentType.getString(context, "boss_id");

                                    BossDefinition def = ModBosses.get(bossId);
                                    if (def == null) {
                                        context.getSource().sendFailure(Component.literal("Unknown boss ID: " + bossId));
                                        return 0;
                                    }

                                    ResourceLocation entityLoc = ResourceLocation.parse(def.getBaseEntity());
                                    EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(entityLoc);
                                    if (type == null) {
                                        context.getSource().sendFailure(Component.literal("Unknown base entity: " + def.getBaseEntity()));
                                        return 0;
                                    }

                                    Entity entity = type.create(player.level());
                                    if (entity instanceof LivingEntity living) {
                                        Vec3 pos = player.position();
                                        living.setPos(pos.x, pos.y, pos.z);

                                        BossComponent comp = new BossComponent(def);
                                        BossManager.registerBoss(living, comp);

                                        living.setCustomName(Component.literal(bossId.toUpperCase()));
                                        living.setCustomNameVisible(true);

                                        player.level().addFreshEntity(living);
                                        context.getSource().sendSuccess(() -> Component.literal("Spawned boss: " + bossId), true);
                                        return 1;
                                    } else {
                                        context.getSource().sendFailure(Component.literal("Base entity must be a LivingEntity!"));
                                        return 0;
                                    }
                                })))
                .then(Commands.literal("dump_mobs")
                        .requires(s -> s.hasPermission(2))
                        .executes(context -> {
                            return dumpHostileMobs(context.getSource());
                        }))
        );
    }

    public static int dumpHostileMobs(CommandSourceStack source) {
        try {
            Path outputPath = FMLPaths.CONFIGDIR.get().resolve("hostile_mobs.txt");
            File outFile = outputPath.toFile();

            List<String> hostileMobIds = new ArrayList<>();

            for (ResourceLocation loc : ForgeRegistries.ENTITY_TYPES.getKeys()) {
                EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(loc);
                if (type == null) continue;

                boolean isMonsterCategory = type.getCategory() == MobCategory.MONSTER;
                boolean isEnemyClass = Enemy.class.isAssignableFrom(type.getBaseClass()) || Monster.class.isAssignableFrom(type.getBaseClass());

                if (isMonsterCategory || isEnemyClass) {
                    hostileMobIds.add(loc.toString());
                }
            }

            Collections.sort(hostileMobIds);

            try (PrintWriter writer = new PrintWriter(new FileWriter(outFile))) {
                writer.println("# Registered Hostile Mobs Dump");
                writer.println("# Generated: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                writer.println("# Total Hostile Mobs: " + hostileMobIds.size());
                writer.println();

                for (String mobId : hostileMobIds) {
                    writer.println(mobId);
                }
            }

            String absolutePath = outFile.getAbsolutePath();
            source.sendSuccess(() -> Component.literal("Exported " + hostileMobIds.size() + " hostile mobs to " + absolutePath), true);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Failed to dump hostile mobs: " + e.getMessage()));
            return 0;
        }
    }

    @SubscribeEvent
    public static void onRegisterStageCommand(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("stage")
                .then(Commands.literal("ready")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            if (player.getServer() == null) return 0;
                            ServerLevel arenaLevel = player.getServer().getLevel(ModDimensions.ARENA);
                            if (arenaLevel == null) return 0;

                            StageContext ctx = StageContext.getOrCreate(arenaLevel);
                            if (ctx.getStageState() != StageState.RETRY_INTERMISSION) {
                                context.getSource().sendFailure(Component.literal("No retry intermission is currently active!"));
                                return 0;
                            }

                            context.getSource().sendSuccess(() -> Component.literal("§a[Borrowed Time] Preparation cut short! Commencing stage retry immediately..."), true);
                            ctx.startRetryStage(arenaLevel);
                            return 1;
                        }))
        );
    }
}
