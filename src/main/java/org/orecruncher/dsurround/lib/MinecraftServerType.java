package org.orecruncher.dsurround.lib;

public enum MinecraftServerType {
    VANILLA(false),
    PAPER(false),
    FABRIC(true),
    FORGE(true),
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
        if ("vanilla".equals(brand))
            return VANILLA;
        if ("paper".equals(brand))
            return PAPER;
        if ("forge".equals(brand))
            return FORGE;
        if ("fabric".equals(brand))
            return FABRIC;
        return OTHER;
    }
}
