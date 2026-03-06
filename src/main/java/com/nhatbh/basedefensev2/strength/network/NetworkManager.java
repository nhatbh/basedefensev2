package com.nhatbh.basedefensev2.strength.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class NetworkManager {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("basedefensev2", "strength_sync"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public static void register() {
         int id = 0;
         INSTANCE.registerMessage(id++, EntityStrengthSyncPacket.class,
                 EntityStrengthSyncPacket::toBytes,
                 EntityStrengthSyncPacket::new,
                 EntityStrengthSyncPacket::handle);

         INSTANCE.registerMessage(id++, com.nhatbh.basedefensev2.elemental.network.MobElementSyncPacket.class,
                 com.nhatbh.basedefensev2.elemental.network.MobElementSyncPacket::toBytes,
                 com.nhatbh.basedefensev2.elemental.network.MobElementSyncPacket::new,
                 com.nhatbh.basedefensev2.elemental.network.MobElementSyncPacket::handle);

         INSTANCE.registerMessage(id++, com.nhatbh.basedefensev2.stage.network.StageHudSyncPacket.class,
                 com.nhatbh.basedefensev2.stage.network.StageHudSyncPacket::toBytes,
                 com.nhatbh.basedefensev2.stage.network.StageHudSyncPacket::new,
                 com.nhatbh.basedefensev2.stage.network.StageHudSyncPacket::handle);

         INSTANCE.registerMessage(id++, com.nhatbh.basedefensev2.sanctity.network.SanctitySyncPacket.class,
                 com.nhatbh.basedefensev2.sanctity.network.SanctitySyncPacket::toBytes,
                 com.nhatbh.basedefensev2.sanctity.network.SanctitySyncPacket::new,
                 com.nhatbh.basedefensev2.sanctity.network.SanctitySyncPacket::handle);
    }

    public static void sendToTracking(Object packet, net.minecraft.world.entity.Entity entity) {
         INSTANCE.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity), packet);
    }
}
