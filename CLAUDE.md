# Dynamic Surroundings — working notes

Client-side Minecraft mod (MIT). Minecraft 26.2, Fabric and NeoForge through Architectury. Versions are in
`gradle.properties`. This branch is the 26.2 port, carrying 0.4.7; `MIGRATION.md` records what changed and what still needs
checking in game.

## Layout
- `common/` — almost all code, resources and tests (`org.orecruncher.dsurround`).
- `fabric/`, `neoforge/` — thin platform entry points (`FabricMod`, `NeoForgeMod`): GUI layers, config screen hook.
- `processor/` — annotation processor that generates event invokers (`@GenerateInvoker` → `IxxxInvoker` in
  `common/build/generated/...`; never edit those).
- Config data the mod ships: `common/src/main/resources/assets/<mod>/dsconfigs/` (blocks, biomes, dimensions, sound
  factories/mappings, tags). A folder per mod namespace; only read when that mod is installed.

## Build and test (Git Bash on Windows; use absolute paths, `cd` can be reset between commands)
- Compile everything: `./gradlew :common:compileJava :fabric:compileJava :neoforge:compileJava -q`
- Tests (common only, ~2100): `./gradlew :common:test -q` ends with `Tests: N run, N failed, N skipped`, after a
  `FAILED <class>.<test>: <message>` line per failure; read `common/build/test-results/test/*.xml` only for a stack trace.
- One class: `./gradlew :common:test --tests "*BlockInfoTests" -q`
- Game jar for `javap` (check real signatures rather than assuming; 26.2 is unobfuscated):
  `~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged-deobf/26.2/minecraft-merged-deobf-26.2.jar`.
  NeoForge-patched classes: `~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/neoforge-<version>-minecraft-merged-deobf/26.2/*.jar`.
- Does the built mod link against another Minecraft version? `python scripts/version-compat.py BASE.jar OTHER.jar`
  (Mojang client jars; compile first) lists every class, member, override and mixin target/`@At` call site that
  resolves in BASE and not in OTHER. Behaviour changes and game data aren't covered. (26.2 → 26.3: no; 26.3 moved the
  render API from `com.mojang.blaze3d` to `com.mojang.renderpearl`.)
- Decompiled sources (to see how vanilla draws something): `./gradlew :common:genSources`, then the `-sources.jar`
  under `.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-merged-*/26.2/`.

## Checking in the dev client
- Launch in the background, logging to a file:
  `/c/repos/DynamicSurroundingsFabric/gradlew -p /c/repos/DynamicSurroundingsFabric :fabric:runClient > <log> 2>&1`
- Wait for and grep the log: `Loaded the dsurround:pipeline/<name> shader`, `Dependency registration validated`,
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
  GPU/CPU render timers in it (`dsurround.RenderTimer` events) and what it was made on (`dsurround.Environment`); unzip
  it and read it with the JDK's `jfr` (`jfr print --json --events ...`), or summarize it with
  `python scripts/jfr-summary.py <zip or jfr>` (timers, threads, mod packages, our frames, allocation, GC;
  `--callers <method>` for who calls something).
  Players can make them too, so nothing private may get in: a new event type or a new `jdk.jfr` user needs a look at
  `ProfileRecording.PRIVATE` and `Redaction`. Record under Run, not IntelliJ's Debug (its async stack trace agent
  throws thousands of Throwables a second).
- The dev config is `fabric/run/config/dsurround/dsurround.json`. Don't edit the user's dev settings to force a case;
  cover it with a test.
- `defaultRequire: 1` in `dsurround.mixins.json`: a mixin that doesn't match crashes when its target loads, so a clean
  launch means every early-loaded mixin applied. Cloth's mixin only applies when a config screen first opens; the
  sky, fog, particle renderer, debug renderer and client level mixins only once a world is rendered.
  Exception: features hooked into code other mods often replace are `require = 0` and listed in
  `mixinutils/OptionalHook`, which checks at client start whether each applied (logs it, shows it in `/dsconflicts`
  and diagnostics, and fails the launch in dev). A new optional hook goes in that list (a test checks its handler).
