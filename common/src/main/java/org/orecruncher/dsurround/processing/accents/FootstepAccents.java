package org.orecruncher.dsurround.processing.accents;

import dev.architectury.platform.Platform;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.config.libraries.IItemLibrary;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.sound.ISoundFactory;

public class FootstepAccents {

    static final ITagLibrary TAG_LIBRARY = ContainerManager.resolve(ITagLibrary.class);

    private final ObjectArray<IFootstepAccentProvider> providers = new ObjectArray<>();

    public FootstepAccents(Configuration config, IItemLibrary itemLibrary) {
        this.providers.add(new ArmorAccents(config, itemLibrary));
        this.providers.add(new FloorSqueakAccent(config));

        // Only register these providers if Presence Footsteps is not installed
        if (!Platform.isModLoaded(Constants.MOD_PRESENCE_FOOTSTEPS)) {
            this.providers.add(new WaterySurfaceAccent(config));
        }
    }

    public void collect(final LivingEntity entity, final BlockPos pos, final BlockState blockState, final ObjectArray<ISoundFactory> in) {
        var isWaterLogged = blockState.getBlock() instanceof SimpleWaterloggedBlock && !blockState.getFluidState().isEmpty();
        this.providers.forEach(provider -> {
            if (provider.isEnabled())
                provider.collect(entity, pos, blockState, isWaterLogged, in);
        });
    }
}