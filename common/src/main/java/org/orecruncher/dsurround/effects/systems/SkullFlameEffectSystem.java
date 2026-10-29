package org.orecruncher.dsurround.effects.systems;

import net.minecraft.server.level.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.effects.IEffectSystem;
import org.orecruncher.dsurround.effects.blocks.AbstractParticleEmitterEffect;
import org.orecruncher.dsurround.effects.blocks.SkullEyes;
import org.orecruncher.dsurround.effects.particles.EyeFlame;
import org.orecruncher.dsurround.effects.particles.FlameTongue;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.gui.ColorPalette;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.tags.BlockEffectTags;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Little flames burning in the eyes of skeleton skulls placed in the world, blue soul fire in a wither skeleton's,
 * lighting the floor and wall around them. No smoke: they flicker and lick up the face.
 */
public class SkullFlameEffectSystem extends AbstractEffectSystem implements IEffectSystem {

    public SkullFlameEffectSystem(IModLog logger, Configuration config) {
        super(logger, config, "SkullFlames");
    }

    @Override
    public boolean isEnabled() {
        return this.config.blockEffects.skullFlamesEnabled;
    }

    @Override
    public int getDiagnosticColor() {
        return ColorPalette.ORANGE.getValue();
    }

    @Override
    public void blockScan(Level world, BlockState state, BlockPos pos) {
        if (!this.hasSystemAtPosition(pos))
            this.create(world, state, pos);
    }

    /**
     * A block changed: a skull placed, broken or replaced by another turned another way
     */
    @Override
    public void blockUpdated(Level world, BlockState state, BlockPos pos) {
        if (this.systems.get(pos.asLong()) instanceof SkullFlameEffect effect && effect.state == state)
            return;
        this.blockUnscan(world, state, pos);
        this.create(world, state, pos);
    }

    private void create(Level world, BlockState state, BlockPos pos) {
        var palette = paletteOf(state);
        if (palette == null)
            return;
        SkullEyes.of(state).ifPresent(eyes ->
                this.systems.put(pos.asLong(), new SkullFlameEffect(world, pos, state, eyes, palette)));
    }

    /**
     * The flames a block burns with: soul fire for a wither skeleton's skull, fire for a skeleton's, none for
     * anything else
     */
    private static FlameTongue.Palette paletteOf(BlockState state) {
        if (TAG_LIBRARY.is(BlockEffectTags.SOUL_SKULL_FLAMES, state))
            return FlameTongue.SOUL;
        if (TAG_LIBRARY.is(BlockEffectTags.SKULL_FLAMES, state))
            return FlameTongue.FIRE;
        return null;
    }

    private static class SkullFlameEffect extends AbstractParticleEmitterEffect {

        // Each eye puts out a flame every this many ticks: they live a few times that, so a few overlap
        private static final int EMIT_TICKS = 2;
        // How often it checks its skull is still there, in ticks
        private static final int CHECK_TICKS = 10;
        // How far from the middle of the eye a flame starts, across the face and up: the eye is 2 pixels by 1
        private static final double ACROSS = 0.035D;
        private static final double UP = 0.012D;
        // Its starting speed, out from the face and up, in blocks a tick
        private static final double OUT_SPEED = 0.004D;
        private static final double UP_SPEED = 0.006D;

        private final BlockState state;
        private final FlameTongue.Palette palette;
        private final Vec3 left;
        private final Vec3 right;
        private final Vec3 light;
        private final Vec3 facing;
        private final Vec3 lateral;

        SkullFlameEffect(Level world, BlockPos pos, BlockState state, SkullEyes eyes, FlameTongue.Palette palette) {
            super(1, world, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, EMIT_TICKS);
            this.state = state;
            this.palette = palette;
            var corner = Vec3.atLowerCornerOf(pos);
            this.left = corner.add(eyes.left());
            this.right = corner.add(eyes.right());
            this.light = corner.add(eyes.light());
            this.facing = eyes.facing();
            this.lateral = eyes.lateral();
            // So the checks of many skulls are spread over the ticks
            this.age = RANDOM.nextInt(CHECK_TICKS);
        }

        @Override
        public boolean shouldRemove() {
            return (this.age % CHECK_TICKS) == 0 && this.world.getBlockState(this.getPos()) != this.state;
        }

        @Override
        protected void handleParticles() {
            if (this.cameraDistanceSq() > particleRangeSq())
                return;
            super.handleParticles();
        }

        @Override
        protected Collection<Particle> produceParticles() {
            if (!(this.world instanceof ClientLevel level))
                return List.of();

            // Follow vanilla's handling of the Particles option: none on Minimal, about a third fewer on Decreased
            var status = GameUtils.getGameSettings().particles().get();
            if (status == ParticleStatus.MINIMAL)
                return List.of();
            boolean decreased = status == ParticleStatus.DECREASED;

            // The eyes take turns carrying the light, one flame each time: two or three alive at once make it
            boolean leftLight = (this.age / EMIT_TICKS & 1) == 0;
            var particles = new ArrayList<Particle>(2);
            if (!decreased || RANDOM.nextInt(3) != 0)
                particles.add(this.flame(level, this.left, leftLight));
            if (!decreased || RANDOM.nextInt(3) != 0)
                particles.add(this.flame(level, this.right, !leftLight));
            return particles;
        }

        private Particle flame(ClientLevel level, Vec3 eye, boolean carriesLight) {
            double across = RANDOM.triangle(0D, ACROSS);
            double x = eye.x + this.lateral.x * across;
            double y = RANDOM.triangle(eye.y, UP);
            double z = eye.z + this.lateral.z * across;
            return EyeFlame.create(level, x, y, z,
                    this.facing.x * OUT_SPEED, UP_SPEED, this.facing.z * OUT_SPEED,
                    this.palette, carriesLight, this.light.x, this.light.y, this.light.z);
        }
    }
}
