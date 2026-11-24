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
| `player.jump` | sblitzer's "Sharp Exhale", one unvoiced huff pushed out from the stomach (no voice in it), whole (0.4 s, ends within a jump's 0.6 s), at volume 0.28; one clip, the factory varying its pitch 1.0-1.2 (0.85-1.15 sounded too deep at its low end). Replaced salemaudio's soft grunts ("Soft Male Vocal, Short Pained/Exhertion Groans", CC0, +3 semitones, recipe `jump_salem` in git) on 2026-11-16, which the author didn't like. Tried first: scottemoil's "breathing_sounds.wav" (CC0), relaxed breaths cut short, raised 2 semitones: too vocal, and slow to their peak, so the 'hup' came late; TSP-Talk's push-up effort breathing and sblitzer's "Sharp Breathing" were offered too | CC0 | `jump_sblitzer` |
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

## Rain on surfaces: sources (2026-11-21, 5.3.0)

Rain on a surface (`RainSurface`) is filtered, which changes its tone but can't add a metal roof's ring or the patter of
drops on leaves: those need recordings of their own, layered under the rain. Searched Freesound (CC0; CC BY not needed,
CC0 had plenty) for rain on metal and tin roofs and rain on leaves; HQ previews measured with `sound-ambience.py`
(steady stretch, events, band balance). Offered to the author as loudness-matched 20 s excerpts:
- Metal: A Sassaby "Medium rain on a tin roof" (771981, 123 s, 85 s steady, no events); B kyles "rain light on tin roof
  soft" (451152, 47 s, steady throughout); C cocaine "Rain On Metal Roof" (611424, Burya library, swells from light to
  heavy and back, 30 s steady at its peak); D mdsn "rain water drops on metal roof" (322579, outside, 50 s steady, more
  low end).
- Leaves: A newlocknew "Drops On Grass, Bushes, Ferns And Leaves" (865323, 162 s steady, brightest; a faint distant
  highway in its description, below the drops); B Garuda1982 "rain in the forest" (536843, 603 s, steady throughout,
  close drops); C nyoz "Tropical Rain - light in a forest with drop hitting the leaves" (615747, 62 s); D yaya-dc "Pluie
  calme sur feuilles" (667775, 63 s, 40 s steady).
