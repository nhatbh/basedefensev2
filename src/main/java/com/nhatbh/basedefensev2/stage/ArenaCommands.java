package com.nhatbh.basedefensev2.stage;

import com.mojang.brigadier.CommandDispatcher;
import com.nhatbh.basedefensev2.stage.core.StageContext;
import com.nhatbh.basedefensev2.stage.core.StageState;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.nhatbh.basedefensev2.boss.core.BossComponent;
import com.nhatbh.basedefensev2.boss.core.BossDefinition;
import com.nhatbh.basedefensev2.boss.core.BossManager;
import com.nhatbh.basedefensev2.registry.ModBosses;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
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
                                    
                                    ResourceLocation entityLoc = new ResourceLocation(def.getBaseEntity());
                                    EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(entityLoc);
                                    if (type == null) {
                                        context.getSource().sendFailure(Component.literal("Unknown base entity: " + def.getBaseEntity()));
                                        return 0;
                                    }
                                    
                                    Entity entity = type.create(player.level());
                                    if (entity instanceof LivingEntity living) {
                                        Vec3 pos = player.position();
                                        living.setPos(pos.x, pos.y, pos.z);
                                        
                                        // Apply Boss Stats
                                        if (def.getBaseStats() != null) {
                                            var atts = living.getAttributes();
                                            if (atts.hasAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH)) {
                                                atts.getInstance(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(def.getBaseStats().health);
                                                living.setHealth(def.getBaseStats().health);
                                            }
                                            if (atts.hasAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)) {
                                                atts.getInstance(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(def.getBaseStats().speed);
                                            }
                                            if (atts.hasAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)) {
                                                atts.getInstance(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE).setBaseValue(def.getBaseStats().damage);
                                            }
                                        }
                                        
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
                                }))
                )
        );
    }
}
