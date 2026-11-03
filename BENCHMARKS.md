# Benchmarks

What Dynamic Surroundings' effects cost to draw, and what enhanced sound processing costs, measured by the bench
runner, with the numbers the Performance presets (Low/Medium/High) were chosen from. Kept as a baseline: after a change to an effect, rerun its scenario and compare.

## Method

The bench runner (`scripts/bench/README.md`) is a development client that plays a scenario by itself: for each run it
applies some settings, goes to a scene in a fixed world (seed 20261030), waits for the terrain and a warm-up, takes a
screenshot and records a `/dsprofile`. Every scenario runs its list twice, the second time reversed (A B C, C B A), so
a drift during the session (heat, the world settling) falls on every label alike.

    ./gradlew :fabric:runClientBench -Pscenario=scripts/bench/<scenario>.json
    python scripts/bench-summary.py <results folder> --spread

- **Frame GPU ms** is the GPU time to draw a frame (the world and the screen over it), mean over the recording; the
  range after it is the lowest and highest of the two repeats. It is the number to compare: with vsync off and no
  frame limit, fps mostly follows the CPU, and our effects mostly cost GPU.
- **Timer ms** are our render timers (`dsurround.RenderTimer`), GPU/CPU a frame. A `*` timer runs on the tick; its time
  is a tick's.
- Differences under about 0.05 ms of frame GPU are within the noise between repeats.

### Machine and versions

| | |
|---|---|
| CPU | AMD Ryzen 5 3600 (6 cores, 12 threads) |
| Memory | 64 GB |
| GPU | AMD Radeon RX 7600, driver 32.0.32015.7029, OpenGL |
| OS | Windows 11 Home 10.0.26300 |
| Java | 25.0.4.1 (Eclipse Adoptium) |
| Game | Minecraft 1.21.11, Fabric Loader 0.19.5, Fabric API 0.141.6+1.21.11, Architectury 19.0.1 |
| Mod | 5.0.0, commit `aed3d02f` on `OreCruncher/1.21.11` |
| Video | 1920x1080, Fancy graphics, render distance 16, vsync off, no frame limit (260) |

Recorded 2026-11-02.

## Results

### Ground fog (`swamp-fog.json`)

A swamp at dawn, standing on its floor in the mist.

| Run | Frame GPU ms | Ground Fog GPU | March | Smooth | Composite |
|---|---|---|---|---|---|
| off | 1.203 (1.195-1.211) | - | - | - | - |
| Fast | 1.290 (1.280-1.301) | 0.288 | 0.112 | 0.042 | 0.105 |
| Fancy | 1.500 (1.491-1.508) | 0.486 | 0.280 | 0.071 | 0.106 |
| Fabulous | 2.546 (2.533-2.560) | 1.522 | 1.118 | 0.252 | 0.121 |

The fog's own CPU time is 0.02 ms in each case; Ground Fog Handler takes 0.04 ms a tick.

At 2560x1369, the size of the window the ATM10 recordings were made in, on the same floor (a one-off scenario, not
committed): Fancy 0.83 ms of fog GPU, Fabulous 2.64 ms (march 1.94).

**Why High uses Fancy:** Fabulous costs about three times as much as Fancy for a difference mostly at the mist's
edges.

**Where you stand matters:** an earlier version of this scene stood on a treetop above the mist and measured Fabulous
at 0.86 ms at 2560x1369, a third of what it costs inside the mist. A fog scene should stand in the fog.

### Rain and snow (`precipitation.json`)

A cleared, floored stage with a wall of clear glass ahead and the wind behind the player, in a full storm. Low is
density 50 and range 10, Medium is the default 100 and 14, and High is 100 and 20.

| Run | Frame GPU ms | Precipitation GPU/CPU | Drops GPU/CPU | Waiting for Quads CPU | Precipitation Handler ms/tick |
|---|---|---|---|---|---|
| rain Low | 1.093 (1.072-1.114) | 0.015/0.041 | 0.009/0.021 | 0.001 | 0.105 |
| rain Medium | 1.134 (1.124-1.143) | 0.029/0.085 | 0.022/0.052 | 0.003 | 0.133 |
| rain High | 1.177 (1.170-1.184) | 0.040/0.320 | 0.033/0.096 | 0.171 | 0.246 |
| snow Low | 1.449 (1.444-1.453) | 0.014/0.040 | 0.014/0.029 | 0.001 | 0.090 |
| snow Medium | 1.503 (1.493-1.513) | 0.022/0.088 | 0.022/0.077 | 0.001 | 0.165 |
| snow High | 1.598 (1.594-1.602) | 0.038/0.215 | 0.038/0.152 | 0.050 | 0.283 |

