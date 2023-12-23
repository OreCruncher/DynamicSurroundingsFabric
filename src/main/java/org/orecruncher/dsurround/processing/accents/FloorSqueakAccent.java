package org.orecruncher.dsurround.processing.accents;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.block.Waterloggable;
import net.minecraft.entity.LivingEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.orecruncher.dsurround.config.Configuration;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.random.XorShiftRandom;
import org.orecruncher.dsurround.sound.ISoundFactory;
import org.orecruncher.dsurround.sound.SoundFactoryBuilder;
import org.orecruncher.dsurround.tags.BlockEffectTags;

@Environment(EnvType.CLIENT)
class FloorSqueakAccent  implements IFootstepAccentProvider {

    private static final ISoundFactory floorSqueakFactory = SoundFactoryBuilder
            .create(SoundEvent.of(new Identifier("dsurround", "footsteps.floor_squeak")))
            .category(SoundCategory.PLAYERS).volume(0.3F).pitchRange(0.8F, 1.2F).build();

    private final Configuration config;

    FloorSqueakAccent(Configuration config) {
        this.config = config;
    }

    @Override
    public void provide(LivingEntity entity, BlockPos pos, BlockState state, ObjectArray<ISoundFactory> acoustics) {
        if (state.isIn(BlockEffectTags.FLOOR_SQUEAKS)) {
            // Check for waterlogged.  Don't want to squeak if waterlogged.
            if (state.getBlock() instanceof Waterloggable && !state.getFluidState().isEmpty())
                return;

            // 1 in 10 chance of a squeak
            if (XorShiftRandom.current().nextInt(10) == 0)
                acoustics.add(floorSqueakFactory);
        }
    }

    @Override
    public boolean isEnabled() {
        return this.config.footstepAccents.enableFloorSqueaks;
    }
}