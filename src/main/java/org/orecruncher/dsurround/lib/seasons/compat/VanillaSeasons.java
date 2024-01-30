package org.orecruncher.dsurround.lib.seasons.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.locale.Language;
import net.minecraft.world.level.Level;
import org.orecruncher.dsurround.config.libraries.IReloadEvent;
import org.orecruncher.dsurround.mixinutils.IBiomeExtended;

import java.util.Optional;

public class VanillaSeasons extends AbstractSeasonProvider {

    private String currentSeasonString;

    public VanillaSeasons() {
        super("Vanilla");
    }

    @Override
    public Optional<String> getCurrentSeason(Level world) {
        return Optional.of(this.currentSeasonString);
    }

    @Override
    public Optional<String> getCurrentSeasonTranslated(Level world) {
        return Optional.of(this.currentSeasonString);
    }

    @Override
    public float getTemperature(Level world, BlockPos blockPos) {
        var biome = world.getBiome(blockPos).value();
        return ((IBiomeExtended)(Object)biome).dsurround_getTemperature(blockPos);
    }

    @Override
    protected void reloadResources(IReloadEvent.Scope scope) {
        if (scope == IReloadEvent.Scope.TAGS)
            return;
        this.currentSeasonString = Language.getInstance().getOrDefault("dsurround.text.seasons.spring");
    }
}
