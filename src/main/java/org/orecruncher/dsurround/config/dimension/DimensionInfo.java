package org.orecruncher.dsurround.config.dimension;

import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.config.data.DimensionConfigRule;
import org.orecruncher.dsurround.lib.world.WorldUtils;

public class DimensionInfo {

    public static final DimensionInfo NONE = new DimensionInfo();
    private static final int SPACE_HEIGHT_OFFSET = 32;
    protected final boolean isFlatWorld;
    // Attributes about the dimension. This is information is loaded from local configs.
    protected Identifier name;
    protected int seaLevel;
    protected int skyHeight;
    protected int cloudHeight;
    protected int spaceHeight;
    protected boolean alwaysOutside = false;
    protected boolean playBiomeSounds = true;

    DimensionInfo() {
        this.name = new Identifier(Client.ModId, "no_dimension");
        this.isFlatWorld = false;
    }

    public DimensionInfo(final World world) {
        // Attributes that come from the world object itself. Set now because the config may override.
        this.name = world.getRegistryKey().getValue();
        this.seaLevel = world.getSeaLevel();
        this.skyHeight = world.getHeight();
        this.cloudHeight = this.skyHeight;
        this.spaceHeight = this.skyHeight + SPACE_HEIGHT_OFFSET;
        this.isFlatWorld = WorldUtils.isSuperFlat(world);

        // Force sea level based on known world types that give heartburn
        if (this.isFlatWorld)
            this.seaLevel = 0;
    }

    public void update(DimensionConfigRule config) {
        if (this.name.equals(config.dimensionId())) {
            config.seaLevel().ifPresent(v -> this.seaLevel = v);
            config.skyHeight().ifPresent(v -> this.skyHeight = v);
            config.alwaysOutside().ifPresent(v -> this.alwaysOutside = v);
            config.playBiomeSounds().ifPresent(v -> this.playBiomeSounds = v);
            config.cloudHeight().ifPresentOrElse(
                    v -> this.cloudHeight = v,
                    () -> this.cloudHeight = this.skyHeight / 2);

            this.spaceHeight = this.skyHeight + SPACE_HEIGHT_OFFSET;
        }
    }

    public Identifier getName() {
        return this.name;
    }

    public int getSeaLevel() {
        return this.seaLevel;
    }

    public int getSkyHeight() {
        return this.skyHeight;
    }

    public int getCloudHeight() {
        return this.cloudHeight;
    }

    public int getSpaceHeight() {
        return this.spaceHeight;
    }

    public boolean playBiomeSounds() {
        return this.playBiomeSounds;
    }

    public boolean alwaysOutside() {
        return this.alwaysOutside;
    }

    public boolean isFlatWorld() {
        return this.isFlatWorld;
    }

}
