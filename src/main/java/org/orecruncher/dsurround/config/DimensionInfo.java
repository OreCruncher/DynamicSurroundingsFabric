package org.orecruncher.dsurround.config;

import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionType;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.config.data.DimensionConfig;
import org.orecruncher.dsurround.lib.world.WorldUtils;

public class DimensionInfo {

    private static final int SPACE_HEIGHT_OFFSET = 32;

    public static final  DimensionInfo NONE = new DimensionInfo();

    // Attributes about the dimension. This is information is loaded from local configs.
    protected Identifier name;
    protected int seaLevel;
    protected int skyHeight;
    protected int cloudHeight;
    protected int spaceHeight;
    protected boolean hasHaze = false;
    protected boolean hasAuroras = false;
    protected boolean hasFog = false;
    protected boolean alwaysOutside = false;
    protected boolean playBiomeSounds = true;

    protected final boolean isFlatWorld;

    DimensionInfo() {
        this.name = new Identifier(Client.ModId, "no_dimension");
        this.isFlatWorld = false;
    }

    public DimensionInfo(final World world, final DimensionConfig dimConfig) {
        // Attributes that come from the world object itself. Set now because the config may override.
        DimensionType dt = world.getDimension();
        this.name = world.getRegistryKey().getValue();
        this.seaLevel = world.getSeaLevel();
        this.skyHeight = world.getHeight();
        this.cloudHeight = this.skyHeight;
        this.spaceHeight = this.skyHeight + SPACE_HEIGHT_OFFSET;
        this.isFlatWorld = WorldUtils.isSuperFlat(world);

        if (dt.isNatural() && dt.hasSkyLight()) {
            this.hasAuroras = true;
            this.hasFog = true;
        }

        // Force sea level based on known world types that give heartburn
        if (this.isFlatWorld)
            this.seaLevel = 0;

        // Override based on player config settings
        if (dimConfig != null) {
            if (dimConfig.seaLevel != null)
                this.seaLevel = dimConfig.seaLevel;
            if (dimConfig.skyHeight != null)
                this.skyHeight = dimConfig.skyHeight;
            if (dimConfig.hasHaze != null)
                this.hasHaze = dimConfig.hasHaze;
            if (dimConfig.cloudHeight != null)
                this.cloudHeight = dimConfig.cloudHeight;
            else
                this.cloudHeight = this.hasHaze ? this.skyHeight / 2 : this.skyHeight;
            if (dimConfig.alwaysOutside != null)
                this.alwaysOutside = dimConfig.alwaysOutside;

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

    public boolean hasHaze() {
        return this.hasHaze;
    }

    public boolean hasAuroras() {
        return this.hasAuroras;
    }

    public boolean hasFog() {
        return this.hasFog;
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