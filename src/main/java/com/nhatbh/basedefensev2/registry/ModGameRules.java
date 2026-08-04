package com.nhatbh.basedefensev2.registry;

import net.minecraft.world.level.GameRules;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import com.nhatbh.basedefensev2.BaseDefenseMod;

@Mod.EventBusSubscriber(modid = BaseDefenseMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModGameRules {

    public static GameRules.Key<GameRules.BooleanValue> RULE_STRICT_PVP_DISABLED;
    public static GameRules.Key<GameRules.BooleanValue> RULE_REVEAL_ALL_STAGES;

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            RULE_STRICT_PVP_DISABLED = GameRules.register(
                "strictPvPDisabled", 
                GameRules.Category.PLAYER, 
                GameRules.BooleanValue.create(true) 
            );
            RULE_REVEAL_ALL_STAGES = GameRules.register(
                "revealAllStages",
                GameRules.Category.MISC,
                GameRules.BooleanValue.create(false)
            );
        });
    }
}
