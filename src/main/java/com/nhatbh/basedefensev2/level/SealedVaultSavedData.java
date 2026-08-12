package com.nhatbh.basedefensev2.level;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.fml.ModList;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SealedVaultSavedData extends SavedData {
    private static final String DATA_NAME = "bdv2_sealed_vault";

    public static class VaultRecord {
        public int failedStageOrder;
        public List<ItemStack> items = new ArrayList<>();

        public VaultRecord(int failedStageOrder) {
            this.failedStageOrder = failedStageOrder;
        }

        public CompoundTag serializeNBT() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("failedStageOrder", failedStageOrder);
            ListTag list = new ListTag();
            for (ItemStack stack : items) {
                if (!stack.isEmpty()) {
                    list.add(stack.save(new CompoundTag()));
                }
            }
            tag.put("items", list);
            return tag;
        }

        public static VaultRecord deserializeNBT(CompoundTag tag) {
            int stageOrder = tag.getInt("failedStageOrder");
            VaultRecord record = new VaultRecord(stageOrder);
            ListTag list = tag.getList("items", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag itemTag = list.getCompound(i);
                ItemStack stack = ItemStack.of(itemTag);
                if (!stack.isEmpty()) {
                    record.items.add(stack);
                }
            }
            return record;
        }
    }

    private final Map<UUID, VaultRecord> vaults = new HashMap<>();

    public static SealedVaultSavedData get(ServerLevel level) {
        return level.getServer().getLevel(net.minecraft.world.level.Level.OVERWORLD)
                .getDataStorage().computeIfAbsent(
                        SealedVaultSavedData::load,
                        SealedVaultSavedData::new,
                        DATA_NAME
                );
    }

    public static SealedVaultSavedData load(CompoundTag tag) {
        SealedVaultSavedData data = new SealedVaultSavedData();
        CompoundTag vaultsTag = tag.getCompound("vaults");
        for (String uuidStr : vaultsTag.getAllKeys()) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                CompoundTag vaultTag = vaultsTag.getCompound(uuidStr);
                VaultRecord record = VaultRecord.deserializeNBT(vaultTag);
                data.vaults.put(uuid, record);
            } catch (Exception e) {
                // Ignore invalid entries
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag vaultsTag = new CompoundTag();
        for (Map.Entry<UUID, VaultRecord> entry : vaults.entrySet()) {
            vaultsTag.put(entry.getKey().toString(), entry.getValue().serializeNBT());
        }
        tag.put("vaults", vaultsTag);
        return tag;
    }

    /**
     * Vaults all equipment from player main inventory, armor, offhand, and curios.
     */
    public void vaultPlayerItems(ServerPlayer player, int failedStageOrder) {
        UUID uuid = player.getUUID();
        VaultRecord record = new VaultRecord(failedStageOrder);

        // 1. Vault Main Inventory & Offhand
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                record.items.add(stack.copy());
            }
        }
        player.getInventory().clearContent();

        // 2. Vault Curios (if installed)
        if (ModList.get().isLoaded("curios")) {
            try {
                CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
                    var curiosMap = handler.getCurios();
                    for (var entry : curiosMap.entrySet()) {
                        var stackHandler = entry.getValue().getStacks();
                        for (int i = 0; i < stackHandler.getSlots(); i++) {
                            ItemStack curioStack = stackHandler.getStackInSlot(i);
                            if (!curioStack.isEmpty()) {
                                record.items.add(curioStack.copy());
                                stackHandler.setStackInSlot(i, ItemStack.EMPTY);
                            }
                        }
                    }
                });
            } catch (Throwable t) {
                // Curios integration fallback
            }
        }

        if (!record.items.isEmpty()) {
            vaults.put(uuid, record);
            setDirty();
            player.sendSystemMessage(Component.literal("§c§l[EQUIPMENT CONFISCATED] §r§7Your equipment has been sealed away in the Rift. Reclaim victory over Stage " + failedStageOrder + " to recover your items!"));
        }
    }

    /**
     * Restores vaulted items to player if current stage order meets or exceeds failedStageOrder.
     */
    public boolean restorePlayerItems(ServerPlayer player, int currentStageOrder) {
        UUID uuid = player.getUUID();
        VaultRecord record = vaults.get(uuid);
        if (record == null) return false;

        if (currentStageOrder >= record.failedStageOrder) {
            for (ItemStack stack : record.items) {
                if (!player.getInventory().add(stack)) {
                    // Inventory full: drop stack at player location
                    ItemEntity itemEntity = new ItemEntity(player.level(), player.getX(), player.getY() + 0.5, player.getZ(), stack);
                    itemEntity.setPickUpDelay(10);
                    player.level().addFreshEntity(itemEntity);
                }
            }
            vaults.remove(uuid);
            setDirty();
            player.sendSystemMessage(Component.literal("§a§l[EQUIPMENT RECLAIMED] §r§eYour sealed equipment has been returned to you!"));
            return true;
        }
        return false;
    }

    /**
     * Checks all online players and restores items for those eligible.
     */
    public void restoreAllEligiblePlayers(ServerLevel level, int currentStageOrder) {
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            restorePlayerItems(player, currentStageOrder);
        }
    }

    /**
     * Checks if a player's items are currently vaulted.
     */
    public boolean isPlayerVaulted(UUID uuid) {
        return vaults.containsKey(uuid);
    }

    /**
     * Gets the target stage order required to unlock a player's vaulted items.
     */
    public int getVaultedStageOrder(UUID uuid) {
        VaultRecord record = vaults.get(uuid);
        return record != null ? record.failedStageOrder : -1;
    }
}