Precipitation's GPU cost is small at every setting. What grows with range is CPU: the drops' step (the handler's tick)
and, at High, waiting for the quads' thread (`DropMeshes.WAIT_NANOS` bounds the wait at 2 ms a frame). Snow scenes
also draw window frost (about 0.10 ms GPU), which no precipitation setting changes.

### Waterfalls and flame jets, crowd, dust range (`performance-rest.json`)

**Waterfalls and flame jets** are built on a stage by the scene's commands. These five runs were rerun on their own
after `waterfall-default` was made to set the block range to 40 itself (the bench folder's config had it at 32).

| Run | Frame GPU ms | Frame CPU ms | Point Lights GPU | Area Block Effects ms/tick |
|---|---|---|---|---|
| default (block range 40) | 0.921 (0.882-0.960) | 1.124 | 0.106 | 0.267 |
| block range 32 | 0.930 (0.900-0.961) | 1.212 | 0.121 | 0.246 |
| block range 64 | 0.955 (0.947-0.963) | 1.216 | 0.045 | 0.448 |
| no flame or firefly lights | 0.940 (0.939-0.942) | 1.203 | 0 | 0.246 |
| no mist or froth | 0.814 (0.808-0.820) | 1.016 | 0.148 | 0.221 |

Mist and froth are the costliest part: about 0.11 ms of GPU and 0.1-0.2 ms of CPU a frame. The block range changes
little on the GPU; 64 costs about 0.2 ms a tick more in Area Block Effects than 32 or 40. Point Lights varies from
run to run with how many flame jets are burning, so its column says little about the settings.

The same scene and camera in the full `performance-rest` session an hour earlier measured every label about 0.25 ms
of GPU and 0.4 ms of CPU cheaper (default 0.684, no mist 0.639, the others 0.675-0.679), with mist and froth saving
0.04 ms. Compare labels within one session, not across sessions.

**Foam rotation once a tick** (`988aa231`): each foam patch had worked out its rotation from its slope and spin every
frame; it now does so once a tick and blends the last two each frame. The waterfall scene at its defaults, the old and
the new build back to back, render thread CPU samples in two recordings each:

| Build | fps | `WaterFoam` samples | of which its rotation |
|---|---|---|---|
| before | 870 | 199, 193 | 92, 99 |
| after | 880 | 90, 76 | 10, 12 |

The foam's share of the render thread fell from about 12% to about 5.5%. The frame rate doesn't follow in a scene this
small, but by a large waterfall the saving is render thread time freed (see Field recordings).

**Crowd** (entity effect range):

| Run | Frame GPU ms | EntityEffect Handler ms/tick |
|---|---|---|
| range 16 | 1.060 (1.046-1.074) | 0.026 |
| range 32 | 1.060 (1.045-1.075) | 0.030 |
| range 48 | 1.068 (1.046-1.090) | 0.032 |

The entity effect range makes no measurable difference here.

**Dust storm** (precipitation range):

| Run | Frame GPU ms | Drops GPU/CPU | Precipitation Handler ms/tick |
|---|---|---|---|
| default (range 14) | 1.430 (1.329-1.532) | 0.678/0.027 | 0.155 |
| range 20 (High) | 1.343 (1.326-1.360) | 0.612/0.034 | 0.235 |

The dust's GPU cost doesn't grow with range: the far puffs are few and small on screen. The default's repeats are
0.2 ms apart, so the two are within the noise.

### Burning mobs (`burning-lights.json`)

Zombies kept burning all night in plains.

| Run | Frame GPU ms | Point Lights GPU | EntityEffect Handler ms/tick |
|---|---|---|---|
| default | 1.113 (1.104-1.122) | 0.075 | 0.068 |
| no light | 1.082 (1.080-1.083) | 0 | 0.067 |
| no flames | 1.047 (1.046-1.049) | 0 | 0.028 |

The scene is viewed from a distance. In ATM10, standing right beside a pen of burning zombies, the lights took about
1.05 ms of GPU (see Field recordings). Each light is drawn over every pixel it may reach, so the cost depends on how
close the camera is.

### Dust storm, default against Low (`dust-low.json`)

| Run | Frame GPU ms | Drops GPU/CPU | Cloud Handler ms/tick |
|---|---|---|---|
| default | 1.398 (first repeat) | 0.573/0.024 | 0.371 |
| Low (density 50, range 10, no dynamic clouds) | 1.258 (1.253-1.263) | 0.408/0.018 | 0.002 |

The default's second repeat had a 2-second hitch (its frame GPU mean read 2.28 ms), so only the first is shown. Low
saves about 0.2 ms of dust GPU and the clouds' 0.37 ms a tick.

### Enhanced sounds (`sound.json`)

Button clicks played around the player 20 times a second and 8 beacon hums kept playing, at y 245, out of the reverb
rays' reach of the ground: in open air (sounds 1.5-6 blocks away), inside a hollow stone box 13 blocks across (1.5-5
blocks away), and in the box with the sounds outside it (13-16 blocks away). The mod's ambient sounds are off. Measured
at the commit that added this scenario (after `787ccf11`); master volume 5%.

- **Trace** is the rays of one calculation that traced (occlusion and reverb), mean.
- **Start** is a sound's calculation and upload as it starts. It runs on the sound engine's thread, which waits for
  it. **Starts ms/s** is that thread's time on it each second.
- **Update ms/s** is the worker threads' time on the recalculations, each sound about three times a second.
- Calculations a second: **traced**; **reused**, the sound's own last result while nothing changed. Almost none took
  another sound's result (shared), so that column is left out: the clicks rarely land on the same block.

| Run | Trace ms | Start ms | Starts ms/s | Update ms/s | Traced/s | Reused/s |
|---|---|---|---|---|---|---|
| open | 0.528 | 0.520 | 10.5 | 3.8 | 26.2 | 45.4 |
| open, occlusion on | 0.560 | 0.555 | 11.1 | 3.8 | 25.9 | 45.1 |
| enclosed | 0.508 | 0.482 | 9.8 | 3.6 | 25.5 | 45.9 |
| enclosed, simplified shared airspace | 0.366 | 0.349 | 7.1 | 2.9 | 26.2 | 45.0 |
| occluded | 0.597 | 0.589 | 11.9 | 3.5 | 25.3 | 38.0 |
| occluded, occlusion on | 0.615 | 0.610 | 12.3 | 3.1 | 24.2 | 39.0 |

A trace costs about 0.5-0.6 ms wherever it is. Open air costs as much as the box: every ray travels its full 128
blocks. Simplified shared airspace makes a trace about 28% cheaper, and occlusion adds 3-4%. The cost that matters is
the starts: at 20 new sounds a second, 10-12 ms of every second on the sound engine's thread. The updates cost less
because most reuse their result. Frame times don't change between runs (about 1500 fps throughout): none of this is on
the render thread.

Since then, two changes (same scenario, later session):

- **Chunk kept in hand between block reads** (`ChunkCachingView`): a trace 23-28% cheaper. Open 0.405 ms, enclosed
  0.391, occluded 0.429; the sound engine's thread spends 7.5-8.5 ms a second on starts.
- **Start budget** (`StartBudget`, 2 ms of start calculations every 50 ms): the `burst` scene starts 150 clicks a
  second in the box. 96 a second are calculated as they start and 54 (37%) start without effects, calculated on the
  workers' next pass; the sound engine's thread spends 30.6 ms a second on starts instead of about 48. At 20 a second
  (`enclosed`) none are deferred.

About 17 calculations a second are skipped in every scene. Six minecarts in a mineshaft about 240 blocks below play
silent riding loops, which are skipped as out of range. The occluded scene skips about 9 more: hums 15-16 blocks
away, just past their range.

## Results, 2026-11-03: lightning, clouds, aurora, NeoForge

Same machine and settings, commit `dd14609e` (no change to what is drawn since `988aa231`), NeoForge 21.11.48 beside
Fabric. Three new scenarios: `lightning.json` and `clouds.json` on a beach (open, flat, and it rains there), and
`aurora.json` facing north (auroras lie north, within 30 degrees). A repeat with a 2.5-second stall (see Rerunning)
is left out where it moved the frame by more than the noise; such rows give the clean repeat alone.

### Lightning (`lightning.json`)

A held thunderstorm at night, 90-second recordings (sky strikes come by chance, about 0.3 a second), with dynamic
clouds (strikes light them from inside) and without (the glow drawn as patches).

| Run | Fabric frame GPU ms | NeoForge frame GPU ms | Lightning GPU peak | Lightning CPU |
|---|---|---|---|---|
| on | 1.472 (1.459-1.484) | 1.543 (1.539-1.548) | 0.003-0.014 | 0.02-0.07 |
| off | 1.462 (1.458-1.467) | 1.528 (1.518-1.539) | - | - |
| no clouds, on | 1.438 (1.435-1.442) | 1.480 (1.461-1.499) | 0.014 | 0.05-0.09 |
| no clouds, off | 1.448 (1.445-1.450) | 1.486 (1.468-1.503) | - | - |

Lightning makes no difference to the frame beyond the noise, with or without the clouds: the highest any frame's
lightning took was 0.014 ms of GPU. Its CPU depends on how many strikes a run gets. One NeoForge recording read 0.34 ms;
it was made while the previous scenario's recordings were being summarized on the same machine. The camera looks a
little down at the sea, so most sky strikes are out of view; a bolt filling the screen would cost more.

### Dynamic clouds (`clouds.json`)

Dynamic clouds against vanilla's: fair weather in the afternoon, a held storm (its lowered deck), and `/dsprofile`'s
clouds scene (a storm with the sky half covered by tall, broken clouds). Lightning off. Vanilla's clouds have no timer,
so the frame's difference is ours less theirs.

| Run | Fabric frame GPU ms | NeoForge frame GPU ms | Clouds GPU | Cloud Handler ms/tick |
|---|---|---|---|---|
| fair, dynamic | 1.205 (1.140-1.270) | 1.196 | 0.022 | 0.33-0.40 |
| fair, vanilla | 1.188 | 1.212 | - | 0.002 |
| storm, dynamic | 1.430 | 1.504 (one repeat) | 0.107-0.110 | 0.30-0.35 |
| storm, vanilla | 1.364 | 1.405 | - | 0.002 |
| tall clouds, dynamic | 1.407 (one repeat) | 1.462 | 0.117-0.122 | 0.29-0.34 |
| tall clouds, vanilla | 1.361 | 1.407 | - | 0.002 |

Drawing ours costs at most about 0.1 ms of GPU more than vanilla's, in the storm and the tall clouds. Their cost is on
the tick: working out the field (`- Field *`) is about 0.3 ms a tick in every weather, about half of all the handlers'
time in these scenes.

### Aurora, classic (`aurora.json`)

A clear winter night facing north, every night with an aurora (chance 100).

| Run | Aurora GPU, Fabric / NeoForge | Screen covered | Aurora CPU |
|---|---|---|---|
| default (up to 3 bands; 2 that night) | 0.030 / 0.026 | 28-29% | 0.14-0.18 |
| one band | 0.020 / 0.017 | 16-17% | 0.07-0.10 |

The aurora costs about 0.05 ms of GPU per million pixels it covers. Its CPU in a recording includes the recording's own
coverage count (`AuroraCoverage`, run only while recording, inside the timer), so players pay less than that. The two
platforms' scenes stood in different places (Fabric's bench world remembered the scene from when it was on snowy
plains), so only the aurora's own timer compares between them.

