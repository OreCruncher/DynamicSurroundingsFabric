package org.orecruncher.dsurround.processing;

import it.unimi.dsi.fastutil.objects.Reference2FloatOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import joptsimple.internal.Strings;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundEvent;
import org.orecruncher.dsurround.config.BiomeLibrary;
import org.orecruncher.dsurround.config.InternalBiomes;
import org.orecruncher.dsurround.config.SoundEventType;
import org.orecruncher.dsurround.config.biome.BiomeInfo;
import org.orecruncher.dsurround.lib.TickCounter;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.math.TimerEMA;
import org.orecruncher.dsurround.processing.scanner.BiomeScanner;
import org.orecruncher.dsurround.sound.MinecraftAudioPlayer;
import org.orecruncher.dsurround.sound.SoundFactoryBuilder;

import java.util.Collection;

@Environment(EnvType.CLIENT)
public final class BiomeSoundHandler extends ClientHandler {

    public static final int SCAN_INTERVAL = 4;
    public static final int MOOD_SOUND_MIN_RANGE = 8;
    public static final int MOOD_SOUND_MAX_RANGE = 16;

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
        return BiomeScanner.isUnderground()
                || !Scanners.isInside()
                || BiomeScanner.getDimInfo().alwaysOutside();
    }

    private void generateBiomeSounds() {
        final float area = Scanners.getBiomeArea();
        for (final Reference2IntMap.Entry<BiomeInfo> kvp : Scanners.getBiomes().reference2IntEntrySet()) {
            final Collection<SoundEvent> acoustics = kvp.getKey().findBiomeSoundMatches();
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

            final BiomeInfo internalPlayerBiomeInfo = BiomeLibrary.getBiomeInfo(InternalBiomes.PLAYER);
            final BiomeInfo internalVillageBiomeInfo = BiomeLibrary.getBiomeInfo(InternalBiomes.VILLAGE);
            final ObjectArray<SoundEvent> playerSounds = new ObjectArray<>();

            playerSounds.addAll(internalPlayerBiomeInfo.findBiomeSoundMatches());
            playerSounds.addAll(internalVillageBiomeInfo.findBiomeSoundMatches());
            playerSounds.forEach(fx -> WORK_MAP.put(fx, 1.0F));

            if (biomeSounds) {
                BiomeInfo playerBiome = BiomeScanner.playerLogicBiomeInfo();
                handleAddOnSounds(player, playerBiome);
                handleAddOnSounds(player, internalPlayerBiomeInfo);
                handleAddOnSounds(player, internalVillageBiomeInfo);
            }
        }

        queueAmbientSounds();
    }

    private void handleAddOnSounds(PlayerEntity player, BiomeInfo info) {
        SoundEvent sound = info.getExtraSound(SoundEventType.MOOD, RANDOM);
        if (sound != null) {
            SoundInstance instance = SoundFactoryBuilder
                    .create(sound)
                    .build()
                    .createAsMood(player, MOOD_SOUND_MIN_RANGE, MOOD_SOUND_MAX_RANGE);
            MinecraftAudioPlayer.INSTANCE.play(instance);
        }

        sound = info.getExtraSound(SoundEventType.ADDITION, RANDOM);
        if (sound != null) {
            SoundInstance instance = SoundFactoryBuilder
                    .create(sound)
                    .build()
                    .createAsAdditional();
            MinecraftAudioPlayer.INSTANCE.play(instance);
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
        MinecraftAudioPlayer.INSTANCE.stopAll();
    }

    @Override
    protected void gatherDiagnostics(Collection<String> left, Collection<String> right, Collection<TimerEMA> timers) {
        left.add(Strings.EMPTY);
        this.emitters.forEach(backgroundAcousticEmitter -> left.add("EMITTER: " + backgroundAcousticEmitter.toString()));
    }
}