package com.nhatbh.basedefensev2.effects;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.client.extensions.common.IClientMobEffectExtensions;

import java.util.function.Consumer;

/**
 * Suppression potion effect.
 * While active on an entity, slows down poise exhaustion recovery timer by 1.5x.
 * Hidden from status effect HUD and inventory text displays.
 */
public class SuppressionEffect extends MobEffect {
    public SuppressionEffect() {
        super(MobEffectCategory.HARMFUL, 0x4A6984);
    }

    @Override
    public void initializeClient(Consumer<IClientMobEffectExtensions> consumer) {
        consumer.accept(new IClientMobEffectExtensions() {
            @Override
            public boolean isVisibleInInventory(MobEffectInstance instance) {
                return false;
            }

            @Override
            public boolean isVisibleInGui(MobEffectInstance instance) {
                return false;
            }
        });
    }
}