- A mixin `method` given by name alone applies to the first overload only. NeoForge adds overloads (its
  `WeatherEffectRenderer.render` takes the level render state too, and the vanilla one only forwards to it), so give
  the descriptor, or every overload's, wherever NeoForge might add one: the injection still "applies", to a method
  nothing calls, and neither `OptionalHook` nor a clean launch notices.

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
- Shaders: one `effects/ModShader` per `RenderPipeline` (`SoftParticles.SHADER` and `FLAME_SHADER`,
  `AuroraRenderer.SHADER`, `RealisticAuroraRenderer.SHADER`, `PointLights.SHADER`, `PrecipitationShaders.SHADERS`, `FrostRenderer.SHADERS`,
  `GroundFogVolume.SHADERS`), listed in `ModShaders.SHADERS`. Not added to vanilla's `RenderPipelines` (a failure there
  rejects the reload); `MixinShaderManager` compiles them after each shader reload. Drawing goes through
  `ModShader.run` / `RenderFailSafe`: a failure is logged once and the effect turned off until resources reload.
- Uniform blocks: `Std140Builder.putVec3` always takes 16 bytes, but std140 puts a following `float` in a `vec3`'s
  fourth slot. Write a `vec3` and the `float` after it as one `putVec4` (and `putVec4()` in the size), or every value
  after it is read 4 bytes off (ground fog drew nothing until this was found).
- Realistic aurora (`effects/aurora/RealisticAurora*`, `shaders/core/aurora_realistic.*`; MIGRATION.md has the
  whole): the Java model mirrors the shader (`Arc.northAt` is `arc_north`, constants such as `FIELD_CELL`), and tests
  check the shader's text and constants against it, so change both. Its uniform block is in
  `shaders/include/aurora_realistic.glsl`, as both stages declare it and a block must match across them. Anything that
  moves at a speed that changes goes through `RealisticAuroraMotion`, never `time × speed`. `/dsaurora activity|phase|
  colour` holds a look to check it; a GLSL error (a redeclared name, say) turns the whole effect off, so check the log
  for `Loaded the dsurround:pipeline/aurora_realistic shader` after any shader change.
- View bobbing is in 26.2's projection matrix, and moves the camera a little as well as turning it. A screen pass that
  turns pixels back into directions (the realistic aurora) takes each one between two unprojected points along the
  pixel's line, not from the camera to one point near it, or every direction swings as the player walks.
- `RenderPipeline.Builder` defaults to no depth state: no test and no writes. Set one on every pipeline that should
  test or write depth; a Fabulous variant that writes none leaves its target "infinitely far", and the transparency
  pass lays the scene over it (`ModShaderTests` checks the `*_depth`/`*_depth_write` pipelines).
- Drawing our own geometry while particles draw (`MixinQuadParticleFeatureRenderer`): use the particle renderer's
  `dynamicTransforms` (written in `finishPrepare`), not `RenderSystem.getModelViewMatrixCopy()`, which no longer holds
  the camera's rotation then. Particle pipelines cull back faces: wind quads counter-clockwise as the camera sees them.
- Rendering is 26.2's: pipelines, `RenderPass`, GPU buffers, uniform blocks (std140, `MappableRingBuffer`), particle
  layers and extraction. Depth is reverse-Z (cleared to 0). See `MIGRATION.md` for how the aurora, soft particles and
  point lights hook in. GPU timings (`RenderTimer`) go through the device's timestamp queries: no raw OpenGL, as 26.2
  can run on Vulkan.
- World and dimension queries go through `runtime/oracle` (`ILevelOracle`, `IDimensionOracle`, `IMinecraftClock`).
- Config: moving a property between groups → annotate the new field `@MovedFrom({"newest.old.path", "older.path"})`;
  old values carry over on load. Translation keys follow `dsurround.config.<group>.<field>[.tooltip]`.
- Fabulous graphics (as found on 1.21.1; recheck on 26.2 as the staged systems are rebuilt) draws translucent things,
  particles and weather to targets of their own, which vanilla's
  `transparency` post shader lays over the scene sorted by each target's depth, taking colours as premultiplied:
  `behind * (1 - alpha) + colour`, and skipping alpha 0. So anything we draw there must leave sensible alpha and depth:
  - The weather target is cleared and gets no copy of the scene's depth from vanilla. It keeps one depth a pixel, the
    nearest drawn, so rain behind a wall drawn with rain in front of it showed through the wall: `effects/WeatherDepth`
    copies the main target's depth into it at the start of the weather pass while any of ours draws there. Write depth
    (ground fog, damp spots), or the scene is laid over it.
  - The particles target's depth is the scene's from before water; the translucent target's has the water's surface.
    On 26.2 particles are drawn before the water, so its depth isn't there yet: with Fabulous a soft layer is drawn
    twice (`MixinQuadParticleFeatureRenderer` wraps `drawIndexed`): its depth first, tested as usual, so the nearest
    puff's is left (particles aren't drawn in order; writing depth as they come leaves patches), then its colour with no
    test and no writes. Water then isn't laid over mist above it.
  - Blend alpha as "over" (`ONE, ONE_MINUS_SRC_ALPHA`), not the default's `ONE, ZERO`, or piled-up colour outgrows its
    alpha; additive light needs colour already scaled and a tiny non-zero alpha (`PointLights`).
