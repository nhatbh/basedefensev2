package com.nhatbh.basedefensev2.stage;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.nhatbh.basedefensev2.boss.core.BossComponent;
import com.nhatbh.basedefensev2.boss.core.BossDefinition;
import com.nhatbh.basedefensev2.boss.core.BossManager;
import com.nhatbh.basedefensev2.registry.ModBosses;
import com.nhatbh.basedefensev2.stage.config.StageConfig;
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
                        .executes(context -> executeVoteCommand(context, true)))
                .then(Commands.literal("vote")
                        .then(Commands.literal("yes").executes(context -> executeVoteCommand(context, true)))
                        .then(Commands.literal("no").executes(context -> executeVoteCommand(context, false))))
                .then(Commands.literal("ff")
                        .executes(context -> com.nhatbh.basedefensev2.sanctity.events.ForfeitManager.handleForfeitCommand(context.getSource().getPlayerOrException())))
                .then(Commands.literal("forfeit")
                        .executes(context -> com.nhatbh.basedefensev2.sanctity.events.ForfeitManager.handleForfeitCommand(context.getSource().getPlayerOrException())))
                .then(Commands.literal("leave")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            if (player.getServer() == null) return 0;
                            ServerLevel arenaLevel = player.getServer().getLevel(ModDimensions.ARENA);
                            if (arenaLevel == null) return 0;

                            StageContext ctx = StageContext.getOrCreate(arenaLevel);

                            if (ctx.isActive() && ctx.getStageState() != StageState.SCAVENGE && ctx.getStageState() != StageState.ENDED) {
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

                                String nextStageId = ctx.getNextStageId(arenaLevel);
                                String nextStageStr = nextStageId != null ? nextStageId : "None";
                                context.getSource().sendSuccess(() -> Component.literal("§6[The Rift Status] §eCurrent Stage: §c" + stageId 
                                        + " §7(Assault " + currentWave + "/" + totalWaves + " - " + stateName + ") | §eNext Stage: §c" + nextStageStr), false);
                            } else {
                                int ticksRemaining = ctx.getTicksUntilNextStage(arenaLevel);
                                String nextStageId = ctx.getNextStageId(arenaLevel);
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
                .then(Commands.literal("worldlevel")
                        .executes(context -> executeWorldLevelCommand(context.getSource())))
                .then(Commands.literal("setworldlevel")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.argument("level", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0))
                                .executes(context -> executeSetWorldLevelCommand(context.getSource(), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "level")))))
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
                .then(Commands.literal("classify")
                        .executes(context -> {
                            if (context.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer sp) {
                                com.nhatbh.basedefensev2.strength.network.NetworkManager.INSTANCE.send(
                                        net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> sp),
                                        new com.nhatbh.basedefensev2.stage.network.OpenGuiPacket(com.nhatbh.basedefensev2.stage.network.OpenGuiPacket.GuiType.CLASSIFY)
                                );
                            }
                            return 1;
                        }))
                .then(Commands.literal("info")
                        .executes(context -> {
                            if (context.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer sp) {
                                ServerLevel overworld = sp.getServer().overworld();
                                ServerLevel arena = sp.getServer().getLevel(ModDimensions.ARENA);
                                var worldData = com.nhatbh.basedefensev2.stage.generator.WorldStageSavedData.get(overworld);
                                var stages = worldData.getAllStages();
                                String json = new com.google.gson.Gson().toJson(stages);

                                int currentStageNum = 1;
                                if (arena != null) {
                                    var ctx = com.nhatbh.basedefensev2.stage.core.StageContext.getOrCreate(arena);
                                    if (ctx != null && ctx.getActiveConfig() != null) {
                                        currentStageNum = ctx.getActiveConfig().order + 1;
                                    }
                                }

                                com.nhatbh.basedefensev2.strength.network.NetworkManager.INSTANCE.send(
                                        net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> sp),
                                        new com.nhatbh.basedefensev2.stage.network.OpenGuiPacket(
                                                com.nhatbh.basedefensev2.stage.network.OpenGuiPacket.GuiType.INFO,
                                                json,
                                                currentStageNum
                                        )
                                );
                            }
                            return 1;
                        }))
                .then(Commands.literal("reroll")
                        .requires(s -> s.hasPermission(2))
                        .executes(context -> executeRerollCommand(context.getSource())))
                .then(Commands.literal("fastforward")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.argument("seconds", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                                .executes(context -> executeFastForwardCommand(context.getSource(), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "seconds")))))
                .then(Commands.literal("postpone")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.argument("seconds", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                                .executes(context -> executePostponeCommand(context.getSource(), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "seconds")))))
                .then(Commands.literal("test_wave")
                        .requires(s -> s.hasPermission(2))
                        .executes(context -> executeTestWaveCommand(context.getSource())))
                .then(Commands.literal("spawn_test_boss")
                        .requires(s -> s.hasPermission(2))
                        .executes(context -> executeSpawnTestBossCommand(context.getSource())))
                .then(Commands.literal("gauntlet")
                        .requires(s -> s.hasPermission(2))
                        .executes(context -> executeGauntletCommand(context.getSource())))
        );
    }

    private static int executeTestWaveCommand(CommandSourceStack source) {
        if (source.getServer() == null) return 0;
        ServerLevel level = source.getServer().getLevel(ModDimensions.ARENA);
        if (level == null) level = source.getLevel();

        StageConfig customConfig = com.nhatbh.basedefensev2.stage.generator.RandomStageGenerator.generateTestZombieStage();
        StageContext ctx = StageContext.getOrCreate(level);
        ctx.forceStartStage(level, customConfig);

        final String stageId = customConfig.id;
        source.sendSuccess(() -> Component.literal(String.format("§6[The Rift OP] §aInitiated zombie test stage: %s!", stageId)), true);
        return 1;
    }

    private static int executeSpawnTestBossCommand(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            // Ensure test zombie boss definition is generated & registered
            com.nhatbh.basedefensev2.stage.generator.RandomStageGenerator.generateTestZombieStage();

            BossDefinition def = ModBosses.get("test_zombie_boss");
            if (def == null) {
                source.sendFailure(Component.literal("Failed to load test_zombie_boss definition!"));
                return 0;
            }

            ResourceLocation entityLoc = ResourceLocation.parse(def.getBaseEntity());
            EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(entityLoc);
            if (type == null) {
                source.sendFailure(Component.literal("Unknown base entity: " + def.getBaseEntity()));
                return 0;
            }

            Entity entity = type.create(player.level());
            if (entity instanceof LivingEntity living) {
                Vec3 pos = player.position();
                living.setPos(pos.x, pos.y, pos.z);

                BossComponent comp = new BossComponent(def);
                BossManager.registerBoss(living, comp);

                living.setCustomName(Component.literal("LIGHTNING LANCE ZOMBIE"));
                living.setCustomNameVisible(true);

                player.level().addFreshEntity(living);
                source.sendSuccess(() -> Component.literal("§6[The Rift OP] §aSpawned test Lightning Lance boss directly!"), true);
                return 1;
            } else {
                source.sendFailure(Component.literal("Base entity must be a LivingEntity!"));
                return 0;
            }
        } catch (Exception e) {
            source.sendFailure(Component.literal("Error spawning test boss: " + e.getMessage()));
            return 0;
        }
    }

    private static int executeGauntletCommand(CommandSourceStack source) {
        if (source.getServer() == null) return 0;
        ServerLevel level = source.getServer().getLevel(ModDimensions.ARENA);
        if (level == null) level = source.getLevel();

        StageConfig customConfig = com.nhatbh.basedefensev2.stage.generator.RandomStageGenerator.generateGauntletStage();
        StageContext ctx = StageContext.getOrCreate(level);
        ctx.forceStartStage(level, customConfig);

        int totalBosses = customConfig.waves != null ? customConfig.waves.size() : 0;
        source.sendSuccess(() -> Component.literal(String.format("§6[The Rift OP] §aBoss & Miniboss Gauntlet Stage initiated! Total waves: %d", totalBosses)), true);
        return 1;
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

    private static int executeVoteCommand(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, boolean voteYes) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (player.getServer() == null) return 0;
        ServerLevel arenaLevel = player.getServer().getLevel(ModDimensions.ARENA);
        if (arenaLevel == null) return 0;

        StageContext ctx = StageContext.getOrCreate(arenaLevel);
        ctx.processVote(player, arenaLevel, voteYes);
        return 1;
    }

    private static int executeRerollCommand(CommandSourceStack source) {
        if (source.getServer() == null) return 0;
        ServerLevel arenaLevel = source.getServer().getLevel(ModDimensions.ARENA);
        if (arenaLevel == null) return 0;

        StageContext ctx = StageContext.getOrCreate(arenaLevel);
        ctx.rerollStages(arenaLevel);
        source.sendSuccess(() -> Component.literal("§6[The Rift OP] §aSuccessfully rerolled stage selection order!"), true);
        return 1;
    }

    private static int executeFastForwardCommand(CommandSourceStack source, int seconds) {
        if (source.getServer() == null) return 0;
        ServerLevel arenaLevel = source.getServer().getLevel(ModDimensions.ARENA);
        if (arenaLevel == null) return 0;

        StageContext ctx = StageContext.getOrCreate(arenaLevel);
        ctx.fastForwardTimer(arenaLevel, seconds);
        source.sendSuccess(() -> Component.literal(String.format("§6[The Rift OP] §aFast-forwarded stage timer by %d seconds!", seconds)), true);
        return 1;
    }

    private static int executePostponeCommand(CommandSourceStack source, int seconds) {
        if (source.getServer() == null) return 0;
        ServerLevel arenaLevel = source.getServer().getLevel(ModDimensions.ARENA);
        if (arenaLevel == null) arenaLevel = source.getLevel();

        StageContext ctx = StageContext.getOrCreate(arenaLevel);
        ctx.postponeTimer(arenaLevel, seconds);
        source.sendSuccess(() -> Component.literal(String.format("§6[The Rift OP] §aPostponed stage timer by %d seconds!", seconds)), true);
        return 1;
    }

    @SubscribeEvent
    public static void onRegisterStageCommand(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("stage")
                .then(Commands.literal("ready")
                        .executes(context -> executeVoteCommand(context, true)))
                .then(Commands.literal("vote")
                        .then(Commands.literal("yes").executes(context -> executeVoteCommand(context, true)))
                        .then(Commands.literal("no").executes(context -> executeVoteCommand(context, false))))
                .then(Commands.literal("worldlevel")
                        .executes(context -> executeWorldLevelCommand(context.getSource())))
                .then(Commands.literal("setworldlevel")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.argument("level", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0))
                                .executes(context -> executeSetWorldLevelCommand(context.getSource(), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "level")))))
                .then(Commands.literal("reroll")
                        .requires(s -> s.hasPermission(2))
                        .executes(context -> executeRerollCommand(context.getSource())))
                .then(Commands.literal("fastforward")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.argument("seconds", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                                .executes(context -> executeFastForwardCommand(context.getSource(), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "seconds")))))
                .then(Commands.literal("postpone")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.argument("seconds", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                                .executes(context -> executePostponeCommand(context.getSource(), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "seconds")))))
        );
    }

    private static int executeWorldLevelCommand(CommandSourceStack source) {
        if (source.getServer() == null) return 0;
        ServerLevel overworld = source.getServer().overworld();
        com.nhatbh.basedefensev2.level.WorldLevelSavedData data = com.nhatbh.basedefensev2.level.WorldLevelSavedData.get(overworld);
        int wl = data.getWorldLevel();
        int baseLvl = com.nhatbh.basedefensev2.level.MobLevelConfig.getOverworldBaseLevel(wl);

        source.sendSuccess(() -> Component.literal(String.format("§6[World Level] §eCurrent World Level: §b§l%d§e | Overworld Base: §aLv. %d",
                wl, baseLvl)), false);
        return 1;
    }

    private static int executeSetWorldLevelCommand(CommandSourceStack source, int newLevel) {
        if (source.getServer() == null) return 0;
        ServerLevel overworld = source.getServer().overworld();
        com.nhatbh.basedefensev2.level.WorldLevelSavedData data = com.nhatbh.basedefensev2.level.WorldLevelSavedData.get(overworld);
        data.setWorldLevel(newLevel);

        int baseLvl = com.nhatbh.basedefensev2.level.MobLevelConfig.getOverworldBaseLevel(newLevel);

        source.sendSuccess(() -> Component.literal(String.format("§6[World Level OP] §aSet World Level to §b§l%d§a (Base: Lv. %d)",
                newLevel, baseLvl)), true);
        return 1;
    }
}
