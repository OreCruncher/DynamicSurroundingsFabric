# Bench scenarios

A development client that runs a scenario by itself and closes: it opens (or makes) a world, and for each run applies
some settings, goes to a scene, waits for the terrain and a warm-up, takes a screenshot and records a `/dsprofile`.
The code is `common/.../bench` (`ScenarioRunner`), started only in development runs and left out of the published jars
(the platform builds fail if it gets in).

    ./gradlew :fabric:runClientBench -Pscenario=scripts/bench/dust-low.json
    ./gradlew :fabric:runClientBenchPotatoCpu -Pscenario=...     (4 CPUs, 2 GB, pinned, as runClientPotatoCpu)
    ./gradlew :fabric:runClientBench -PgraphicsBackend=vulkan -Pscenario=...     (a launch argument: the option is reset after a failed start)

NeoForge has the same tasks. The runs use their own folder, `<platform>/run-bench` (config, options, worlds), so your
dev settings and worlds are never touched. Results go to `<platform>/run-bench/bench-results/<scenario>-<time>/`:
the recordings (`01-default-dsurround-weather-....zip`), `screenshots/`, and `manifest.json`, which says what each run
was (settings, where the scene was and how it was found), and `"finished": true` once everything ran (or `"error"`).
`bench-results/latest.txt` names the newest folder. `python scripts/bench-summary.py [folder]` compares the runs (each
label averaged over the repeats; `--spread` adds the lowest and highest); `python scripts/jfr-summary.py` looks into one.

Without `-Pscenario`, `runClientBench` is a plain client in the bench folder, for marking scenes.

## Format

```jsonc
{
  "world": "bench",            // folder in saves; made if missing (creative, cheats on)
  "seed": 20261030,            // seed to make it with
  "setup": ["gamerule advance_time false"],   // commands once the world is open
  "warmup": 15,                // seconds after the terrain is drawn, before recording
  "record": 60,                // seconds recorded a run
  "repeats": 2,                // the runs this many times, every other time reversed (A B, B A)
  "video": {"width": 1920, "height": 1080, "vsync": false, "maxFps": 260,    // each optional; 260 is no limit
            "improvedTransparency": true},                                  // stays set: say false in the next scenario
  "screenshots": true,         // one a run, as recording starts, without the HUD
  "scenes": {
    "swamp": {
      "biome": "minecraft:swamp",   // found if the world doesn't remember the scene
      "yaw": 90, "pitch": 5,        // optional; otherwise toward the most of the biome, a little down
      "commands": ["time set 23500"],
      "profile": "ground_fog"       // /dsprofile scene: general, ground_fog, weather, clouds, frost
    }
  },
  "runs": [
    {"label": "fancy", "scene": "swamp", "settings": {"weatherOptions.groundFogQuality": "FANCY"}}
  ]
}
```

- **Setting sets**: `"settingSets": {"off": {...}}` names a group of settings once; a run applies it with
  `"use": "off"` (or a list, in order), then its own `settings` on top (`compat.json` turns every effect off this way).
- **Settings** are dotted config paths, as presets use. Before each run, every path any run sets is put back to what it
  was when the session started, so runs don't leak into each other, and again at the end; settings needing a restart are
  refused up front. A session killed partway leaves `bench-results/settings-to-restore.json`, and the next one puts those
  back first.
- **Commands** are typed as the player would: the server's (`time`, `weather`, `gamerule`, `summon`) run on the
  built-in server with full permission, as the player and where they stand; the mod's (`dsweather ...`) on the client.
  1.21.11's gamerules are snake case (`advance_time`, `advance_weather`, `spawn_mobs`). A server command that fails
  (a fill above the build limit) is logged with what it said and listed in the manifest's `failedCommands`, unless all
  it said was that it had nothing to change (a fill that builds a scene, on the scene's next run). A scene found where another scene built stands on what that one left (the finder reads the height
  map): build in the air from an absolute height (`tp @s ~ 245 ~`), as `sound.json` does.
- **Settled commands**: a scene's `"settledCommands"` run as the warm-up starts, as `commands` are, at least 3 seconds
  after them: a stage cleared under a cliff drops gravel and sand onto itself, so `compat.json` clears again here, then
  starts its water.
