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
  `FireflyLights.SHADER`), listed in `ModShaders.SHADERS`, registered by both platforms. Drawing goes through
  `ModShader.run` / `RenderFailSafe`: a failure is logged once, state restored, and the effect turned off until
  resources reload.
- Config: moving a property between groups → annotate the new field `@MovedFrom({"newest.old.path", "older.path"})`;
  old values carry over on load. Translation keys follow `dsurround.config.<group>.<field>[.tooltip]`.
- Mixin plugin (`mixinutils/DSurroundMixinPlugin`) skips mixins whose target belongs to a mod that isn't installed
  (currently Cloth Config).
- Tags in `dsconfigs/tags/<registry>/...` are client-side tag definitions. Reference other tags with `#`
  (`#c:is_cold`); a plain `c:` entry is a mistake (a test checks).

## Environment gotchas
- Git Bash mangles backslash escapes inside heredocs passed to Python (`'\\'`, `§`, `\s`): use the Edit/Write
  tools for code containing backslashes, `chr()`/`os.sep` in scripts, and avoid two heredocs in one command.
- Language files: edit as text, line by line. Re-serialising the JSON turns `§` escapes into literal `§`.
  Only `en_us` is maintained here; other languages come through Crowdin (`crowdin.yml`) and must stay a subset of
  `en_us` (`LanguageFileTests`).
- The user commits; don't commit unless asked.

## Planned: 26.2 migration
Expect the most work in rendering (the code notes `ParticleRenderType` becomes a record and particle rendering is
reworked; the aurora, soft particles and firefly lights use `ShaderInstance`, core shader JSON, `RenderSystem`,
`Tesselator`, `BufferUploader`), mixin target signatures (`LevelRenderer.renderSky`, `SoundEngine.play`,
`ParticleEngine` render order, `FogRenderer`), the sound engine hooks, and availability of Architectury, Cloth Config
and Mod Menu for 26.2. NeoForge 26.2's client launch class is `net.neoforged.fml.startup.Client`. Work on its own
branch or worktree, area by area, verifying APIs against the 26.2 jars, keeping progress in a `MIGRATION.md`.

A working 26.2 port exists: branch `OreCruncher/26.2` (Minecraft 26.2, NeoForge 26.2.0.82, Architectury 21.1.10,
Cloth Config 26.2.155), split from 0.4.5. Its build files and adapted code are the reference for 26.2 APIs. The plan is
to port this branch's later changes onto it (in a worktree), not to migrate this branch from scratch: read it with
`git show OreCruncher/26.2:<path>` and compare with `git diff <merge-base> HEAD`.

Open issues found in the 26.2 branch, to check when working on it:
- `ITagSync` probably doesn't fire for the tags sent at login. 26.2 has no `TagCollector`, and its
  `MixinClientPacketListener` hooks `handleUpdateTags`, which only runs in play (`/reload`). Login tags are applied in
  `ClientConfigurationPacketListenerImpl.handleConfigurationFinished` → `RegistryDataCollector.collectGameRegistries`,
  which nothing hooks. On 1.21.1 `MixinTagCollector` covers both paths (it used to fire only for single-player: an
  `if (local)` carried over from Fabric's `TAGS_LOADED` `client` flag, removed in 0.4.6). Check: a datapack tag
  that changes an effect should work on joining a world, not only after `/reload`.
