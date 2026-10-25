# Dynamic Surroundings — working notes

Client-side Minecraft mod (MIT). Minecraft 1.21.1, Fabric and NeoForge through Architectury. Versions are in
`gradle.properties`.

## Layout
- `common/` — almost all code, resources and tests (`org.orecruncher.dsurround`).
- `fabric/`, `neoforge/` — thin platform entry points (`FabricMod`, `NeoForgeMod`): shader registration, GUI layers,
  config screen hook.
- `processor/` — annotation processor that generates event invokers (`@GenerateInvoker` → `IxxxInvoker` in
  `common/build/generated/...`; never edit those).
- Config data the mod ships: `common/src/main/resources/assets/<mod>/dsconfigs/` (blocks, biomes, dimensions, sound
  factories/mappings, tags). A folder per mod namespace; only read when that mod is installed.

## Build and test (Git Bash on Windows; use absolute paths, `cd` can be reset between commands)
- Compile everything: `./gradlew :common:compileJava :fabric:compileJava :neoforge:compileJava -q`
- Tests (common only, ~2100): `./gradlew :common:test -q > /dev/null 2>&1; python scripts/test-summary.py` (counts
  and each failure's message from `common/build/test-results/test/*.xml`; the console output is noisy). It warns
  `STALE` and exits 1 when a source changed after the newest results: that run wrote none, usually a compile error.
- One class: `./gradlew :common:test --tests "*BlockInfoTests" -q`
- Sound loudness survey (needs ffmpeg): `python scripts/sound-survey.py [--csv out.csv]` — level per OGG, events
  whose variants differ, stereo files, clipping, leading silence, files `sounds.json` doesn't use.
- Mapped game jar for `javap` (check real signatures rather than assuming):
  `.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-merged-*/1.21.1-*/minecraft-merged-*.jar`.
  NeoForge-patched classes: `~/.gradle/caches/fabric-loom/1.21.1/neoforge/<version>/minecraft-merged-*.jar`.

## Checking in the dev client
- Launch in the background, logging to a file:
  `/c/repos/DynamicSurroundingsFabric/gradlew -p /c/repos/DynamicSurroundingsFabric :fabric:runClient > <log> 2>&1`
- Wait for and grep the log: `Loaded the dsurround:<shader> shader`, `Dependency registration validated`,
  `N biome configs loaded`, mixin errors (`InvalidInjection`, `MixinApplyError`), config migration
  (`has moved to`).
- Stopping the Gradle task leaves the game JVM running; kill it (PowerShell):
  `Get-CimInstance Win32_Process -Filter "Name='java.exe' or Name='javaw.exe'" | Where-Object { $_.CommandLine -match 'KnotClient|devlaunchinjector|net.fabricmc.loader.impl.launch' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }`
  That doesn't match the NeoForge client (`:neoforge:runClient`). Both run as Architectury's `TransformerRuntime`, so
  match NeoForge's by its platform folder in `-Darchitectury.main.class`:
  `Get-CimInstance Win32_Process -Filter "Name='java.exe' or Name='javaw.exe'" | Where-Object { $_.CommandLine -match '\\neoforge\\\.gradle\\architectury' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }`
  The user may have their own client running: check what is running (and when it started) before killing, and stop
  only the one you launched.
- Profiling: `/dsprofile [scene] [seconds]` records a JFR file, zipped, to `config/dsurround/profiles` with the mod's
  GPU/CPU render timers in it (`dsurround.RenderTimer` events; precipitation's carry a `count` of drops/spots a frame,
  so two recordings can be checked for the same storm) and what it was made on (`dsurround.Environment`); unzip
  it and read it with the JDK's `jfr` (`jfr print --json --events ...`), or summarize it with
  `python scripts/jfr-summary.py <zip or jfr>` (timers: mean and end of the running averages, and peak; for a
  timer measured rarely, once a second or less, trust the end, not the mean; threads, mod packages, our frames, allocation, GC;
  `--callers <method>` for who calls something).
  Players can make them too, so nothing private may get in: a new event type or a new `jdk.jfr` user needs a look at
  `ProfileRecording.PRIVATE` and `Redaction`. Record under Run, not IntelliJ's Debug (its async stack trace agent
  throws thousands of Throwables a second).
- The dev config is `fabric/run/config/dsurround/dsurround.json`. Don't edit the user's dev settings to force a case;
  cover it with a test.
