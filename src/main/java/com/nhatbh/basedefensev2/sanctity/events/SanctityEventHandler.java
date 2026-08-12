package com.nhatbh.basedefensev2.sanctity.events;

import com.nhatbh.basedefensev2.sanctity.data.AltarSavedData;
import com.nhatbh.basedefensev2.sanctity.data.ReviveState;
import com.nhatbh.basedefensev2.sanctity.data.ReviveStateProvider;
import com.nhatbh.basedefensev2.sanctity.network.PlayerReviveStateSyncPacket;
import com.nhatbh.basedefensev2.sanctity.network.SanctitySyncPacket;
import com.nhatbh.basedefensev2.strength.network.NetworkManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import com.mojang.brigadier.arguments.IntegerArgumentType;

import com.mojang.authlib.GameProfile;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import com.nhatbh.basedefensev2.utils.UUIDHelper;
import java.util.List;
import java.util.UUID;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SanctityEventHandler {
    private static final UUID KNOCKED_DOWN_MODIFIER_UUID = UUIDHelper.generateAttributeModifierUUID("sanctity",
            "knocked_down_restriction");
    private static final AttributeModifier KNOCKED_DOWN_MODIFIER = new AttributeModifier(KNOCKED_DOWN_MODIFIER_UUID,
            "Knocked Down Restriction", -1.0, AttributeModifier.Operation.MULTIPLY_TOTAL);

    @SubscribeEvent
    public static void onPlayerLoggedIn(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.getServer() != null) {
            ServerLevel overworld = player.getServer().getLevel(net.minecraft.world.level.Level.OVERWORLD);
            if (overworld != null) {
                int currentStageOrder = com.nhatbh.basedefensev2.level.WorldLevelSavedData.get(overworld).getWorldLevel();
                com.nhatbh.basedefensev2.level.SealedVaultSavedData.get(overworld).restorePlayerItems(player, currentStageOrder);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            player.getCapability(ReviveStateProvider.REVIVE_STATE).ifPresent(state -> {
                // Prevent actual death
                event.setCanceled(true);
                player.setHealth(1.0f); // Set to low health to signify knocked down

                if (state.getKnockedDownTimer() > 0) {
                    state.setKnockedDown(true);
                    state.setDeathPos(player.blockPosition());
                    player.displayClientMessage(
                            Component.literal("You are KNOCKED DOWN! Wait for rescue or timer to expire.")
                                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                            true);

                    // Clear targets targeting this player
                    if (player.level() instanceof ServerLevel serverLevel) {
                        for (Entity entity : serverLevel.getAllEntities()) {
                            if (entity instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() == player) {
                                mob.setTarget(null);
                                if (com.nhatbh.basedefensev2.boss.core.BossManager.isBoss(mob)) {
                                    com.nhatbh.basedefensev2.boss.core.BossManager.selectRandomTarget(mob);
                                }
                            }
                        }
                    }
                } else {
                    enterSpectator(player, state);
                }
                syncReviveState(player, state);
            });
        }
    }

    public static void enterSpectator(ServerPlayer player, ReviveState state) {
        state.setKnockedDown(false);
        state.setSpectatorTimer(ReviveState.MAX_SPECTATOR_TICKS); // 2 minutes max
        player.setGlowingTag(false);
        state.setDeathPos(player.blockPosition());
        player.setGameMode(GameType.SPECTATOR);

        Component message = Component.literal("You have died. Hold [I] to initiate revival.").withStyle(ChatFormatting.YELLOW);
        player.sendSystemMessage(message);
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            player.getCapability(ReviveStateProvider.REVIVE_STATE).ifPresent(state -> {
                if (state.isKnockedDown()) {
                    event.setCanceled(true);
                }
            });
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player) {
            player.getCapability(ReviveStateProvider.REVIVE_STATE).ifPresent(state -> {
                if (state.isKnockedDown()) {
                    event.setCanceled(true);
                }
            });
        }
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            player.getCapability(ReviveStateProvider.REVIVE_STATE).ifPresent(state -> {
                if (state.isKnockedDown()) {
                    event.setCanceled(true);
                }
            });
        }
    }

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            player.getCapability(ReviveStateProvider.REVIVE_STATE).ifPresent(state -> {
                if (state.isKnockedDown()) {
                    event.setCanceled(true);
                }
            });
        }
    }

    @SubscribeEvent
    public static void onInteractItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            player.getCapability(ReviveStateProvider.REVIVE_STATE).ifPresent(state -> {
                if (state.isKnockedDown()) {
                    event.setCanceled(true);
                }
            });
        }
    }

    private static void handleReviveTick(ServerPlayer player) {
        player.getCapability(ReviveStateProvider.REVIVE_STATE).ifPresent(state -> {
            // Handle Rescue logic AS A RESCUER
            if (state.getRescueTargetId() != -1) {
                Entity targetEntity = player.level().getEntity(state.getRescueTargetId());
                if (targetEntity instanceof ServerPlayer targetServerPlayer) {
                    targetServerPlayer.getCapability(ReviveStateProvider.REVIVE_STATE).ifPresent(targetState -> {
                        // Check distance and KD state
                        if (targetState.isKnockedDown() && player.distanceToSqr(targetEntity) < 17.0) {
                            targetState.setRescueProgress(targetState.getRescueProgress() + 1);
                            
                            if (targetState.getRescueProgress() >= 100) {
                                targetState.setKnockedDown(false);
                                targetState.setDeathPos(null);
                                targetState.resetRescue();
                                targetServerPlayer.setGlowingTag(false);
                                removeKnockedDownModifiers(targetServerPlayer);
                                targetState.setKnockedDownTimer(Math.max(0, targetState.getKnockedDownTimer() - 600));
                                applyReviveStatsAndBuffs(targetServerPlayer);
                                targetServerPlayer.sendSystemMessage(Component.literal("You have been rescued!").withStyle(ChatFormatting.GREEN));
                                
                                state.setRescueTargetId(-1);
                            }
                        } else {
                            // Out of range or target not KD anymore
                            state.setRescueTargetId(-1);
                            targetState.resetRescue();
                        }
                    });
                } else {
                    state.setRescueTargetId(-1);
                }
            }

            // Handle Knocked Down Timer AS THE TARGET
            if (state.isKnockedDown()) {
                state.setKnockedDownTimer(Math.max(0, state.getKnockedDownTimer() - 1));

                player.setGlowingTag(true);

                // Restrict movement via modifiers
                player.setDeltaMovement(0, player.getDeltaMovement().y, 0);
                var speedAttr = player.getAttribute(Attributes.MOVEMENT_SPEED);
                if (speedAttr != null && !speedAttr.hasModifier(KNOCKED_DOWN_MODIFIER)) {
                    speedAttr.addTransientModifier(KNOCKED_DOWN_MODIFIER);
                }

                // Apply Jump Boost 128 to block jump
                player.addEffect(new MobEffectInstance(MobEffects.JUMP, 20, 128, false, false, false));

                // Apply DOWNED effect while knocked down so other mods can recognize & handle interaction
                player.addEffect(new MobEffectInstance(com.nhatbh.basedefensev2.registry.ModEffects.DOWNED.get(), 20, 0, false, false, true));

                // Apply complextalents:engaged effect while knocked down
                net.minecraft.world.effect.MobEffect engagedEffect = net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS.getValue(net.minecraft.resources.ResourceLocation.parse("complextalents:engaged"));
                if (engagedEffect != null) {
                    player.addEffect(new MobEffectInstance(engagedEffect, 20, 0, false, false, false));
                }

                if (state.getKnockedDownTimer() <= 0) {
                    enterSpectator(player, state);
                }
            } else {
                removeKnockedDownModifiers(player);
                player.setGlowingTag(false);
                if (player.hasEffect(com.nhatbh.basedefensev2.registry.ModEffects.DOWNED.get())) {
                    player.removeEffect(com.nhatbh.basedefensev2.registry.ModEffects.DOWNED.get());
                }
                var jumpEffect = player.getEffect(MobEffects.JUMP);
                if (jumpEffect != null && jumpEffect.getAmplifier() == 128) {
                    player.removeEffect(MobEffects.JUMP);
                }
            }

            // Handle Spectator Timer & Radius
            if (player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR && state.getDeathPos() != null) {
                // Tick down 2-minute spectator timer unless player already queued for revive
                if (!state.wantsRevive()) {
                    int remaining = Math.max(0, state.getSpectatorTimer() - 1);
                    state.setSpectatorTimer(remaining);
                    if (remaining <= 0) {
                        state.setWantsRevive(true);
                    }
                }

                if (player.position().distanceToSqr(Vec3.atCenterOf(state.getDeathPos())) > 10000) { // 100 blocks radius
                    player.teleportTo(state.getDeathPos().getX(), state.getDeathPos().getY(),
                            state.getDeathPos().getZ());
                    player.displayClientMessage(Component.literal("You cannot wander too far from your body!")
                            .withStyle(ChatFormatting.RED), true);
                }
            }
        });
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            long tickCount = event.getServer().getTickCount();

            if (tickCount % 10 == 0) {
                ForfeitManager.tick(event.getServer());
            }

            // Find all levels
            for (ServerLevel level : event.getServer().getAllLevels()) {
                // Find and tick all players (real and fake) in this level
                for (Entity entity : level.getEntities().getAll()) {
                    if (entity instanceof ServerPlayer player) {
                        handleReviveTick(player);
                    }
                }
            }

            // Global Sync: Sync revive state every 10 ticks to save network & iteration load
            if (tickCount % 10 == 0) {
                for (ServerLevel level : event.getServer().getAllLevels()) {
                    for (Entity entity : level.getEntities().getAll()) {
                        if (entity instanceof ServerPlayer player) {
                            player.getCapability(ReviveStateProvider.REVIVE_STATE).ifPresent(state -> {
                                syncReviveState(player, state);
                            });
                        }
                    }
                }
            }

            ServerLevel overworld = event.getServer().getLevel(net.minecraft.world.level.Level.OVERWORLD);
            if (overworld == null)
                return;

            AltarSavedData data = AltarSavedData.get(overworld);

            if (overworld.getGameTime() % 10 == 0) {
                data.regenGrace(10.0);
            }

            if (overworld.getGameTime() % 20 == 0) {
                syncToAll(overworld, data);

                // Handle periodic revive check
                List<ServerPlayer> players = overworld.getServer().getPlayerList().getPlayers();
                for (ServerPlayer player : players) {
                    player.getCapability(ReviveStateProvider.REVIVE_STATE).ifPresent(state -> {
                        if (state.wantsRevive() && player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
                            if (data.getSanctity() >= 1) {
                                data.deductSanctity(1);
                                revivePlayer(player);
                                state.setWantsRevive(false);
                                state.setKnockedDownTimer(ReviveState.INITIAL_KNOCKDOWN_TICKS);
                                syncReviveState(player, state);
                                syncToAll(overworld, data);
                            }
                        }
                    });
                }
            }

            if (overworld.getGameTime() % 20 == 0) {
                checkGameOver(overworld);
            }
        }
    }

    private static void checkGameOver(ServerLevel level) {
        AltarSavedData data = AltarSavedData.get(level);

        net.minecraft.server.MinecraftServer server = level.getServer();
        if (server == null) return;

        ServerLevel arenaLevel = server.getLevel(com.nhatbh.basedefensev2.stage.ModDimensions.ARENA);
        com.nhatbh.basedefensev2.stage.core.StageContext stageCtx = arenaLevel != null ? com.nhatbh.basedefensev2.stage.core.StageContext.getOrCreate(arenaLevel) : null;

        boolean sanctityZero = data.getSanctity() <= 0;

        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        boolean noPlayerAlive = true;
        for (ServerPlayer player : players) {
            if (player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
                noPlayerAlive = false;
                break;
            }
        }

        if (sanctityZero && noPlayerAlive) {
            int failedStageOrder;
            if (stageCtx != null && stageCtx.isActive()) {
                failedStageOrder = (stageCtx.getActiveConfig() != null) ? stageCtx.getActiveConfig().order : (com.nhatbh.basedefensev2.level.WorldLevelSavedData.get(level).getWorldLevel() + 1);
                stageCtx.endStageOnGameOver(arenaLevel != null ? arenaLevel : level);
            } else {
                int currentWorldLevel = com.nhatbh.basedefensev2.level.WorldLevelSavedData.get(level).getWorldLevel();
                failedStageOrder = currentWorldLevel + 1;
            }

            triggerSoftGameOver(level, failedStageOrder);
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ff")
                .executes(context -> ForfeitManager.handleForfeitCommand(context.getSource().getPlayerOrException())));

        event.getDispatcher().register(Commands.literal("forfeit")
                .executes(context -> ForfeitManager.handleForfeitCommand(context.getSource().getPlayerOrException())));

        event.getDispatcher().register(Commands.literal("surrender")
                .executes(context -> ForfeitManager.handleForfeitCommand(context.getSource().getPlayerOrException())));

        event.getDispatcher().register(Commands.literal("revive")
                .then(Commands.literal("confirm")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            player.getCapability(ReviveStateProvider.REVIVE_STATE).ifPresent(state -> {
                                if (player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
                                    AltarSavedData data = AltarSavedData.get(player.serverLevel());
                                    if (data.getSanctity() >= 1) {
                                        state.setWantsRevive(true);
                                        player.sendSystemMessage(Component.literal("Revival process initiated...")
                                                .withStyle(ChatFormatting.GREEN));
                                    } else {
                                        player.sendSystemMessage(Component.literal("Not enough Lives to revive!")
                                                .withStyle(ChatFormatting.RED));
                                    }
                                    syncReviveState(player, state);
                                }
                            });
                            return 1;
                        }))
                .then(Commands.literal("timer")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("ticks", IntegerArgumentType.integer(0))
                                        .executes(context -> {
                                            int ticks = IntegerArgumentType.getInteger(context, "ticks");
                                            for (ServerPlayer player : EntityArgument.getPlayers(context, "targets")) {
                                                player.getCapability(ReviveStateProvider.REVIVE_STATE)
                                                        .ifPresent(state -> {
                                                            state.setKnockedDownTimer(ticks);
                                                            syncReviveState(player, state);
                                                        });
                                            }
                                            context.getSource()
                                                    .sendSuccess(() -> Component.literal(
                                                            "Set revive timer for targets to " + ticks + " ticks."),
                                                            true);
                                            return 1;
                                        }))))
                .then(Commands.literal("reset")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(context -> {
                                    for (ServerPlayer player : EntityArgument.getPlayers(context, "targets")) {
                                        player.getCapability(ReviveStateProvider.REVIVE_STATE).ifPresent(state -> {
                                            state.setKnockedDownTimer(ReviveState.INITIAL_KNOCKDOWN_TICKS);
                                            state.setKnockedDown(false);
                                            player.setGlowingTag(false);
                                            state.setWantsRevive(false);
                                            state.setDeathPos(null);
                                            state.resetRescue();
                                            removeKnockedDownModifiers(player);
                                            syncReviveState(player, state);
                                        });
                                    }
                                    context.getSource().sendSuccess(
                                            () -> Component.literal("Reset revive state for targets."), true);
                                    return 1;
                                })))
                .then(Commands.literal("rescue")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(context -> {
                                    for (ServerPlayer player : EntityArgument.getPlayers(context, "targets")) {
                                        player.getCapability(ReviveStateProvider.REVIVE_STATE).ifPresent(state -> {
                                            if (state.isKnockedDown()) {
                                                state.setKnockedDown(false);
                                                state.setDeathPos(null);
                                                state.resetRescue();
                                                player.setHealth(player.getMaxHealth() * 0.2f);
                                                removeKnockedDownModifiers(player);
                                                player.sendSystemMessage(
                                                        Component.literal("You have been rescued (Admin)!")
                                                                .withStyle(ChatFormatting.GREEN));
                                                syncReviveState(player, state);
                                            }
                                        });
                                    }
                                    context.getSource()
                                            .sendSuccess(() -> Component.literal("Immediately rescued targets."), true);
                                    return 1;
                                })))
                .then(Commands.literal("spawn_test")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            ServerLevel level = player.serverLevel();

                            GameProfile profile = new GameProfile(UUID.randomUUID(),
                                    "TestDummy_" + level.getRandom().nextInt(1000));
                            FakePlayer dummy = FakePlayerFactory.get(level, profile);

                            dummy.setPos(player.getX(), player.getY(), player.getZ());

                            // Essential: Sync player info to clients before adding entity
                            level.getServer().getPlayerList().broadcastAll(new ClientboundPlayerInfoUpdatePacket(
                                    ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER, dummy));

                            level.addFreshEntity(dummy);

                            dummy.getCapability(ReviveStateProvider.REVIVE_STATE).ifPresent(state -> {
                                state.setKnockedDown(true);
                                state.setKnockedDownTimer(ReviveState.INITIAL_KNOCKDOWN_TICKS);
                                state.setDeathPos(dummy.blockPosition());
                                // Syncing dummy state won't do much for the dummy, but logic uses it
                            });

                            context.getSource()
                                    .sendSuccess(() -> Component.literal("Spawned test dummy at your position."), true);
                            return 1;
                        })));

        event.getDispatcher().register(Commands.literal("sanctity")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("set")
                        .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                .executes(context -> {
                                    int amount = IntegerArgumentType.getInteger(context, "amount");
                                    AltarSavedData data = AltarSavedData.get(context.getSource().getLevel());
                                    data.setSanctity(amount);
                                    syncToAll(context.getSource().getLevel(), data);
                                    context.getSource()
                                            .sendSuccess(() -> Component.literal("Sanctity set to " + amount), true);
                                    return 1;
                                })))
                .then(Commands.literal("add")
                        .then(Commands.argument("amount", IntegerArgumentType.integer())
                                .executes(context -> {
                                    int amount = IntegerArgumentType.getInteger(context, "amount");
                                    AltarSavedData data = AltarSavedData.get(context.getSource().getLevel());
                                    data.setSanctity(Math.max(0, data.getSanctity() + amount));
                                    syncToAll(context.getSource().getLevel(), data);
                                    context.getSource()
                                            .sendSuccess(() -> Component.literal(
                                                    "Added " + amount + " Sanctity. Current: " + data.getSanctity()),
                                                    true);
                                    return 1;
                                })))
                .then(Commands.literal("restore")
                        .executes(context -> {
                            AltarSavedData data = AltarSavedData.get(context.getSource().getLevel());
                            int maxSanctity = com.nhatbh.basedefensev2.config.SanctityConfig.data.maxSanctity;
                            data.setSanctity(maxSanctity);
                            data.setRetriesUsed(0);
                            syncToAll(context.getSource().getLevel(), data);
                            context.getSource().sendSuccess(
                                    () -> Component.literal("Base health fully restored to " + maxSanctity + "!"), true);
                            return 1;
                        }))
                .then(Commands.literal("get")
                        .executes(context -> {
                            AltarSavedData data = AltarSavedData.get(context.getSource().getLevel());
                            context.getSource().sendSuccess(
                                    () -> Component.literal("Current Sanctity: " + data.getSanctity()), false);
                            return 1;
                        })));
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AltarSavedData data = AltarSavedData.get((ServerLevel) player.level());
            var config = com.nhatbh.basedefensev2.config.SanctityConfig.data;
            NetworkManager.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                    new SanctitySyncPacket(data.getSanctity(), data.getGrace(), config.maxSanctity, config.maxGrace, data.getRetriesUsed(), config.maxWorldRetries));

            player.getCapability(ReviveStateProvider.REVIVE_STATE).ifPresent(state -> syncReviveState(player, state));
        }
    }

    private static void syncReviveState(ServerPlayer player, ReviveState state) {
        NetworkManager.INSTANCE.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                new PlayerReviveStateSyncPacket(player.getId(), state.getKnockedDownTimer(), state.getSpectatorTimer(), state.isKnockedDown(), state.wantsRevive(),
                        state.getRescueProgress(), state.getDeathPos()));
    }

    private static void revivePlayer(ServerPlayer player) {
        player.setGameMode(GameType.SURVIVAL);
        player.setGlowingTag(false);
        applyReviveStatsAndBuffs(player);
        player.getCapability(ReviveStateProvider.REVIVE_STATE).ifPresent(state -> {
            state.setDeathPos(null);
            state.setKnockedDown(false);
            state.setWantsRevive(false);
            state.setKnockedDownTimer(Math.max(0, state.getKnockedDownTimer() - 600));
            removeKnockedDownModifiers(player);
            syncReviveState(player, state);
        });

        if (player.level().dimension() == com.nhatbh.basedefensev2.stage.ModDimensions.ARENA) {
            net.minecraft.world.phys.Vec3 safePos = com.nhatbh.basedefensev2.stage.utils.ArenaBarrierManager
                    .getClosestPointInside((ServerLevel) player.level(), player.position());
            player.teleportTo((ServerLevel) player.level(), safePos.x, safePos.y, safePos.z, player.getYRot(),
                    player.getXRot());
            player.sendSystemMessage(
                    Component.translatable("message.basedefensev2.revived").withStyle(ChatFormatting.GREEN));
            return;
        }

        BlockPos respawnPos = player.getRespawnPosition();
        net.minecraft.server.MinecraftServer server = player.getServer();
        if (server == null)
            return;

        ServerLevel respawnLevel = server.getLevel(player.getRespawnDimension());

        if (respawnLevel == null || respawnPos == null) {
            respawnLevel = server.getLevel(net.minecraft.world.level.Level.OVERWORLD);
            respawnPos = respawnLevel != null ? respawnLevel.getSharedSpawnPos() : BlockPos.ZERO;
        }

        if (respawnLevel != null) {
            Vec3 target = Vec3.atCenterOf(respawnPos);
            player.teleportTo(respawnLevel, target.x(), target.y(), target.z(), player.getYRot(), player.getXRot());
        }

        player.sendSystemMessage(
                Component.translatable("message.basedefensev2.revived").withStyle(ChatFormatting.GREEN));
    }

    private static void applyReviveStatsAndBuffs(ServerPlayer player) {
        // Regenerate health to 50% of max HP
        player.setHealth(player.getMaxHealth() * 0.5f);

        // Grant 25% maxHP + 5 Absorption shield
        float shieldAmount = (player.getMaxHealth() * 0.25f) + 5.0f;
        player.setAbsorptionAmount(shieldAmount);

        // Apply True Hit Immunity (100 ticks = 5s) & Speed II repositioning boost (100 ticks = 5s)
        applyTrueHitImmunity(player, 100);
    }

    private static void applyTrueHitImmunity(ServerPlayer player, int durationTicks) {
        net.minecraft.world.effect.MobEffect trueHitImmunity = net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS
                .getValue(net.minecraft.resources.ResourceLocation.parse("complextalents:true_hit_immunity"));
        if (trueHitImmunity != null) {
            player.addEffect(new MobEffectInstance(trueHitImmunity, durationTicks, 0, false, false, true));
        }
        // Grant Speed II (+40% speed boost) for 5 seconds to reposition safely
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, durationTicks, 1, false, false, true));
    }

    public static void triggerSoftGameOver(ServerLevel level, int failedStageOrder) {
        net.minecraft.server.MinecraftServer server = level.getServer();
        if (server == null) return;

        ServerLevel overworld = server.getLevel(net.minecraft.world.level.Level.OVERWORLD);
        ServerLevel targetLevel = overworld != null ? overworld : level;

        // 1. Reset Sanctity, Grace, and Retries
        AltarSavedData altarData = AltarSavedData.get(targetLevel);
        var config = com.nhatbh.basedefensev2.config.SanctityConfig.data;
        altarData.setSanctity(config.maxSanctity);
        altarData.setGrace(config.maxGrace);
        altarData.setRetriesUsed(0);

        // 2. Reset World Level to 0
        com.nhatbh.basedefensev2.level.WorldLevelSavedData.get(targetLevel).setWorldLevel(0);

        // 3. Reset Arena & Reroll Stages
        ServerLevel arenaLevel = server.getLevel(com.nhatbh.basedefensev2.stage.ModDimensions.ARENA);
        if (arenaLevel != null) {
            com.nhatbh.basedefensev2.stage.core.StageContext stageCtx = com.nhatbh.basedefensev2.stage.core.StageContext.getOrCreate(arenaLevel);
            stageCtx.resetForSoftGameOver(arenaLevel);
        }

        // 4. Revive, Teleport, Vault Items, & Apply Penalties to all players
        com.nhatbh.basedefensev2.level.SealedVaultSavedData vaultData = com.nhatbh.basedefensev2.level.SealedVaultSavedData.get(targetLevel);
        Component title = Component.literal("§c§lDEFEATED");
        Component subtitle = Component.literal("§7You lose... All of your items were lost to the Rift. Clear Stage " + failedStageOrder + " to reclaim them!");

        net.minecraft.core.BlockPos spawnPos = targetLevel.getSharedSpawnPos();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            // A. Physical Item Confiscation (Main Inv, Armor, Offhand, Curios, Backpacks)
            vaultData.vaultPlayerItems(player, failedStageOrder);

            // B. Apply ComplexTalents penalties (60-80% XP loss, Weapon Mastery level reduction, Spell forgetting)
            com.nhatbh.basedefensev2.integration.ComplexTalentsPenaltyHelper.applySoftGameOverPenalties(player);

            // C. Revive & Reset Player State
            player.setGameMode(GameType.SURVIVAL);
            player.removeEffect(MobEffects.DARKNESS);
            player.removeEffect(MobEffects.BLINDNESS);
            player.setHealth(player.getMaxHealth());

            player.getCapability(ReviveStateProvider.REVIVE_STATE).ifPresent(state -> {
                state.setKnockedDown(false);
                state.setWantsRevive(false);
                state.setDeathPos(null);
                state.resetRescue();
                removeKnockedDownModifiers(player);
                syncReviveState(player, state);
            });

            // D. Teleport back to pre-arena position
            com.nhatbh.basedefensev2.stage.TeleportManager.teleportBack(player);

            // E. Play dramatic sound & title animation
            player.playNotifySound(net.minecraft.sounds.SoundEvents.WITHER_DEATH, net.minecraft.sounds.SoundSource.AMBIENT, 1.0f, 0.8f);
            player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
            player.connection.send(new ClientboundSetTitleTextPacket(title));
            player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        }

        syncToAll(targetLevel, altarData);
    }

    private static void removeKnockedDownModifiers(ServerPlayer player) {
        var speedAttr = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null)
            speedAttr.removeModifier(KNOCKED_DOWN_MODIFIER_UUID);
    }

    private static void syncToAll(ServerLevel level, AltarSavedData data) {
        var config = com.nhatbh.basedefensev2.config.SanctityConfig.data;
        SanctitySyncPacket packet = new SanctitySyncPacket(data.getSanctity(), data.getGrace(), config.maxSanctity,
                config.maxGrace, data.getRetriesUsed(), config.maxWorldRetries);
        level.getServer().getPlayerList().getPlayers().forEach(player -> {
            NetworkManager.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), packet);
        });
    }
}