- **Shot commands**: a scene's `"shotCommands"` run just before its screenshot, as `commands` are, and `"shotDelay"`
  is how many ticks the shot then waits for what they start to show (2 at least): `["dsweather lightning"]` with a delay
  of 6 to 10 catches a strike in the shot.
- **Sounds**: a scene's `"sounds"` play around where the player's eyes are as the warm-up starts (after the commands
  have moved them), through the warm-up and the recording, for enhanced sound processing to work on (`sound.json`):
  `{"id": "minecraft:block.stone_button.click_on", "perSecond": 20, "minRadius": 1.5, "radius": 6, "seed": 1}` plays
  one-shots at that rate, and `"count": 8` instead keeps that many loops playing (a sound made to loop, such as
  `minecraft:block.beacon.ambient`), at places evenly through the shell from `minRadius` to `radius` (default 0 to 8);
  `volume` and `pitch` default to 1. Places and variants come from `seed`, so every run of a scene hears the same. A
  sound of volume 1 is heard out to 16 blocks, and one whose volume works out to 0 isn't played at all: the scenario's
  `"volume"` sets the master volume for the session (0.05 is quiet). Each run's manifest says whether processing ran
  (`enhancedSounds`: not without a sound device, or with it off), `soundsPlayed`, and `soundsMuted` if the blocks'
  volume is 0. Enhanced sounds on or off, and the reverb rays and bounces, need a restart, so can't be compared in one
  scenario.

## Scenes

A scene with a `biome` is found the first time: the nearest of the biome from 0,0 (as `/locate biome`), often a sliver
at its edge; then the biome is sampled on the ground every 64 blocks out to 2048 from there for where the most of it is,
and every 16 blocks around that for the place with the most of the ground 64 blocks around being that biome
(`biomeShare` in the manifest), on dry ground of it, the height read from the generated chunk (never inside a
mountain), looking toward the most of it. It is remembered in the world's folder, `dsurround-bench.json`, with the world's seed
and a fingerprint of the mods; a remembered scene is checked to still be in its biome (worldgen mods move biomes), and
found again if not (the manifest says so).

What a scene needs that a biome doesn't have can often be built by its commands where the biome is found (summoned
mobs, a lava pool, water sources up in the air: see `performance-rest.json`), which needs no setting up in a new world.
Otherwise stand there in the bench world and run `/dsbench mark <name>`, which remembers the position and where you
look; a marked scene is used as it is, even when the scene has a biome. `/dsbench list` lists the world's scenes.

## Modpacks

The mod alongside a Modrinth modpack, to find what other mods break (and what breaks ours): conflicts, hooks that
didn't apply, drawing gone wrong.

    python scripts/modpack-fetch.py smp-aesthia                  fetch the pack into neoforge/run-pack/smp-aesthia
    ./gradlew :neoforge:runClientPack -Ppack=smp-aesthia -Pscenario=scripts/bench/compat.json
    python scripts/pack-triage.py smp-aesthia                    what needs a look

- **Fetching** (`modpack-fetch.py`, its header has the details): the pack's newest version for this branch's game and
  loader, each file checked against the pack's hash and cached in `build/modpack-cache`; the pack's config, options,
  resource packs and shader packs come too (Iris off unless `--shaders`; a pack with no shader pack of its own gets one
  with `--shader-pack <zip>`). Left out: the pack's copies of the mods a pack run loads itself (ours, Architectury,
  Cloth Config), Crash Assistant and Dynamic FPS, which hold an unattended run up, and any `--leave-out <mod id>`.
  `pack.json` in the folder says what was fetched and left out. A CurseForge pack: install it with the CurseForge app
  and copy its instance's `mods` and `config` into a `run-pack/<name>` folder instead.
