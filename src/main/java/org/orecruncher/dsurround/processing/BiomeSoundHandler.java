package org.orecruncher.dsurround.processing;

import it.unimi.dsi.fastutil.objects.Reference2FloatOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import joptsimple.internal.Strings;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.world.biome.Biome;
import org.orecruncher.dsurround.config.BiomeLibrary;
import org.orecruncher.dsurround.config.SoundEventType;
import org.orecruncher.dsurround.lib.biome.BiomeUtils;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.TickCounter;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.math.TimerEMA;
import org.orecruncher.dsurround.sound.SoundFactory;

import java.util.Collection;

@Environment(EnvType.CLIENT)
public final class BiomeSoundHandler extends ClientHandler {

    public static final int SCAN_INTERVAL = 4;
    public static final int SPOT_SOUND_MIN_RANGE = 8;
    public static final int SPOT_SOUND_MAX_RANGE = 16;

    // Reusable map for biome acoustic work
    private static final Reference2FloatOpenHashMap<SoundEvent> WORK_MAP = new Reference2FloatOpenHashMap<>(8, 1F);

    static {
        WORK_MAP.defaultReturnValue(0F);
    }

    private final ObjectArray<BiomeSoundEmitter> emitters = new ObjectArray<>(8);

    BiomeSoundHandler() {
        super("Biome Sounds");
    }

    private boolean doBiomeSounds() {
        return Scanners.isUnderground()
                || !Scanners.isInside()
                || Scanners.getDimInfo().alwaysOutside();
    }

    private void generateBiomeSounds() {
        final float area = Scanners.getBiomeArea();
        for (final Reference2IntMap.Entry<Biome> kvp : Scanners.getBiomes().reference2IntEntrySet()) {
            final Collection<SoundEvent> acoustics = BiomeLibrary.findBiomeSoundMatches(kvp.getKey());
            final float volume = 0.05F + 0.95F * (kvp.getIntValue() / area);
            for (final SoundEvent acoustic : acoustics) {
                WORK_MAP.addTo(acoustic, volume);
            }
        }
    }

    @Override
    public void process(final PlayerEntity player) {
        this.emitters.forEach(BiomeSoundEmitter::tick);
        if ((TickCounter.getTickCount() % SCAN_INTERVAL) == 0) {
            handleBiomeSounds(player);
        }
    }

    @Override
    public void onConnect() {
        clearSounds();
    }

    @Override
    public void onDisconnect() {
        clearSounds();
    }

    private void handleBiomeSounds(final PlayerEntity player) {
        WORK_MAP.clear();

        // Only gather data if the player is alive. If the player is dead the biome sounds will cease playing.
        if (player.isAlive()) {

            final boolean biomeSounds = doBiomeSounds();

            if (biomeSounds)
                generateBiomeSounds();

            /*
            final ObjectArray<SoundEvent> playerSounds = new ObjectArray<>();
            BiomeLibrary.PLAYER_INFO.findSoundMatches(playerSounds);
            BiomeLibrary.VILLAGE_INFO.findSoundMatches(playerSounds);
            playerSounds.forEach(fx -> WORK_MAP.put(fx, 1.0F));
            */

            if (biomeSounds) {
                Biome playerBiome = BiomeUtils.getPlayerBiome(player);
                SoundEvent sound = BiomeLibrary.getExtraSound(playerBiome, SoundEventType.MOOD, RANDOM);
                if (sound != null) {
                    SoundInstance instance = SoundFactory.createAsMood(sound, player, SPOT_SOUND_MIN_RANGE, SPOT_SOUND_MAX_RANGE);
                    GameUtils.getSoundHander().play(instance);
                }

                sound = BiomeLibrary.getExtraSound(playerBiome, SoundEventType.ADDITION, RANDOM);
                if (sound != null) {
                    SoundInstance instance = SoundFactory.createAsAdditional(sound);
                    GameUtils.getSoundHander().play(instance);
                }
            }

            /*
            final IAcoustic sound = BiomeLibrary.PLAYER_INFO.getSpotSound(RANDOM);
            if (sound != null)
                sound.playNear(player, SPOT_SOUND_MIN_RANGE, SPOT_SOUND_MAX_RANGE);

             */
        }

        queueAmbientSounds();
    }

    private void queueAmbientSounds() {
        // Iterate through the existing emitters:
        // * If done, remove
        // * If not in the incoming list, fade
        // * If it does exist, update volume throttle and un-fade if needed
        this.emitters.removeIf(entry -> {
            if (entry.isDonePlaying()) {
                return true;
            }
            final float volume = WORK_MAP.getFloat(entry.getSoundEvent());
            if (volume > 0) {
                entry.setVolumeScale(volume);
                if (entry.isFading())
                    entry.unfade();
                WORK_MAP.removeFloat(entry.getSoundEvent());
            } else if (!entry.isFading()) {
                entry.fade();
            }
            return false;
        });

        // Any sounds left in the list are new and need an emitter created.
        WORK_MAP.forEach((fx, volume) -> {
            final BiomeSoundEmitter e = new BiomeSoundEmitter(fx);
            e.setVolumeScale(volume);
            this.emitters.add(e);
        });
    }

    public void clearSounds() {
        this.emitters.forEach(BiomeSoundEmitter::stop);
        this.emitters.clear();
        WORK_MAP.clear();
        GameUtils.getSoundHander().stopAll();
    }

    @Override
    protected void gatherDiagnostics(Collection<String> left, Collection<String> right, Collection<TimerEMA> timers) {
        left.add(Strings.EMPTY);
        this.emitters.forEach(backgroundAcousticEmitter -> left.add("EMITTER: " + backgroundAcousticEmitter.toString()));
    }
}