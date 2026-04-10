package com.nhatbh.basedefensev2.sanctity.data;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ReviveStateProvider implements ICapabilitySerializable<CompoundTag> {
    public static Capability<ReviveState> REVIVE_STATE = CapabilityManager.get(new CapabilityToken<>() {});

    private ReviveState reviveState = null;
    private final LazyOptional<ReviveState> optional = LazyOptional.of(this::createReviveState);

    private ReviveState createReviveState() {
        if (this.reviveState == null) {
            this.reviveState = new ReviveState();
        }
        return this.reviveState;
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == REVIVE_STATE) {
            return optional.cast();
        }
        return LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag nbt = new CompoundTag();
        createReviveState().saveNBTData(nbt);
        return nbt;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        createReviveState().loadNBTData(nbt);
    }
}
