package org.orecruncher.dsurround.runtime.audio.voicechat;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.threading.RecordingLog;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the voice channel bookkeeping: when a voice's processing starts, follows it, stops and is forgotten. The
 * OpenAL side is faked.
 */
public class VoiceSoundsTests {

    private static final UUID PLAYER = UUID.randomUUID();
    private static final UUID OTHER_PLAYER = UUID.randomUUID();
    private static final Vec3 HERE = new Vec3(10, 64, 10);
    private static final Vec3 THERE = new Vec3(12, 64, 10);

    /**
     * Records what's done to it.
     */
    private static class FakeVoice implements VoiceSounds.Voice {
        final int sourceId;
        final List<String> calls = new ArrayList<>();
        final List<Vec3> positions = new ArrayList<>();
        boolean due = true;

        FakeVoice(int sourceId, Vec3 position) {
            this.sourceId = sourceId;
            this.positions.add(position);
        }

        @Override
        public int sourceId() {
            return this.sourceId;
        }

        @Override
        public void start() {
            this.calls.add("start");
        }

        @Override
        public void update(Vec3 position) {
            this.calls.add("update");
            this.positions.add(position);
        }

        @Override
        public void stop() {
            this.calls.add("stop");
        }

        @Override
        public boolean shouldExecute() {
            return this.due;
        }

        @Override
        public Void call() {
            return null;
        }
    }

    /**
     * Makes voices with the given maker, in a context with four sends, and counts how often it is closed.
     */
    private static class FakeFactory implements VoiceSounds.VoiceFactory {
        private final BiFunction<Integer, Vec3, VoiceSounds.Voice> maker;
        int closed;

        FakeFactory(BiFunction<Integer, Vec3, VoiceSounds.Voice> maker) {
            this.maker = maker;
        }

        @Override
        public VoiceSounds.Voice create(int sourceId, Vec3 position) {
            return this.maker.apply(sourceId, position);
        }

        @Override
        public int sends() {
            return 4;
        }

        @Override
        public void close() {
            this.closed++;
        }
    }

    private static VoiceSounds.VoiceFactory factory(BiFunction<Integer, Vec3, VoiceSounds.Voice> maker) {
        return new FakeFactory(maker);
    }

    private static final class FakeBackend implements VoiceSounds.Backend {
        final List<FakeVoice> made = new ArrayList<>();
        final List<FakeFactory> opened = new ArrayList<>();
        boolean opens = true;
        boolean active = true;

        @Override
        public VoiceSounds.VoiceFactory open(long device) {
            if (!this.opens)
                return null;
            var factory = new FakeFactory((sourceId, position) -> {
                var voice = new FakeVoice(sourceId, position);
                this.made.add(voice);
                return voice;
            });
            this.opened.add(factory);
            return factory;
        }

        @Override
        public boolean isActive() {
            return this.active;
        }
    }

    private final FakeBackend backend = new FakeBackend();
    private final RecordingLog log = new RecordingLog();
    private long now;
    private final VoiceSounds sounds = new VoiceSounds(this.backend, () -> this.now, this.log);

    private List<Callable<?>> due() {
        var result = new ArrayList<Callable<?>>();
        this.sounds.forEachDue(result::add);
        return result;
    }

    @Test
    void nothingIsProcessedBeforeVoiceChatMakesItsContext() {
        this.sounds.onSound(PLAYER, 1, HERE);

        assertTrue(this.backend.made.isEmpty());
        assertTrue(this.due().isEmpty());
    }

    @Test
    void nothingIsProcessedWhenTheBackendDoesNotOpen() {
        // Enhanced sounds off, or setting up failed
        this.backend.opens = false;
        this.sounds.onContextCreated(0L);

        this.sounds.onSound(PLAYER, 1, HERE);

        assertTrue(this.backend.made.isEmpty());
    }

    @Test
    void aVoiceStartsOnItsFirstPacketAndFollowsItsPosition() {
        this.sounds.onContextCreated(0L);

        this.sounds.onSound(PLAYER, 1, HERE);
        this.sounds.onSound(PLAYER, 1, THERE);

        assertEquals(1, this.backend.made.size());
        var voice = this.backend.made.getFirst();
        assertEquals(List.of("start", "update"), voice.calls);
        assertEquals(List.of(HERE, THERE), voice.positions);
    }

    @Test
    void eachChannelHasItsOwnVoice() {
        this.sounds.onContextCreated(0L);

        this.sounds.onSound(PLAYER, 1, HERE);
        this.sounds.onSound(OTHER_PLAYER, 2, THERE);

        assertEquals(2, this.backend.made.size());
        assertEquals(2, this.due().size());
    }

    @Test
    void aNewSourceForTheChannelStartsAgain() {
        // Its speaker was remade
        this.sounds.onContextCreated(0L);
        this.sounds.onSound(PLAYER, 1, HERE);

        this.sounds.onSound(PLAYER, 7, HERE);

        assertEquals(2, this.backend.made.size());
        assertEquals(7, this.backend.made.get(1).sourceId);
        assertEquals(List.of("start"), this.backend.made.get(1).calls);
        assertEquals(1, this.due().size(), "the old source is no longer processed");
    }

    @Test
    void aVoiceThatIsNotPositionalHasItsFiltersTakenOff() {
        // Group chat, say
        this.sounds.onContextCreated(0L);
        this.sounds.onSound(PLAYER, 1, HERE);

        this.sounds.onSound(PLAYER, 1, null);

        var voice = this.backend.made.getFirst();
        assertEquals(List.of("start", "stop"), voice.calls);
        assertTrue(this.due().isEmpty());

        // And starts again when it is positional again
        this.sounds.onSound(PLAYER, 1, HERE);
        assertEquals(2, this.backend.made.size());
    }

