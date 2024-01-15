package org.orecruncher.dsurround.effects.systems;

import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SupportType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.effects.BlockEffectUtils;
import org.orecruncher.dsurround.effects.IBlockEffect;
import org.orecruncher.dsurround.effects.IEffectSystem;
import org.orecruncher.dsurround.effects.blocks.AbstractParticleEmitterEffect;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.sound.*;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.orecruncher.dsurround.effects.BlockEffectUtils.HAS_FLUID;

public class WaterfallEffectSystem extends AbstractEffectSystem implements IEffectSystem {

    private static final int SOUND_CHECK_INTERVAL = 4;
    private static final int SOUND_INSTANCE_CAP = 32;
    private final static Vec3i[] CARDINAL_OFFSETS = {
            new Vec3i(-1, 0, 0),
            new Vec3i(1, 0, 0),
            new Vec3i(0, 0, -1),
            new Vec3i(0, 0, 1)
    };

    private static final ISoundFactory[] ACOUSTICS = new ISoundFactory[BlockEffectUtils.MAX_STRENGTH + 1];

    static {
        var factory = SoundFactoryBuilder.create(new ResourceLocation(Constants.MOD_ID, "waterfall.0"))
                .pitch(0.8F, 1.2F)
                .build();
        Arrays.fill(ACOUSTICS, factory);

        factory = SoundFactoryBuilder.create(new ResourceLocation(Constants.MOD_ID, "waterfall.1"))
                .pitch(0.8F, 1.2F)
                .build();
        ACOUSTICS[2] = ACOUSTICS[3] = factory;

        factory = SoundFactoryBuilder.create(new ResourceLocation(Constants.MOD_ID, "waterfall.2"))
                .pitch(0.8F, 1.2F)
                .build();
        ACOUSTICS[4] = factory;

        factory = SoundFactoryBuilder.create(new ResourceLocation(Constants.MOD_ID, "waterfall.3"))
                .pitch(0.8F, 1.2F)
                .build();
        ACOUSTICS[5] = ACOUSTICS[6] = factory;

        factory = SoundFactoryBuilder.create(new ResourceLocation(Constants.MOD_ID, "waterfall.4"))
                .pitch(0.8F, 1.2F)
                .build();
        ACOUSTICS[7] = ACOUSTICS[8] = factory;

        factory = SoundFactoryBuilder.create(new ResourceLocation(Constants.MOD_ID, "waterfall.5"))
                .pitch(0.8F, 1.2F)
                .build();
        ACOUSTICS[9] = ACOUSTICS[10] = factory;
    }

    // Keep track of sound plays outside the effect.
    private final IAudioPlayer audioPlayer;
    private final Long2ObjectOpenHashMap<BackgroundSoundLoop> waterfallSoundInstances = new Long2ObjectOpenHashMap<>();
    private long soundCheckThrottle;

    public WaterfallEffectSystem(IModLog logger, Configuration config) {
        super(logger, config,"Waterfall");
        this.audioPlayer = ContainerManager.resolve(IAudioPlayer.class);
        this.soundCheckThrottle = 0;
    }

    @Override
    public boolean isEnabled() {
        return this.config.blockEffects.waterfallsEnabled;
    }

    @Override
    public void clear() {
        super.clear();
        this.waterfallSoundInstances.values().forEach(this.audioPlayer::stop);
        this.waterfallSoundInstances.clear();
    }

