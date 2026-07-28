package com.nhatbh.basedefensev2.boss.client;

import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

public class BossResourceBarRegistry {

    public static class ResourceBarInfo {
        public final Supplier<String> nameSupplier;
        public final Supplier<Float> currentSupplier;
        public final Supplier<Float> maxSupplier;
        public final Supplier<Integer> colorTopSupplier;
        public final Supplier<Integer> colorBottomSupplier;

        public ResourceBarInfo(Supplier<String> nameSupplier, Supplier<Float> currentSupplier, Supplier<Float> maxSupplier, Supplier<Integer> colorTopSupplier, Supplier<Integer> colorBottomSupplier) {
            this.nameSupplier = nameSupplier;
            this.currentSupplier = currentSupplier;
            this.maxSupplier = maxSupplier;
            this.colorTopSupplier = colorTopSupplier;
            this.colorBottomSupplier = colorBottomSupplier;
        }
    }

    private static final Map<LivingEntity, ResourceBarInfo> REGISTRY = new WeakHashMap<>();

    public static void registerBar(LivingEntity boss, String name, Supplier<Float> currentSupplier, Supplier<Float> maxSupplier, int colorTop, int colorBottom) {
        registerBar(boss, () -> name, currentSupplier, maxSupplier, () -> colorTop, () -> colorBottom);
    }

    public static void registerBar(LivingEntity boss, Supplier<String> nameSupplier, Supplier<Float> currentSupplier, Supplier<Float> maxSupplier, Supplier<Integer> colorTopSupplier, Supplier<Integer> colorBottomSupplier) {
        REGISTRY.put(boss, new ResourceBarInfo(nameSupplier, currentSupplier, maxSupplier, colorTopSupplier, colorBottomSupplier));
    }

    public static void unregisterBar(LivingEntity boss) {
        REGISTRY.remove(boss);
    }

    public static ResourceBarInfo getBar(LivingEntity boss) {
        return REGISTRY.get(boss);
    }
}
