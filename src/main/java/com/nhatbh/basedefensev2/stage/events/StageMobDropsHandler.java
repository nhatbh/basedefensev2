package com.nhatbh.basedefensev2.stage.events;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Random;

@Mod.EventBusSubscriber(modid = "basedefensev2")
public class StageMobDropsHandler {

    private static final Random RANDOM = new Random();

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        Entity entity = event.getEntity();
        if (entity.level().isClientSide) return;

        if (entity.getPersistentData().getBoolean("bdv2_stage_mob")) {
            // Clear default drops
            event.getDrops().clear();

            // Handle custom loot if present
            if (entity.getPersistentData().contains("bdv2_custom_loot", Tag.TAG_LIST)) {
                ListTag lootList = entity.getPersistentData().getList("bdv2_custom_loot", Tag.TAG_COMPOUND);
                if (lootList.isEmpty()) return;

                int totalWeight = 0;
                for (int i = 0; i < lootList.size(); i++) {
                    totalWeight += lootList.getCompound(i).getInt("weight");
                }

                if (totalWeight <= 0) return;

                int roll = RANDOM.nextInt(totalWeight);
                int currentWeight = 0;

                for (int i = 0; i < lootList.size(); i++) {
                    CompoundTag lootTag = lootList.getCompound(i);
                    currentWeight += lootTag.getInt("weight");

                    if (roll < currentWeight) {
                        String itemRes = lootTag.getString("item");
                        if (itemRes.isEmpty() || itemRes.equals("minecraft:air")) {
                            // Selected "nothing"
                            break;
                        }

                        ResourceLocation loc = itemRes.contains(":") 
                            ? ResourceLocation.parse(itemRes) 
                            : ResourceLocation.fromNamespaceAndPath("minecraft", itemRes);
                        Item item = ForgeRegistries.ITEMS.getValue(loc);

                        if (item != null) {
                            int min = lootTag.getInt("min");
                            int max = lootTag.getInt("max");
                            int count = min >= max ? min : min + RANDOM.nextInt(max - min + 1);

                            if (count > 0) {
                                ItemStack stack = new ItemStack(item, count);
                                ItemEntity itemEntity = new ItemEntity(
                                    entity.level(), 
                                    entity.getX(), 
                                    entity.getY(), 
                                    entity.getZ(), 
                                    stack
                                );
                                event.getDrops().add(itemEntity);
                            }
                        }
                        break;
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onExperienceDrop(LivingExperienceDropEvent event) {
        if (event.getEntity().getPersistentData().getBoolean("bdv2_stage_mob")) {
            event.setDroppedExperience(0);
        }
    }
}