- `defaultRequire: 1` in `dsurround.mixins.json`: a mixin that doesn't match crashes at startup, so a clean launch
  means every early-loaded mixin applied. Cloth's mixin only applies when a config screen first opens.
  Exception: features hooked into code other mods often replace are `require = 0` and listed in
  `mixinutils/OptionalHook`, which checks at client start whether each applied (logs it, shows it in `/dsconflicts`
  and diagnostics, and fails the launch in dev). A new optional hook goes in that list (a test checks its handler).

## Test conventions
- Anything touching `Blocks`, `BiomeTrait`, tag classes, etc. needs the game bootstrapped first. Use a static block
  at the top of the test class (before static fields that use those classes), not `@BeforeAll`:
  `static { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }`
- No mocking library. `testing/Fakes.of(Interface.class, Map.of("method", args -> ...))` makes proxy fakes;
  `lib/threading/RecordingLog` captures logging; `Randomizer.create(seed)` for repeatable randomness.
- Resource files from tests: anchor on a mod-owned file (`/assets/dsurround/lang/en_us.json`) — other `assets`
  folders are on the class path too.

## Architecture worth knowing
- Dependency container (`lib/di`): classes it creates take dependencies in their constructors. Config groups are
  registered automatically (`ConfigurationData.registerWith`). Static `ContainerManager.resolve` remains only in
  static helpers, mixin support and codec-created objects, where nothing can inject.
- `ConfigServices` is handed by `BlockLibrary`/`BiomeLibrary` to the `BlockInfo`/`BiomeInfo` they build.
  `BiomeLibrary` resolves the condition evaluator lazily: evaluator → `BiomeVariables` → biome library is a cycle.
- Shaders: one `effects/ModShader` per core shader (`SoftParticles.SHADER`, `AuroraRenderer.SHADER`,
  `PointLights.SHADER`), listed in `ModShaders.SHADERS`, registered by both platforms. Drawing goes through
  `ModShader.run` / `RenderFailSafe`: a failure is logged once, state restored, and the effect turned off until
  resources reload.
- Config: moving a property between groups → annotate the new field `@MovedFrom({"newest.old.path", "older.path"})`;
  old values carry over on load. Translation keys follow `dsurround.config.<group>.<field>[.tooltip]`.
- Fabulous graphics draws translucent things, particles and weather to targets of their own, which vanilla's
  `transparency` post shader lays over the scene sorted by each target's depth, taking colours as premultiplied:
  `behind * (1 - alpha) + colour`, and skipping alpha 0. So anything we draw there must leave sensible alpha and depth:
  - The weather target is cleared but gets no copy of the scene's depth: read the main target's, and write depth (ground
    fog, damp spots), or the scene is laid over it. Something soft that writes depth where it has all but faded hides
    what is drawn behind it after: the dust goes after the snow and its flecks there, or they blinked.
  - The particles target's depth is the scene's from before water; the translucent target's has the water's surface.
    Soft particles fade against the translucent depth and write it (`soft_particle_scene_depth`, Fabulous only: a
    shader that writes depth loses early depth testing) so water isn't laid over them.
  - Blend alpha as "over" (`ONE, ONE_MINUS_SRC_ALPHA`), not the default's `ONE, ZERO`, or piled-up colour outgrows its
    alpha; additive light needs colour already scaled and a tiny non-zero alpha (`PointLights`).
