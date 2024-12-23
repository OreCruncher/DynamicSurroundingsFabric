package org.orecruncher.dsurround.lib;

public enum MinecraftServerType {
    VANILLA(false),
    PAPER(false),
    FABRIC(true),
    FORGE(true),
    NEOFORGE(true),
    OTHER(false);

    private final boolean isModded;

    MinecraftServerType(boolean isModded) {
        this.isModded = isModded;
    }

    public boolean isModded() {
        return this.isModded;
    }

    public static MinecraftServerType fromBrand(String serverBrand) {
        var brand = serverBrand.toLowerCase();
        return switch (brand) {
            case "vanilla" -> VANILLA;
            case "paper" -> PAPER;
            case "forge" -> FORGE;
            case "fabric" -> FABRIC;
            case "neoforge" -> NEOFORGE;
            default -> OTHER;
        };
    }
}