- Ruled out: madamdata's park leaves (719747, mostly low rumble), Borgory's soft forest rain (678435, a clipped burst and
  low thumps), Malte007 (rain starting, not steady), GregorQuendel's metal roof (239939, low thumps), MrFossy's shed
  (521773, noisy), car roofs (a car's cabin), and anything with thunder, voices, birds or traffic in its description.
- Chosen: metal A (Sassaby, recipe `rain_metal`) and leaves B (Garuda1982, recipe `rain_leaves`), each four 2 s swells
  like the calm rain's (`weather.rain.metal`, `weather.rain.leaves`), played unfiltered and at pitch 1 under the filtered
  rain where a rain sound lands on that surface, at 0.8 of the calm rain's share; lost in the wash on the bench (2026-11-21), so a layer now takes 0.7 of the
  rain's volume where it plays, after the surface's, and the rain keeps 0.3 (`StormSounds.layerShare`, `keptShare`). One leaf
  stretch (94.3 s) was passed over for a sharper drop.
- Glass and water searched next (2026-11-21), previews measured the same way. Glass offered: A TRP skylight 2012
  (616530, CC0, 89 s, steady throughout), B carthny conservatory roof (522322, CC0, uploaded as MP3), C Vrymaa skylight
  (810879, CC0, its first minute, mic toward the glass; the second is muffled), D AmiableWookie skylight (651676, CC0,
  quiet), E mmorgaine light rain on a skylight (333510, CC0, the brightest). All are from under the glass: rain on a
  window from outside is hardly recorded. Water offered: A YleArkisto light rain on a lake (248282, CC BY 4.0, 213 s
  steady, bright plinks), B TRP rain on a lake (572429, CC0, steady 255-345 s), C Garuda1982 small forest lake (636272,
  CC BY 4.0), D Mjeno rain on a pond (399281, CC0, 38 s), E klangfabrik rain into a puddle (157161, CC0, many drops).
  Ruled out: RyanKingArt's lake (713355, mostly low rumble), AppleCorey's skylight (689084, a large room's reverb, which
  the mod adds itself), TRP's 2011 skylight (577305, dull), xkeril's window (669486, unsteady), JKata's lake shore
  (626171, quiet and hissy), bigfriendlyjiant's lake (182684, unsteady, thumps), and those with thunder, traffic, birds or
  frogs in their descriptions.
- Chosen: glass A (TRP, recipe `rain_glass`, high-passed at 200 Hz for the drumming under the skylight) and water A
  (YleArkisto, recipe `rain_water`, CC BY 4.0: credited in `CREDITS.md` and the event's `ds_credits`), each four 2 s
  swells (`weather.rain.glass`, `weather.rain.water`), played as metal's and leaves' are. Water's spike limit is 1 dB
  looser than leaves' (9.5 dB), as its plinks stand further out of the rain; true peaks within 2.3 dB in each set.

## Seasons pack: Four Seasons recording replaced (2026-11-23)

The Seasons resource pack (`packs/Dynamic Surroundings - Seasons`, music for Serene Seasons' sub-seasons, one movement
each) played Jerzy Kulik's Four Seasons from archive.org (`ETitleA.Vivaldi-TheFourSeasons`, 2014). That item carries no
license: no `licenseurl` or rights field, only the Community Audio collection (formerly `opensource_audio`, which the
pack's credit took for a license). It was rendered with IK Multimedia's Miroslav Philharmonik, whose EULA allows music
made with it, so the gap was the uploader's missing grant, not IK's terms.
- Before that, MIDI was tried as a way to ship the packs' music in the jar (`docs/specifications/midi-playback-design-spec.md`,
  parked): Basic Pitch transcriptions of two Extended tracks were "not even close", and Mutopia's score MIDIs of the
  concertos (CC BY-SA 3.0) through a General MIDI SoundFont lacked the recordings' richness.
- Searched Wikimedia Commons (`Category:The Four Seasons (Vivaldi)`) and Musopen. Two complete sets: John Harrison with
  the Wichita State University Chamber Players (CC BY-SA 1.0 or later, permission ticket 2007022510020031, a featured
  sound on English Wikipedia) and The Modena Chamber Orchestra (via Musopen, public domain mark by its uploader; ruled
  out: Musopen's listing reportedly hidden over a copyright problem, named soloists suggesting a commercial release,
  and the Commons copies' pitch moved 20 cents). The US Air Force Band's (public domain) is Winter alone.
- Chosen by ear: Harrison, live 2000-02-06. Each movement cut where its music ends: the last step (0.1 s) both louder
  than -20 dB against the movement's median and tonal (spectral flatness under 0.012; claps pass the level alone),
  followed while the reverb stays tonal and over -32 dB; starts at the first step over -15 dB, less 0.15 s. Cuts in the
  source, seconds: spring1 0.55-213.1, spring2 1.95-170.4, spring3 2.15-241.9, summer1 0.55-299.7, summer2
  1.35-105.15 (by hand, 1.25 s before the found end, where room noise under the held note began), summer3 0.95-156.2,
  autumn1 0.45-300.2, autumn2 0.35-151.8, autumn3 1.75-198.2, winter1 0.45-206.8, winter2 1.55-129.5, winter3
  1.25-192.3. Faded in 0.08 s and out over the last 1.0 s.
- Leveled by integrated loudness (ffmpeg `loudnorm` analysis): each movement to its old file's loudness less 6 dB, with
  `sounds.json`'s volume raised from 0.5 to 1.0, so each plays as loud as the one it replaced (the old files were
  mastered at about -14.5 LUFS with peaks to +2.3 dBTP; the live recording averages -23 LUFS with peaks at full scale).
  That brings Autumn II up 12.9 dB, from nearly inaudible. A limiter at -2 dBFS (5 ms attack, 80 ms release) takes
  peaks only: 3.5 dB off Spring I's loudest, under 1 dB elsewhere. Encoded stereo 44.1 kHz Vorbis q0: 16 MB for the 12
  (the old set 17 MB); a q2 set (25 MB) was made for comparison. True peaks -0.9 to -6.2 dBTP.
- Credited in the pack's `CREDITS.md` and each event's `ds_credits` (the movement's Commons page). The pack's versions
  stay CC BY-SA 4.0.

## Extended pack: Moonlight Sonata replaced (2026-11-23)
- The old file came from orangefreesounds.com (posted 2015 by "alexander"). The page names no performer ("Artist: /")
  and says only "permitted for commercial use under license open source audio", a link to archive.org's Community
  Audio collection, which is not a license; the site has no terms page. `sound-match.py` (also `--contour --speeds`)
  found it wasn't cut from piano-midi.de's CC BY-SA rendering on Commons nor from Musopen's recording, so its origin
  is unknown. The pack's `CREDITS.md` linked it to Chosic's page 27877, which is Anna's Theme's.
- Commons candidates for the first movement: Paul Pitman for Musopen (public domain, 5:36), Dorian Péquet
  (CC BY-SA 4.0, 6:31), Jfarjona (CC BY 2.5, 7:53). Pitman's chosen: a real pianist and no conditions.
- Cut 4.15 s (the end of 4.2 s of digital silence) to 335.7 s (the file's end, where the last chord still rings at
  -20 dB under the piece's median, so a 3 s fade-out instead of 1 s); fade-in 0.08 s; +7.3 dB to the old file's
  loudness (-25.2 LUFS against -25.1; the event's volume stays 1.0); the same limiter at -2 dBFS, which touched
  nothing (-6.5 dBTP); stereo 44.1 kHz Vorbis q0, 2.1 MB.
- Both packs' `CREDITS.md` files were rewritten from HTML lists to Markdown at the same time.
- Pachelbel's "Cannon in D Major" renamed Canon at the same time: its title, credit, file (`canon_in_dmajor.ogg`) and
  event (`dsurround_ex:music.canon_in_dmajor`), with the factory and biome rule that name it.

## Extended pack: Canon in D replaced (2026-11-23)
- The old file sounded muted in game: about the pack's average loudness (-21.1 LUFS at volume 1.0) but with a wide
  range (10.7 LU against 3-5 for the others), so its soft passages fell far under the rest. It couldn't be brought up:
  its credit was CC BY-ND 3.0 (no derivatives), the event's volume was already 1.0, and the archive.org item it
  credited (PachelbelCanonInDMajor) is dark (taken down), with no Wayback copy, so neither the original nor its
  license can be checked. `sound-match.py` matched it to none of the candidates below.
- Commons candidates: the US Air Force Band's Strolling Strings (public domain, a US government work, 3:49), Kevin
  MacLeod (CC BY 3.0, a production), Michael Fischer v. Mollard (CC BY 4.0, which the user heard as generated),
  Jeffrey C. Hall's Canon and Gigue (CC BY-SA 3.0, synthesizers). The Air Force recording was chosen.
- Cut 0.45 s (the end of the leading silence) to 225.0 s (its reverb gone under -60 dB; 4 s of silence after);
  fade-in 0.08 s, fade-out 1 s; -2.2 dB to -18 LUFS (3 dB over the old file, its range 4.5 LU); the -2 dBFS limiter
  touched nothing (-5.8 dBTP); stereo 44.1 kHz Vorbis q0, 1.6 MB.
- First installed silent: ffmpeg carried the MP3's cover art into the OGG as a Theora video stream ahead of the audio,
  and the game read that stream, so the track "played" (the diagnostics showed it) with nothing heard and nothing
  logged. Remuxed with only the audio (`-map 0:a -c:a copy`, no re-encode). Encode from a source with cover art with
  `-vn` (or `-map 0:a`), and check a new OGG with `ffprobe` shows one stream, `vorbis`.
