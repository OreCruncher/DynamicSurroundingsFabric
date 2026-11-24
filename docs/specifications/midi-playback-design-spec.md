# MIDI Playback for Client-Side Mod — Design Spec

Nov 17, 2026

## Status: parked (2026-11-23)

Parked by the mod author before any code was written. The purpose was to bring the music of the two resource packs
in `packs/` (Seasons and Extended, about 33 MB of OGG) into the mod's jar as MIDI. Two trials showed MIDI can't serve
that purpose:

- **Transcribing the recordings** (Basic Pitch 0.4.0 on *Anna's Theme* and *Gjallar*, rendered through GeneralUser GS)
  was "not even close". Transcription gets a piano piece's harmony roughly right and loses everything else: the
  instruments, the arrangement, drums and drones.
- **MIDI from the score** exists for the Seasons pack's music: the Mutopia Project has all four concertos of
  Vivaldi's Op. 8, a track per part, CC BY-SA 3.0
  (`https://www.mutopiaproject.org/ftp/VivaldiA/O8/<season>/<season>-mids.zip`). Rendered through a General MIDI
  SoundFont, they lacked the richness and vibrancy of the recordings.

The size MIDI would save is also smaller than it looked: the Seasons OGGs are already 64 kbps Vorbis (40 minutes in
17 MB). The packs' music stays OGG.

What remains useful if MIDI is wanted for something else (note block tunes, say): the design below, and the
"Phase 0 API check" near the end, which records how 26.3's sound loading and streaming work and what that changes in
the design. Decisions made before parking are in the open questions list.

## Overview and goals

Any sound event in sounds.json can point at a .mid file instead of an .ogg. Each sound name resolves to whichever file the highest-priority resource pack provides. A .mid is rendered to stereo PCM by the mod's own sampler on a dedicated thread and streamed through the normal sound engine, so every existing way of playing a sound event plays MIDI too.

**Goals**

- A sounds.json entry can name a song supplied as .mid, with no new JSON syntax.
- Every path that plays a sound event works unchanged: the mod's own playback code, `/playsound`, records, music, and sounds the server triggers.
- Resource packs can add .mid songs, or replace an .ogg with a .mid and the reverse.
- Near-zero added download size: the default instruments are Minecraft's own note block sounds, loaded from the game's assets at runtime.
- Sample-accurate timing, independent of tick rate and frame rate.
- An optional .sf2 SoundFont in a later phase, and .sf3 after that, for higher-quality instruments.
- 100% client-side, with one implementation in the Architectury `common` module for both Fabric and NeoForge.

**Non-goals**

- Pre-rendering MIDI to .ogg or caching rendered audio on disk (deferred).
- Encoding Ogg Vorbis in any form.
- MIDI as short sound effects or positional (3D) sounds: MIDI sounds are always music, always streamed, always stereo.
- A separate MIDI playback API: sound events are the API.
- Live MIDI input devices, MIDI output to external hardware, or editing MIDI in game.
- Full SoundFont 2.04 fidelity: filters, LFOs, modulators, reverb and chorus are out of scope for the first SoundFont phase.

## Assumptions and open questions

The target is Minecraft 26.3 on Fabric and NeoForge, built with Architectury. Since 26.1 the game ships unobfuscated, so every class name in this spec is Mojang's official name.

**Assumptions**

- The mod is client-only, plays its .ogg sounds through sound events, and already manages the streaming channel limit.
- The project is an Architectury multiloader mod with `common`, `fabric` and `neoforge` subprojects. All MIDI code lives in `common`, including the mixins (a common mixin config applied on both loaders). No per-loader code is expected.
- Minecraft 26.3 runs on Java 25 and is not obfuscated (Architectury Loom in no-remap mode). Code uses Mojang names: `Sound`, `SoundManager`, `SoundEngine`, `SoundBufferLibrary`, `AudioStream`, `SoundSource`, `Minecraft`.
- `javax.sound.midi` is used for parsing only; no `com.sun.*` classes.
- No new runtime dependencies. Ogg data is decoded with the decoder the game already ships; Phase 0 identifies which one 26.3 uses.
- MIDI sounds are music: always streamed, always stereo, never short effects.

**Open questions (owner: mod author)**

- [x] Loader: Fabric and NeoForge, through Architectury.
- [x] Minecraft version: 26.3.
- [x] Player songs: through resource packs and through the mod's configuration system.
- [x] Playback API: sound events defined in sounds.json; no separate MIDI API.
- [x] Category, volume and pitch: whatever the sound event is played with, as for any vanilla sound.
- [x] Positional playback: no. MIDI sounds are stereo music, which OpenAL doesn't place in 3D.
- [x] Looping: vanilla behavior; the looping flag vanilla passes to the stream restarts the song.
- [x] Both .ogg and .mid in the same pack: the .ogg wins.
- [x] Streaming channels: the mod's `streamingChannels` setting (`MixinLibrary`) caps the pool; MIDI songs share it, with no limit of their own.
- [x] Reload: the .mid's resource goes into vanilla's sound listing under the .ogg name (Phase 0 API check, item 1). Decided 2026-11-23.
- [x] Rendering: inside `MidiAudioStream.read`, on the sound engine thread, as vanilla decodes .ogg; no render worker or ring buffer (Phase 0 API check, item 2). Decided 2026-11-23.
- [ ] How config-folder songs reach the sound system (a built-in resource pack, generated sounds.json entries, or a mapping in the config system). To be decided by the mod author with the implementing agent, which knows the config system.
- [x] Enhanced Sounds (reverb, occlusion through a `SourceContext`) doesn't apply to MIDI songs, whatever category they play in. Decided 2026-11-23 (see "Enhanced Sounds" under Components).
- [ ] Which of the mod's config groups the `midi.*` settings go in, and their final names.

## Architecture

Each sound name is resolved to an .ogg or a .mid when resources reload, and the file's type picks the decoder at playback; everything above the decoder stays vanilla.

```
RESOURCE RELOAD (on every pack change)
  sounds/**.ogg and   ──►  Listing mixin          ──►  vanilla's sound listing + MidiSoundIndex
  sounds/**.mid            (highest-priority pack     (a winning .mid filed under the .ogg name;
                            wins)                      vanilla's file check then passes as is)

PLAYBACK (any caller, then the sound engine)
  Sound event played ──►  SoundEngine            ──►  SoundBufferLibrary.getStream
  (our code,              (Sound mixin: .mid         (mixin: MIDI → MidiAudioStream,
   /playsound)             always streams)            first 4 s rendered on the IO pool)
                                ▲
                                │ one second of stereo PCM per read()
SOUND ENGINE THREAD (vanilla's, as for .ogg decoding)
  MidiAudioStream.read ──► SongRenderer          ◄──  MidiSongCache / MidiLoader + TempoMap
                           (bank, voices, mixer)      (parse once, ticks to samples)
```

Three places in vanilla change: the reload's listing of sound files takes in .mid files, `Sound` reports a MIDI sound as streamed and never preloaded, and the buffer library returns a MidiAudioStream for sounds resolved to .mid. Sounds that resolve to .ogg take the vanilla path untouched.

## Components

Nine components split into a pure-Java core (no Minecraft imports, unit-testable) and a thin Minecraft adapter, made of one stream class, a song cache and three small mixins.

**Core (package `…midi.core`, no Minecraft imports)**

1. **MidiLoader** parses a .mid with `MidiSystem.getSequence(InputStream)` and produces a `MidiSong`. It merges all tracks into one list of events and keeps only what the renderer uses.
2. **TempoMap and MidiSong** convert ticks to absolute sample positions at the output sample rate. Supports PPQ division with Set Tempo meta events on any track (default 120 BPM until the first one) and SMPTE division. The result is an immutable, time-sorted array of `MidiEvent(sampleTime, channel, type, data1, data2)`.
3. **InstrumentBank** is an interface. `Zone find(int bank, int program, int key, int velocity)` returns the sample, root key, fine tune, loop points and envelope for one note. Implementations: `NoteBlockBank` (Phase 1) and `SoundFontBank` (Phase 2 and 3).
4. **Sampler and Voice**: one `Voice` per sounding note. A voice reads its sample at a rate of `2^((key − rootKey + bend + tune) / 12) × sampleRate / outputRate`, with linear interpolation, and applies velocity gain, channel volume and expression, and its envelope. Voices end when the envelope finishes or the sample ends without a loop.
5. **SongRenderer** combines a playhead, per-channel state, and the voice pool. `render(short[] out, int frames)` applies every event whose `sampleTime` falls inside the block at its exact sample offset, mixes active voices into a float buffer, applies master gain and a soft limiter, and converts to 16-bit PCM.

**Minecraft adapter (package `…midi.mc`)**

6. **MidiAudioStream** implements Minecraft's `AudioStream`. `getFormat()` returns 16-bit signed little-endian stereo PCM at the output rate. The engine's `Channel` asks `read(int size)` for one second of audio at a time, four seconds when the stream starts, then one more each time a second finishes playing, on the sound engine thread. `read` renders the requested frames there through `SongRenderer` into a direct `ByteBuffer` it reuses, as vanilla's `JOrbisAudioStream` decodes inside `read`. The first four seconds are rendered when the stream is made, on the IO pool (component 8), and `read` hands them out first, so the engine's opening reads cost nothing. A buffer with nothing in it, once the queued ones finish, stops the source and frees the channel, so `read` returns an empty buffer only at the song's end. When the song ends: if vanilla asked for a looping stream, the renderer resets its playhead to the start and continues within the same buffer, with no gap; otherwise `read` returns an empty buffer and the engine stops the source.
7. **MidiSoundIndex and the listing mixin.** Vanilla turns each sounds.json name into `sounds/<name>.ogg` (`Sound.SOUND_LISTER`). While reloading, `SoundManager.Preparations.listResources` lists every `sounds/**.ogg` (the top pack's resource for each path) into a map. Vanilla's file check (`validateSoundResource`) looks names up in that map, and playback opens files only through it (the `SoundEngine`'s provider is `ResourceProvider.fromMap` of it). One hook at the end of `listResources` also lists `sounds/**.mid`:
   - If only the .ogg exists, nothing changes.
   - If only the .mid exists, its `Resource` goes into the map under the .ogg path.
   - If both exist, the file from the highest-priority resource pack wins: compare the positions of `Resource.source()` in `ResourceManager.listPacks()`. A winning .mid replaces the .ogg's entry. If both come from the same pack, the .ogg wins and a debug line is logged.
   - So vanilla's file check passes for a .mid-only sound with no "does not exist" warning, and `Sound.getPath()` stays as it is. The hook builds a new map rather than changing the one vanilla returned.
   - Each .ogg path filed with a .mid goes into `MidiSoundIndex` (a set of those paths, with the pack each came from), rebuilt on every reload. Paths are what the buffer library receives, so they are the index's keys.
   - **The Sound mixin:** `@ModifyReturnValue` on `Sound.shouldStream()` (true) and `Sound.shouldPreload()` (false) for paths in `MidiSoundIndex`. `Sound` is immutable, and `SOUND_EVENT` entries build new `Sound`s each time they play, so the answer comes from the index rather than from changing the `Sound`. Without the preload half, a `"preload": true` MIDI entry would reach `getCompleteBuffer` at reload.
8. **The buffer library mixin.** On `SoundBufferLibrary.getStream(location, looping)`: if `MidiSoundIndex` has the location, open it through the same provider vanilla uses, get the song from `MidiSongCache` (parsed only on a cache miss), and return a `CompletableFuture<AudioStream>` on `Util.nonCriticalIoPool()`, as vanilla's is, completing with a `MidiAudioStream` whose first four seconds are rendered. Otherwise fall through to vanilla. On NeoForge, `SoundEngine.play` goes through `SoundInstance.getStream`, whose default forwards here, so the one hook covers both loaders. If `getCompleteBuffer` is ever reached for a MIDI sound, return a failed future and log the caller once (vanilla caches that future until the next reload). The .ogg path stays exactly vanilla.
9. **MidiSongCache** holds parsed songs so a song is parsed once, not on every play.
   - **Key:** the resolved .mid resource location plus the pack it came from.
   - **Value:** the immutable `MidiSong` (time-sorted event array and tempo-derived sample times). Streams share it read-only; each stream owns its own playhead, channel state and voices.
   - **Concurrency:** two plays requesting an uncached song at the same moment share one in-flight parse (cache the `CompletableFuture<MidiSong>`, not just the result). A failed parse is not cached, so a fixed file works after the next reload.
   - **Bounds:** least-recently-used eviction by total event count, 4,000,000 events by default (roughly 64–96 MB in the worst case; typical songs use well under 2 MB each). A song in use by a playing stream is never freed from under it.
   - **Invalidation:** cleared on every resource reload, together with `MidiSoundIndex`. Changing the SoundFont doesn't clear it, because parsed songs don't depend on the instruments.

**Enhanced Sounds:** MIDI songs get no `SourceContext`: no reverb, occlusion or other processing. `EnhancedSoundProcessor.shouldIgnoreSound` already skips the MUSIC category, relative sounds and sounds with no attenuation, which covers most music. One more condition there skips any sound whose resolved `Sound` path (`instance.getSound().getPath()`) is in `MidiSoundIndex`, so a MIDI song played as a record or through `/playsound` in another category is skipped too. This is the one change to the mod's existing code outside the MIDI packages.

**Per-channel state:** program, bank (CC0 and CC32), volume (CC7), expression (CC11), pan (CC10, stereo output only), sustain (CC64), and pitch bend (default range ±2 semitones; RPN 0 changes honored). CC120 and CC123 silence the channel. Channel 10 (index 9) is percussion and uses bank 128.

## Instrument sources

Phase 1 ships `NoteBlockBank`, built from the game's note block sounds at zero added size. Phases 2 and 3 add `SoundFontBank` for a SoundFont the player supplies. Both implement the same `InstrumentBank` interface, so the sampler never changes.

### NoteBlockBank (Phase 1)

- **Loading:** after each resource reload, look up each `block.note_block.*` sound event through the sound registry, take its first sound file, read the .ogg from the client resource manager, and decode it to mono `short[]` with the Ogg decoder the game already ships (Phase 0 confirms which). Don't hardcode file paths: resource packs can replace these sounds, and the paths differ from the event names.
- **Never copy Mojang's .ogg files into the mod jar.** They are always read from the player's own game assets.
- **Root keys:** at pitch 1.0 each sound plays the note below. These root keys come from the note block's range in the game. Verify each one by ear in Phase 1.
- **Range:** a note more than 24 semitones from its root is folded back by octaves. If a lower or higher instrument in the same family is listed, use it first.
- **Envelope:** the samples are one-shot with no loop. On note-off (or sustain release), apply a linear fade-out of 150 ms by default (configurable) so long-ringing samples don't blur fast passages.

| GM programs (0-based) | Family | Note block sound | Root key (MIDI) |
| --- | --- | --- | --- |
| 0–7 | Piano | harp | 66 (F#4) |
| 8–15 | Chromatic percussion | bell; iron_xylophone below C5 | 90 / 66 |
| 16–23 | Organ | flute | 78 (F#5) |
| 24–31 | Guitar | guitar | 54 (F#3) |
| 32–39 | Bass | bass | 42 (F#2) |
| 40–55 | Strings, ensemble | harp (no sustained string sound exists) | 66 |
| 56–63 | Brass | bit; didgeridoo below C3 | 66 / 42 |
| 64–79 | Reed, pipe | flute | 78 |
| 80–87 | Synth lead | bit | 66 |
| 88–103 | Synth pad, synth FX | pling | 66 |
| 104–111 | Ethnic | banjo | 66 |
| 112–119 | Percussive | cow_bell; xylophone above C6 | 66 / 90 |
| 120–127 | Sound effects | silent (skipped) | — |

**Percussion (channel 10)**, mapped by key: 35–36 kick → basedrum; 37–40 snare and clap → snare; 42, 44, 46 hi-hats → hat; 41, 43, 45, 47, 48, 50 toms → basedrum, pitched up in steps by key; 49, 51, 52, 55, 57, 59 cymbals → hat, pitched down; any other key → hat. The open hi-hat (46) is cut off by a closed one (42, 44).

### SoundFontBank (Phase 2: .sf2)

- **Source:** one file chosen in config from `config/<modid>/soundfonts/`. Nothing is bundled with the mod.
- **Parser:** RIFF chunks `sdta/smpl` and `pdta` (`phdr`, `pbag`, `pgen`, `inst`, `ibag`, `igen`, `shdr`). Skip the modulator chunks (`pmod`, `imod`).
- **Generators to support:** keyRange, velRange, instrument, sampleID, overridingRootKey, coarseTune, fineTune, scaleTuning, sampleModes (loop on or off, loop until release), sample start/end/loop address offsets (fine and coarse), initialAttenuation, pan, the six-stage volume envelope (delay, attack, hold, decay, sustain, release in timecents and centibels), and exclusiveClass.
- **Rules:** instrument-level generators set absolute values; preset-level generators add to them. Global zones supply defaults. Unspecified values use the SF2 2.04 defaults.
- **Stereo samples** (linked left and right samples) play as a pair when the output is stereo; when the output is mono, only the left sample plays.
- **Memory:** read the `shdr` index up front. Load sample data into a `short[]` cache only for the presets a song actually uses.
- **Unknown program:** fall back to program 0 in the same bank, then to the `NoteBlockBank` mapping.
- **References:** TinySoundFont (C) and MeltySynth (C#), both MIT-licensed, for generator handling and envelope math.

### SoundFontBank (Phase 3: .sf3)

- Same structure as .sf2, but each sample whose `shdr` sampleType has the compressed flag (0x10) holds Ogg Vorbis data. Its start and end values are byte offsets into `smpl`.
- Decode each compressed sample once, the first time it's used, with the same decoder `NoteBlockBank` uses, and cache the PCM. Loop points then refer to the decoded samples.

## Threading, timing and performance

Timing is set by sample positions inside the rendered audio, not by any clock, so it stays exact regardless of tick rate, frame rate or thread scheduling. The mod starts no threads of its own: songs are parsed and their first seconds rendered on vanilla's IO pool, and the rest is rendered where vanilla decodes .ogg, inside `read` on the sound engine thread.

| Thread | Owner | Does | Must never |
| --- | --- | --- | --- |
| Client (render) thread | Minecraft | Plays and stops sound events as usual (vanilla and the mod's existing code) | Parse MIDI, decode samples, or render audio |
| Resource reload workers | Minecraft | Run the listing mixin; build `MidiSoundIndex` | Parse or render songs (only list files) |
| IO pool (`Util.nonCriticalIoPool()`) | Minecraft | Runs the `getStream` future: parses the .mid on a cache miss, decodes the samples it needs, renders the first four seconds | Touch `SoundManager` |
| Sound engine thread | Minecraft | Calls `MidiAudioStream.read`, which renders the next second | Block on I/O or locks; allocate per read |

**Why in `read`:** the engine reads a second of audio at a time and queues four, so a stream always has three or four seconds queued in OpenAL when it's asked for more. Rendering a second takes a few milliseconds (measure in Phase 1), the same kind of work as vanilla's Ogg decoding on that thread. The mod's Enhanced Sounds start-of-sound calculations (`StartBudget`) share the thread, so Phase 1 measures `read`'s time beside them. If it proves too heavy, the fallback is a render worker with a ring buffer of at least five seconds, so it can't run dry under the engine's four-second opening read.

**Output format:** 44,100 Hz, 16-bit signed PCM, always stereo. MIDI sounds are music, so channel pan is honored and they are never placed in 3D (OpenAL only spatializes mono).

**Lifecycle:**

- **Start:** the sound engine asks the buffer library for a stream. On the IO pool, the song comes from `MidiSongCache` (parsed on a miss) and its first four seconds are rendered, then the future completes, so playback starts cleanly.
- **Pause:** when the game pauses sounds, the queued audio stops playing and the engine stops reading. Nothing renders while paused. No special handling is needed.
- **Stop:** vanilla stops the channel and closes the stream, and `close()` releases the stream's voices and buffers. The parsed song stays in the cache.
- **Looping:** follows the looping flag vanilla passes to `getStream`. At the end of the song, notes still held are released and the playhead returns to the start.
- **World leave or resource reload:** vanilla stops all sounds. After a reload, rebuild `NoteBlockBank` and `MidiSoundIndex`, and clear `MidiSongCache`.

**Performance budget:**

- 64 voices per song by default (configurable from 16 to 256). When the pool is full, steal a voice in its release phase first, then the quietest, then the oldest.
- The number of MIDI streams playing at once is bounded by the mod's existing streaming-channel management; no separate limit.
- No allocation in the render loop: mix and output buffers (one second's worth) are allocated per song when it starts.
- **Target:** under 3% of one CPU core for one song with 64 active voices on a mid-range desktop. Measure in Phase 1.

## Usage, config and file locations

There is no new playback API. A MIDI song is a normal sounds.json entry whose name has no extension; the reload step decides whether it is an .ogg or a .mid. The `"stream"` flag is optional for MIDI, because MIDI sounds always stream.

```json
{
  "music.boss_theme": {
    "sounds": [
      { "name": "<modid>:music/boss_theme", "stream": true }
    ]
  }
}
```

**File locations**

| What | Where | Notes |
| --- | --- | --- |
| Bundled and resource-pack songs | `assets/<namespace>/sounds/<path>.mid`, next to the .ogg files | Named in sounds.json without the extension. The .ogg or .mid from the highest-priority pack wins. Re-resolved on every resource reload |
| Player songs (config) | `config/<modid>/midi/*.mid` | How they reach the sound system is an open question for the implementing agent. Names resolve only inside this folder; files over 10 MB are refused |
| SoundFonts (Phase 2+) | `config/<modid>/soundfonts/*.sf2`, `*.sf3` | Never bundled; supplied by the player |

**Config keys** (in the mod's existing config system)

| Key | Default | Range |
| --- | --- | --- |
| `midi.enabled` | true | true / false (false = .mid files are ignored at reload) |
| `midi.soundfont` | empty (use note blocks) | file name in the soundfonts folder |
| `midi.maxVoices` | 64 | 16–256 |
| `midi.masterGain` | 1.0 | 0.0–2.0 |
| `midi.noteOffFadeMs` | 150 | 0–2,000 (note block bank only) |
| `midi.songCacheEvents` | 4,000,000 | 0–20,000,000 (0 = no cache; parse on every play) |

**Debug command (dev builds only):** play MIDI sounds with vanilla `/playsound`. The mod adds `/midi stats` (active streams, cached songs and hit rate, voices, time spent in `read`), `/midi list` (sounds resolved to .mid and the pack each came from), and `/midi render <sound>`, which writes a .wav to `config/<modid>/debug/` for listening outside the game.

## Error handling and edge cases

No MIDI problem may crash the game or stall a thread. A failure is logged once with the sound's id, and the sound simply doesn't play, as vanilla does for a broken .ogg.

| Case | Behavior |
| --- | --- |
| Neither .ogg nor .mid exists | Vanilla's warning; the sound is dropped (unchanged) |
| Both exist in the same pack | The .ogg wins; a debug line is logged |
| Corrupt or unsupported .mid (`InvalidMidiDataException`, format 2) | The stream future completes exceptionally; logged once per sound |
| Empty song (no notes) | The stream ends immediately |
| Note On with velocity 0 | Treat as Note Off |
| Note Off with no matching voice, or a duplicate Note On for a held key | Ignore the stray Off; a repeated On releases the old voice and starts a new one |
| Notes still held at the end of the song | Release them; finish after the release tails, capped at 2 s. When looping, release them and restart at once |
| Program with no instrument in the bank | Use the fallback chain in Instrument sources; log each missing program once per song |
| SoundFont missing, unreadable or fails to parse | Log it; use `NoteBlockBank` for the session; show a one-line toast in dev builds only |
| Note block sound missing after a resource pack change | Fall back to harp; if harp is missing too, the song plays silently and logs once |
| Voice pool full | Steal a voice as described in the performance section |
| Sum of voices exceeds full scale | Soft limiter (tanh-style) before 16-bit conversion; never wrap around |
| Rendering throws inside `read` | Log once with the sound's id and end the stream (an empty buffer); never let it reach the sound engine thread |
| Very long or very dense files | Parsing happens on the IO pool; a cap of 2,000,000 events per song protects memory (fail with a message above it) |
| `getCompleteBuffer` reached for a MIDI sound | Return a failed future; log the caller once (streaming wasn't forced somewhere) |
| Sound engine reloads or the audio device changes | The engine closes the stream; the song is released |
| `midi.enabled` is false | The listing mixin ignores .mid files; .mid-only sounds get vanilla's missing-file warning |

## Licensing considerations

The design ships no third-party audio and no third-party code, so the mod's own license is unaffected. Each rule below keeps it that way.

- **Note block sounds:** these are Mojang assets. They are read at runtime from the player's own game files and never copied into the jar or the config folder.
- **SoundFonts:** not bundled in any phase. Players supply their own files, so the mod distributes nothing. If a default SoundFont is ever bundled, use an MIT-licensed one (FluidR3_GM or MuseScore_General) and ship its license file. Avoid sets whose sample sources are uncertain.
- **Reference code:** porting logic from TinySoundFont or MeltySynth (both MIT) is fine. Keep their copyright notice in a `THIRD_PARTY_NOTICES` file and in the header of each ported source file.
- **Bundled .mid files:** a MIDI file of a copyrighted song is still a copy of that song's composition. Ship only public-domain pieces, your own compositions, or files whose license allows redistribution.
- **Java APIs:** `javax.sound.midi` is a public JDK API. Don't copy or bundle Gervill (`com.sun.media.sound`).

This is not legal advice; check the license of each specific file before shipping it.

## Implementation phases and acceptance criteria

Work goes in five phases. Each phase ends at a gate the mod author signs off on before the next one starts. Phase 0 exists to prove the riskiest part, the mixins into vanilla sound loading, before any MIDI code is written.

### Phase 0: Mixin spike

Build the listing mixin, `MidiSoundIndex`, the Sound mixin, the buffer library mixin, and a `MidiAudioStream` that plays a 440 Hz stereo sine wave for any sound resolved to a .mid, generated inside `read` a second at a time as the real renderer will be.

- [ ] A sounds.json entry whose only file is a .mid survives resource reload with no missing-file warning, and plays the sine wave through `/playsound` on both Fabric and NeoForge in Minecraft 26.3.
- [ ] The same sound plays through the mod's existing playback code, and the volume sliders and its category apply.
- [ ] With an .ogg in the mod and a .mid of the same name in a higher-priority resource pack, the .mid plays; swapping the pack order plays the .ogg.
- [ ] A looping sound loops; existing .ogg sounds behave exactly as before.
- [ ] Played under `records` with `/playsound`, the MIDI sound gets no `SourceContext` (no reverb or occlusion).
- [ ] Stopping, pausing the game, leaving the world and reloading resources all end or pause it cleanly, with no errors in the log.

### Phase 1: MIDI with note block sounds

Build `MidiLoader`, `TempoMap`, `NoteBlockBank`, `Sampler`, `SongRenderer`, and `MidiSongCache`, then replace the sine wave with real rendering inside `read`.

- [ ] A bundled format 0 file and a format 1 file with tempo changes play at the correct speed. Measured length is within 50 ms of a reference player's.
- [ ] Pitches are correct for every mapped instrument, checked by ear against a reference scale file.
- [ ] Drums on channel 10 use basedrum, snare and hat as mapped.
- [ ] Program changes, CC7, CC10, CC11, CC64 and pitch bend take effect.
- [ ] A looping MIDI sound loops with no audible gap.
- [ ] Playing the same song twice, including at the same moment, parses it once; a resource reload clears the cache.
- [ ] Unit tests pass for the tempo map, the pitch-ratio math and the song cache.
- [ ] One song at 64 voices stays under the CPU target and plays 5 minutes at 30 fps with no gaps; `read`'s time per call is measured beside the Enhanced Sounds work on the sound engine thread, to decide whether the render-worker fallback is needed.
- [ ] A song added only by a resource pack plays, and existing .ogg playback is unchanged.

### Phase 2: .sf2 SoundFonts

Build the `SoundFontBank` parser and loop and envelope support in `Voice`.

- [ ] With FluidR3_GM.sf2 or GeneralUser GS selected, all 128 programs and the standard drum kit play.
- [ ] Looped instruments (organ, strings) sustain while held and release with the envelope.
- [ ] A corrupt .sf2 falls back to note blocks without crashing.
- [ ] Only samples a song uses are loaded; memory use is logged.

### Phase 3: .sf3 SoundFonts

- [ ] MuseScore_General.sf3 plays with the same results as its .sf2 version.
- [ ] Compressed samples are decoded once and cached.

### Phase 4: Polish

- [ ] Settings appear in the mod's config screen.
- [ ] Player songs from the config folder, using the approach agreed between the mod author and the implementing agent.
- [ ] README section documenting how to add .mid songs through resource packs and how to add a SoundFont.

## Testing plan

The core package has no Minecraft imports, so most correctness is checked with plain JUnit tests and offline renders. In-game testing covers only the adapter, the sound feel, and performance.

| Layer | How | What it checks |
| --- | --- | --- |
| Unit (JUnit, core only) | Small generated .mid fixtures built in the test with `javax.sound.midi` | Tick-to-sample conversion with tempo changes and SMPTE division; event merging across tracks; Note On with velocity 0; pitch-ratio math; envelope stage timing; voice stealing order; loop restart; song cache (one parse for concurrent requests, LRU eviction never frees a song in use, failed parses not cached) |
| SF2 parser (JUnit, Phase 2+) | A tiny hand-made .sf2 test fixture (one preset, two zones, one looped sample) committed to the test resources | Generator inheritance (preset adds, instrument sets, global zones); key and velocity range lookup; loop points; stereo links |
| Offline render (Gradle task or debug command) | Render fixtures and real songs to .wav through `SongRenderer` | Listen for wrong pitches, clicks at loop points and voice starts, clipping; compare length against a reference player |
| Golden files | Store a checksum of the rendered PCM for each fixture | Catches accidental changes in output after refactoring |
| Resolution (in game, scripted with a test resource pack) | A pack set with .ogg only, .mid only, both in one pack, and both in different packs at each priority | The chosen file for each case, via `/midi list`; no missing-file warning for .mid-only sounds |
| In game, manual | `/playsound` and the mod's own playback on a dev client, on Fabric and NeoForge | Volume sliders, category, looping, pause, world leave, resource reload, SoundFont swap; .ogg sounds unaffected |
| Performance | `/midi stats` plus a profiler, using a dense test file (thousands of notes per second) | Voice cap holds; CPU under target; no gaps at 30 fps; time per `read`; no allocation in the render loop |

**Test songs to collect:** a chromatic scale across all 128 keys on one channel; a General MIDI drum-map test; a format 1 file with several tempo changes; a file using pitch bend and sustain pedal; and one dense file for stress testing. All must be public domain or self-made.

## Notes for the implementing agent (Claude Code)

Treat this spec as the design, not as verified API documentation: method names and signatures for Minecraft and the loader were written without the target version's sources and must be checked against them first.

1. **Confirm game APIs before coding.** (Done for 26.3 on 2026-11-23: see "Phase 0 API check". Repeat it for any later Minecraft version.) You already know this repository, its loaders, build setup and config system. What this spec can't guarantee is Minecraft's internals in 26.3. Look up the real code for: how `Sound` builds its file path (the `.ogg` suffix); where the `SoundManager` reload step checks that files exist and drops missing ones; how `SoundEngine` decides to stream; `SoundBufferLibrary.getStream` and `getCompleteBuffer`; `AudioStream`; how to read a resource's pack and its priority from the resource manager; and the id class (`Identifier`). Report any difference from this spec before writing code that depends on it.
2. **Work one phase at a time.** Stop at each gate in the phases section, list the acceptance criteria met, and wait for sign-off.
3. **Keep `…midi.core` free of Minecraft imports** so its tests run without the game. Everything else, including the mixins, goes in the Architectury `common` module. Test every phase on both Fabric and NeoForge.
4. **Hard rules:**
   - No `com.sun.*` classes and no Gervill.
   - No new runtime dependencies. Decode Ogg data with the decoder Minecraft already ships. Ask before adding any library.
   - Keep mixins minimal and compatible with other sound mods: prefer `@Inject` or MixinExtras `@WrapOperation` over `@Overwrite` or `@Redirect`.
   - Leave the .ogg path behaving exactly as vanilla.
   - Never block the sound engine thread (no I/O or locks in `read`; rendering a second there is the design), and never touch `SoundManager` off the client thread.
   - Never copy Mojang's sound files into the repository or jar.
5. **Follow the existing code.** Match the mod's package layout, config system, logging and code style. MIDI streams go through the mod's existing streaming-channel management.
6. **Config-folder songs are still open.** Propose two or three ways to feed them into the sound system, with trade-offs, and let the mod author choose.
7. **When unsure about SoundFont rules,** follow the SoundFont 2.04 specification, with TinySoundFont and MeltySynth as reference implementations. Don't invent behavior.

## Phase 0 API check (2026-11-23, Minecraft 26.3, NeoForge 26.3.0.101)

Checked against the decompiled 26.3 sources (`genSources`) and `javap` of the NeoForge-patched jar. Differences from
the spec come first. The mod author accepted the proposals in items 1 and 2 on 2026-11-23, and the spec above has
been rewritten to match. This section stays as the record of why.

1. **Playback can only open files the reload listed.** `SoundManager.Preparations.listResources` lists every
   `sounds/**.ogg` once (`Sound.SOUND_LISTER.listMatchingResources`, the top pack's resource per path) into
   `soundCache`. `validateSoundResource` checks `sound.getPath()` against that map, and the `SoundEngine`'s resource
   provider is `ResourceProvider.fromMap(soundCache)`. So `SoundBufferLibrary` can't open a `.mid` unless the reload
   puts it in that map. An index alone isn't enough.
   Proposal: one hook at the end of `listResources` also lists `sounds/**.mid`. Where the `.mid` wins (compare
   `Resource.source()` positions in `ResourceManager.listPacks()`), it puts the `.mid`'s `Resource` in the map
   **under the `.ogg` key**. Validation then passes untouched, `Sound.getPath()` stays as it is, and `getStream`
   receives the `.ogg` id, which `MidiSoundIndex` (or the file's `MThd` header) marks as MIDI. This needs no hook
   on the validation itself.
2. **The engine pulls 1 second at a time, 4 seconds up front.** `Channel.attachBufferStream` sizes each `read` at one
   second of audio (`calculateBufferSize(format, 1)`), reads 4 buffers at once, then refills one per finished buffer
   from `ChannelAccess`'s tick on the sound engine thread. A buffer with nothing in it, once the queue drains, stops
   the source, and the engine frees the channel. A 500 ms ring buffer filled in 1,024-frame blocks would underrun on
   the first `read`. Vanilla's `JOrbisAudioStream` decodes synchronously inside `read`, on that same thread.
   Proposal: drop the render worker and ring buffer, and render inside `read` as JOrbis decodes. The first 4 seconds
   are rendered in `getStream`'s future on `Util.nonCriticalIoPool()`, so the start doesn't wait. One second at 64
   voices should take a few milliseconds a second. The mod's `StartBudget` calculations also run on that thread, so
   measure it. (The other way: keep the worker, with a ring of 5 seconds or more.)
3. **Forcing streaming.** `SoundEngine.play` reads `sound.shouldStream()` both for the channel pool and to choose
   `getStream` over `getCompleteBuffer`. `Sound` is immutable, and `SOUND_EVENT` entries build new `Sound`s at
   play time. Proposal: `@ModifyReturnValue` on `Sound.shouldStream()` (true) and `Sound.shouldPreload()` (false) for
   paths in `MidiSoundIndex`. A `"preload": true` MIDI entry would otherwise reach `getCompleteBuffer` at reload.
4. **As the spec says.** `Sound.SOUND_LISTER = new FileToIdConverter("sounds", ".ogg")`, and `getPath()` adds the
   suffix. `SoundBufferLibrary.getStream(Identifier, boolean)` and `getCompleteBuffer(Identifier)` exist, both on
   `Util.nonCriticalIoPool()`. `getCompleteBuffer` caches its future, a failed one included, until `clear()`.
   Vanilla loops a stream by reopening it when `read` returns an empty buffer (`LoopingAudioStream`).
   `AudioStream` is `getFormat()` and `read(int)`, plus `Closeable`. The id class is `net.minecraft.resources.Identifier`.
5. **NeoForge.** `SoundEngine.play` calls `SoundInstance.getStream(SoundBufferLibrary, Sound, boolean)`, whose
   default forwards to `SoundBufferLibrary.getStream(sound.getPath(), looping)`, so one hook covers both loaders. A
   mod's `SoundInstance` that overrides it bypasses MIDI, as it bypasses `.ogg`. `Sound`, `SoundManager`,
   `Preparations`, `SoundBufferLibrary` and `Channel` are unpatched.
6. **The Ogg decoder** is `JOrbisAudioStream` (JOrbis), a `FiniteAudioStream`. `new JOrbisAudioStream(in).readAll()`
   gives signed 16-bit little-endian PCM in the file's channel count, which is what `NoteBlockBank` needs.
7. **Streaming channels.** Vanilla sizes the pool as `clamp(sqrt(device sources), 2, 8)` in `Library.init`.
   `MixinLibrary`'s `@ModifyConstant(intValue = 8)` raises that cap to `streamingChannels`, and also the static pool's
   minimum (the other 8 in the method), which is harmless. It's a cap, not a count: OpenAL Soft's 256 sources give
   16, held to the setting. MIDI songs share the pool.
8. **Enhanced Sounds** hooks `SoundEngine.play` (`MixinSoundEngine`) and `Channel.updateStream` (`MixinSource`).
   `EnhancedSoundProcessor.shouldIgnoreSound` already skips the MUSIC category, relative sounds and those with no
   attenuation, but a MIDI song played in another category (a record, `/playsound`) would get a `SourceContext`.
   Decided 2026-11-23: MIDI songs never get one (see "Enhanced Sounds" under Components).

## Sources

- [Architectury API setup docs](https://docs.architectury.dev/api/getting-started/setup): Java 25 for Minecraft 26.1+, no obfuscation from 26.1, Loom in no-remap mode.
- [NeoForge SoundInstance javadoc (1.21.x)](https://nekoyue.github.io/ForgeJavaDocs-NG/javadoc/1.21.x-neoforge/net/minecraft/client/resources/sounds/SoundInstance.html): the `getStream(SoundBufferLibrary, Sound, boolean)` hook.
- [NeoForge test SineSound (1.21.x)](https://nekoyue.github.io/ForgeJavaDocs-NG/javadoc/1.21.x-neoforge/net/neoforged/neoforge/debug/client/ClientTests.SineSound.html): NeoForge's own sine-wave test of a custom stream, useful background on how AudioStream is consumed.
- [Minecraft 26.3 release coverage](https://www.gamingonlinux.com/2026/09/minecraft-java-edition-26-3-wilderness-bound-update-released/): 26.3 released September 2026.
