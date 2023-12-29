package org.orecruncher.dsurround.effects.systems;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SideShapeType;
import net.minecraft.client.option.ParticlesMode;
import net.minecraft.fluid.FlowableFluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.config.Configuration;
import org.orecruncher.dsurround.effects.BlockEffectUtils;
import org.orecruncher.dsurround.effects.IEffectSystem;
import org.orecruncher.dsurround.effects.blocks.ParticleJetEffect;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.sound.BackgroundSoundLoop;
import org.orecruncher.dsurround.sound.ISoundFactory;
import org.orecruncher.dsurround.sound.SoundFactoryBuilder;
import org.orecruncher.dsurround.sound.SoundInstanceHandler;

import java.util.Arrays;

import static org.orecruncher.dsurround.effects.BlockEffectUtils.HAS_FLUID;

public class WaterfallEffectSystem extends AbstractEffectSystem implements IEffectSystem {

    private static final Vec3d SPLASH_INTENSITY = new Vec3d(0.05, 0.05, 0.05);
    private final static Vec3i[] CARDINAL_OFFSETS = {
            new Vec3i(-1, 0, 0),
            new Vec3i(1, 0, 0),
            new Vec3i(0, 0, -1),
            new Vec3i(0, 0, 1)
    };

    private static final ISoundFactory[] ACOUSTICS = new ISoundFactory[BlockEffectUtils.MAX_STRENGTH + 1];

    static {
        var factory = SoundFactoryBuilder.create(new Identifier(Constants.MOD_ID, "waterfall.0"))
                .pitchRange(0.8F, 1.2F)
                .build();
        Arrays.fill(ACOUSTICS, factory);

        factory = SoundFactoryBuilder.create(new Identifier(Constants.MOD_ID, "waterfall.1"))
                .pitchRange(0.8F, 1.2F)
                .build();
        ACOUSTICS[2] = ACOUSTICS[3] = factory;

        factory = SoundFactoryBuilder.create(new Identifier(Constants.MOD_ID, "waterfall.2"))
                .pitchRange(0.8F, 1.2F)
                .build();
        ACOUSTICS[4] = factory;

        factory = SoundFactoryBuilder.create(new Identifier(Constants.MOD_ID, "waterfall.3"))
                .pitchRange(0.8F, 1.2F)
                .build();
        ACOUSTICS[5] = ACOUSTICS[6] = factory;

        factory = SoundFactoryBuilder.create(new Identifier(Constants.MOD_ID, "waterfall.4"))
                .pitchRange(0.8F, 1.2F)
                .build();
        ACOUSTICS[7] = ACOUSTICS[8] = factory;

        factory = SoundFactoryBuilder.create(new Identifier(Constants.MOD_ID, "waterfall.5"))
                .pitchRange(0.8F, 1.2F)
                .build();
        ACOUSTICS[9] = ACOUSTICS[10] = factory;
    }

    public WaterfallEffectSystem(Configuration config) {
        super(config,"Waterfall");
    }

    @Override
    public boolean isEnabled() {
        return this.config.blockEffects.waterfallsEnabled;
    }

    @Override
    public void blockScan(World world, BlockState state, BlockPos pos) {
        // Steam jet can form if the blockState in question is a fluid block, there is an air block
        // above, and there is a hot block adjacent.
        if (canWaterfallSpawn(world, state, pos)) {
            // Ignore if steam is already present.  This scan is due to a block update of some
            // sort.
            if (this.hasSystemAtPosition(pos))
                return;

            // We are going for spawn! The location of where the steam column starts
            // is based on whether we have a fluid or a solid water block like a
            // water cauldron.
            var effect = getWaterfallEffect(world, state, pos);
            this.systems.put(pos.asLong(), effect);
        }
    }

    @NotNull
    private static WaterfallEffect getWaterfallEffect(World world, BlockState state, BlockPos pos) {
        var strength = BlockEffectUtils.countVerticalBlocks(world, pos, HAS_FLUID, 1);
        final float height = state.getFluidState().getHeight(world, pos) + 0.1F;
        return new WaterfallEffect(strength, world, pos, height);
    }

    private static boolean canWaterfallSpawn(World world, BlockState state, BlockPos pos) {
        return state.getBlock() != Blocks.LAVA && isValidWaterfallSource(world, state, pos);
    }

    private static boolean isValidWaterfallSource(World world, BlockState state, BlockPos pos) {
        if (state.getFluidState().isEmpty())
            return false;
        if (world.getFluidState(pos.up()).isEmpty())
            return false;
        if (isUnboundedLiquid(world, pos)) {
            var downPos = pos.down();
            if (world.getBlockState(downPos).isSideSolid(world, downPos, Direction.UP, SideShapeType.FULL))
                return true;
            return isBoundedLiquid(world, pos);
        }
        return false;
    }

