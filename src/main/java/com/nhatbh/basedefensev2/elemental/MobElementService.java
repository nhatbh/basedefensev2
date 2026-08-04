package com.nhatbh.basedefensev2.elemental;

import com.nhatbh.basedefensev2.boss.core.BossManager;
import com.nhatbh.basedefensev2.elemental.network.MobElementSyncPacket;
import com.nhatbh.basedefensev2.strength.network.NetworkManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "basedefensev2")
public class MobElementService {

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide) return;

        if (event.getEntity() instanceof net.minecraft.world.entity.monster.Enemy || (event.getEntity() instanceof LivingEntity le && BossManager.isBoss(le))) {
            LivingEntity entity = (LivingEntity) event.getEntity();
            
            // Check if element is already assigned
            if (!entity.getPersistentData().contains("ElementType")) {
                ElementType element = null;
                
                // 1. Try from BossDefinition if it's a boss
                if (BossManager.isBoss(entity)) {
                    var bossComp = BossManager.get(entity);
                    if (bossComp != null && bossComp.getDefinition() != null) {
                        var elements = bossComp.getDefinition().getElements();
                        if (elements != null && !elements.isEmpty()) {
                            element = elements.get(0);
                        }
                    }
                }

                // 2. Try from config if still null
                if (element == null) {
                    net.minecraft.resources.ResourceLocation key = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
                    if (key != null) {
                        element = MobElementConfig.getElementFor(key.toString());
                    }
                }

                // 3. Fallback to PHYSICAL if unmapped
                if (element == null) {
                    element = ElementType.PHYSICAL;
                }
                
                entity.getPersistentData().putString("ElementType", element.name());
                NetworkManager.sendToTracking(new com.nhatbh.basedefensev2.elemental.network.MobElementSyncPacket(entity.getId(), element.name()), entity);
            }
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof LivingEntity living && !living.level().isClientSide) {
            if (living instanceof Enemy || BossManager.isBoss(living)) {
                if (living.getPersistentData().contains("ElementType")) {
                    String elementStr = living.getPersistentData().getString("ElementType");
                    NetworkManager.sendToTracking(new MobElementSyncPacket(living.getId(), elementStr), living);
                }
            }
        }
    }

    public static ElementType getElement(LivingEntity entity) {
        String elementStr = entity.getPersistentData().getString("ElementType");
        ElementType type = ElementType.fromString(elementStr);
        return type != null ? type : ElementType.PHYSICAL;
    }
}
