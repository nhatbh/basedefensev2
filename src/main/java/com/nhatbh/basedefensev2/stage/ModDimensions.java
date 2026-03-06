package com.nhatbh.basedefensev2.stage;

import com.nhatbh.basedefensev2.BaseDefenseMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * Central registry for the arena dimension resource key.
 * The dimension itself is defined in:
 *   data/basedefensev2/dimension/arena.json
 *   data/basedefensev2/dimension_type/arena.json
 */
public class ModDimensions {
    public static final ResourceKey<Level> ARENA = ResourceKey.create(
            Registries.DIMENSION,
            new ResourceLocation(BaseDefenseMod.MODID, "arena")
    );

    private ModDimensions() {}
}