    @Override
    public void tick(Predicate<IBlockEffect> processingPredicate) {
        // Process the waterfall systems first. This should prune
        // the junk prior to processing sound plays.  Note that the
        // sound instance tracking collection should be kept in sync
        // during this process.
        super.tick(processingPredicate);

        // May need to flat out purge if water fall sounds
        // are disabled.
        if (!(this.isEnabled() && this.config.blockEffects.enableWaterfallSounds)) {
            // Fast happy path for purge
            if (this.waterfallSoundInstances.isEmpty())
                return;
            this.waterfallSoundInstances.values().forEach(this.audioPlayer::stop);
            this.waterfallSoundInstances.clear();
            return;
        }

        // Throttle these checks as it can be expensive
        if (((++this.soundCheckThrottle) % SOUND_CHECK_INTERVAL) != 0)
            return;

        var player = GameUtils.getPlayer().orElseThrow();
        var eyePosition = player.getEyePosition();

        // Do a fancy evaluation to determine the desired sound play locations
        var desiredLocations = this.getDesiredWaterfallSoundLocations();

        // We need to process the existing waterfall effect instances
        // to ensure the sounds are being played.
        for (var system : this.systems.values()) {
            var posIndex = system.getPosIndex();

            var waterFallEffect = (WaterfallEffect) system;
            var sound = this.waterfallSoundInstances.get(posIndex);

            // If it is in a desired location, make it happen
            if (desiredLocations.contains(posIndex)) {
                if (sound == null) {
                    int idx = Mth.clamp(waterFallEffect.getStrength(), 0, ACOUSTICS.length - 1);
                    sound = ACOUSTICS[idx].createBackgroundSoundLoopAt(system.getPos());
                    this.waterfallSoundInstances.put(posIndex, sound);
                }

                final boolean inRange = SoundInstanceHandler.inRange(eyePosition, sound, 4);
                final boolean isDone = !this.audioPlayer.isPlaying(sound);

                if (inRange && isDone) {
                    this.audioPlayer.play(sound);
                } else if (!inRange && !isDone) {
                    this.audioPlayer.stop(sound);
                }
            } else if (sound != null) {
                // Not in the list - cross it off
                this.logger.debug("[%s] removing sound instance %s", this.systemName, sound.toString());
                this.audioPlayer.stop(sound);
                this.waterfallSoundInstances.remove(posIndex);
            }
        }

        this.waterfallSoundInstances.values().removeIf(sound -> {
           if (!this.systems.containsKey(sound.getPos().asLong())) {
               this.logger.debug("[%s] Orphan sound removed: %s", this.systemName, sound.toString());
               this.audioPlayer.stop(sound);
               return true;
           }
           return false;
        });
    }

    protected Set<Long> getDesiredWaterfallSoundLocations() {
        // The goal is to determine where waterfall sounds need to be played given that only
        // a subset of instances will be utilized. To do this, we need to examine all
        // the potential play locations and compare the effect of that sound as compared
        // to its peers.  The results are ranked most effective to least effective, with
        // a limit of SOUND_INSTANCE_CAP being returned.

        // If waterfall sounds are disabled, there are no desirable locations, obviously
        if (!this.config.blockEffects.enableWaterfallSounds || this.systems.isEmpty()) {
            return ImmutableSet.of();
        }

        // The next possible happy path is if the number of active waterfall instances is less
        // than the cap. All can be included.
        if (this.systems.size() <= SOUND_INSTANCE_CAP)
            return this.systems.values().stream().map(IBlockEffect::getPosIndex).collect(Collectors.toSet());

        // This is the interesting path where we need to stack rank using an impact
        // heuristic based on strength and distance to player.
        var player = GameUtils.getPlayer().orElseThrow();
        return this.systems.values().stream()
                .map(e -> {
                    var effect = (WaterfallEffect)e;
                    var posIndex = effect.getPosIndex();
                    var strength = effect.getStrength();
                    var pos = effect.getPosition();
                    var weight = (strength * strength) / player.getEyePosition().distanceToSqr(pos);
                    return Pair.of(weight, posIndex);
                })
                .sorted((e1, e2) -> -Double.compare(e1.key(), e2.key()))
                .limit(SOUND_INSTANCE_CAP)
                .map(Pair::value)
                .collect(Collectors.toSet());
    }