### NeoForge against Fabric (`precipitation.json`, `swamp-fog.json`)

Fabric's precipitation was run again the same day to compare with:

| Run | Fabric frame GPU ms | NeoForge frame GPU ms | Precipitation GPU/CPU, Fabric / NeoForge |
|---|---|---|---|
| rain Low | 1.284 | 1.342 | 0.016/0.060 / 0.019/0.053 |
| rain Medium | 1.355 | 1.345 | 0.033/0.111 / 0.035/0.091 |
| rain High | 1.453 | 1.427 | 0.050/0.209 / 0.052/0.168 |
| snow Low | 1.673 | 1.678 | 0.015/0.057 / 0.015/0.050 |
| snow Medium | 1.769 | 1.719 | 0.024/0.114 / 0.022/0.098 |
| snow High | 1.845 | 1.772 | 0.040/0.190 / 0.040/0.171 |

| Ground fog | Fabric fog GPU (2026-11-02) | NeoForge fog GPU |
|---|---|---|
| Fast | 0.288 | 0.348 |
| Fancy | 0.486 | 0.522 |
| Fabulous | 1.522 | 1.500 |

The two platforms draw our effects at the same cost, and the whole frame within the noise. Fabric's frames read about
0.2 ms higher than the day before in every precipitation scene: drift between sessions, not the platform.

