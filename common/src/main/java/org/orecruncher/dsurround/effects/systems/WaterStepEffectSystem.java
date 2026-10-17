package org.orecruncher.dsurround.effects.systems;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.config.libraries.ISoundLibrary;
import org.orecruncher.dsurround.effects.IBlockEffect;
import org.orecruncher.dsurround.effects.blocks.AbstractParticleEmitterEffect;
import org.orecruncher.dsurround.effects.particles.WaterFoam;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.gui.ColorPalette;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.sound.BackgroundSoundLoop;
import org.orecruncher.dsurround.sound.IAudioPlayer;
import org.orecruncher.dsurround.sound.ISoundFactory;
import org.orecruncher.dsurround.tags.FluidTags;

import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Froth where flowing water drops a single block (a step; see {@link WaterfallColumn#isStep}): foam patches on the
 * water below, carried downstream by the current, the odd splash, and a gentle burble from the steps nearest the
 * player. Every step down a slope gets its own. Drops of two or more blocks are waterfalls
 * ({@link WaterfallEffectSystem}) instead.
 * <p>
 * Turned on and off by {@link Configuration.WaterfallOptions#enableFroth}.
 */
public class WaterStepEffectSystem extends AbstractEffectSystem {

    // The steps nearest the player, within SOUND_RANGE, play a sound; how often, in ticks, which ones is worked out
    private static final int SOUND_CAP = 6;
    private static final double SOUND_RANGE_SQ = 16 * 16;
    private static final int SOUND_CHECK_INTERVAL = 10;

    private final IAudioPlayer audioPlayer;
    private final ISoundFactory burble;
    private final Long2ObjectOpenHashMap<BackgroundSoundLoop> sounds = new Long2ObjectOpenHashMap<>();
    private final LongOpenHashSet desiredSounds = new LongOpenHashSet(SOUND_CAP);
    private final long[] nearestPositions = new long[SOUND_CAP];
    private final double[] nearestDistances = new double[SOUND_CAP];
    private int soundCheckThrottle;

    public WaterStepEffectSystem(IModLog logger, Configuration config, IAudioPlayer audioPlayer) {
        super(logger, config, "WaterStep");
        this.audioPlayer = audioPlayer;
        // The quietest waterfall sound, which waterfalls themselves no longer use
        this.burble = ContainerManager.resolve(ISoundLibrary.class).getSoundFactoryOrDefault(Constants.asId("waterfalls/0"));
    }

    @Override
    public boolean isEnabled() {
        return this.config.waterfallOptions.enableFroth;
    }

    @Override
    public int getDiagnosticColor() {
        return ColorPalette.CORN_FLOWER_BLUE.getValue();
    }

    @Override
    public void describeEffect(IBlockEffect effect, Consumer<String> lines) {
        super.describeEffect(effect, lines);
        lines.accept(this.sounds.containsKey(effect.getPosIndex()) ? "sound: playing" : "sound: none");
    }

    private static boolean isWaterStep(Level world, BlockPos pos) {
        return TAG_LIBRARY.is(FluidTags.WATERFALL_SOURCE, world.getFluidState(pos)) && WaterfallColumn.isStep(world, pos);
    }

    @Override
    public void blockScan(Level world, BlockState state, BlockPos pos) {
        if (isWaterStep(world, pos)) {
            if (!this.hasSystemAtPosition(pos))
                this.systems.put(pos.asLong(), new WaterStepEffect(world, pos));
        } else {
            // The block no longer makes a step: drop the effect and, through onRemoveSystem, its sound
            this.blockUnscan(world, state, pos);
        }
    }

    @Override
    protected void onRemoveSystem(long posLong) {
        super.onRemoveSystem(posLong);
        var sound = this.sounds.remove(posLong);
        if (sound != null)
            this.audioPlayer.stop(sound);
    }

    @Override
    public void clear() {
        super.clear();
        this.stopAllSounds();
    }

    private void stopAllSounds() {
        this.sounds.values().forEach(this.audioPlayer::stop);
        this.sounds.clear();
    }

    @Override
    public void tick(Predicate<IBlockEffect> processingPredicate) {
        super.tick(processingPredicate);

        if (!this.isEnabled() || !this.config.waterfallOptions.enableSounds) {
            if (!this.sounds.isEmpty())
                this.stopAllSounds();
            return;
        }

        // Steps can go without onRemoveSystem (their own check, or leaving the scanner's range): stop their sounds
        this.sounds.long2ObjectEntrySet().removeIf(entry -> {
            if (this.systems.containsKey(entry.getLongKey()))
                return false;
            this.audioPlayer.stop(entry.getValue());
            return true;
        });

        if (++this.soundCheckThrottle % SOUND_CHECK_INTERVAL != 0)
            return;

        var listener = GameUtils.getPlayer().orElseThrow().getEyePosition();
        this.chooseNearestSounds(listener);

        // Stop the ones no longer wanted, then start the wanted ones that aren't playing
        this.sounds.long2ObjectEntrySet().removeIf(entry -> {
            if (this.desiredSounds.contains(entry.getLongKey()))
                return false;
            this.audioPlayer.stop(entry.getValue());
            return true;
        });
        for (var posLong : this.desiredSounds) {
            var sound = this.sounds.get(posLong);
            if (sound == null) {
                sound = this.burble.createBackgroundSoundLoopAt(BlockPos.of(posLong));
                this.sounds.put(posLong, sound);
            }
            if (!this.audioPlayer.isPlaying(sound))
                this.audioPlayer.play(sound);
        }
    }

    /**
     * Fills desiredSounds with the steps nearest {@code listener}, up to SOUND_CAP within SOUND_RANGE: a single pass,
     * keeping the nearest so far and replacing the furthest of them when a nearer one turns up.
     */
    private void chooseNearestSounds(Vec3 listener) {
        this.desiredSounds.clear();
        int count = 0;
        int furthest = 0;
        for (var effect : this.systems.values()) {
            double distanceSq = effect.getPosition().distanceToSqr(listener);
            if (distanceSq > SOUND_RANGE_SQ)
                continue;
            if (count < SOUND_CAP) {
                this.nearestPositions[count] = effect.getPosIndex();
                this.nearestDistances[count] = distanceSq;
                if (++count == SOUND_CAP)
                    furthest = indexOfLargest(this.nearestDistances);
            } else if (distanceSq < this.nearestDistances[furthest]) {
                this.nearestPositions[furthest] = effect.getPosIndex();
                this.nearestDistances[furthest] = distanceSq;
                furthest = indexOfLargest(this.nearestDistances);
            }
        }
        for (int i = 0; i < count; i++)
            this.desiredSounds.add(this.nearestPositions[i]);
    }

    private static int indexOfLargest(double[] values) {
        int largest = 0;
        for (int i = 1; i < values.length; i++) {
            if (values[i] > values[largest])
                largest = i;
        }
        return largest;
    }

    /**
     * The froth at one step: every few ticks, a couple of foam patches set on the water around the foot of the step,
     * heading away from it (see {@link WaterFoam#spawnAround}), and now and then a splash.
     */
    private static class WaterStepEffect extends AbstractParticleEmitterEffect {

        // Foam patches added each time particles are made, and the chance of a splash
        private static final int FOAM_PER_UPDATE = 2;
        private static final float SPLASH_CHANCE = 0.25F;

        WaterStepEffect(Level world, BlockPos pos) {
            super(1, world, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 4);
        }

        @Override
        public boolean shouldRemove() {
            // Every half second: is it still a step?
            return this.age % 10 == 0 && !isWaterStep(this.world, this.position);
        }

        @Override
        protected void handleParticles() {
            // Nothing is produced past the vanilla particle distance, so skip the work entirely
            if (this.cameraDistanceSq() > PARTICLE_RANGE_SQ)
                return;
            super.handleParticles();
        }

        @Override
        protected Collection<Particle> produceParticles() {
            var particles = new ObjectArray<Particle>();
            var status = GameUtils.getGameSettings().particles().get();
            if (status == ParticleStatus.MINIMAL || !(this.world instanceof ClientLevel clientLevel))
                return particles;

            int count = status == ParticleStatus.ALL ? FOAM_PER_UPDATE : 1;
            WaterFoam.spawnAround(clientLevel, this.position, count, 0.06D, 0.12D, RANDOM, particles::add);

            if (RANDOM.nextFloat() < SPLASH_CHANCE) {
                double x = this.posX + RANDOM.nextDouble() - 0.5D;
                double z = this.posZ + RANDOM.nextDouble() - 0.5D;
                this.createParticle(ParticleTypes.SPLASH, x, this.position.getY() + 0.9D, z, 0D, 0.05D, 0D)
                        .ifPresent(particles::add);
            }

            return particles;
        }
    }
}
