package org.orecruncher.dsurround.effects.blocks;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.effects.blocks.producers.BlockEffectProducer;
import org.orecruncher.dsurround.effects.blocks.producers.WaterSplashProducer;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.sound.*;

import java.awt.*;
import java.util.Arrays;

@Environment(EnvType.CLIENT)
public class WaterSplashJetEffect extends ParticleJetEffect {

    // Used to spread sound starts over a range to minimize harmonics
    private static final int THROTTLE_RANGE = 10;
    private static final ISoundFactory[] waterfallAcoustics = new ISoundFactory[BlockEffectProducer.MAX_STRENGTH + 1];

    static {
        var factory = SoundFactoryBuilder.create(new Identifier(Client.ModId, "waterfall.0"))
                .pitchRange(0.8F, 1.2F)
                .build();
        Arrays.fill(waterfallAcoustics, factory);

        factory = SoundFactoryBuilder.create(new Identifier(Client.ModId, "waterfall.1"))
                .pitchRange(0.8F, 1.2F)
                .build();
        waterfallAcoustics[2] = waterfallAcoustics[3] = factory;

        factory = SoundFactoryBuilder.create(new Identifier(Client.ModId, "waterfall.2"))
                .pitchRange(0.8F, 1.2F)
                .build();
        waterfallAcoustics[4] = factory;

        factory = SoundFactoryBuilder.create(new Identifier(Client.ModId, "waterfall.3"))
                .pitchRange(0.8F, 1.2F)
                .build();
        waterfallAcoustics[5] = waterfallAcoustics[6] = factory;

        factory = SoundFactoryBuilder.create(new Identifier(Client.ModId, "waterfall.4"))
                .pitchRange(0.8F, 1.2F)
                .build();
        waterfallAcoustics[7] = waterfallAcoustics[8] = factory;

        factory = SoundFactoryBuilder.create(new Identifier(Client.ModId, "waterfall.5"))
                .pitchRange(0.8F, 1.2F)
                .build();
        waterfallAcoustics[9] = waterfallAcoustics[10] = factory;
    }

    protected BackgroundSoundLoop sound;
    protected int particleLimit;
    protected final double deltaY;

    protected final float red;
    protected final float green;
    protected final float blue;

    public WaterSplashJetEffect(final int strength, final World world, final BlockPos loc, final double dY) {
        super(0, strength, world, loc.getX() + 0.5D, loc.getY() + 0.5D, loc.getZ() + 0.5D, 4);
        this.deltaY = loc.getY() + dY;
        setSpawnCount((int) (strength * 2.5F));

        var color = new Color(this.world.getBiome(this.position).getWaterColor());
        this.red = color.getRed() / 255F;
        this.green = color.getGreen() / 255F;
        this.blue = color.getBlue() / 255F;
    }

    public void setSpawnCount(final int limit) {
        this.particleLimit = MathHelper.clamp(limit, 5, 20);
    }

    public int getSpawnCount() {
        var state = GameUtils.getGameSettings().particles;
        return switch (state) {
            case MINIMAL -> 0;
            case ALL -> this.particleLimit;
            default -> this.particleLimit / 2;
        };
    }

    @Override
    public boolean shouldDie() {
        // Check every half second
        return (this.particleAge % 10) == 0
                && !WaterSplashProducer.isValidSpawnBlock(this.world, this.position);
    }

    @Override
    protected void soundUpdate() {
        if (!Client.Config.blockEffects.enableWaterfallSounds) {
            if (this.sound != null) {
                MinecraftAudioPlayer.INSTANCE.stop(this.sound);
                this.sound = null;
            }
            return;
        }

        if (this.sound == null) {
            final int idx = MathHelper.clamp(this.jetStrength, 0, waterfallAcoustics.length - 1);
            this.sound = waterfallAcoustics[idx].createBackgroundSoundLoopAt(this.position);
        }

        final boolean inRange = SoundInstanceHandler.inRange(GameUtils.getPlayer().getEyePos(), this.sound, 4);
        final boolean isDone = !MinecraftAudioPlayer.INSTANCE.isPlaying(this.sound);

        if (inRange && isDone) {
            MinecraftAudioPlayer.INSTANCE.play(this.sound);
        } else if (!inRange && !isDone) {
            MinecraftAudioPlayer.INSTANCE.stop(this.sound);
        }
    }

    @Override
    protected void cleanUp() {
        if (this.sound != null) {
            MinecraftAudioPlayer.INSTANCE.stop(this.sound);
            this.sound = null;
        }
        super.cleanUp();
    }

    @Override
    protected void spawnJetParticle() {
        if (!Client.Config.blockEffects.enableWaterfallParticles)
            return;

        final int splashCount = getSpawnCount();

        for (int j = 0; (float) j < splashCount; ++j) {
            final double xOffset = (RANDOM.nextFloat() * 2.0F - 1.0F);
            final double zOffset = (RANDOM.nextFloat() * 2.0F - 1.0F);

            final int motionStr = this.jetStrength; // + 3;
            final double motionX = xOffset * (motionStr / 20.0D);
            final double motionZ = zOffset * (motionStr / 20.0D);
            final double motionY = 0.1D + RANDOM.nextFloat() * motionStr / 20.0D;

            var particle = this.createParticle(ParticleTypes.SPLASH, this.posX + xOffset,
                    this.deltaY, this.posZ + zOffset, motionX, motionY, motionZ);
            particle.setVelocity(motionX, motionY, motionZ);
            particle.setMaxAge(particle.getMaxAge() * 2);
            //particle.setColor(this.red, this.green, this.blue);
            this.addParticle(particle);
        }
    }
}