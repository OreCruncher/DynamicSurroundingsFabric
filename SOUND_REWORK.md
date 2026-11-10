# Sound rework

Every sound the mod ships should have a known source and license, credited in `CREDITS.md`, and sound clean, level
with its neighbors and not repetitive. Started 2026-11-09 on the 5.2.0 branch (`OreCruncher/26.3-5.2.0`).

## Still to do (2026-11-10)

Nothing: every sound the mod plays is credited, the mod's own or the game's own, and all allow commercial use (only
some textures are CC BY-NC).

Also: the 5.2.0 docs need the new `volume` script on biome loops, `lib.smoothStep` and the new sound events (noted in
the docs memory).

## Done

Replaced (all credited in `CREDITS.md`; the recipe in `scripts/sound-recipes/` gives the source, its notes and how it
was cut, so it can be cut again):

| Event | Now | License | Recipe |
|---|---|---|---|
| `electricarc` | weaveofkev, "Welding_Electric Sparks" (the old was Sound Fishing's, whose licence forbids sharing the files on their own) | CC0 | `electricarc` |
| `weather.rain` | mlaramie, "even more rain" (the old was Wirsbo's Better Rain, no license) | CC0 | `rain_calm` |
| `weather.rain.medium` (new) | Ryding, "Medium rain", between the calm rain and vanilla's (`StormSounds.mediumShare`) | CC BY 4.0 | `rain_medium` |
| `player.heartbeat` | acertingart, "Heartbeat" (the old possibly SoundBible's personal-use-only one) | CC BY 4.0 | `heartbeat` |
| `wolf` | betchkal, a lone wolf in Alaska: reverb reduced, sped up 1.4x, pitch factory | CC0 | `wolf_lone` |
| `whale` | NOAA's humpback song (NOAA Fisheries' "Sounds in the ocean") | public domain | `whale_humpback` |
| `whale.right`, `whale.minke` (new) | NOAA's right whale (cleaned: 48 kHz copy, low-pass, denoise) and a rare minke (weight 3) | public domain | `whale_right_*`, `whale_minke` |
| `loon` | nicStage, "Loon / Diver call 3", six clips | CC BY 4.0 | `loon` |
| `seagulls` | Canardo55's two herring gulls (a motorboat left out), nigelcoop's trill, nine clips | CC0 | `seagulls_herring*`, `seagulls_trill` |
| `seagulls.flock` (new) | Soojay's flock, seven stretches, weight 10 to the lone calls' 4 | CC0 | `seagulls_flock` |
| `owl` | felix.blume's great horned owl duet, 11 hoot sequences, surf reduced | CC0 | `owl` |
| `nightingale` | reinsamba, "nightingale song 5", 12 phrases | CC BY 4.0 | `nightingale` |
| `weather.dust` | Vrymaa's dune sand, six slices (the old was a generated rumble) | CC0 | `dust` |
| `biome.forest` | bajko's spring forest, 129 s loop; the old loop (joepsporck) kept as `biome.forest.sparse` for sparse forests | CC0 | `forest` |
| `biome.forest.deep`, `biome.crickets.deep` | Vonora's evening woodland (airplane filtered out), Borgory's crickets (hum and a high insect's trill filtered out); the old ones came from an ambience-mixing site | CC0 | `deep_forest`, `deep_crickets` |
| `biome.forest.silent` | the same ZHRØ recording, recut: rumble and hiss reduced, 1.1 MB to 151 kB | CC0 | `silent_forest` |
| `biome.jungle` | CHRISFOPFILMS' Madagascar rainforest, from 20 s (a voice before) | CC0 | `jungle_madagascar` |
| `biome.jungle.sparse` (new) | nonamethefish's Peruvian Amazon, for Sparse Jungle and Floodplain | CC0 | `jungle_peru` |
| `raptor` | red-shouldered hawks: kvgarlic's (credited) and Vrymaa's two; the uncredited red-tailed hawk dropped (its replacement was hissy and short) | CC0 | `raptor_redshouldered` |
| `eagle` (new, split from `raptor`, equal weight) | Kinoton's bald eagles, four phrases, highs softened (-10 dB shelf above 2 kHz) | CC0 | `raptor_eagle` |
| `player.jump` | salemaudio's soft grunts, five, raised 3 semitones (pitch and formants) to sound neither man nor woman, at volume 0.28; factory narrowed to 0.9-1.1 | CC0 | `jump_salem` |
| `item.utility.equip` | dawith's "Zippo click sound" (OpenGameArt), softened (-12 dB shelf above 2.5 kHz), quieter, at factory volume 0.15; tried first: vanilla's equip rustles, then a generated double click (`click-synth.py`). The old `utility_draw` matched no vanilla click at any speed, nor the zippo | CC0 | `utility_zippo` |
| `waterfall.5` | InspectorJ's "Waterfall, Large, A" (the source of `waterfall.4`): its loop played three times through, `asetrate=44100*0.65`, soxr resample, `bass=g=14:f=150`, the middle copy kept, matched to the old loudness (-28.5 LUFS). The old one was orangefreesounds' CC BY-NC | CC BY 4.0 | none (ffmpeg, as here) |
| `waterfall.0` (removed) | played by no waterfall (strength is always 1 or more), only by the waterspout, which now plays `waterfall.1` at pitch 0.8-1.0; the `waterfalls/0` fallback points at `waterfall.1`. Its CC BY-NC credit gone | | |
| `biome.underground` + `biome.underground.deep` (new) | two drones the mod makes itself (`scripts/drone-synth.py`), blended by height around y 0 (the old was band-passed noise of unknown origin) | the mod's own | "match" and "deep" settings in the commit |

Credited without replacing: `bird`, `woodpecker`, `red_shouldered_hawk` (Freesound, matched by audio; confirmed 2026-11-10 against kvgarlic's original: r 0.89-0.90 in step, contour 0.82 at the same speed, a mono mix of 3.1-8.1 s), `item.tool.swing`,
`item.potion.equip`, `bees` (NPS, Yosemite, matched), `player.tummy` (SoundBible, Sampling Plus 1.0), the sword,
blunt, tool, shield and bow-draw items (made from Presence Footsteps' sounds, by the author's memory), and, as public
domain by the author's memory: `lilypad.frog`, `ice`, `soulsand.laughter`, `monstergrowl`, `pageflip`,
`pageflipheavy`, `newsprint`, `netherbrick.breathing`, `item.bow.pull`, `item.bow.loose` (and `.skeleton`).

Removed, as nothing played them: `dimension.nether` (its credit with it), `dimension.theend`, `insectcrawl` (and its
subtitle key in every language), `waterdrops`. `waterdrips` (InspectorJ, credited) also plays nowhere, but is kept for
later effects.

Rules changed with the sounds: forests split on `SPARSE_VEGETATION`, and silent forest, pine and the cold forest rules
made exclusive, so each forest biome plays one forest loop (checked against a `/dsdump biomes`); jungles split on
`SPARSE_VEGETATION || SWAMP`. Pitch factories (0.9-1.1, or 0.85-1.15 for the wolf) on wolf, whales, loon, seagulls,
flock, owl and nightingale.

## Re-encoding the streamed sounds (2026-11-10)

The 31 streamed loops were surveyed for how high their content reaches against how they were stored (`sound-cut.py`'s
report gives sizes). 16 were remade from their originals (all in the author's `New Sounds/originals`, with every
other download of the rework), matched to each old file first with `sound-match.py`, for 3.26 MB down to about 2.2 MB
with several loops longer:
- Thunder 1-6, cut again from their originals at 16-32 kHz: thunder1 swuing, 2 bone666138 "Thunder - 2", 3
  Yoyodaman234, 4 aaronstar, 5 sarson, 6 bone666138 "Thunder - 1" (each r 1.00, the same stretch).
- Desert wind, sparse forest, bog (87 s), savanna day (60 s, moved past low thumps): the old loops' tone had been
  reshaped (matched only within a band, r 0.80-0.95), so the originals were given it again with `sound-clean.py
  --match` before cutting. The desert wind is slugzilla's, not "nageor"'s: credit fixed.
- Underwater river and water: the originals untouched (r 0.99-1.00), recut.
- Wind, underwater ocean and deep ocean matched nothing (wind: not felix.blume's Atacama wind at any speed, nor in any
  band): rebuilt from their credited recordings (the wind toned to the old loop; ocean and deep ocean AlienXXX's,
  slowed to 0.85x and 0.70x and toned), so the credits are now true. `biome.ocean.deep.under` credits AlienXXX.
- Arctic wind: DBlover's howl (r 1.00), 40 s, kept stereo (`"channel": "stereo"`), bass -4 dB (`"bass"`) and the
  gusts' thuds high-passed; leveled by loudness (LUFS), as its average level was mostly sub-bass.
- Left as they were: the loops made in this rework (already 16-24 kHz), pine forest (re-encoding grew it), crickets
  and savanna night (content to 20 kHz), river, beach, ocean and plains (smaller only with audible loss).

## How sounds are made now

- `scripts/sound-cut.py` cuts clips from a source as a recipe says: mono, filtered, faded, brought to a level, encoded
  once. Clips are matched to the file they replace by the loudest 50 ms (`target_rms_peak`). Loops (`"loop": s`)
  crossfade the recording past their end into their start, and are matched by the whole clip (`target_rms`). For an
  ambience whose old file's level was mostly rumble, match perceived loudness instead (ffmpeg's `ebur128`, LUFS) and set
  `target_rms` from the difference. A bright call that hurts (2-5 kHz) takes `treble` [dB, Hz], a high shelf. `rate` and `quality` keep files small (24 kHz q2 for loops, 16 kHz where nothing is
  above 6-8 kHz); the report gives each file's size beside the old one.
- `scripts/sound-clean.py` before cutting: filters first (free where the noise is outside the sound's band), then
  denoise. The noise profile is the quarter-second average's 15th percentile; for noise that swells (surf), use
  `--noise-pct 70 --over 2`. Keep the floor gentle (-12 to -15 dB): deeper ones warble (heard on the owls). A high
  insect's steady trill or a low hum as loud as the sound wants a filter, not denoising.
- `scripts/drone-synth.py` makes a drone from noise, seamless and the mod's own.
- Longer clips and loops are welcome (less repetition), kept small.
- Measure, don't trust listening alone (the author has tinnitus): band levels over time, events over a running median,
  tonal peaks, a seam check (the loop played twice, its level around the join against the rest).
- Matching an old file to a candidate source: a true Pearson r on every sample, either polarity, three excerpts that
  must line up (`scripts/sound-match.py`; a control cut of a candidate scores 1.00). The early quick
  matchers read over 1 and inflated near-silence: their footstep matches were withdrawn. A quarter second of tone
  matches any tone at some speed, so tonal calls (howls, hoots) need a pitch-contour match (`sound-match.py --contour`); a pitched or slowed copy, `--speeds`.

## Provenance research (2026-11-09 and 10)

Searched for every uncredited sound: Freesound (the author's 81-entry download history, 2017-2024, and keyword leads,
against public previews), SoundBible (~1000 candidates), freesfx.co.uk (~300), CreativeMD's AmbientSounds, Environs, the
NPS Sound Gallery and Yellowstone library, NOAA's "Sounds in the ocean", and 11 of felix.blume's jungle recordings.
What was found:
- BetterRain (github.com/OreCruncher/BetterRain, December 2015, the mod's first repository) added most uncredited
  sounds in Dec 2015 - Feb 2016, with no source in its commits. Its calm rain was Wirsbo's (Better Rain, 1.7.10;
  "please include my name in the credits", permission for modpacks only): replaced rather than modified.
- The electric arc's MP3 carried an ID3 tag naming Sound Fishing (sound-fishing.net), whose licence forbids sharing the
  sounds on their own: replaced.
- The 2018 batch (the author's "DSurround Stuff - 06-04-2018.zip", sender unknown) held the originals of the deep
  forest, deep crickets, nightingale and loon files. The author recalls the deep pair came from an ambience-mixing site;
  such sites' terms vary (myNoise: no redistribution; Tabletop Audio: CC BY-NC-ND), so all four were replaced.
- Matched: `bird`, `woodpecker`, `item.tool.swing`, `item.potion.equip` (Freesound, CC0); `bees` (NPS); `player.tummy`
  (SoundBible); `red_shouldered_hawk` likely (kvgarlic, 0.80).
- The old dust and underground sounds were generated (filtered noise, no microphone hiss); the water drops were 8-9 ms
  clicks too short to trace.
- The author has no Freesound history before January 2017, so the 2015-16 sounds came from elsewhere.
