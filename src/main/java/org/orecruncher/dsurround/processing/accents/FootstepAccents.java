package org.orecruncher.dsurround.processing.accents;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.BlockPos;
import org.orecruncher.dsurround.config.Configuration;
import org.orecruncher.dsurround.config.libraries.IItemLibrary;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.sound.ISoundFactory;

@Environment(EnvType.CLIENT)
public class FootstepAccents {

    private final ObjectArray<IFootstepAccentProvider> providers = new ObjectArray<>();

    public FootstepAccents(Configuration config, IItemLibrary itemLibrary) {
        this.providers.add(new ArmorAccents(config, itemLibrary));
        this.providers.add(new WaterySurfaceAccent(config));
        this.providers.add(new FloorSqueakAccent(config));
    }

    public void provide(final LivingEntity entity, final BlockPos pos, final BlockState blockState, final ObjectArray<ISoundFactory> in) {
        this.providers.forEach(provider -> {
            if (provider.isEnabled())
                provider.provide(entity, pos, blockState, in);
        });
    }
}