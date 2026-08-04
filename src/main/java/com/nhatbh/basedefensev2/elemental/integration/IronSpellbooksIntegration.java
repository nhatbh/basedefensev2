package com.nhatbh.basedefensev2.elemental.integration;

import com.nhatbh.basedefensev2.elemental.ElementType;
import com.nhatbh.basedefensev2.elemental.events.ElementalDamageEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "basedefensev2")
public class IronSpellbooksIntegration {




    @SubscribeEvent
    public static void onMobEffectAdded(MobEffectEvent.Added event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide)
            return;

        MobEffectInstance effectInstance = event.getEffectInstance();
        if (effectInstance == null)
            return;

        String descriptionId = effectInstance.getEffect().getDescriptionId();
        if ("effect.traveloptics.wet".equals(descriptionId)) {
            MinecraftForge.EVENT_BUS.post(new ElementalDamageEvent(target, target, ElementType.AQUA, 0));
        }
    }
}
