package org.orecruncher.dsurround.bench;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.orecruncher.dsurround.lib.random.IRandomizer;
import org.orecruncher.dsurround.lib.random.Randomizer;

import java.util.ArrayList;
import java.util.List;

/**
 * Plays a scene's sounds around where the player stood when they began, for enhanced sound processing to work on
 * through the warm-up and the recording: one-shots at a steady rate, and loops kept playing. Places and variants come
 * from each sound's seed, so every run of a scene hears the same sounds in the same places. On the client thread.
 */
final class BenchSounds {

    // How often a loop that stopped (the engine out of sources, say) is started again
    private static final int LOOP_CHECK_TICKS = 20;

    private final List<Player> players = new ArrayList<>();
    private int ticks;

    BenchSounds(List<Scenario.Sounds> sounds, Vec3 center) {
        for (var sound : sounds)
            this.players.add(new Player(sound, center));
    }

    /**
     * Plays what is due this tick
     */
    void tick() {
        this.ticks++;
        for (var player : this.players)
            player.tick(this.ticks % LOOP_CHECK_TICKS == 1);
    }

    /**
     * Stops the loops
     */
    void stop() {
        for (var player : this.players)
            player.stop();
    }

    /**
     * Sounds played this far, one-shots and loop starts
     */
    int played() {
        int played = 0;
        for (var player : this.players)
            played += player.played;
        return played;
    }

    private static final class Player {
        private final Scenario.Sounds sound;
        private final Vec3 center;
        private final Identifier id;
        private final IRandomizer places;
        private final RandomSource variants;
        private final List<SoundInstance> loops = new ArrayList<>();
        private double due;
        private int played;

        Player(Scenario.Sounds sound, Vec3 center) {
            this.sound = sound;
            this.center = center;
            this.id = Identifier.parse(sound.id());
            this.places = Randomizer.create(sound.seed());
            this.variants = RandomSource.create(sound.seed());
        }

        void tick(boolean checkLoops) {
            var manager = Minecraft.getInstance().getSoundManager();
            if (this.sound.count() > 0) {
                if (this.loops.isEmpty()) {
                    for (int i = 0; i < this.sound.count(); i++)
                        this.loops.add(this.instance(true));
                    this.loops.forEach(this::play);
                } else if (checkLoops) {
                    for (var loop : this.loops)
                        if (!manager.isActive(loop))
                            this.play(loop);
                }
            }
            if (this.sound.perSecond() > 0) {
                this.due += this.sound.perSecond() / 20.0;
                while (this.due >= 1) {
                    this.due--;
                    this.play(this.instance(false));
                }
            }
        }

        void stop() {
            var manager = Minecraft.getInstance().getSoundManager();
            this.loops.forEach(manager::stop);
            this.loops.clear();
        }

        private void play(SoundInstance instance) {
            Minecraft.getInstance().getSoundManager().play(instance);
            this.played++;
        }

        /**
         * A sound at a random place in the shell from minRadius to radius around the center
         */
        private SoundInstance instance(boolean looping) {
            // A direction evenly over the sphere, and a distance evenly through the shell's volume
            double z = this.places.nextDouble() * 2 - 1;
            double angle = this.places.nextDouble() * Math.PI * 2;
            double flat = Math.sqrt(1 - z * z);
            double inner = Math.pow(this.sound.minRadius(), 3);
            double outer = Math.pow(this.sound.radius(), 3);
            double distance = Math.cbrt(inner + this.places.nextDouble() * (outer - inner));
            var at = this.center.add(flat * Math.cos(angle) * distance, z * distance, flat * Math.sin(angle) * distance);
            return new SimpleSoundInstance(this.id, SoundSource.BLOCKS, this.sound.volume(), this.sound.pitch(), this.variants,
                    looping, 0, SoundInstance.Attenuation.LINEAR, at.x, at.y, at.z, false);
        }
    }
}
