package org.orecruncher.dsurround.effects.systems;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SupportType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.config.libraries.ISoundLibrary;
import org.orecruncher.dsurround.effects.BlockEffectUtils;
import org.orecruncher.dsurround.effects.IBlockEffect;
import org.orecruncher.dsurround.effects.IEffectSystem;
import org.orecruncher.dsurround.effects.blocks.AbstractParticleEmitterEffect;
import org.orecruncher.dsurround.effects.particles.WaterfallCascade;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.sound.*;
import org.orecruncher.dsurround.tags.FluidTags;

import java.util.Arrays;
import java.util.Collection;
import java.util.function.Predicate;

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
        var soundLibrary = ContainerManager.resolve(ISoundLibrary.class);

        // A missing sound factory (a resource pack removing one, say) must not fail class loading, which would take
        // the game down. The base sound never throws: an undefined factory gets a default. The louder tiers fall
        // back to the base sound, with a warning.
        var base = soundLibrary.getSoundFactoryOrDefault(Constants.asId("waterfalls/0"));
        Arrays.fill(ACOUSTICS, base);

        ACOUSTICS[2] = ACOUSTICS[3] = factoryOrBase(soundLibrary, "waterfalls/1", base);
        ACOUSTICS[4] = factoryOrBase(soundLibrary, "waterfalls/2", base);
        ACOUSTICS[5] = ACOUSTICS[6] = factoryOrBase(soundLibrary, "waterfalls/3", base);
        ACOUSTICS[7] = ACOUSTICS[8] = factoryOrBase(soundLibrary, "waterfalls/4", base);
        ACOUSTICS[9] = ACOUSTICS[10] = factoryOrBase(soundLibrary, "waterfalls/5", base);
    }

    private static ISoundFactory factoryOrBase(ISoundLibrary soundLibrary, String name, ISoundFactory base) {
        return soundLibrary.getSoundFactory(Constants.asId(name)).orElseGet(() -> {
            Library.LOGGER.warn("Waterfall sound factory %s not found; using waterfalls/0 instead", name);
            return base;
        });
    }

    // Keep track of sound plays outside the effect.
    private final IAudioPlayer audioPlayer;
    private final Long2ObjectOpenHashMap<BackgroundSoundLoop> waterfallSoundInstances = new Long2ObjectOpenHashMap<>();
    private long soundCheckThrottle;

    // Waterfalls found this tick whose column height changed; reused between ticks
    private final LongArrayList staleStrengths = new LongArrayList();

    // Reused each time sound locations are chosen, so choosing them allocates nothing
    private final LongOpenHashSet desiredSoundLocations = new LongOpenHashSet(SOUND_INSTANCE_CAP);
    private final long[] candidatePositions = new long[SOUND_INSTANCE_CAP];
    private final double[] candidateWeights = new double[SOUND_INSTANCE_CAP];

    public WaterfallEffectSystem(IModLog logger, Configuration config, IAudioPlayer audioPlayer) {
        super(logger, config, "Waterfall");
        this.audioPlayer = audioPlayer;
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
        this.refreshStaleStrengths();

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

        // Effects can be removed without going through onRemoveSystem (their own shouldRemove() check, or the
        // scanner dropping effects that left range), so stop their sounds here. The map holds at most
        // SOUND_INSTANCE_CAP entries, so doing this every tick is cheap and a sound never outlives its waterfall.
        this.pruneOrphanSounds();

        // Throttle these checks as it can be expensive
        if (((++this.soundCheckThrottle) % SOUND_CHECK_INTERVAL) != 0)
            return;

        var player = GameUtils.getPlayer().orElseThrow();
        var eyePosition = player.getEyePosition();

        // Do a fancy evaluation to determine the desired sound play locations
        var desiredLocations = this.getDesiredWaterfallSoundLocations(player.level(), eyePosition);

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
                final var removed = sound;
                this.logger.debug(() -> "[%s] removing sound instance %s".formatted(this.systemName, removed));
                this.audioPlayer.stop(sound);
                this.waterfallSoundInstances.remove(posIndex);
            }
        }
    }

    /**
     * Replaces waterfalls whose column has grown or shrunk since they were created, so the splashes and sound match
     * the new strength. Each waterfall recounts its column as part of its periodic validity check; this only acts
     * on the ones that found a change. The old sound is stopped here and the next sound pass starts one for the new
     * strength.
     */
    private void refreshStaleStrengths() {
        for (var effect : this.systems.values()) {
            if (((WaterfallEffect) effect).isStrengthStale())
                this.staleStrengths.add(effect.getPosIndex());
        }
        if (this.staleStrengths.isEmpty())
            return;

        for (int i = 0; i < this.staleStrengths.size(); i++) {
            long posLong = this.staleStrengths.getLong(i);
            var old = (WaterfallEffect) this.systems.get(posLong);
            var replacement = old.rebuild();
            old.remove();
            this.onRemoveSystem(posLong);
            if (replacement != null)
                this.systems.put(posLong, replacement);
        }
        this.staleStrengths.clear();
    }

    /**
     * Stops and forgets any sound whose waterfall effect no longer exists.
     */
    private void pruneOrphanSounds() {
        if (this.waterfallSoundInstances.isEmpty())
            return;

        this.waterfallSoundInstances.values().removeIf(sound -> {
            if (!this.systems.containsKey(sound.getPos().asLong())) {
                this.logger.debug(() -> "[%s] Orphan sound removed: %s".formatted(this.systemName, sound));
                this.audioPlayer.stop(sound);
                return true;
            }
            return false;
        });
    }

    /**
     * Where waterfall sounds should play. Only SOUND_INSTANCE_CAP sounds are allowed, so when there are more
     * waterfalls than that, the ones with the most impact are chosen: impact is strength squared over the squared
     * distance from the listener, so loud nearby waterfalls win.
     * <p>
     * The result is a set reused between calls; it is only valid until the next call.
     */
    protected LongSet getDesiredWaterfallSoundLocations(final Level world, final Vec3 listener) {
        this.desiredSoundLocations.clear();

        // If waterfall sounds are disabled, there are no desirable locations, obviously
        if (!this.config.blockEffects.enableWaterfallSounds || this.systems.isEmpty())
            return this.desiredSoundLocations;

        // Happy path: no more waterfalls than the cap, so every one that should play, does
        if (this.systems.size() <= SOUND_INSTANCE_CAP) {
            for (var effect : this.systems.values()) {
                if (shouldPlaySoundFilter(world, effect))
                    this.desiredSoundLocations.add(effect.getPosIndex());
            }
            return this.desiredSoundLocations;
        }

        // Keep the SOUND_INSTANCE_CAP highest weights seen so far in two small arrays, with the index of the lowest
        // of them. A new waterfall only gets in by beating that lowest one. This is a single pass with no sorting
        // or allocation, and the fluid check is only done for waterfalls that would get in.
        final var positions = this.candidatePositions;
        final var weights = this.candidateWeights;
        int count = 0;
        int lowest = 0;

        for (var e : this.systems.values()) {
            var effect = (WaterfallEffect) e;
            var strength = effect.getStrength();
            var weight = (strength * strength) / effect.distanceSqTo(listener);

            if (count == SOUND_INSTANCE_CAP && weight <= weights[lowest])
                continue;
            if (!shouldPlaySoundFilter(world, effect))
                continue;

            if (count < SOUND_INSTANCE_CAP) {
                positions[count] = effect.getPosIndex();
                weights[count] = weight;
                count++;
                if (count == SOUND_INSTANCE_CAP)
                    lowest = indexOfLowest(weights);
            } else {
                positions[lowest] = effect.getPosIndex();
                weights[lowest] = weight;
                lowest = indexOfLowest(weights);
            }
        }

        for (int i = 0; i < count; i++)
            this.desiredSoundLocations.add(positions[i]);
        return this.desiredSoundLocations;
    }

    private static int indexOfLowest(final double[] values) {
        int lowest = 0;
        for (int i = 1; i < values.length; i++) {
            if (values[i] < values[lowest])
                lowest = i;
        }
        return lowest;
    }

    private static boolean shouldPlaySoundFilter(Level world, IBlockEffect effect) {
        return TAG_LIBRARY.is(FluidTags.WATERFALL_SOUND, world.getFluidState(effect.getPos()));
    }

    @Override
    public void blockScan(Level world, BlockState state, BlockPos pos) {
        // A waterfall forms where falling water lands: see canWaterfallSpawn().
        if (canWaterfallSpawn(world, state, pos)) {
            // Ignore if a waterfall is already present. This scan is due to a block update of some sort.
            if (this.hasSystemAtPosition(pos))
                return;

            var effect = createWaterfallEffect(world, state, pos);
            this.systems.put(pos.asLong(), effect);
        } else {
            // The block no longer supports a waterfall: drop the effect and, through onRemoveSystem, its sound
            this.blockUnscan(world, state, pos);
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
        final float height = state.getFluidState().getHeight(world, pos) + 0.1F;
        return new WaterfallEffect(columnStrength(world, pos), world, pos, height);
    }

    /**
     * The height of the fluid column from {@code pos} upward, capped at BlockEffectUtils.MAX_STRENGTH.
     */
    private static int columnStrength(Level world, BlockPos pos) {
        return BlockEffectUtils.countVerticalBlocks(world, pos, HAS_FLUID, 1);
    }

    private static boolean canWaterfallSpawn(Level world, BlockState state, BlockPos pos) {
        return TAG_LIBRARY.is(FluidTags.WATERFALL_SOURCE, state.getFluidState()) && isValidWaterfallSource(world, pos);
    }

    /**
     * A waterfall lands at {@code pos} when falling water is directly above, the water here can spread sideways
     * (a side is open or only partly filled), and below is a source block or a solid top face to land on.
     */
    private static boolean isValidWaterfallSource(Level world, BlockPos pos) {
        // Falling water above, not just any fluid: otherwise the bottom of a still pool next to flowing water
        // qualifies. Every vanilla flowing fluid has the FALLING property; anything without it can't be falling.
        var above = world.getFluidState(pos.above());
        if (!above.hasProperty(FlowingFluid.FALLING) || !above.getValue(FlowingFluid.FALLING))
            return false;
        if (!isUnboundedLiquid(world, pos))
            return false;

        var downPos = pos.below();
        var blockState = world.getBlockState(downPos);
        return blockState.getFluidState().isSource() || blockState.isFaceSturdy(world, downPos, Direction.UP, SupportType.FULL);
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

    private static class WaterfallEffect extends AbstractParticleEmitterEffect {

        private static final Configuration.BlockEffects CONFIG = ContainerManager.resolve(Configuration.BlockEffects.class);
        private static final Configuration.WorksInProgressOptions WIP_OPTIONS = ContainerManager.resolve(Configuration.WorksInProgressOptions.class);

        // Beyond this distance, splashes are halved. Beyond PARTICLE_RANGE_SQ there are none.
        private static final double PARTICLE_FULL_DISTANCE_SQ = 16 * 16;

        // A still water surface is drawn at 8/9 of the block's height
        private static final double WATER_SURFACE_HEIGHT = 8D / 9D;

        protected final double deltaY;
        // The visible surface of the pool where the waterfall lands. Not the fluid height: water with water above
        // it reports a full block (1.0), which is above the surface that is drawn.
        protected final double waterSurfaceY;
        protected int particleLimit;
        private double cameraDistanceSq;
        private boolean strengthStale = false;

        public WaterfallEffect(final int strength, final Level world, final BlockPos loc, final double dY) {
            super(strength, world, loc.getX() + 0.5D, loc.getY() + 0.5D, loc.getZ() + 0.5D, 4);
            this.deltaY = loc.getY() + dY;
            this.waterSurfaceY = loc.getY() + WATER_SURFACE_HEIGHT;
            this.setSpawnCount((int) (strength * 2.5F));
        }

        public void setSpawnCount(final int limit) {
            this.particleLimit = Mth.clamp(limit, 5, 20);
        }

        @Override
        public boolean shouldRemove() {
            // Check every half second: is it still a waterfall, and is the column still the same height?
            if ((this.age % 10) != 0)
                return false;
            if (!canWaterfallSpawn(this.world, this.world.getBlockState(this.position), this.position))
                return true;
            this.strengthStale = columnStrength(this.world, this.position) != this.strength;
            return false;
        }

        /**
         * True if the column height changed at the last check, so this effect should be replaced.
         */
        boolean isStrengthStale() {
            return this.strengthStale && !this.isDone();
        }

        /**
         * A new effect for the same spot at the current strength, or null if it is no longer a waterfall.
         */
        @Nullable
        WaterfallEffect rebuild() {
            var state = this.world.getBlockState(this.position);
            return canWaterfallSpawn(this.world, state, this.position)
                    ? createWaterfallEffect(this.world, state, this.position)
                    : null;
        }

        private int getSplashParticleSpawnCount(final ParticleStatus status) {
            var count = switch (status) {
                case MINIMAL -> 0;
                case ALL -> this.particleLimit;
                default -> this.particleLimit / 2;
            };

            if (count < 4)
                return count;

            var x = count / 2;
            return RANDOM.nextInt(count - x) + x;
        }

        /**
         * Squared distance from this waterfall to {@code point}.
         */
        double distanceSqTo(final Vec3 point) {
            return point.distanceToSqr(this.posX, this.posY, this.posZ);
        }

        @Override
        protected void handleParticles() {
            if (!CONFIG.enableWaterfallParticles)
                return;

            // Nothing is produced past the vanilla particle distance, so skip the work entirely
            this.cameraDistanceSq = this.cameraDistanceSq();
            if (this.cameraDistanceSq > PARTICLE_RANGE_SQ)
                return;

            super.handleParticles();
        }

        @Override
        protected Collection<Particle> produceParticles() {

            var particles = new ObjectArray<Particle>();

            // Minimal particles means no waterfall particles at all, including the cascade
            final ParticleStatus status = GameUtils.getGameSettings().particles().get();
            if (status == ParticleStatus.MINIMAL)
                return particles;

            var particleCount = this.getSplashParticleSpawnCount(status);
            if (this.cameraDistanceSq > PARTICLE_FULL_DISTANCE_SQ)
                particleCount /= 2;
            for (int i = 0; i < particleCount; i++) {
                final double xOffset = RANDOM.nextFloat(-1.0F, 1.0F);
                final double zOffset = RANDOM.nextFloat(-1.0F, 1.0F);

                final double motionStr = (this.strength + 1) / 20D;
                final double motionX = xOffset * motionStr;
                final double motionZ = zOffset * motionStr;
                final double motionY = 0.1D + RANDOM.nextFloat() * motionStr;

                var posX = this.posX + xOffset;
                var posZ = this.posZ + zOffset;

                var particle = this.createParticle(ParticleTypes.SPLASH, posX, this.deltaY, posZ, motionX, motionY, motionZ);

                particle.ifPresent(p -> {
                    p.setParticleSpeed(motionX, motionY, motionZ);
                    p.setLifetime(p.getLifetime() * 2);
                    particles.add(p);
                });

            }

            // The effect's own world, not whatever the client has loaded now (they differ briefly during a
            // dimension change). Effects only exist client side, so it is always a ClientLevel.
            if (this.strength > 1 && WIP_OPTIONS.enableWaterfallCascade && this.world instanceof ClientLevel clientLevel) {
                final double xOffset = RANDOM.nextFloat(-0.15F, 0.15F);
                final double zOffset = RANDOM.nextFloat(-0.15F, 0.15F);
                final double yOffset = RANDOM.nextFloat(-0.25F, 0.25F);
                var cascadeParticle = WaterfallCascade.create(clientLevel, this.posX + xOffset, this.waterSurfaceY + yOffset, this.posZ + zOffset, this.strength);
                if (cascadeParticle != null)
                    particles.add(cascadeParticle);
            }

            return particles;
        }
    }
}
