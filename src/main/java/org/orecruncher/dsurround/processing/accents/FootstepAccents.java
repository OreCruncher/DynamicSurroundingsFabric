package org.orecruncher.dsurround.processing.accents;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.orecruncher.dsurround.config.Configuration;
import org.orecruncher.dsurround.config.libraries.IItemLibrary;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.sound.ISoundFactory;

public class FootstepAccents {

    private final ObjectArray<IFootstepAccentProvider> providers = new ObjectArray<>();

    public FootstepAccents(Configuration config, IItemLibrary itemLibrary) {
        this.providers.add(new ArmorAccents(config, itemLibrary));
        this.providers.add(new WaterySurfaceAccent(config));
        this.providers.add(new FloorSqueakAccent(config));
    }

    public void provide(final LivingEntity entity, final BlockPos pos, final BlockState blockState, final ObjectArray<ISoundFactory> in) {
        var isWaterLogged = blockState.getBlock() instanceof SimpleWaterloggedBlock && !blockState.getFluidState().isEmpty();
        this.providers.forEach(provider -> {
            if (provider.isEnabled())
                provider.provide(entity, pos, blockState, isWaterLogged, in);
        });
    }
}