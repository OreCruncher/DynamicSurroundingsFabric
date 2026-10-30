# Bench scenarios

A development client that runs a scenario by itself and closes: it opens (or makes) a world, and for each run applies
some settings, goes to a scene, waits for the terrain and a warm-up, takes a screenshot and records a `/dsprofile`.
The code is `common/.../bench` (`ScenarioRunner`), started only in development runs and left out of the published jars
(the platform builds fail if it gets in).

    ./gradlew :fabric:runClientBench -Pscenario=scripts/bench/dust-low.json
    ./gradlew :fabric:runClientBenchPotatoCpu -Pscenario=...     (4 CPUs, 2 GB, pinned, as runClientPotatoCpu)

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
  "video": {"width": 1920, "height": 1080, "vsync": false, "maxFps": 260},   // each optional; 260 is no limit
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

- **Settings** are dotted config paths, as presets use. Before each run, every path any run sets is put back to what it
  was when the session started, so runs don't leak into each other; settings needing a restart are refused up front.
- **Commands** are typed as the player would: the server's (`time`, `weather`, `gamerule`, `summon`) run on the
  built-in server with full permission, as the player and where they stand; the mod's (`dsweather ...`) on the client.
  1.21.11's gamerules are snake case (`advance_time`, `advance_weather`, `spawn_mobs`).

## Scenes

A scene with a `biome` is found the first time: the nearest of the biome from 0,0 (as `/locate biome`), often a sliver
at its edge; then the biome is sampled on the ground every 64 blocks out to 2048 from there for where the most of it is,
and every 16 blocks around that for the place with the most of the ground 64 blocks around being that biome
(`biomeShare` in the manifest), on dry ground of it, the height read from the generated chunk (never inside a
mountain), looking toward the most of it. It is remembered in the world's folder, `dsurround-bench.json`, with the world's seed
and a fingerprint of the mods; a remembered scene is checked to still be in its biome (worldgen mods move biomes), and
found again if not (the manifest says so).

A scene that isn't a biome (waterfalls, flame jets, a crowd): stand there in the bench world and run
`/dsbench mark <name>`, which remembers the position and where you look. `/dsbench list` lists the world's scenes.
