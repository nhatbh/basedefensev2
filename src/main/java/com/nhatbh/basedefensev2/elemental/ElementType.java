package com.nhatbh.basedefensev2.elemental;

public enum ElementType {
    // Elemental Schools (Primal Cycle)
    FIRE("Fire", "§c"),
    ICE("Ice", "§b"),
    LIGHTNING("Lightning", "§e"),
    NATURE("Nature", "§a"),
    AQUA("Aqua", "§3"),

    // Arcane Schools (Unique Mechanics)
    HOLY("Holy", "§e"),
    EVOCATION("Evocation", "§d"),
    ENDER("Ender", "§5"),
    ELDRITCH("Eldritch", "§4"),
    BLOOD("Blood", "§c"),

    // Fallback/Physical
    PHYSICAL("Physical", "§7");

    private final String displayName;
    private final String colorCode;

    ElementType(String displayName, String colorCode) {
        this.displayName = displayName;
        this.colorCode = colorCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getColorCode() {
        return colorCode;
    }

    public String getFormattedName() {
        return colorCode + displayName + "§r";
    }

    public static ElementType fromString(String name) {
        if (name == null || name.isEmpty()) return null;
        try {
            return ElementType.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
