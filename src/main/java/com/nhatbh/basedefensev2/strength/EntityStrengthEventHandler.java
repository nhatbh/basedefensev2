package com.nhatbh.basedefensev2.strength;

import com.nhatbh.basedefensev2.boss.core.AbstractBossEntity;
import com.nhatbh.basedefensev2.boss.core.BossDefinition;
import com.nhatbh.basedefensev2.strength.network.EntityStrengthSyncPacket;
import com.nhatbh.basedefensev2.strength.network.NetworkManager;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "basedefensev2")
public class EntityStrengthEventHandler {

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide) return;
        Entity entity = event.getEntity();

        if (entity instanceof Monster monster) {
            EntityStrengthData data = EntityStrengthData.get(monster);
            if (data == null) {
                if (monster instanceof AbstractBossEntity boss) {
                    BossDefinition def = boss.getDefinition();
                    data = new EntityStrengthData(
                        def.getMaxPoise(),
                        def.getMaxPoise(),
                        def.getPoiseDamageReduction(),
                        true,
                        0
                    );
                } else {
                    float maxHp = monster.getMaxHealth();
                    float maxStrength = maxHp * 1.0f; 
                    float reductionValue = 4.0f + (maxHp * 0.05f);
                    data = new EntityStrengthData(
                        maxStrength,
                        maxStrength,
                        reductionValue,
                        false,
                        0
                    );
                }
                data.save(monster);
            }
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) return;

        EntityStrengthData data = EntityStrengthData.get(entity);
        if (data == null) return;

        DamageSource source = event.getSource();
        if ("SkipStrengthDamage".equals(source.getMsgId()) || entity.getPersistentData().getBoolean("SkipStrengthDamage")) {
            entity.getPersistentData().remove("SkipStrengthDamage");
            return;
        }

        boolean hadStrength = data.currentStrength > 0;
        
        if (hadStrength) {
            float rawDamage = event.getAmount();
            float strengthDamage = rawDamage;

            if (source.isIndirect()) {
                strengthDamage *= 0.5f;
            }

            if (source.getEntity() instanceof LivingEntity attacker) {
                if (attacker.getAttributes().hasAttribute(ModAttributes.STRENGTH_DAMAGE_MULTIPLIER.get())) {
                    strengthDamage *= attacker.getAttributeValue(ModAttributes.STRENGTH_DAMAGE_MULTIPLIER.get());
                }
            }

            data.currentStrength -= strengthDamage;

            if (source.getEntity() instanceof net.minecraft.world.entity.player.Player player) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "Strength Hit! Target: " + entity.getName().getString() + " | Dmg: " + strengthDamage + " | Remaining: " + data.currentStrength + "/" + data.maxStrength
                ));
            }

            if (data.currentStrength <= 0) {
                if (source.getEntity() instanceof net.minecraft.world.entity.player.Player player) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Strength Broken for " + entity.getName().getString() + "!"));
                }
                data.currentStrength = 0;
                data.recoveryTicks = 300; 
                if (entity instanceof AbstractBossEntity boss) {
                    boss.onPoiseBroken();
                }
                MinecraftForge.EVENT_BUS.post(new EntityEvents.PoiseBroken(entity));
            }

            if (data.isPercentageBased) {
                event.setAmount(rawDamage * (1.0f - data.reductionValue));
            } else {
                event.setAmount(Math.max(0, rawDamage - data.reductionValue));
            }
            
            data.save(entity);
            syncStrength(entity, data);
        }
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        
        EntityStrengthData data = EntityStrengthData.get(entity);
        if (data != null && data.currentStrength <= 0 && data.recoveryTicks > 0) {
            data.recoveryTicks -= 1;
            
            if (data.recoveryTicks <= 0) {
                data.recoveryTicks = 0;
                data.currentStrength = data.maxStrength;
                if (!entity.level().isClientSide) {
                    syncStrength(entity, data);
                }
            }
            data.save(entity);
        }
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        if (event.getEntity() instanceof AbstractBossEntity boss) {
            boss.handleSequenceDamage(event);
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof LivingEntity living) {
            EntityStrengthData data = EntityStrengthData.get(living);
            if (data != null) {
                NetworkManager.sendToTracking(
                    new EntityStrengthSyncPacket(living.getId(), data.currentStrength, data.maxStrength, data.recoveryTicks),
                    living
                );
            }
        }
    }

    private static void syncStrength(LivingEntity entity, EntityStrengthData data) {
         NetworkManager.sendToTracking(
             new EntityStrengthSyncPacket(entity.getId(), data.currentStrength, data.maxStrength, data.recoveryTicks),
             entity
         );
    }
}
