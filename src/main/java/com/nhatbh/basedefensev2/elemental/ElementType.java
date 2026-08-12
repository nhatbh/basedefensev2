package com.nhatbh.basedefensev2.elemental;

public enum ElementType {
    // Elemental Schools (Primal Cycle)
    FIRE("Fire", "§c", "🔥", 0xFFFF5555),
    ICE("Ice", "§b", "❄", 0xFFAADDFF),
    LIGHTNING("Lightning", "§e", "⚡", 0xFFFFDD55),
    NATURE("Nature", "§a", "🌿", 0xFF55FF55),
    AQUA("Aqua", "§3", "💧", 0xFF55FFFF),

    // Arcane Schools (Unique Mechanics)
    HOLY("Holy", "§e", "✨", 0xFFFFEE88),
    EVOCATION("Evocation", "§d", "🔮", 0xFFFF77FF),
    ENDER("Ender", "§5", "👁", 0xFFAA55FF),
    ELDRITCH("Eldritch", "§4", "🐙", 0xFFAA2222),
    BLOOD("Blood", "§c", "🩸", 0xFFDD2222),

    // Fallback/Physical
    PHYSICAL("Physical", "§7", "⚔", 0xAAAAAA);

    private final String displayName;
    private final String colorCode;
    private final String icon;
    private final int color;

    ElementType(String displayName, String colorCode, String icon, int color) {
        this.displayName = displayName;
        this.colorCode = colorCode;
        this.icon = icon;
        this.color = color;
    }

    public int getColor() {
        return color;
    }

    public String getIcon() {
        return icon;
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
