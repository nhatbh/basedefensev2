package com.nhatbh.basedefensev2.stage.network;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.nhatbh.basedefensev2.stage.config.StageConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class OpenGuiPacket {

    private static final Gson GSON = new Gson();
    private static final Type STAGE_LIST_TYPE = new TypeToken<List<StageConfig>>() {}.getType();

    public enum GuiType {
        CLASSIFY,
        INFO
    }

    private final GuiType guiType;
    private final String stagesJson;
    private final int currentStageNum;

    public OpenGuiPacket(GuiType guiType) {
        this(guiType, "", 1);
    }

    public OpenGuiPacket(GuiType guiType, String stagesJson, int currentStageNum) {
        this.guiType = guiType;
        this.stagesJson = stagesJson != null ? stagesJson : "";
        this.currentStageNum = currentStageNum;
    }

    public OpenGuiPacket(FriendlyByteBuf buf) {
        this.guiType = buf.readEnum(GuiType.class);
        this.stagesJson = buf.readUtf(32767);
        this.currentStageNum = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeEnum(guiType);
        buf.writeUtf(stagesJson != null ? stagesJson : "", 32767);
        buf.writeInt(currentStageNum);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                if (guiType == GuiType.CLASSIFY) {
                    net.minecraft.client.Minecraft.getInstance().setScreen(new com.nhatbh.basedefensev2.classification.MobClassificationScreen());
                } else if (guiType == GuiType.INFO) {
                    List<StageConfig> stages = new ArrayList<>();
                    if (!stagesJson.isEmpty()) {
                        try {
                            List<StageConfig> deserialized = GSON.fromJson(stagesJson, STAGE_LIST_TYPE);
                            if (deserialized != null) {
                                stages.addAll(deserialized);
                            }
                        } catch (Exception e) {
                            // Fallback to local if json failed
                        }
                    }
                    com.nhatbh.basedefensev2.stage.gui.StageInfoScreen.openScreenWithData(stages, currentStageNum);
                }
            });
        });
        ctx.setPacketHandled(true);
        return true;
    }
}
