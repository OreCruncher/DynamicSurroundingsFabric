package org.orecruncher.dsurround.runtime.audio.voicechat;

import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.audiochannel.ClientLocationalAudioChannel;
import de.maxhenkel.voicechat.api.events.ClientSoundEvent;
import de.maxhenkel.voicechat.api.events.ClientVoicechatConnectionEvent;
import de.maxhenkel.voicechat.api.events.CreateOpenALContextEvent;
import de.maxhenkel.voicechat.api.events.DestroyOpenALContextEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.OpenALSoundEvent;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.openal.EXTThreadLocalContext;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.function.SingletonSupplier;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.runtime.audio.AudioUtilities;

import java.util.UUID;

/**
 * Simple Voice Chat's plugin: hands its OpenAL events to {@link VoiceSounds}, and with the voiceChatHearSelf option
 * plays the player's own voice back to them, so its processing can be heard without a second player. Loaded only by
 * voice chat (the {@code voicechat} entrypoint on Fabric, the annotation on NeoForge), so the only class that needs
 * its API; the API isn't shipped with the mod.
 * <p>
 * Voice chat can make and initialize it before the mod is bootstrapped (on Fabric, mods' entrypoints run in no set
 * order), so until its events fire nothing may use the mod's container: the logger and config are resolved lazily.
 */
@ForgeVoicechatPlugin
public final class VoiceChatPlugin implements VoicechatPlugin {

    private static final IModLog LOGGER = ContainerManager.memoize(IModLog.class);
    // Resolved on first use: voice chat may load its plugins before the mod's container is ready
    private static final SingletonSupplier<Configuration.Logging> CONFIG = SingletonSupplier.from(() -> ContainerManager.resolve(Configuration.Logging.class));
    private static final UUID OWN_VOICE = UUID.randomUUID();

    private final VoiceSounds voices = VoiceSounds.create(LOGGER);

    // Plays the player's own voice back to them (voiceChatHearSelf); null while not connected
    private volatile ClientLocationalAudioChannel ownVoice;

    @Override
    public String getPluginId() {
        return Constants.MOD_ID;
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(CreateOpenALContextEvent.class, this::onCreateContext);
        registration.registerEvent(DestroyOpenALContextEvent.class, event -> this.voices.onContextDestroyed());
        // Each packet raises Pre, then this, then Post: once is enough
        registration.registerEvent(OpenALSoundEvent.class, this::onSound);
        registration.registerEvent(ClientVoicechatConnectionEvent.class, this::onConnection);
        registration.registerEvent(ClientSoundEvent.class, this::onMicrophone);
    }

    private void onConnection(ClientVoicechatConnectionEvent event) {
        var api = event.getVoicechat();
        this.ownVoice = event.isConnected() ? api.createLocationalAudioChannel(OWN_VOICE, api.createPosition(0D, 0D, 0D)) : null;
    }

    /**
     * The player's microphone audio, about to be sent. With voiceChatHearSelf, also played back to them from their
     * head, as another player's voice would be, so its processing can be heard alone.
     */
    private void onMicrophone(ClientSoundEvent event) {
        var channel = this.ownVoice;
        // Only for hearing the mod's processing: not while it's off (with Sound Physics Remastered, which has its own)
        if (channel == null || !CONFIG.get().voiceChatHearSelf || !AudioUtilities.doEnhancedSounds())
            return;
        GameUtils.getPlayer().ifPresent(player -> {
            var eyes = player.getEyePosition();
            channel.setLocation(event.getVoicechat().createPosition(eyes.x(), eyes.y(), eyes.z()));
            // A copy: voice chat goes on to encode and send the original
            channel.play(event.getRawAudio().clone());
        });
    }

    private void onCreateContext(CreateOpenALContextEvent event) {
        // Voice chat raises it with the new context current; made sure of in case that changes
        long previous = EXTThreadLocalContext.alcGetThreadContext();
        EXTThreadLocalContext.alcSetThreadContext(event.getContext());
        try {
            this.voices.onContextCreated(event.getDevice());
        } finally {
            EXTThreadLocalContext.alcSetThreadContext(previous);
        }
    }

    private void onSound(OpenALSoundEvent event) {
        var channel = event.getChannelId();
        if (channel == null)
            return;
        var position = event.getPosition();
        this.voices.onSound(channel, event.getSource(), position == null ? null : new Vec3(position.getX(), position.getY(), position.getZ()));
    }
}
