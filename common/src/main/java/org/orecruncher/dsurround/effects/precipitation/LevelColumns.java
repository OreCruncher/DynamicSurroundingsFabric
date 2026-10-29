package org.orecruncher.dsurround.effects.precipitation;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.config.libraries.IBiomeLibrary;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.lib.seasons.ISeasonalInformation;
import org.orecruncher.dsurround.runtime.weather.DustStorms;
import org.orecruncher.dsurround.tags.BlockEffectTags;

import java.util.function.IntPredicate;

/**
 * The columns of the player's level, for the drop field. Rain or snow by the biome and the season's temperature at the
 * position, so high mountains get snow, as vanilla decides it, with sleet just above the snow line; dust where the
 * biome gets neither and is dry and sandy, colored by the loose ground it is blown from (the dust sources tag).
 */
public final class LevelColumns implements IColumnSource {

    /**
     * Just above the snow line, the rain is part snow: at most this share, right at the line, thinning to none this far
     * above it in temperature
     */
    static final float SLEET_MAX_SHARE = 0.5F;
    static final float SLEET_BAND = 0.1F;

    private final ISeasonalInformation seasons;
    private final IBiomeLibrary biomeLibrary;
    private final ITagLibrary tagLibrary;
    private final Configuration.WeatherOptions options;
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    // The share of snow in the rain where precipitation() last looked
    private float snowShare;
    private @Nullable ClientLevel level;

    public LevelColumns(ISeasonalInformation seasons, IBiomeLibrary biomeLibrary, ITagLibrary tagLibrary, Configuration.WeatherOptions options) {
        this.seasons = seasons;
        this.biomeLibrary = biomeLibrary;
        this.tagLibrary = tagLibrary;
        this.options = options;
    }

    /**
     * The level the columns are in; null when there is none
     */
    public void setLevel(@Nullable ClientLevel level) {
        this.level = level;
    }

    private ClientLevel level() {
        assert this.level != null;
        return this.level;
    }

    /**
     * The top of the highest block that blocks motion, as the {@code MOTION_BLOCKING} heightmap has it, or higher where
     * a block with a shape that depends on the world is solid there (see {@link #topOfSolid})
     */
    @Override
    public int groundHeight(int x, int z) {
        var level = this.level();
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        if (surface <= ground)
            return ground;
        return topOfSolid(ground, surface, y -> this.solidButNotMarked(level, x, y, z));
    }

    /**
     * Whether the block at the position is solid as it stands in the world, though its state says it isn't. Minecraft
     * judges each block state solid or not from its shape as it is made; a block whose shape depends on the world (a
     * mod's machine whose sides follow its configuration, Mekanism's energy cube) has none then, so it counts as not
     * solid, and is left out of the {@code MOTION_BLOCKING} heightmap unless its mod marks it solid. Rain would fall
     * through it. Here its real shape is judged the way Minecraft judges the others.
     */
    private boolean solidButNotMarked(ClientLevel level, int x, int y, int z) {
        this.pos.set(x, y, z);
        var state = level.getBlockState(this.pos);
        return !state.isSolid() && !state.isAir() && isSolid(state.getCollisionShape(level, this.pos));
    }

    // Looking down from the top of a column for such a block goes no further than this: vines hanging down a cliff
    // would otherwise have a long way to look
    static final int MAX_LOOK_DOWN = 8;

    /**
     * Where precipitation stops in a column: the top of the highest block from {@code surface} down that {@code solidAt}
     * says is solid, looking no further than {@link #MAX_LOOK_DOWN} blocks and no lower than {@code ground}, or
     * {@code ground} if there is none
     *
     * @param ground  the top of the highest block the heightmap says blocks motion
     * @param surface the top of the highest block that isn't air
     */
    static int topOfSolid(int ground, int surface, IntPredicate solidAt) {
        int lowest = Math.max(ground, surface - MAX_LOOK_DOWN);
        for (int y = surface - 1; y >= lowest; y--)
            if (solidAt.test(y))
                return y + 1;
        return ground;
    }

    /**
     * Whether a collision shape is solid by Minecraft's own test (as block states are judged when made): something
     * there, filling most of the block, or a full block tall
     */
    static boolean isSolid(VoxelShape shape) {
        if (shape.isEmpty())
            return false;
        var box = shape.bounds();
        return box.getSize() >= 0.7291666666666666D || box.getYsize() >= 1D;
    }

    @Override
    public PrecipitationKind precipitation(int x, int y, int z) {
        this.pos.set(x, y, z);
        // Whether anything falls, then rain or snow, and how much snow is mixed into the rain, all from one look at
        // the temperature: Serene Seasons builds a string each time it is asked. Both are the season provider's, so
        // the same as vanilla's precipitation would say (seasonal, with Serene Seasons).
        this.snowShare = 0F;
        Biome.Precipitation precipitation;
        if (!this.seasons.hasPrecipitationAt(this.pos)) {
            precipitation = Biome.Precipitation.NONE;
        } else {
            var temperature = this.seasons.getTemperatureAt(this.pos);
            precipitation = temperature < ISeasonalInformation.SNOW_TEMPERATURE ? Biome.Precipitation.SNOW : Biome.Precipitation.RAIN;
            this.snowShare = sleetShare(temperature);
        }
        var dusty = precipitation == Biome.Precipitation.NONE
                && DustStorms.enabled(this.options)
                && DustStorms.isDusty(precipitation, this.level().getBiome(this.pos).value(), this.biomeLibrary);
        return kindOf(precipitation, dusty);
    }

    @Override
    public float snowShare(int x, int y, int z) {
        return this.snowShare;
    }

    @Override
    public boolean isOpen(int x, int y, int z) {
        this.pos.set(x, y, z);
        var level = this.level();
        var state = level.getBlockState(this.pos);
        return state.getFluidState().isEmpty() && state.getCollisionShape(level, this.pos).isEmpty();
    }

    @Override
    public int light(int x, int y, int z) {
        return LevelRenderer.getLightColor(this.level(), this.pos.set(x, y, z));
    }

    @Override
    public int dustColor(int x, int y, int z) {
        this.pos.set(x, y, z);
        var state = this.level().getBlockState(this.pos);
        if (!this.tagLibrary.is(BlockEffectTags.DUST_SOURCES, state))
            return NOT_A_DUST_SOURCE;
        var mapColor = state.getMapColor(this.level(), this.pos);
        return mapColor == MapColor.NONE ? DustStorms.SAND_COLOR : mapColor.col;
    }

    @Override
    public int fallbackDustColor(int x, int y, int z) {
        return DustStorms.dustColor(this.level().getBiome(this.pos.set(x, y, z)).value(), this.biomeLibrary);
    }

    /**
     * The share of the rain that falls as snow at a temperature: half at the snow line, none from {@link #SLEET_BAND}
     * above it. Below the line it is all snow, which isn't asked.
     */
    static float sleetShare(float temperature) {
        float above = temperature - ISeasonalInformation.SNOW_TEMPERATURE;
        if (above < 0F || above >= SLEET_BAND)
            return 0F;
        return SLEET_MAX_SHARE * (1F - above / SLEET_BAND);
    }

    /**
     * What a storm brings to a column, from what the biome and season give it: rain or snow as they say, and where
     * they give nothing, dust if the biome is dusty
     */
    static PrecipitationKind kindOf(Biome.Precipitation precipitation, boolean dusty) {
        return switch (precipitation) {
            case RAIN -> PrecipitationKind.RAIN;
            case SNOW -> PrecipitationKind.SNOW;
            case NONE -> dusty ? PrecipitationKind.DUST : PrecipitationKind.NONE;
        };
    }
}