- **On an obfuscated game (1.21.11)** the development run renames the game's lambdas (intermediary names such as
  `method_62216`), so a published mod whose mixin targets one fails to load there, with a crash report naming the
  mixin (C2ME, Noisium). Leave such mods out (`--leave-out`) when they don't touch what we draw; 26.2 isn't obfuscated.
  Fabric runs it under Mojang's names, where a mixin can also clash with a name the game has there, or find nothing
  without its refmap (Enchantment Glint Outline, FallingTree: left out). Where only one option of a mod fails, the
  fetch turns that option off in the pack's config instead (`DEV_RUN_SETTINGS`: ModernFix's worldgen allocation), and
  `pack.json` lists it. The same table turns on what packs ship off and runs need, on any game: Voxy's rendering, so
  its far terrain is drawn beside our effects (`renderClouds` in `options.txt` stays the pack's).
- **Running** (`runClientPack`): the pack's folder, an 8 GB heap, and none of the mods the development client
  otherwise plays with (Biomes O' Plenty, TerraBlender, Serene Seasons, GlitchCore, Simple Voice Chat, Mod Menu), so
  the world is the pack's. A pack made for a newer loader than `gradle.properties` has: `-Pneoforge_version=<the
  pack's>` (the fetch says so; the first run with it sets that version up). Without `-Pscenario` it is a plain client
  in the pack's folder. A pack's own screens before the title screen (NeoForge's list of loading warnings) are opened
  over after 5 seconds; the manifest's `openedOver` names the screen.
- **`compat.json`**: each scene with our effects at their defaults and with every visual one off, back to back, so a
  pair of screenshots differs only by what we draw; short recordings. Run it without the pack too (`runClientBench`)
  for a baseline: what looks wrong in the pack but not there is the pack's doing.
- **Triage** (`pack-triage.py`): whether the game started (and the loader's errors if not), our startup lines
  (features standing aside, hooks that didn't apply, shaders, drawing that failed), mixin trouble, exceptions through
  our code apart from the rest, warnings by logger and the most repeated, crash reports, each run finished or not,
  and the screenshot pairs. The recordings say more: a render timer missing from an "on" run means that effect never
  drew (`jfr-summary.py`).

## RenderDoc

A bench run measures what things cost; RenderDoc shows what a frame drew: every draw, its shader, its target, and any
target at any point in the frame. `scripts/renderdoc-frame.py` drives it, with no change to the mod. Install RenderDoc
first (`RENDERDOC_HOME` if it isn't in `C:\Program Files\RenderDoc`), and open it once by hand: its first-run screen
holds up anything scripted, and looks like a hang.

    python scripts/renderdoc-frame.py capture scripts/bench/aurora.json --run realistic-fast
    python scripts/renderdoc-frame.py list <capture> --shader ordered_dither
    python scripts/renderdoc-frame.py save <capture> --shader ordered_dither --next --name march
    python scripts/renderdoc-frame.py swap <capture> --shader ordered_dither --edits edits.json

`capture` builds through Gradle, starts the game under RenderDoc, takes a frame `--wait` seconds into the run's
recording, and closes the game. A scenario can be short, as the recording is only what holds the scene: `"record": 60`
and `"repeats": 1` are plenty. `--backend vulkan` works too (on NeoForge, with `earlyWindowControl = false`, as for any
Vulkan run). Captures go in `<platform>/run-bench/renderdoc`, 25 MB on OpenGL and 60 MB on Vulkan at 1920x1080.

`list` also writes each fragment shader out as the capture has it. On OpenGL that is generated source (`float start =
ordered_dither(param);`), which is what edits have to match. `--shader` finds a draw by text in that source, as event
numbers change from one capture to the next.

- Keep it apart from the bench. A game under RenderDoc runs slower, so nothing from that run belongs next to a bench
  number, and it adds nothing to a results table: use it when a table can't say why.
- Replay times aren't the game's: each draw is timed alone, with no waits, fences or sync, so a stall such as the ring
  buffer's fence (`BENCHMARKS.md`) doesn't show, and a shader edit's cost isn't what the bench finds. For the grain on Fast,
  double the samples read the same as the original in replay; the bench said 42% more. Bench anything worth keeping.
- `swap` needs an OpenGL capture: Vulkan's replay wants SPIR-V, not source. `list` and `save` work on both.
- A `base` variant comes first in `swap`: it should match the frame exactly, which shows a difference is the edit's.
