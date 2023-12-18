package org.orecruncher.dsurround.processing;

import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import joptsimple.internal.Strings;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.player.PlayerEntity;
import org.orecruncher.dsurround.config.libraries.IBiomeLibrary;
import org.orecruncher.dsurround.config.Configuration;
import org.orecruncher.dsurround.config.InternalBiomes;
import org.orecruncher.dsurround.config.SoundEventType;
import org.orecruncher.dsurround.config.biome.BiomeInfo;
import org.orecruncher.dsurround.lib.system.ITickCount;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.math.ITimer;
import org.orecruncher.dsurround.sound.IAudioPlayer;
import org.orecruncher.dsurround.sound.ISoundFactory;

import java.util.Collection;

@Environment(EnvType.CLIENT)
public final class BiomeSoundHandler extends ClientHandler {

    public static final int SCAN_INTERVAL = 4;
    public static final int MOOD_SOUND_MIN_RANGE = 8;
    public static final int MOOD_SOUND_MAX_RANGE = 16;

    // Reusable map for biome acoustic work
    private final Object2FloatOpenHashMap<ISoundFactory> workMap = new Object2FloatOpenHashMap<>(8, Hash.DEFAULT_LOAD_FACTOR);
    private final ObjectArray<BiomeSoundEmitter> emitters = new ObjectArray<>(8);
    private final IBiomeLibrary biomeLibrary;
    private final IAudioPlayer audioPlayer;
    private final ITickCount tickCount;
    private final Scanners scanner;

    public BiomeSoundHandler(IBiomeLibrary biomeLibrary, IAudioPlayer audioPlayer, ITickCount tickCount, Scanners scanner, Configuration config, IModLog logger) {
        super("Biome Sounds", config, logger);
        this.audioPlayer = audioPlayer;
        this.biomeLibrary = biomeLibrary;
        this.tickCount = tickCount;
        this.scanner = scanner;
        this.workMap.defaultReturnValue(0F);
    }

    private boolean doBiomeSounds() {
        return true; //!Scanners.isInside() || BiomeScanner.getDimInfo().alwaysOutside();
    }

    private void generateBiomeSounds() {
        final float area = this.scanner.getBiomeArea();
        for (var kvp : this.scanner.getBiomes().reference2IntEntrySet()) {
            var acoustics = kvp.getKey().findBiomeSoundMatches();
            final float volume = 0.05F + 0.95F * (kvp.getIntValue() / area);
            for (var acoustic : acoustics) {
                this.workMap.addTo(acoustic, volume);
            }
        }
    }

    @Override
    public void process(final PlayerEntity player) {
        this.emitters.forEach(BiomeSoundEmitter::tick);
        if ((this.tickCount.getTickCount() % SCAN_INTERVAL) == 0) {
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
        this.workMap.clear();

        // Only gather data if the player is alive. If the player is dead the biome sounds will cease playing.
        if (player.isAlive()) {

            final boolean biomeSounds = doBiomeSounds();

            if (biomeSounds)
                generateBiomeSounds();

            final BiomeInfo internalPlayerBiomeInfo = this.biomeLibrary.getBiomeInfo(InternalBiomes.PLAYER);
            final BiomeInfo internalVillageBiomeInfo = this.biomeLibrary.getBiomeInfo(InternalBiomes.VILLAGE);
            final ObjectArray<ISoundFactory> playerSounds = new ObjectArray<>();

            playerSounds.addAll(internalPlayerBiomeInfo.findBiomeSoundMatches());
            playerSounds.addAll(internalVillageBiomeInfo.findBiomeSoundMatches());
            playerSounds.forEach(fx -> this.workMap.put(fx, 1.0F));

            if (biomeSounds) {
                BiomeInfo playerBiome = this.scanner.playerLogicBiomeInfo();
                handleAddOnSounds(player, playerBiome);
                handleAddOnSounds(player, internalPlayerBiomeInfo);
                handleAddOnSounds(player, internalVillageBiomeInfo);
            }
        }

        queueAmbientSounds();
    }

    private void handleAddOnSounds(PlayerEntity player, BiomeInfo info) {
        ISoundFactory sound = info.getExtraSound(SoundEventType.MOOD, RANDOM);
        if (sound != null) {
            var instance = sound.createAsMood(player, MOOD_SOUND_MIN_RANGE, MOOD_SOUND_MAX_RANGE);
            this.audioPlayer.play(instance);
        }

        sound = info.getExtraSound(SoundEventType.ADDITION, RANDOM);
        if (sound != null) {
            var instance = sound.createAsAdditional();
            this.audioPlayer.play(instance);
        }
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
            final float volume = this.workMap.getFloat(entry.getSoundEvent());
            if (volume > 0) {
                entry.setVolumeScale(volume);
                if (entry.isFading())
                    entry.unfade();
                this.workMap.removeFloat(entry.getSoundEvent());
            } else if (!entry.isFading()) {
                entry.fade();
            }
            return false;
        });

        // Any sounds left in the list are new and need an emitter created.
        this.workMap.forEach((fx, volume) -> {
            final BiomeSoundEmitter e = new BiomeSoundEmitter(fx);
            e.setVolumeScale(volume);
            this.emitters.add(e);
        });
    }

    public void clearSounds() {
        this.emitters.forEach(BiomeSoundEmitter::stop);
        this.emitters.clear();
        this.workMap.clear();
    }

    @Override
    protected void gatherDiagnostics(Collection<String> left, Collection<String> right, Collection<ITimer> timers) {
        left.add(Strings.EMPTY);
        this.emitters.forEach(backgroundAcousticEmitter -> left.add("EMITTER: " + backgroundAcousticEmitter.toString()));
    }
}
