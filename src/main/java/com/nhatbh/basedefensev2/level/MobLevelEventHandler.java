package com.nhatbh.basedefensev2.level;

import com.nhatbh.basedefensev2.level.network.MobLevelSyncPacket;
import com.nhatbh.basedefensev2.strength.network.NetworkManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = "basedefensev2")
public class MobLevelEventHandler {

    private static final UUID LEVEL_MODIFIER_UUID = UUID.fromString("b21495c6-993d-4c31-a083-d9d10e53a23a");

    public static boolean isEligibleEntity(LivingEntity living) {
        if (living == null || living instanceof Player || living instanceof ArmorStand) {
            return false;
        }
        if (com.nhatbh.basedefensev2.integration.ComplexTalentsPenaltyHelper.isFriendlySummon(living)) {
            return false;
        }
        return true;
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide) return;

        if (event.getEntity() instanceof LivingEntity living && isEligibleEntity(living)) {
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

            // Sync level to tracking clients
            NetworkManager.sendToTracking(new MobLevelSyncPacket(living.getId(), level), living);
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof LivingEntity living && !living.level().isClientSide && isEligibleEntity(living)) {
            int level = MobLevelData.getLevel(living);
            NetworkManager.sendToTracking(new MobLevelSyncPacket(living.getId(), level), living);
        }
    }

    public static void applyAttributeBonuses(LivingEntity entity, int level) {
        if (level <= 1 || com.nhatbh.basedefensev2.boss.core.BossManager.isBoss(entity)) return;
        double levelBonusMultiplier = MobLevelConfig.getLevelBonusMultiplier(level);

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

                // All stats use MULTIPLY_BASE for percentage-based scaling
                AttributeModifier modifier = new AttributeModifier(
                        LEVEL_MODIFIER_UUID,
                        "Mob Level Bonus",
                        bonus,
                        AttributeModifier.Operation.MULTIPLY_BASE
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
        if (entity != null && !entity.level().isClientSide && isEligibleEntity(entity)) {
            int level = MobLevelData.getLevel(entity);
            if (level > 1 && MobLevelConfig.BONUS_XP_PER_LEVEL > 0) {
                float xpMultiplier = 1.0f + (float) (MobLevelConfig.getLevelBonusMultiplier(level) * MobLevelConfig.BONUS_XP_PER_LEVEL);
                event.setDroppedExperience(Math.round(event.getOriginalExperience() * xpMultiplier));
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDrops(LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity != null && !entity.level().isClientSide && isEligibleEntity(entity)) {
            int level = MobLevelData.getLevel(entity);
            if (level > 1 && MobLevelConfig.BONUS_LOOT_COEFFICIENT > 0) {
                double bonusMultiplier = MobLevelConfig.BONUS_LOOT_COEFFICIENT * Math.pow(level - 1, MobLevelConfig.BONUS_LOOT_EXPONENT);
                if (bonusMultiplier <= 0) return;

                java.util.List<ItemEntity> extraDrops = new java.util.ArrayList<>();

                for (ItemEntity itemEntity : event.getDrops()) {
                    ItemStack stack = itemEntity.getItem();
                    if (!stack.isEmpty() && stack.isStackable()) {
                        int count = stack.getCount();
                        int extraCount = 0;
                        for (int j = 0; j < count; j++) {
                            double rollValue = bonusMultiplier;
                            while (rollValue >= 1.0) {
                                extraCount++;
                                rollValue -= 1.0;
                            }
                            if (entity.getRandom().nextDouble() < rollValue) {
                                extraCount++;
                            }
                        }

                        if (extraCount > 0) {
                            int maxStack = stack.getMaxStackSize();
                            int spaceInOriginal = maxStack - count;
                            int toAddToOriginal = Math.min(extraCount, spaceInOriginal);
                            if (toAddToOriginal > 0) {
                                stack.setCount(count + toAddToOriginal);
                                itemEntity.setItem(stack);
                                extraCount -= toAddToOriginal;
                            }

                            while (extraCount > 0) {
                                int currentBatch = Math.min(extraCount, maxStack);
                                ItemStack newStack = stack.copy();
                                newStack.setCount(currentBatch);
                                ItemEntity newEntity = new ItemEntity(
                                    entity.level(),
                                    itemEntity.getX(),
                                    itemEntity.getY(),
                                    itemEntity.getZ(),
                                    newStack
                                );
                                newEntity.setDeltaMovement(itemEntity.getDeltaMovement());
                                extraDrops.add(newEntity);
                                extraCount -= currentBatch;
                            }
                        }
                    }
                }

                if (!extraDrops.isEmpty()) {
                    event.getDrops().addAll(extraDrops);
                }
            }
        }
    }
}