    private static boolean isUnboundedLiquid(final World provider, final BlockPos pos) {
        var mutable = new BlockPos.Mutable();
        for (final Vec3i cardinal_offset : CARDINAL_OFFSETS) {
            final BlockPos tp = mutable.set(pos, cardinal_offset);
            final BlockState state = provider.getBlockState(tp);
            if (state.isAir())
                return true;
            final FluidState fluidState = state.getFluidState();
            final int height = fluidState.getLevel();
            if (height > 0 && height < 8)
                return true;
        }

        return false;
    }

    private static boolean isBoundedLiquid(World provider, BlockPos pos) {
        var mutable = new BlockPos.Mutable();
        for (final Vec3i cardinal_offset : CARDINAL_OFFSETS) {
            final BlockPos tp = mutable.set(pos, cardinal_offset);
            final BlockState state = provider.getBlockState(tp);
            if (state.isAir())
                return false;
            final FluidState fluidState = state.getFluidState();
            if (fluidState.isEmpty()) {
                continue;
            }
            if (fluidState.get(FlowableFluid.FALLING))
                return false;
            final int height = fluidState.getLevel();
            if (height > 0 && height < 8)
                return false;
        }

        return true;
    }

    private static class WaterfallEffect extends ParticleJetEffect {

        private static final Configuration.BlockEffects CONFIG = ContainerManager.resolve(Configuration.BlockEffects.class);

        protected BackgroundSoundLoop sound;
        protected int particleLimit;
        protected final double deltaY;

        public WaterfallEffect(final int strength, final World world, final BlockPos loc, final double dY) {
            super(strength, world, loc.getX() + 0.5D, loc.getY() + 0.5D, loc.getZ() + 0.5D, 4);
            this.deltaY = loc.getY() + dY;
            setSpawnCount((int) (strength * 2.5F));
        }

        public void setSpawnCount(final int limit) {
            this.particleLimit = MathHelper.clamp(limit, 5, 20);
        }

        @Override
        public boolean shouldDie() {
            // Check every half second
            return (this.particleAge % 10) == 0
                    && !canWaterfallSpawn(this.world, this.world.getBlockState(this.position), this.position);
        }

        @Override
        protected void soundUpdate() {
            if (!CONFIG.enableWaterfallSounds) {
                if (this.sound != null) {
                    AUDIO_PLAYER.stop(this.sound);
                    this.sound = null;
                }
                return;
            }

            if (this.sound == null) {
                final int idx = MathHelper.clamp(this.jetStrength, 0, ACOUSTICS.length - 1);
                this.sound = ACOUSTICS[idx].createBackgroundSoundLoopAt(this.position);
            }

            var player = GameUtils.getPlayer().orElseThrow();
            final boolean inRange = SoundInstanceHandler.inRange(player.getEyePos(), this.sound, 4);
            final boolean isDone = !AUDIO_PLAYER.isPlaying(this.sound);

            if (inRange && isDone) {
                AUDIO_PLAYER.play(this.sound);
            } else if (!inRange && !isDone) {
                AUDIO_PLAYER.stop(this.sound);
            }
        }

        @Override
        protected void cleanUp() {
            if (this.sound != null) {
                AUDIO_PLAYER.stop(this.sound);
                this.sound = null;
            }
            super.cleanUp();
        }

        private int getSplashParticleSpawnCount() {
            ParticlesMode state = GameUtils.getGameSettings().map(v -> v.getParticles().getValue()).orElse(ParticlesMode.ALL);
            var count = switch (state) {
                case MINIMAL -> 0;
                case ALL -> this.particleLimit;
                default -> this.particleLimit / 2;
            };

            var x = count / 2;
            return RANDOM.nextInt(count - x) + x;
        }

        @Override
        protected void spawnJetParticle() {
            if (!CONFIG.enableWaterfallParticles)
                return;

            var intensity = SPLASH_INTENSITY.multiply(this.jetStrength);

            for (int i = 0; i <= this.getSplashParticleSpawnCount(); i++) {

                final double xOffset = (RANDOM.nextFloat() * 2.0F - 1.0F);
                final double zOffset = (RANDOM.nextFloat() * 2.0F - 1.0F);

                final double motionX = xOffset * intensity.getX();
                final double motionZ = zOffset * intensity.getZ();
                final double motionY = 0.1D + RANDOM.nextFloat() * intensity.getY();

                var posX = this.posX + xOffset;
                var posY = this.deltaY;
                var posZ = this.posZ + zOffset;

                var particle = this.createParticle(ParticleTypes.SPLASH, posX, posY, posZ, motionX, motionY, motionZ);

                particle.ifPresent(p -> {
                    p.setVelocity(motionX, motionY, motionZ);
                    p.setMaxAge(p.getMaxAge() * 2);
                    this.addParticle(p);
                });
            }
        }
    }
}