## Field recordings

These are `/dsprofile` recordings made in play on the same machine, mostly from the ATM10 modpack (NeoForge, 0.4.9 on
Minecraft 1.21.1) at 2560x1369. They are not repeatable like the bench, but show how a large pack or a big scene
changes the picture.

- **Ground fog, swamp:** Fabulous 4.0 ms of fog GPU. This led to the floor-level bench scene and to High using Fancy.
- **Rain, plains:** before 0.4.9, some frames waited 10-30 ms for the quads' thread, which the busy pack starved.
  After the bounded wait, the wait peaks at about 2 ms.
- **Burning mobs, beside a pen of zombies:** Point Lights went from about 0.18 to 1.05 ms of GPU, half the frame's GPU.
  This is parked until players report it.
- **Waterfall foam, beside a large waterfall** (dev client, Fabric 0.4.9, 120 fps limit; 60 seconds before and after
  the foam's rotation was worked out once a tick): the rotation went from 6.1% of the render thread to 0.4%, and the
  foam's whole share from about 19% to 13%; the rest is writing its vertices. Both held the 120 fps limit. Not the same
  amount of foam in each, so the figures are close, not exact.
- **Waterfall foam in ATM10** (0.4.9 with the foam's rotation once a tick, 120 fps limit, block effect range 64):
  foam took 3.7% of the render thread (its rotation 0.4%) and the mist puffs 1.8%; the frame was held by the pack's
  other mods and its own server, at 95 fps.
- **The first recording after the client starts:** Java Flight Recorder's first start slowed the game for about 8
  seconds, with frames up to a second. Since 0.4.9 the first recording waits 10 seconds longer, with a throwaway
  recording running.

## Rerunning

- Run every scenario from a clean tree, the machine otherwise idle, and note the commit. The first six scenarios take
  about an hour and a quarter, lightning, clouds and aurora about 45 minutes more; `compat.json` is a visual check, not a measurement. `sound.json` needs a sound device
  (each run's manifest says `enhancedSounds: true`) and plays its clicks aloud, quietly. Run it first in a long set:
  a device that sleeps when the machine is idle (wireless headphones) can be gone by the end.
- Idle means idle: summarizing one scenario's recordings while the next records showed in that run's CPU timers.
- The first run of a session reads high (its world settling: longer GC pauses, slower frames); in an A B C, C B A set
  it lands on the first label. Compare that label's two repeats before trusting it.
- About one recording in twenty here has a stall of 2-2.8 seconds: every thread waits on the render thread, which has
  no Java samples through it (stuck in native code: the driver or the system, not ours). It isn't tied to an effect
  or a platform (vanilla clouds had one) and the logs say nothing. One such frame can lift a recording's frame GPU mean
  by up to 0.9 ms: check the longest frame (`jfr-summary.py`) of any repeat that disagrees with its twin.
- Check each scenario's screenshots before trusting its numbers: the camera must be where the scenario means it to be
  (in the mist, facing the glass).
- Read `--spread`: when a label's repeats are further apart than the differences between labels, look for a hitch in
  that run (`python scripts/jfr-summary.py <zip>`: the longest frame, GC) and rerun it rather than averaging it in.
- Have each run set what it compares explicitly, the default included: the bench folder's config
  (`fabric/run-bench/config/dsurround/dsurround.json`) is not guaranteed to be at the defaults.
- Absolute numbers drift between sessions (the waterfall scene by about a third of its frame time in one day), so
  compare labels from the same session. To compare a change, run the old and the new build back to back.
- Add a dated section for a new baseline rather than overwriting this one, so the history stays comparable. Update the
  machine table if the hardware or driver changed.