- Enhanced sounds: a `SourceContext` per playing source, built from an `ISoundEmitter` (a game sound's
  `InstanceEmitter`, or a voice) and its OpenAL context's `Efx`. Uploads happen on the thread where that context is
  current (`Efx.isCurrent`, asserted). The worker recalculates whatever the `ISourceProvider`s registered with
  `SourceProviders` hand it. Worker threads read the world only through `WorldContext.blocks`
  (`lib/block/NoBlockEntityView`): on the client `Level.getBlockEntity` can create a block entity from any thread,
  and mod blocks call it from shape methods, which corrupted chunk maps (#166).
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
- Biome traits (`config/biome/biometraits`) hold tables of what vanilla has, so **on every Minecraft version upgrade
  bring them up to date** from the new game's data (`data/minecraft/worldgen/biome/*.json` in the game jar, and its
  biome tags): `VanillaBiomeAnalyzer` needs an entry for each new biome (and removed ones dropped);
  `BiomeEffectsAnalyzer` each biome's `background_music` and ambient `loop` sound ids (unzip the biome files and grep
  `"sound"`/`"loop"`: 26.2 added `music.overworld.sulfur_caves`); `BiomeNameFallbackAnalyzer` any new naming words;
  the `dsconfigs/tags` `is_*` files any vanilla tag changes. Also check how the client gets music and climate:
  `BiomeEvidence.of` reads music from environment attributes on 26.2 (it was `BiomeSpecialEffects` on 1.21.1).
  `everySoundInTheTableIsVanillas` catches only removed ids, not new ones.
