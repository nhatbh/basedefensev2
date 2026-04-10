package com.nhatbh.basedefensev2.utils;

import com.nhatbh.basedefensev2.BaseDefenseMod;
import java.util.UUID;

/**
 * Utility class for generating deterministic UUIDs for the mod.
 * This ensures consistent UUIDs across game sessions and prevents conflicts.
 */
public class UUIDHelper {

    private static final String MOD_NAMESPACE = BaseDefenseMod.MODID;

    /**
     * Generate a deterministic UUID for an attribute modifier
     * @param category The category of the modifier (e.g., "elemental_reactions", "talents", "effects")
     * @param name The unique name within the category (e.g., "superconduct_armor_reduction")
     * @return A deterministic UUID that will be consistent across game sessions
     */
    public static UUID generateAttributeModifierUUID(String category, String name) {
        String fullName = MOD_NAMESPACE + ":" + category + ":" + name;
        return UUID.nameUUIDFromBytes(fullName.getBytes());
    }

    /**
     * Generate a deterministic UUID for a simple name
     * @param name The unique name
     * @return A deterministic UUID based on the mod ID and name
     */
    public static UUID generateUUID(String name) {
        String fullName = MOD_NAMESPACE + ":" + name;
        return UUID.nameUUIDFromBytes(fullName.getBytes());
    }

    /**
     * Generate a deterministic UUID for entity-specific modifiers
     * @param category The category of the modifier
     * @param entityType The entity type or ID
     * @param modifierName The modifier name
     * @return A deterministic UUID unique to this entity and modifier combination
     */
    public static UUID generateEntitySpecificUUID(String category, String entityType, String modifierName) {
        String fullName = MOD_NAMESPACE + ":" + category + ":" + entityType + ":" + modifierName;
        return UUID.nameUUIDFromBytes(fullName.getBytes());
    }
}
