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
- Tests (common only, ~1450): `./gradlew :common:test -q`, then read `common/build/test-results/test/*.xml` for
  counts and failure messages (the console output is noisy).
- One class: `./gradlew :common:test --tests "*BlockInfoTests" -q`
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
- The dev config is `fabric/run/config/dsurround/dsurround.json`. Don't edit the user's dev settings to force a case;
  cover it with a test.
- `defaultRequire: 1` in `dsurround.mixins.json`: a mixin that doesn't match crashes at startup, so a clean launch
  means every early-loaded mixin applied. Cloth's mixin only applies when a config screen first opens.

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
- Mixin plugin (`mixinutils/DSurroundMixinPlugin`) skips mixins whose target belongs to a mod that isn't installed
  (currently Cloth Config).
- Tags in `dsconfigs/tags/<registry>/...` are client-side tag definitions. Reference other tags with `#`
  (`#c:is_cold`); a plain `c:` entry is a mistake (a test checks).
- Weather. `runtime/weather` is what the weather is (nothing there draws); `effects/precipitation` draws it; each has
  a `package-info.java` map.
  - `WeatherSimulation.rainLevel(vanilla)` is the storm's strength: use it, not the world's rain level, for anything
    that should follow the storm (fog, sounds, scripts, particles, aurora). A mixin feeds it into vanilla's weather.
  - `WindService` ticks `Wind` and publishes it via static `SceneWind` for particles to drift; `WindSounds` scales
    sound factories marked `followsWind`. `DustStorms` decides where storms raise dust.
  - `processing/PrecipitationHandler` runs particle weather (`PrecipitationField`, `LevelColumns`, `Landings`) and
    stands aside for shader packs, a failed shader, the Particle Rain mod (`KnownMod`) and dimensions that draw their
    own weather.
  - Per dimension (`dimensions.json`): `particleWeather` and `simulateWind`, guessed when absent
    (`DimensionInfo.hasVanillaWeather`, sky light). The client always reports sea level 63, so a modded dimension
    with another needs a `seaLevel` rule.
  - `/dsweather` holds the storm strength and the wind's speed and direction for testing; disconnecting clears them.
  - Weather script variables live in `runtime/variables/WeatherVariables` (`weather.*`).

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

1.21.1 work lands on `main` (0.4.6 was squash-merged as `4808dca`); start each 1.21.1 release branch from `main`.
`main` is merged into 26.2 (as of 0.4.6, `4808dca`), so `git diff $(git merge-base main OreCruncher/26.2) main` lists
the 1.21.1 changes still to port. Port them in the worktree by merging `main` into `OreCruncher/26.2` and resolving
against 26.2's APIs (see its `MIGRATION.md`), never by rebasing the 26.2 branch.