- Weather. `runtime/weather` is what the weather is (nothing there draws); `effects/precipitation` draws it; each has
  a `package-info.java` map.
  - `WeatherSimulation.rainLevel(vanilla)` is the storm's strength: use it, not the world's rain level, for anything
    that should follow the storm (fog, sounds, scripts, particles, aurora). Mixins feed it into vanilla's weather:
    drawn (`MixinWeatherEffectRenderer`), ticked with its splashes and sounds (`ClientLevel.tickWeatherEffects`), and
    its rain fog (`MixinAtmosphericFogEnvironment`). The mod's weather fog adds only a dust storm's.
  - `WindService` ticks `Wind` and publishes it via static `SceneWind` for particles to drift; `WindSounds` scales
    sound factories marked `followsWind`. `DustStorms` decides where storms raise dust.
  - `Shelter.exposure` is how open to the weather a place is (sky light, and a roof from the `MOTION_BLOCKING`
    heightmap: glass counts, leaves and fluids don't). Wind, wind sounds, the weather's fog and ground fog all use it;
    don't gate weather on sky light alone, which leaks through windows and glass roofs.
  - On 1.21.1 our weather and ground fog replaced `renderSnowAndRain` only when `renderLevel` called it, as other mods
    call it themselves (Ars Nouveau, into its sky texture). On 26.2 the same holds: particle weather cancels
    `WeatherEffectRenderer.render` only inside the level renderer's weather pass (`MixinLevelRenderer`).
  - `processing/PrecipitationHandler` runs particle weather (`PrecipitationField`, `LevelColumns`, `Landings`) and
    stands aside for shader packs, a failed shader, mods that draw weather as particles (`FeatureConflict`, the table of features that stand aside for other mods) and dimensions that draw their
    own weather.
  - Ground fog (`effects/groundfog`, run by `processing/GroundFogHandler`): mist in biomes with `groundFog` in
    `biomes.json`, drawn by a ray-marched screen pass (`GroundFogVolume`) over a per-column grid (`GroundFogField`),
    scaled by `GroundFogConditions` (time of day, wind, rain, shelter). Ground heights come from the client's
    `MOTION_BLOCKING` heightmap: the server never sends `MOTION_BLOCKING_NO_LEAVES`, so on the client it is empty.
    Noise positions wrap at `GroundFogVolume.NOISE_WRAP`, a whole number of every noise's cells (a test checks), or a
    wrap shows as a jump. Three fullscreen passes (march, smooth, composite) share `shaders/include/ground_fog.glsl`;
    their vertex shader inverts the matrices the world was drawn with (26.2 puts view bobbing in the projection). Drawn
    at the start of the weather pass, or with Fabulous after the weather and before the world border.
  - Per dimension (`dimensions.json`): `particleWeather`, `simulateWind` and `groundFog`, guessed when absent
    (`DimensionInfo.drawsOwnWeather`: NeoForge's custom weather renderer for the dimension; sky light). The client always reports sea level 63, so a modded dimension
    with another needs a `seaLevel` rule.
  - `/dsweather` holds the storm strength, the wind's speed and direction, and a ground fog thickness for testing;
    disconnecting clears them.
  - Weather script variables live in `runtime/variables/WeatherVariables` (`weather.*`).
  - Window frost (`effects/frost`, run by `processing/WindowFrostHandler`): on the outside of glass, stained and tinted
    included (`effects/frosted_windows` tag; `effects/windows`, the same glass, for rain streaks). Independent of
    particle weather. Without Fabulous, in the main pass after the translucent terrain, so water and stained glass
    behind a window are under its frost, its depth tested in the shader against a copy taken before the translucent
    features and blocks (`effects/DepthCopy`), so those in front don't hide it; 26.2 draws particles before that, and
    frost breath is soft (writes no depth), so it doesn't cut the frost out. With Fabulous, on clear glass at the start
    of the weather pass, to the weather target (`MixinLevelRenderer`); on stained glass as without Fabulous, but into
    the translucent target, tested against the main target's depth: a target holds one depth a pixel, so frost in the
    weather target was covered by water behind stained glass, merged into one layer with it.
    "Outside" uses the `MOTION_BLOCKING` heightmap, then sky light on either side (`canSeeSky` is only sky light 15,
    which a glass roof doesn't reduce).

## Environment gotchas
- Git Bash mangles backslash escapes inside heredocs passed to Python (`'\\'`, `§`, `\s`): use the Edit/Write
  tools for code containing backslashes, `chr()`/`os.sep` in scripts, and avoid two heredocs in one command.
- Language files: edit as text, line by line. Re-serialising the JSON turns `§` escapes into literal `§`.
  Only `en_us` is maintained here; other languages come through Crowdin (`crowdin.yml`) and must stay a subset of
  `en_us` (`LanguageFileTests`).
- The user commits; don't commit unless asked.

## Branches
- `main`: the 1.21.1 line (0.4.6 was squash-merged as `4808dca`; each 1.21.1 release branches from `main`).
- `OreCruncher/0.4.7`: merged into this branch directly, before its squash to `main`; merge it again for its fixes.
  Once squashed, check `git diff OreCruncher/0.4.7 main` is empty and record `main` with `git merge -s ours main` (a
  normal merge would reopen every conflict this branch resolved). Port 1.21.1 work by merging, resolving against 26.2's
  APIs (see `MIGRATION.md`); never rebase this branch.
  - After `git merge OreCruncher/0.4.7` stops: `python scripts/port-merge.py merge --show` renames 1.21.1's names
    (`scripts/port-renames.txt`) and merges each conflict again, staging what resolves (and files git merged with old
    names in them), then prints only the conflicts left. Then compile; add any rename the compiler finds to the table
    and run `port-merge.py check-table`. `port-merge.py convert --to 1.21.1 FILE...` renames code copied to 0.4.7.
- Biome dumps: `python scripts/dump-diff.py OLD NEW [--sounds]` lists only what changed (traits, their sources, the
  "Biomes to check" list); `--keep DUMP` saves a copy to `build/dumps` before the next dump overwrites it.
- `OreCruncher/26.2`: the 26.2 port, started from 0.4.5 and carrying 0.4.6 and 0.4.7 (NeoForge 26.2.0.82, Architectury
  21.1.10, Cloth Config 26.2.155). NeoForge 26.2's client launch class is `net.neoforged.fml.startup.Client`.