    @Test
    void turningProcessingOffTakesFiltersOff() {
        this.sounds.onContextCreated(0L);
        this.sounds.onSound(PLAYER, 1, HERE);

        this.backend.active = false;
        this.sounds.onSound(PLAYER, 1, HERE);
        this.sounds.onSound(PLAYER, 1, HERE);

        assertEquals(List.of("start", "stop"), this.backend.made.getFirst().calls);
        assertEquals(1, this.backend.made.size(), "nothing new is started while off");
    }

    @Test
    void onlyVoicesDueAreRecalculated() {
        this.sounds.onContextCreated(0L);
        this.sounds.onSound(PLAYER, 1, HERE);
        this.sounds.onSound(OTHER_PLAYER, 2, HERE);

        this.backend.made.getFirst().due = false;

        assertEquals(List.of(this.backend.made.get(1)), this.due());
    }

    @Test
    void aChannelGoneQuietIsForgotten() {
        this.sounds.onContextCreated(0L);
        this.sounds.onSound(PLAYER, 1, HERE);
        this.sounds.onSound(OTHER_PLAYER, 2, HERE);

        this.now += VoiceSounds.FORGET_AFTER_NANOS;
        this.sounds.onSound(OTHER_PLAYER, 2, HERE);
        this.now += 1;

        assertEquals(List.of(this.backend.made.get(1)), this.due());
    }

    @Test
    void destroyingTheContextForgetsEveryVoice() {
        this.sounds.onContextCreated(0L);
        this.sounds.onSound(PLAYER, 1, HERE);

        this.sounds.onContextDestroyed();
        this.sounds.onSound(PLAYER, 1, HERE);

        assertTrue(this.due().isEmpty());
        assertEquals(1, this.backend.made.size(), "nothing is started without a context");
    }

    @Test
    void destroyingTheContextSilencesItsEffectsOnce() {
        // So reverbs still ringing aren't cut off when voice chat destroys the context
        this.sounds.onContextCreated(0L);
        this.sounds.onSound(PLAYER, 1, HERE);

        this.sounds.onContextDestroyed();
        this.sounds.onContextDestroyed();

        assertEquals(1, this.backend.opened.getFirst().closed);
    }

    @Test
    void failingToSilenceTheEffectsIsLoggedNotThrown() {
        // Thrown on the thread leaving the world, it would stop voice chat closing its device
        var sounds = new VoiceSounds(new VoiceSounds.Backend() {
            @Override
            public VoiceSounds.VoiceFactory open(long device) {
                return new FakeFactory(FakeVoice::new) {
                    @Override
                    public void close() {
                        throw new IllegalStateException("OpenAL error");
                    }
                };
            }

            @Override
            public boolean isActive() {
                return true;
            }
        }, () -> 0L, this.log);
        sounds.onContextCreated(0L);

        assertDoesNotThrow(sounds::onContextDestroyed);

        var errors = this.log.at(IModLog.Level.ERROR);
        assertEquals(1, errors.size());
        assertInstanceOf(IllegalStateException.class, errors.getFirst().throwable());
        assertEquals("Voice chat: not connected", sounds.diagnostics());
    }

    @Test
    void aNewContextStartsAfresh() {
        // Voice chat remade its context (a device change): sources from the old one mean nothing
        this.sounds.onContextCreated(0L);
        this.sounds.onSound(PLAYER, 1, HERE);

        this.sounds.onContextCreated(0L);
        this.sounds.onSound(PLAYER, 1, HERE);

        assertEquals(2, this.backend.made.size());
        assertEquals(1, this.due().size());
    }

    @Test
    void aFailingVoiceIsLoggedNotThrown() {
        // Thrown on voice chat's speaker thread, it would stop the voice playing
        var failing = factory((sourceId, position) -> new FakeVoice(sourceId, position) {
            @Override
            public void stop() {
                throw new IllegalStateException("OpenAL error");
            }
        });
        var sounds = new VoiceSounds(new VoiceSounds.Backend() {
            @Override
            public VoiceSounds.VoiceFactory open(long device) {
                return failing;
            }

            @Override
            public boolean isActive() {
                return true;
            }
        }, () -> 0L, this.log);
        sounds.onContextCreated(0L);
        sounds.onSound(PLAYER, 1, HERE);

        assertDoesNotThrow(() -> sounds.onSound(PLAYER, 1, null));

        var errors = this.log.at(IModLog.Level.ERROR);
        assertEquals(1, errors.size());
        assertInstanceOf(IllegalStateException.class, errors.getFirst().throwable());
    }

    @Test
    void diagnosticsSayWhetherVoicesAreProcessed() {
        assertEquals("Voice chat: not connected", this.sounds.diagnostics());

        this.sounds.onContextCreated(0L);
        this.sounds.onSound(PLAYER, 1, HERE);
        this.sounds.onSound(OTHER_PLAYER, 2, HERE);
        assertEquals("Voice chat: 2 voices (sends: 4)", this.sounds.diagnostics());

        this.backend.active = false;
        assertEquals("Voice chat: processing off", this.sounds.diagnostics());

        this.sounds.onContextDestroyed();
        assertEquals("Voice chat: not connected", this.sounds.diagnostics());
    }

    @Test
    void diagnosticsSayWhenTheContextIsNotProcessed() {
        // Enhanced sounds off, or setting up failed
        this.backend.opens = false;
        this.sounds.onContextCreated(0L);

        assertEquals("Voice chat: not processed", this.sounds.diagnostics());
    }
}
