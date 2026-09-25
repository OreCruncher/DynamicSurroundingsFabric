package org.orecruncher.dsurround.runtime.oracle.impl;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import org.orecruncher.dsurround.config.DimensionInfo;
import org.orecruncher.dsurround.config.libraries.AssetLibraryEvent;
import org.orecruncher.dsurround.eventing.ClientState;
import org.orecruncher.dsurround.lib.CachingSupplier;
import org.orecruncher.dsurround.runtime.oracle.IDimensionOracle;
import org.orecruncher.dsurround.config.libraries.IDimensionLibrary;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.events.HandlerPriority;

public final class DimensionOracle implements IDimensionOracle {

    private final IDimensionLibrary dimensionLibrary;

    private final CachingSupplier<DimensionInfo> dimensionInfo;

    public DimensionOracle(IDimensionLibrary dimensionLibrary) {
        this.dimensionLibrary = dimensionLibrary;
        this.dimensionInfo = CachingSupplier.from(() -> this.dimensionLibrary.getData(GameUtils.getMC().level));

        // Need to reset the cached dimension info whenever the client world
        // changes or if there is a resource reload.
        ClientState.CLIENT_LEVEL_LOAD_EVENT.register(_ -> this.dimensionInfo.clear(), HandlerPriority.HIGH);
        AssetLibraryEvent.RELOAD.register((_, _) -> this.dimensionInfo.clear(), HandlerPriority.HIGH);
    }

    public Identifier name() {
        return this.dimensionInfo.get().getName();
    }

    public ClientLevel level() {
        return GameUtils.getWorld().orElseThrow();
    }

    public int seaLevel() {
        return this.dimensionInfo.get().getSeaLevel();
    }

    public boolean alwaysOutside() {
        return this.dimensionInfo.get().alwaysOutside();
    }

    public int getSpaceHeight() {
        return this.dimensionInfo.get().getSpaceHeight();
    }

    public int getCloudHeight() {
        return this.dimensionInfo.get().getCloudHeight();
    }

    public boolean getCompassWobble() {
        return this.dimensionInfo.get().getCompassWobble();
    }

    public boolean natural() {
        return this.dimensionInfo.get().natural();
    }

    public boolean isSuperFlat() {
        return this.dimensionInfo.get().isFlatWorld();
    }
}
