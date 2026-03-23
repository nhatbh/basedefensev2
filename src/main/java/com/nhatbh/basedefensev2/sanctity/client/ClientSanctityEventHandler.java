package com.nhatbh.basedefensev2.sanctity.client;

import com.nhatbh.basedefensev2.BaseDefenseMod;
import com.nhatbh.basedefensev2.sanctity.network.ClientSanctityData;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = BaseDefenseMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientSanctityEventHandler {

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        // Reset client-side sanctity data when leaving a world to prevent "bleeding" into the next world
        ClientSanctityData.reset();
    }
}