- Enhanced sounds: a `SourceContext` per playing source, built from an `ISoundEmitter` (a game sound's
  `InstanceEmitter`, or a voice) and its OpenAL context's `Efx`. Uploads happen on the thread where that context is
  current (`Efx.isCurrent`, asserted). The worker recalculates whatever the `ISourceProvider`s registered with
  `SourceProviders` hand it. Worker threads read the world only through `WorldContext.blocks`
  (`lib/block/NoBlockEntityView`): on the client `Level.getBlockEntity` can create a block entity from any thread,
  and mod blocks call it from shape methods, which corrupted chunk maps (#166). Nor may they call a `SoundInstance`'s
  `getVolume()`/`getPitch()`: each draws on the instance's `LegacyRandomSource`, which the sound engine also uses on the
  client thread and which throws when two threads meet in it; take what the worker needs when the sound starts
  (`InstanceEmitter.hearingRangeSq`).
- Simple Voice Chat (`runtime/audio/voicechat`): voices play in its own OpenAL context, so they get their own `Efx`
  (made in its `CreateOpenALContextEvent`). `VoiceSounds` keeps the channels (tested with a fake `Backend`);
  `OpenALBackend` does the OpenAL side. `VoiceChatPlugin` is the only class using its API (compile-only; loaded by the
  `voicechat` entrypoint / NeoForge annotation), so nothing else may reference it.
- Mixin plugin (`mixinutils/DSurroundMixinPlugin`) skips mixins whose target belongs to a mod that isn't installed
  (currently Cloth Config).
- Features that turn themselves off for another mod are listed in `lib/platform/FeatureConflict` (checked with
  `isBlocked()`), not by skipping mixins: it logs them at startup and feeds the diagnostics overlay and `/dsconflicts`.
  A new one needs a `dsurround.conflict.<name>` entry in `en_us` (a test checks).
- Tags in `dsconfigs/tags/<registry>/...` are client-side tag definitions. Reference other tags with `#`
  (`#c:is_cold`); a plain `c:` entry is a mistake (a test checks).
- Weather. `runtime/weather` is what the weather is (nothing there draws); `effects/precipitation` draws it; each has
  a `package-info.java` map.
  - `WeatherSimulation.rainLevel(vanilla)` is the storm's strength: use it, not the world's rain level, for anything
    that should follow the storm (fog, sounds, scripts, particles, aurora). A mixin feeds it into vanilla's weather.
  - `WindService` ticks `Wind` and publishes it via static `SceneWind` for particles to drift; `WindSounds` scales
    sound factories marked `followsWind`. `DustStorms` decides where storms raise dust.
  - `Shelter.exposure` is how open to the weather a place is (sky light, and a roof from the `MOTION_BLOCKING`
    heightmap: glass counts, leaves and fluids don't). Wind, wind sounds, the weather's fog and ground fog all use it;
    don't gate weather on sky light alone, which leaks through windows and glass roofs.
  - Our weather and ground fog replace `renderSnowAndRain` only when `renderLevel` calls it (a flag set by the
    `MixinLevelRenderer` wrap). Other mods call it themselves (Ars Nouveau, into its sky texture); those get vanilla's.
  - `processing/PrecipitationHandler` runs particle weather (`PrecipitationField`, `LevelColumns`, `Landings`) and
    stands aside for shader packs, a failed shader, mods that draw weather as particles (`FeatureConflict`, the table of features that stand aside for other mods) and dimensions that draw their
    own weather.
  - Drops stop at `LevelColumns.groundHeight`: the `MOTION_BLOCKING` heightmap, raised where a block above it is solid as
    it stands but not marked solid (a mod's dynamic-shape block without `forceSolidOn`, e.g. Mekanism's energy cube, which
    the heightmap leaves out). Shelter, ground fog and frost still read the plain heightmap.
  - Ground fog (`effects/groundfog`, run by `processing/GroundFogHandler`): mist in biomes with `groundFog` in
    `biomes.json`, drawn by a ray-marched screen pass (`GroundFogVolume`) over a per-column grid (`GroundFogField`),
    scaled by `GroundFogConditions` (time of day, wind, rain, shelter). Ground heights come from the client's
    `MOTION_BLOCKING` heightmap: the server never sends `MOTION_BLOCKING_NO_LEAVES`, so on the client it is empty.
    Noise positions wrap at `GroundFogVolume.NOISE_WRAP`, a whole number of every noise's cells (a test checks), or a
    wrap shows as a jump.
  - Dynamic clouds (`effects/clouds`, run by `processing/CloudHandler`): blocky columns on vanilla's 12-block cells,
    each with its own base and top, in place of vanilla's (the `renderClouds` call in `renderLevel` is wrapped, so
    Sodium's replacement is skipped too; `OptionalHook.DYNAMIC_CLOUDS`). `runtime/weather/CloudCover` is what the
    weather calls for: fair puffs that build up in the afternoon, a storm's lowered, thickened deck, thunder towers,
    high streaks. Lightning strikes start from `CloudHandler.cloudBase()` when ours are drawn. Every player sees the
    same clouds: they drift with `runtime/weather/UpperWind` and change shape by the game time alone (never a session's
    ticks or the player's wind), and each cell's cover follows the biome under it. Quads are kept in 4x4-cell
    `CloudSections`, rebuilt only where cells changed (at most every `SECTION_TICKS`), positions from an anchor cell so
    they survive the grid moving. The grid follows `weatherOptions.cloudDistance`, not the render distance; the clouds'
    fog is the terrain's stretched out to it (`CloudRenderer`).
  - Per dimension (`dimensions.json`): `particleWeather`, `simulateWind`, `groundFog` and `dynamicClouds`, guessed when absent
    (`DimensionInfo.hasVanillaWeather`, sky light). The client always reports sea level 63, so a modded dimension
    with another needs a `seaLevel` rule.
  - `/dsweather` holds the storm strength, the wind's speed and direction, a ground fog thickness, and the clouds'
    coverage and thickness (`clouds 0-100 [thickness]`) for testing;
    disconnecting clears them.
  - Weather script variables live in `runtime/variables/WeatherVariables` (`weather.*`).
  - Lightning (`effects/lightning`, run by `processing/LightningHandler`): branching bolts in place of vanilla's (from
    `ClientLevel.addEntity`, shaped from the bolt's seed; vanilla's render cancelled, both `OptionalHook.LIGHTNING_BOLTS`)
    and the sky's own strikes through a thunderstorm, mostly upwind (`Strikes.strikeAngle`, from `WindService`), with
    distance-delayed thunder. Drawn last in the
    `renderSnowAndRain` wrap; with Fabulous it writes depth, bolts before the cloud glow. Gated by
    `Configuration.ScaryContent` (`allowScaryContent` && `enableLightning`; script `global.allowLightning()`) and
    vanilla's Hide Lightning Flashes (no sky strikes, no flicker). `/dsweather lightning [ground|spider|sheet]`.
    With dynamic clouds drawn, a strike's cloud glow lights the clouds from inside (`CloudLights`, up to 8 points a
    frame, passed by `LightningHandler` to `CloudHandler`) instead of being drawn as patches. Strikes dim with distance
    against the fog's reach (`Strikes.visibility`, from the fog in effect in the weather pass).
  - Window frost (`effects/frost`, run by `processing/WindowFrostHandler`): on the outside of clear glass
    (`effects/frosted_windows` tag; `effects/windows` is all glass, for rain streaks). Drawn after the translucent blocks,
    independent of particle weather: clear glass is cutout, so frost behind it shows through. Without Fabulous, right
    after the tripwire layer and before particles (they write depth: breath in front cut it out); with Fabulous, in the
    `renderSnowAndRain` wrap before the weather, to the weather target.
    "Outside" uses the `MOTION_BLOCKING` heightmap, then sky light on either side (`canSeeSky` is only sky light 15,
    which a glass roof doesn't reduce).

## Environment gotchas
- Git Bash mangles backslash escapes inside heredocs passed to Python (`'\\'`, `§`, `\s`): use the Edit/Write
  tools for code containing backslashes, `chr()`/`os.sep` in scripts, and avoid two heredocs in one command.
- Language files: edit as text, line by line. Re-serialising the JSON turns `§` escapes into literal `§`.
  Only `en_us` is maintained here; other languages come through Crowdin (`crowdin.yml`) and must stay a subset of
  `en_us` (`LanguageFileTests`).
- The user commits; don't commit unless asked.

## 26.2 port
Branch `OreCruncher/26.2` (Minecraft 26.2, NeoForge 26.2.0.82, Architectury 21.1.10, Cloth Config 26.2.155), checked
out in the worktree `C:\repos\DynamicSurroundingsFabric-26.2`. Its build files and adapted code are the reference for
26.2 APIs: read them with `git show OreCruncher/26.2:<path>`. The areas that differ most are rendering (aurora, soft
particles and firefly lights: shaders, `RenderSystem`, particle render types), mixin target signatures
(`LevelRenderer.renderSky`, `SoundEngine.play`, `ParticleEngine` render order, `FogRenderer`) and the sound engine
hooks. NeoForge 26.2's client launch class is `net.neoforged.fml.startup.Client`.

1.21.1 work lands on `main` (0.4.7 was squash-merged as `f3edc2f`); start each 1.21.1 release branch from `main`.
`main` is merged into 26.2 (as of 0.4.7, `f3edc2f`, recorded with `-s ours`), so
`git diff $(git merge-base main OreCruncher/26.2) main` lists the 1.21.1 changes still to port. The release branch in
progress (`OreCruncher/0.4.8`) is merged into `OreCruncher/26.2` directly, in the worktree, resolving against 26.2's
APIs (see its `MIGRATION.md`; `scripts/port-merge.py` there redoes the renames), never by rebasing the 26.2 branch.
After its squash to `main`, record `main` in 26.2 with `git merge -s ours main`.
