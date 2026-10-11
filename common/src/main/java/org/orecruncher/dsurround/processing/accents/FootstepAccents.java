package org.orecruncher.dsurround.processing.accents;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.config.libraries.IItemLibrary;
import org.orecruncher.dsurround.config.libraries.ISoundLibrary;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.platform.KnownMod;
import org.orecruncher.dsurround.runtime.weather.IWeatherSimulation;

public class FootstepAccents {

    private final ObjectArray<IFootstepAccentProvider> providers = new ObjectArray<>();

    public FootstepAccents(Configuration config, IItemLibrary itemLibrary, ISoundLibrary soundLibrary, ITagLibrary tagLibrary, IWeatherSimulation weatherSimulation) {
        this.providers.add(new ArmorAccents(config, itemLibrary));
        this.providers.add(new FloorSqueakAccent(config, soundLibrary, tagLibrary));

        // Only register these providers if Presence Footsteps is not installed
        if (!KnownMod.PRESENCE_FOOTSTEPS.isInstalled()) {
            this.providers.add(new WaterySurfaceAccent(config, soundLibrary, tagLibrary, weatherSimulation));
        }
    }

    public void collect(final LivingEntity entity, final BlockPos pos, final BlockState blockState, final StepAccents in) {
        var isWaterLogged = blockState.getBlock() instanceof SimpleWaterloggedBlock && !blockState.getFluidState().isEmpty();
        this.providers.forEach(provider -> {
            if (provider.isEnabled())
                provider.collect(entity, pos, blockState, isWaterLogged, in);
        });
    }
}