    @Override
    public void blockScan(Level world, BlockState state, BlockPos pos) {
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
            var effect = createWaterfallEffect(world, state, pos);
            this.systems.put(pos.asLong(), effect);
        } else if (this.hasSystemAtPosition(pos)) {
            this.onRemoveSystem(pos.asLong());
        }
    }

    @Override
    protected void onRemoveSystem(long posLong) {
        // Do not unhook - this keeps the sound instance data
        // in sync with the system data.
        super.onRemoveSystem(posLong);
        var sound = this.waterfallSoundInstances.get(posLong);
        if (sound != null) {
            this.audioPlayer.stop(sound);
            this.waterfallSoundInstances.remove(posLong);
        }
    }

    @NotNull
    private static WaterfallEffect createWaterfallEffect(Level world, BlockState state, BlockPos pos) {
        var strength = BlockEffectUtils.countVerticalBlocks(world, pos, HAS_FLUID, 1);
        final float height = state.getFluidState().getHeight(world, pos) + 0.1F;
        return new WaterfallEffect(strength, world, pos, height);
    }

    private static boolean canWaterfallSpawn(Level world, BlockState state, BlockPos pos) {
        return state.getBlock() != Blocks.LAVA && isValidWaterfallSource(world, state, pos);
    }

    private static boolean isValidWaterfallSource(Level world, BlockState state, BlockPos pos) {
        if (state.getFluidState().isEmpty())
            return false;
        if (world.getFluidState(pos.above()).isEmpty())
            return false;
        if (isUnboundedLiquid(world, pos)) {
            var downPos = pos.below();
            if (world.getBlockState(downPos).isFaceSturdy(world, downPos, Direction.UP, SupportType.FULL))
                return true;
            return isBoundedLiquid(world, pos);
        }
        return false;
    }

    private static boolean isUnboundedLiquid(final Level provider, final BlockPos pos) {
        var mutable = new BlockPos.MutableBlockPos();
        for (final Vec3i cardinal_offset : CARDINAL_OFFSETS) {
            final BlockPos tp = mutable.setWithOffset(pos, cardinal_offset);
            final BlockState state = provider.getBlockState(tp);
            if (state.isAir())
                return true;
            final FluidState fluidState = state.getFluidState();
            final int height = fluidState.getAmount();
            // Fluids with a height of 0 are empty.
            if (height > 0 && height < FluidState.AMOUNT_FULL)
                return true;
        }

        return false;
    }

    private static boolean isBoundedLiquid(Level provider, BlockPos pos) {
        var mutable = new BlockPos.MutableBlockPos();
        for (final Vec3i cardinal_offset : CARDINAL_OFFSETS) {
            final BlockPos tp = mutable.setWithOffset(pos, cardinal_offset);
            final BlockState state = provider.getBlockState(tp);
            if (state.isAir())
                return false;
            final FluidState fluidState = state.getFluidState();
            if (fluidState.isEmpty()) {
                continue;
            }
            if (fluidState.hasProperty(FlowingFluid.FALLING))
                return false;
            final int height = fluidState.getAmount();
            if (height > 0 && height < 8)
                return false;
        }

        return true;
    }

    private static class WaterfallEffect extends AbstractParticleEmitterEffect {

        private static final Configuration.BlockEffects CONFIG = ContainerManager.resolve(Configuration.BlockEffects.class);

        protected int particleLimit;
        protected final double deltaY;

        public WaterfallEffect(final int strength, final Level world, final BlockPos loc, final double dY) {
            super(strength, world, loc.getX() + 0.5D, loc.getY() + 0.5D, loc.getZ() + 0.5D, 4);
            this.deltaY = loc.getY() + dY;
            setSpawnCount((int) (strength * 2.5F));
        }

        public void setSpawnCount(final int limit) {
            this.particleLimit = Mth.clamp(limit, 5, 20);
        }

        @Override
        public boolean shouldRemove() {
            // Check every half second
            return (this.age % 10) == 0
                    && !canWaterfallSpawn(this.world, this.world.getBlockState(this.position), this.position);
        }

        private int getSplashParticleSpawnCount() {
            ParticleStatus state = GameUtils.getGameSettings().particles().get();
            var count = switch (state) {
                case MINIMAL -> 0;
                case ALL -> this.particleLimit;
                default -> this.particleLimit / 2;
            };

            var x = count / 2;
            return RANDOM.nextInt(count - x) + x;
        }

        @Override
        protected void handleParticles() {
            if (!CONFIG.enableWaterfallParticles)
                return;

            for (int i = 0; i <= this.getSplashParticleSpawnCount(); i++) {
                this.produceParticle().ifPresent(this::addParticle);
            }
        }

        @Override
        protected Optional<Particle> produceParticle() {
            final double xOffset = (RANDOM.nextFloat() * 2.0F - 1.0F);
            final double zOffset = (RANDOM.nextFloat() * 2.0F - 1.0F);

            final double motionX = xOffset * 0.05D;
            final double motionZ = zOffset * 0.05D;
            final double motionY = 0.1D + RANDOM.nextFloat() * 0.05D;

            var posX = this.posX + xOffset;
            var posZ = this.posZ + zOffset;

            var particle = this.createParticle(ParticleTypes.SPLASH, posX, this.deltaY, posZ, motionX, motionY, motionZ);

            particle.ifPresent(p -> {
                p.setParticleSpeed(motionX, motionY, motionZ);
                p.setLifetime(p.getLifetime() * 2);
            });

            return particle;
        }
    }
}
