package com.nhatbh.basedefensev2.events;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.*;

/**
 * SavedData storage for recovered dropped items belonging to players.
 * Preserves items across server restarts.
 * Uses cached SimpleContainer instances per player UUID to prevent item duplication.
 */
public class RecoveredItemSavedData extends SavedData {

    private static final String DATA_NAME = "basedefense_recovered_items";
    public static final int MAX_SLOTS = 54; // 6-row chest inventory

    private final Map<UUID, NonNullList<ItemStack>> playerItemsMap = new HashMap<>();
    private final Map<UUID, SimpleContainer> activeContainers = new HashMap<>();

    public RecoveredItemSavedData() {
    }

    public static RecoveredItemSavedData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().getLevel(net.minecraft.world.level.Level.OVERWORLD);
        if (overworld == null) overworld = level;
        DimensionDataStorage storage = overworld.getDataStorage();
        return storage.computeIfAbsent(RecoveredItemSavedData::load, RecoveredItemSavedData::new, DATA_NAME);
    }

    public static RecoveredItemSavedData load(CompoundTag tag) {
        RecoveredItemSavedData data = new RecoveredItemSavedData();
        ListTag playersList = tag.getList("Players", Tag.TAG_COMPOUND);

        for (int i = 0; i < playersList.size(); i++) {
            CompoundTag playerEntry = playersList.getCompound(i);
            UUID playerUuid = playerEntry.getUUID("UUID");

            ListTag itemsList = playerEntry.getList("Items", Tag.TAG_COMPOUND);
            NonNullList<ItemStack> items = NonNullList.withSize(MAX_SLOTS, ItemStack.EMPTY);

            for (int j = 0; j < itemsList.size(); j++) {
                CompoundTag itemTag = itemsList.getCompound(j);
                int slot = itemTag.getByte("Slot") & 255;
                if (slot >= 0 && slot < MAX_SLOTS) {
                    items.set(slot, ItemStack.of(itemTag));
                }
            }
            data.playerItemsMap.put(playerUuid, items);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        // Sync active containers into map before saving
        for (Map.Entry<UUID, SimpleContainer> entry : activeContainers.entrySet()) {
            updateFromContainer(entry.getKey(), entry.getValue());
        }

        ListTag playersList = new ListTag();

        for (Map.Entry<UUID, NonNullList<ItemStack>> entry : playerItemsMap.entrySet()) {
            CompoundTag playerEntry = new CompoundTag();
            playerEntry.putUUID("UUID", entry.getKey());

            ListTag itemsList = new ListTag();
            NonNullList<ItemStack> items = entry.getValue();

            for (int slot = 0; slot < items.size(); slot++) {
                ItemStack stack = items.get(slot);
                if (!stack.isEmpty()) {
                    CompoundTag itemTag = new CompoundTag();
                    itemTag.putByte("Slot", (byte) slot);
                    stack.save(itemTag);
                    itemsList.add(itemTag);
                }
            }
            playerEntry.put("Items", itemsList);
            playersList.add(playerEntry);
        }

        tag.put("Players", playersList);
        return tag;
    }

    public NonNullList<ItemStack> getItems(UUID playerUuid) {
        return playerItemsMap.computeIfAbsent(playerUuid, uuid -> NonNullList.withSize(MAX_SLOTS, ItemStack.EMPTY));
    }

    public boolean hasItems(UUID playerUuid) {
        SimpleContainer active = activeContainers.get(playerUuid);
        if (active != null) {
            return !active.isEmpty();
        }

        NonNullList<ItemStack> items = playerItemsMap.get(playerUuid);
        if (items == null) return false;
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) return true;
        }
        return false;
    }

    /**
     * Wipes all stored items for all players. Called before each new item sweep cycle.
     */
    public void clearAll() {
        for (SimpleContainer container : activeContainers.values()) {
            container.clearContent();
        }
        activeContainers.clear();
        playerItemsMap.clear();
        setDirty();
    }

    /**
     * Attempts to add an itemstack into the player's recovered storage container.
     */
    public boolean addItem(UUID playerUuid, ItemStack stack) {
        if (stack.isEmpty()) return false;

        SimpleContainer container = getOrCreateContainer(playerUuid);
        ItemStack remaining = container.addItem(stack.copy());

        updateFromContainer(playerUuid, container);
        setDirty();

        return remaining.isEmpty() || remaining.getCount() < stack.getCount();
    }

    /**
     * Updates stored items from a Container.
     */
    public void updateFromContainer(UUID playerUuid, Container container) {
        NonNullList<ItemStack> items = getItems(playerUuid);
        for (int i = 0; i < MAX_SLOTS && i < container.getContainerSize(); i++) {
            items.set(i, container.getItem(i).copy());
        }
        setDirty();
    }

    /**
     * Returns the single live SimpleContainer instance for the player UUID.
     * Sharing the exact same SimpleContainer instance across all open menus guarantees thread-safe,
     * single-server-thread anti-duplication protection!
     */
    public synchronized SimpleContainer getOrCreateContainer(UUID playerUuid) {
        return activeContainers.computeIfAbsent(playerUuid, uuid -> {
            NonNullList<ItemStack> items = getItems(uuid);
            SimpleContainer container = new SimpleContainer(MAX_SLOTS);
            for (int i = 0; i < MAX_SLOTS; i++) {
                container.setItem(i, items.get(i).copy());
            }
            container.addListener(c -> {
                updateFromContainer(uuid, c);
                setDirty();
            });
            return container;
        });
    }
}
