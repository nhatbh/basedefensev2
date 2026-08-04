package com.nhatbh.basedefensev2.stage.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class OpenGuiPacket {

    public enum GuiType {
        CLASSIFY,
        INFO
    }

    private final GuiType guiType;

    public OpenGuiPacket(GuiType guiType) {
        this.guiType = guiType;
    }

    public OpenGuiPacket(FriendlyByteBuf buf) {
        this.guiType = buf.readEnum(GuiType.class);
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeEnum(guiType);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                if (guiType == GuiType.CLASSIFY) {
                    net.minecraft.client.Minecraft.getInstance().setScreen(new com.nhatbh.basedefensev2.classification.MobClassificationScreen());
                } else if (guiType == GuiType.INFO) {
                    com.nhatbh.basedefensev2.stage.gui.StageInfoScreen.openScreen();
                }
            });
        });
        ctx.setPacketHandled(true);
        return true;
    }
}
