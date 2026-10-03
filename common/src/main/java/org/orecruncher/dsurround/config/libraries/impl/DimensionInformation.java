package org.orecruncher.dsurround.config.libraries.impl;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import org.orecruncher.dsurround.config.DimensionInfo;
import org.orecruncher.dsurround.config.libraries.IDimensionInformation;
import org.orecruncher.dsurround.config.libraries.IDimensionLibrary;
import org.orecruncher.dsurround.lib.GameUtils;

/**
 * The current dimension's settings. The info is cached for the current level and the dimension library's version,
 * and rebuilt when either changes: a different level (changing dimension, joining another world) or a reload. No
 * events are needed to keep it current, so it doesn't depend on the order handlers run in.
 * <p>
 * Client thread only. Methods other than {@link #level()} need to be in a world.
 */
public class DimensionInformation implements IDimensionInformation {

    private final IDimensionLibrary dimensionLibrary;
    private final VersionedCache<ClientLevel, DimensionInfo> info = new VersionedCache<>();

    public DimensionInformation(IDimensionLibrary dimensionLibrary) {
        this.dimensionLibrary = dimensionLibrary;
    }

    public ResourceLocation name() {
        return this.getInfo().getName();
    }

    public ClientLevel level() {
        return GameUtils.getWorld().orElseThrow();
    }

    public int seaLevel() {
        return this.getInfo().getSeaLevel();
    }

    public boolean alwaysOutside() {
        return this.getInfo().alwaysOutside();
    }

    public int getSpaceHeight() {
        return this.getInfo().getSpaceHeight();
    }

    public int getCloudHeight() {
        return this.getInfo().getCloudHeight();
    }

    public boolean getCompassWobble() {
        return this.getInfo().getCompassWobble();
    }

    private DimensionInfo getInfo() {
        return this.info.get(this.level(), this.dimensionLibrary.getVersion(), this.dimensionLibrary::getData);
    }
}
