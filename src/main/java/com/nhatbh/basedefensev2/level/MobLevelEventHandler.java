package com.nhatbh.basedefensev2.level;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = "basedefensev2")
public class MobLevelEventHandler {

    private static final UUID LEVEL_MODIFIER_UUID = UUID.fromString("b21495c6-993d-4c31-a083-d9d10e53a23a");

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide) return;

        if (event.getEntity() instanceof LivingEntity living && living instanceof Enemy) {
            ResourceLocation mobId = ForgeRegistries.ENTITY_TYPES.getKey(living.getType());
            if (!MobLevelConfig.canLevelUp(mobId)) {
                return;
            }

            int level = MobLevelData.getLevel(living);
            if (!living.getPersistentData().contains(MobLevelData.NBT_KEY_LEVEL) && event.getLevel() instanceof ServerLevel serverLevel) {
                level = MobLevelCalculator.calculateLevel(living, serverLevel, living.blockPosition(), -1);
                MobLevelData.setLevel(living, level);
            }

            applyAttributeBonuses(living, level);
        }
    }

    public static void applyAttributeBonuses(LivingEntity entity, int level) {
        if (level <= 1) return;
        int levelBonusMultiplier = level - 1;

        for (Map.Entry<String, Double> entry : MobLevelConfig.ATTRIBUTE_BONUSES.entrySet()) {
            ResourceLocation attrLoc = ResourceLocation.tryParse(entry.getKey());
            if (attrLoc == null) continue;

            Attribute attribute = ForgeRegistries.ATTRIBUTES.getValue(attrLoc);
            if (attribute == null) continue;

            AttributeInstance instance = entity.getAttribute(attribute);
            if (instance != null) {
                // Remove previous level modifier if present
                instance.removeModifier(LEVEL_MODIFIER_UUID);

                double bonus = entry.getValue() * levelBonusMultiplier;
                AttributeModifier modifier = new AttributeModifier(
                        LEVEL_MODIFIER_UUID,
                        "Mob Level Bonus",
                        bonus,
                        AttributeModifier.Operation.ADDITION
                );
                instance.addTransientModifier(modifier);

                // Heal entity if max health was increased
                if (attribute == net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH) {
                    entity.setHealth(entity.getMaxHealth());
                }
            }
        }
    }

    @SubscribeEvent
    public static void onExperienceDrop(LivingExperienceDropEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity != null && !entity.level().isClientSide) {
            int level = MobLevelData.getLevel(entity);
            if (level > 1 && MobLevelConfig.BONUS_XP_PER_LEVEL > 0) {
                float xpMultiplier = 1.0f + (level - 1) * MobLevelConfig.BONUS_XP_PER_LEVEL;
                event.setDroppedExperience(Math.round(event.getOriginalExperience() * xpMultiplier));
            }
        }
    }
}
