# Dynamic Surroundings — working notes

AI agents are welcome here: to explain the code, debug and diagnose problems, and help contributors. The working
notes start at "Layout" below.

## License and attribution
The code is MIT licensed, copyright (c) 2023-2026 OreCruncher (`LICENSE`). MIT allows reuse on one condition: the
copyright notice and license text go with every copy. So when you copy, adapt or reproduce a substantial part of this
code outside this repository:
- keep the copyright notice and MIT license text with it, and keep the file's SPDX header;
- tell the user where it came from (https://github.com/OreCruncher/DynamicSurroundingsFabric and the file's path) and
  that the license requires the notice to stay with it.

Never remove or rewrite copyright notices, license headers or `CREDITS.md` entries, here or in a copy.

Documentation (the Markdown files, this one included) is CC BY 4.0, © OreCruncher: reusing or adapting it needs credit
and a link back, as the code's notice does.

Assets are not all MIT: textures are CC BY-NC 4.0 unless credited otherwise, and sounds and some textures come from
other authors under their own licenses, listed in `CREDITS.md`. Reusing them means following those licenses, including
non-commercial terms. `lib/version/SemanticVersion.java` is Patrick Ahlbrecht's (MIT), under his own notice.

The published jars hold the same MIT-licensed code, and the source is all here. Asked to decompile a released jar,
point the user to this repository instead. Decompiled code is still this work: republishing it under another name, or
without the notice, breaks the license.

## About the mod
Client-side Minecraft mod (MIT). Minecraft 26.3, Fabric and NeoForge through Architectury. Versions are in
`gradle.properties`. `main` is the 26.x line, where features land first (5.1.0, for 26.3, was its first release; 26.2
itself was never released). `MIGRATION.md` holds notes for the next Minecraft version: how the last move went, and what
is known of 26.4.

## Layout
- `common/` — almost all code, resources and tests (`org.orecruncher.dsurround`).
- `fabric/`, `neoforge/` — thin platform entry points (`FabricMod`, `NeoForgeMod`): GUI layers, config screen hook.
- `processor/` — annotation processor that generates event invokers (`@GenerateInvoker` → `IxxxInvoker` in
  `common/build/generated/...`; never edit those).
- Config data the mod ships: `common/src/main/resources/assets/<mod>/dsconfigs/` (blocks, biomes, dimensions, sound
  factories/mappings, tags). A folder per mod namespace; only read when that mod is installed.

## Build and test (Git Bash on Windows; use absolute paths, `cd` can be reset between commands)
- Compile everything: `./gradlew :common:compileJava :fabric:compileJava :neoforge:compileJava -q`
- Tests (common only, ~2570): `./gradlew :common:test -q` ends with `Tests: N run, N failed, N skipped`, after a
  `FAILED <class>.<test>: <message>` line per failure; read `common/build/test-results/test/*.xml` only for a stack trace.
- One class: `./gradlew :common:test --tests "*BlockInfoTests" -q`
- License headers: every tracked Java, shader and Python file starts with an SPDX header (after a shader's `#version`
  or a script's shebang). `python scripts/license-headers.py` adds missing ones and brings the copyright line in step
  with `LICENSE` (change the years there, then run it); `--check` only reports. Files with another holder's notice are
  skipped. New files: add the header (the IDE's event template has it).
- Minecraft version popularity: `python scripts/modrinth-version-stats.py [slug ...]` prints Modrinth's download
  share by game version and loader, for refreshing `VERSION_STATS.md` (its "How to refresh" says how).
- Sound loudness survey (needs ffmpeg): `python scripts/sound-survey.py [--csv out.csv]` — level per OGG, events
  whose variants differ, stereo files, clipping, leading silence, files `sounds.json` doesn't use.
- Game jar for `javap` (check real signatures rather than assuming; 26.3 is unobfuscated):
  `~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged-deobf/26.3/minecraft-merged-deobf-26.3.jar`.
  NeoForge-patched classes: `~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/neoforge-<version>-minecraft-merged-deobf/26.3/*.jar`.
- Does the built mod link against another Minecraft version? `python scripts/version-compat.py BASE.jar OTHER.jar`
  (Mojang client jars; compile first) lists every class, member, override and mixin target/`@At` call site that
  resolves in BASE and not in OTHER. Behavior changes and game data aren't covered. It also says nothing of how a
  frame is drawn: 26.2 → 26.3 looked like renames, and the weather pass was gone (see `MIGRATION.md`).
- Decompiled sources (to see how vanilla draws something): `./gradlew :common:genSources`, then the `-sources.jar`
  under `.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-merged-*/26.3/`.

## Checking in the dev client
- Launch in the background, logging to a file, from the checkout being worked on (each worktree is its own branch, and
  `/c/repos/DynamicSurroundingsFabric` holds `main`, so another's `gradlew` tests other code):
  `./gradlew :fabric:runClient > <log> 2>&1`
- Wait for and grep the log: `Loaded the dsurround:pipeline/<name> shader`, `Dependency registration validated`,
  `N biome configs loaded`, mixin errors (`InvalidInjection`, `MixinApplyError`), config migration
  (`has moved to`).
- Quitting the game ends a development run within about 2 seconds: `lib/platform/DevExit` waits for the render (main)
  thread to end, then halts the JVM that Architectury's thread pools would hold open until vanilla's post-main
  watchdog crash. To quit a client you launched without killing it, close its window (PowerShell, the process found as
  below: `(Get-Process -Id <pid>).CloseMainWindow()`); the Gradle task then ends successfully.
  Stopping the Gradle task instead leaves the game JVM running; kill it (PowerShell):
  `Get-CimInstance Win32_Process -Filter "Name='java.exe' or Name='javaw.exe'" | Where-Object { $_.CommandLine -match 'KnotClient|devlaunchinjector|net.fabricmc.loader.impl.launch' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }`
  That doesn't match the NeoForge client (`:neoforge:runClient`). Both run as Architectury's `TransformerRuntime`, so
  match NeoForge's by its platform folder in `-Darchitectury.main.class`:
  `Get-CimInstance Win32_Process -Filter "Name='java.exe' or Name='javaw.exe'" | Where-Object { $_.CommandLine -match '\\neoforge\\\.gradle\\architectury' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }`
  The user may have their own client running: check what is running (and when it started) before killing, and stop
  only the one you launched.
- Profiling: `/dsprofile [scene] [seconds]` records a JFR file, zipped, to `config/dsurround/profiles` with the mod's
  GPU/CPU render timers in it (`dsurround.RenderTimer` events, set against the whole frame's drawing and the frames'
  lengths in `dsurround.Frames`, once a second; a timer of something run on the tick (the clouds' field) is made with
  `perTick`, and recordings set its time a frame (20 a second over the frames) against the frame's; precipitation's
  carry a `count` of drops/spots/window streaks a frame, so two recordings can be checked for the same storm), each client tick
  handler's time every tick
  (`dsurround.HandlerTick`, through `profiling/HandlerTicks`), what random block effects sampled, found, started,
  spawned and played by 1-block distance shell once a second (`dsurround.RandomBlockSamples`, through `profiling/RandomBlockSamples`),
  the classic aurora's shape (bands, distance, height...) and the pixels its quads covered a frame, overdraw counted,
  once a second while drawn (`dsurround.Aurora`, through `profiling/AuroraSamples`; `AuroraCoverage` clips the quads),
  what enhanced sound processing did once a second (`dsurround.EnhancedSounds`, through `profiling/SoundSamples`:
  calculations as sounds start, on the sound engine's thread, which waits for them; the workers' updates and passes;
  uploads; each calculation traced (timed), shared, reused or skipped; whether it ran at all),
  what it was made on (`dsurround.Environment`: graphics card, settings, frame size, frame rate limit, the potato run used) and each
  optional hook's status, conflict and non-text setting (`dsurround.Feature`, from `ProfileFeature`); unzip
  it and read it with the JDK's `jfr` (`jfr print --json --events ...`), or summarize it with
  `python scripts/jfr-summary.py <zip or jfr>` (frames; timers: mean and end of the running averages, share of the frame, and peak (CPU peaks a JVM pause could explain left out: a GC pause inside a timed draw reads as its spike); for a
  timer measured rarely, once a second or less, trust the end, not the mean; handler percentiles per tick (ticks a GC pause or other JVM stop landed in left out) and what
  running them in parallel would gain at best, `--dispatch-us`/`--worth-ms` for its cost and threshold, dependencies
  in `FIRST_HANDLERS`/`HANDLER_CHAINS`; random block effects' rate a block by distance against what
  `RandomBlockSampler` means (its constants are copied into the script: keep them in step), `--shell-width` rows; the
  classic aurora's shape and coverage against its GPU time (ns a covered pixel); enhanced sounds' work a second; threads,
  mod packages, our frames, allocation, GC;
  `--callers <method>` for who calls something).
  Each zip also holds a short summary (`ProfileSummary`, made from the finished recording, under 1900 characters to
  fit a Discord message), which chat offers as a [Copy summary] button with a headline (fps, costliest effect's share).
  Players can make them too, so nothing private may get in: a new event type or a new `jdk.jfr` user needs a look at
  `ProfileRecording.PRIVATE` and `Redaction`. The first recording since the game started waits
  `Profiler.WARM_UP_SECONDS` longer, with a throwaway recording running (`ProfileRecording.warmUp`): JFR's first start
  slowed the game for about 8 seconds (frames up to a second in ATM10), and later recordings showed nothing of it. A
  recording soon after joining a world still shows the pack settling in. Record under Run, not IntelliJ's Debug (its async stack trace agent
  throws thousands of Throwables a second).
- Bench scenarios (`scripts/bench/README.md`): `./gradlew :fabric:runClientBench -Pscenario=<file>` (or
  `runClientBenchPotatoCpu`) opens or makes a world in `<platform>/run-bench`, records each run's settings in its scene
  (biomes found and remembered in the world's `dsurround-bench.json`; others marked with `/dsbench mark <name>`) with
  a screenshot, writes `bench-results/<scenario>-<time>/manifest.json` (`latest.txt` names it) and closes the game.
  Use it for comparing settings instead of asking for recordings; `python scripts/bench-summary.py [folder]` tables
  each label's frame, render timers and tick handlers (and enhanced sounds' work where recorded), averaged over the
  repeats. A scene's `"sounds"` play seeded one-shots or loops around the player through warm-up and recording
  (`sound.json`: open air, a stone box, sounds outside the box; needs a sound device and a master volume above 0). The
  code (`bench/`, started by reflection from `profiling/BenchHook` in development only) is left out of the published
  jars; `shadowJar` fails if it
  gets in.
- Modpacks (`scripts/bench/README.md`, "Modpacks"): `python scripts/modpack-fetch.py <modrinth slug>` fetches one into
  `<platform>/run-pack/<slug>`; `./gradlew :neoforge:runClientPack -Ppack=<slug> [-Pneoforge_version=<the pack's>]
  -Pscenario=scripts/bench/compat.json` runs it without the dev client's own extra mods (BoP, Serene Seasons...);
  `python scripts/pack-triage.py <slug>` reports what needs a look. Compare with `compat.json` run plainly
  (`runClientBench`): what looks wrong only in the pack is the pack's doing. A render timer missing from an "on" run
  means that effect never drew. CurseForge packs: the user installs them and copies the instance's mods and config in.
- What a frame drew (`scripts/bench/README.md`, "RenderDoc"): `python scripts/renderdoc-frame.py capture <scenario>`
  takes a frame of a bench scene, and `list`, `save` and `swap` read it from a script: each draw, any target, a pass
  redrawn with an edited shader (OpenGL captures). For what's wrong in a picture, not for costs: replay times aren't
  the game's, so a change's cost comes from the bench.
- A client on a vanilla server (one that sends no `c:` tags, for the tag fallback in `ServerResourceFinder`), through
  `scripts/vanilla-server.py` (`build/vanilla-server`, or `--folder`):
  1. `python scripts/vanilla-server.py server` sets up the folder, starts the server and returns once it's ready. The
     first time in a folder it refuses without `--accept-eula`: Mojang's EULA is the user's to accept, so ask them.
  2. `python scripts/vanilla-server.py client neoforge` (or `fabric`), in the background. The client joins by itself:
     `joined the game` in `server.log`, as `Dev` on NeoForge and `Player<number>` on Fabric.
  3. Ask the user to type `/dsdump biomes` and `/dsdump tags` in that client, both before quitting. Keys sent to the
     window (`SendKeys`) don't arrive reliably, and stray ones hit the game's bindings.
  4. `python scripts/vanilla-server.py check neoforge [--against <single player biomes.txt>]` reports the members only
     a `c:` tag can give our tags, with any that are missing, and exits 1 if there are any. It reads the dumps from
     `<platform>/run-pack/none/config/dsurround/dumps`.
  5. `python scripts/vanilla-server.py stop`, and stop the client as above (or quit it: `DevExit` ends it).

  Why the script does what it does, for when something else needs a vanilla server:
  - `:neoforge:runClient` can't join. The dev client's extra mods (Biomes O' Plenty and the others `-Ppack` leaves out)
    need NeoForge on the server: "You are trying to connect to a server that is not running NeoForge, but you have mods
    that require it". Dynamic Surroundings, Architectury and Cloth Config don't, so `client` is
    `runClientPack -Ppack=none`, whose folder (`<platform>/run-pack/none`, ignored by git) leaves the dev settings and
    dumps alone.
  - `--quickPlayMultiplayer` goes on `clientPack` alone, from a Gradle init script: that run inherits `client`'s
    arguments, and on both the launch crashes with "Found multiple arguments for option quickPlayMultiplayer".
  - 26.3 turns the whitelist on by default ("You are not white-listed on this server"), and the dev client's account is
    offline, so `server.properties` needs `white-list=false`, `online-mode=false` and `enforce-secure-profile=false`.
  - The server has no console when started this way, so it's stopped by killing it. `Saving chunks` right after `Done (`
    in its log isn't a shutdown.
  - The `c:` tags are in `forge-universal.jar` and in `fabric-convention-tags-v2`, where older versions stay in Gradle's
    cache: use the one the client's log names. Read jars with Python's `zipfile`: `unzip` here silently extracted only
    some files, and the count came out low (202 where the truth was 332) with nothing to show for it.
- Low-end stand-ins (both platforms, IDE configs too): `runClientPotatoCpu` (4 logical CPUs, 2 GB heap),
  `runClientPotatoFill` (3840x2160 window, a slower GPU's load per pixel), `runClientPotato` (both). The Gradle tasks
  pin the CPUs with `scripts/run-affinity.ps1`; after an IDE launch run it by hand (see the script).
- The dev config is `fabric/run/config/dsurround/dsurround.json`. Don't edit the user's dev settings to force a case;
  cover it with a test.
- `defaultRequire: 1` in `dsurround.mixins.json`: a mixin that doesn't match crashes when its target loads, so a clean
  launch means every early-loaded mixin applied. Cloth's mixin only applies when a config screen first opens; the
  sky, fog, particle renderer, debug renderer and client level mixins only once a world is rendered.
  Exception: features hooked into code other mods often replace are `require = 0` and listed in
  `mixinutils/OptionalHook`, which checks at client start whether each applied (logs it, shows it in `/dsconflicts`
  and diagnostics, and fails the launch in dev). A new optional hook goes in that list (a test checks its handler).
- The class tweaker (`dsurround.classtweaker`) has to be asked for by the jar as well: `"accessWidener"` in `fabric.mod.json` (that key, though the file is a class tweaker: Fabric Loader only warns of any other),
  and on NeoForge `META-INF/accesstransformer.cfg`, generated from it by `accessTransformer` and declared in
  `neoforge.mods.toml`. Loom applies it to development runs and pack runs whatever the jar says, so none of them show a
  jar that doesn't: a real launcher crashed at once, in `MixinMinecraftClient`, writing a `final` field (`shadowJar` now
  fails without it). `remapJar` added this up to 1.21.11, and 26.x has no `remapJar`. So try a release's jars in a real
  launcher, once each loader, before uploading: no dev run covers them.
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
  registered automatically (`ConfigurationData.registerWith`). `resolve` gives one shared instance, registered or
  not (an unregistered class is created once and kept); `create` makes a new one each time (entity effects, which
  hold per-entity state). A class registered as another type's implementation (`WeatherSimulation` for
  `IWeatherSimulation`) can't be asked for by itself: it throws, and startup validation reports it, as it would be a
  second instance. Static `ContainerManager.resolve` remains only in
  static helpers, mixin support and codec-created objects, where nothing can inject.
- `ConfigServices` is handed by `BlockLibrary`/`BiomeLibrary` to the `BlockInfo`/`BiomeInfo` they build.
  `BiomeLibrary` resolves the condition evaluator lazily: evaluator → `BiomeVariables` → biome library is a cycle.
- Shaders: one `effects/ModShader` per `RenderPipeline` (`SoftParticles.SHADER` and `FLAME_SHADER`,
  `AuroraRenderer.SHADER`, `RealisticAuroraRenderer.SHADER`, `PointLights.SHADER`, `PrecipitationShaders.SHADERS`, `FrostRenderer.SHADERS`,
  `GroundFogVolume.SHADERS`), listed in `ModShaders.SHADERS`. Not added to vanilla's `RenderPipelines` (a failure there
  rejects the reload); `MixinShaderManager` compiles them after each shader reload. Drawing goes through
  `ModShader.run` / `RenderFailSafe`: a failure is logged once and the effect turned off until resources reload.
  With an Iris shader pack every `ModShader` is unusable; a pipeline of ours that still draws then (the soft particles'
  vanilla-shader fallbacks, fireflies) must be given one of the pack's programs with `IrisCompat.assignPipeline`, or
  Iris draws it with none and it vanishes ("Missing program ... in override list" in the log).
- Uniform blocks: `Std140Builder.putVec3` always takes 16 bytes, but std140 puts a following `float` in a `vec3`'s
  fourth slot. Write a `vec3` and the `float` after it as one `putVec4` (and `putVec4()` in the size), or every value
  after it is read 4 bytes off (ground fog drew nothing until this was found).
  Our uniform blocks are plain `GpuBuffer`s (`USAGE_UNIFORM | USAGE_COPY_DST`, written each frame with `writeToBuffer`
  from `Std140Builder.onStack`; made once with their data where it doesn't change while the device lives), not
  `MappableRingBuffer`s: its `rotate()` sets a fence, and the one after the aurora held the GPU idle 2.2 ms a frame on
  1.21.11 (AMD). 26.2's recordings didn't show it, but other drivers might.
- Realistic aurora (`effects/aurora/RealisticAurora*`, `shaders/core/aurora_realistic.*`; the package's
  `package-info.java` has the whole): the Java model mirrors the shader (`Arc.northAt` is `arc_north`, constants such as `FIELD_CELL`), and tests
  check the shader's text and constants against it, so change both. Its uniform block is in
  `shaders/include/aurora_realistic.glsl`. Anything that
  moves at a speed that changes goes through `RealisticAuroraMotion`, never `time × speed`. `/dsaurora activity|phase|
  colour` holds a look to check it; a GLSL error (a redeclared name, say) turns the whole effect off, so check the log
  for `Loaded the dsurround:pipeline/aurora_realistic shader` after any shader change.
- Vulkan: never pass a matrix or array between shader stages. `IntermediaryShaderModule.rebind` gives each fragment
  input one location, but a `mat4` takes four, so they overlap and don't match the vertex side: that crashed AMD's driver
  (26.10.1, RX 7600) in `vkCreateGraphicsPipelines` at startup. A fragment shader must also declare every output of its
  vertex shader, in order, as locations are counted over the ones it declares. With two color targets, write
  `location = 0` first: ground fog wrote its `sceneDistance` before `fragColor`, and the color was lost, with the
  timers looking healthy. Vanilla does none of these, so none of it shows there. Work matrices out in Java and put them
  in the effect's own block;
  `effects/LevelProjection` holds the frame's projection, view bobbing included, which `RenderSystem` only has as a
  GPU buffer. Timestamps are ticks: `RenderTimer` scales them by `DeviceInfo.timestampPeriod`. NeoForge's early window
  is OpenGL, so Vulkan needs `earlyWindowControl = false` in `config/fml.toml`. A failed start resets the game's
  Vulkan option to default: `-PgraphicsBackend=vulkan`, on any run, is a launch argument, which holds.
- View bobbing is in the projection matrix, and moves the camera a little as well as turning it. A screen pass that
  turns pixels back into directions (the realistic aurora) takes each one between two unprojected points along the
  pixel's line, not from the camera to one point near it, or every direction swings as the player walks.
- `RenderPipeline.Builder` defaults to no depth state: no test and no writes. Set one on every pipeline that should
  test or write depth (`ModShaderTests` checks the clouds' depth pass).
- Particle pipelines cull back faces: wind quads counter-clockwise as the camera sees them.
- Effects write their geometry with `effects/QuadWriter` (particle or position-texture-color quads, straight into
  memory) and draw it with `effects/MeshDraw`, which checks the writer's format against the pipeline's: those two are
  the only places an effect meets the game's buffer types, so a render API change lands there. `BufferBuilder` and
  `VertexConsumer` remain only where geometry is handed to vanilla's own drawing (footprints, the light level overlay,
  lightning for shader packs).
  A shader effect's per-frame values are worked out with no GPU into a frame record (`GroundFogFrame`, `AuroraFrame`,
  `RealisticAuroraFrame`, `PointLightFrame`), unit-tested; the renderer only packs it into its uniform block (a `pack`
  method: the matrix, then the record's vec4s in order), so that packing is all a render API change touches. A new
  value goes in the record, in the block's order, and in its shader include.
  Passes over the whole screen (ground fog's three, the realistic aurora and its composite) go through
  `effects/ScreenPasses`: `pipeline` starts their pipelines (one triangle from the vertex index, no vertex buffer, no
  culling), `draw` opens the pass, sets the pipeline, binds and draws, and `Reduced` keeps a target at a share of the
  screen's size.
- Rendering is 26.3's: pipelines (`com.mojang.renderpearl.api`), `RenderPass`, GPU buffers, uniform blocks (std140, `MappableRingBuffer`), particle
  layers and extraction. Depth is reverse-Z (cleared to 0). "How 26.3 draws a level" below has how the aurora, soft
  particles and point lights hook in. GPU timings (`RenderTimer`) go through the device's timestamp queries: no raw OpenGL, as 26.3
  can run on Vulkan.
- World and dimension queries go through `runtime/oracle` (`ILevelOracle`, `IDimensionOracle`, `IMinecraftClock`).
- Config: moving a property between groups → annotate the new field `@MovedFrom({"newest.old.path", "older.path"})`;
  old values carry over on load (also rename the path in `dsconfigs/presets.json`). Translation keys follow
  `dsurround.config.<group>.<field>[.tooltip]`.
- Presets (`dsconfigs/presets.json`, `lib/config/ConfigPreset`, loaded on reload by `config/PresetLoader` into
  `ConfigurationData.setPresets`; a row of buttons from `compat/PresetButtonsEntry` per `group`, 5 a row: features (the
  default) and performance (Low/Medium/High, values measured with the bench); `/dsconfig preset [name]`): one-shot
  partial sets of property values; they add up, and nothing remembers which was applied. Cloth's entries keep their own
  copies of values, so applying one saves and rebuilds the screen. Its confirmation (`compat/PresetConfirmScreen`) lists
  what would change (`ConfigPreset.changes`), by group, scrolling. Modpacks add presets in any namespace's
  `dsconfigs/presets.json` (resource pack or `config/dsurround/configs/<ns>/`): ours are read first, a same id replaces,
  `"hidden": true` removes, inline `name`/`description` beat the lang keys. Bad values, Scary Content and
  restart-needing settings are dropped and logged at load. Feature presets never touch the HUD (but for the target portrait, in RPG), quality or ranges;
  performance presets also set waterfall mist/froth/rings, dynamic clouds and flame/firefly lights, features too, so a feature
  preset after Low turns them back on (accepted) (`PresetLoaderTests`; `DsConfigFuzzTests` skips the file, which has no
  codec).
- How 26.3 draws a level (`LevelRenderer.addMainPass`): there is no weather pass and no separate weather, translucent,
  particles or clouds target (`OutputTarget` is gone, and Fabulous is only a graphics preset). With Improved Transparency
  off, translucent things, clouds, weather and the world border all draw inside the one render pass the solid world
  opened, on the main target; opening a second pass, or copying or uploading anything, throws "Close the existing render
  pass" (a texture made on first use has to be made before the pass). With it on, they go through order-independent
  transparency (`executeOit`: three `OitStage`s and a composite).
  So everything of ours that needs its own pass goes through `eventing/IWorldRender`, fired from `MixinLevelRenderer`
  before `executeOutline`, outside any pass, with phases for the order of the layers (particle lights, frost, clouds,
  ground fog, weather, default, lightning). A new effect registers with a phase; one with no phase runs in default.
  The aurora's `ISkyRender` comes once the sky's pass is closed, for the same reason. What follows from drawing after the
  pass:
  - Soft particles are drawn inside the open pass, so they read the previous frame's depth, copied in the particle lights
    phase: there is no gap to copy this frame's in, with Improved Transparency off. The copy is one frame old in a fast
    turn; the fade hides most of it. With it on they read this frame's, copied before its stages
    (`SoftParticles.captureBeforeTransparency`), and so does everything else that reads the copy: point lights, ground
    fog, dust. That copy also goes back to the main target before our effects draw, or mist in the depth hides clouds and
    weather behind it in a hole of its shape. So all of them see solid things only: with it on, clouds, rain and fog
    behind glass or water are drawn over it, untinted by stained glass, and point lights fall on what is behind it (seen
    in game, 2026-11-05; accepted, as the depth can't tell glass from particles once the stages have run).
  - Frost tests against the depth the main pass left, translucent blocks in it, so water or stained glass in front of
    frost hides it.
  - With Improved Transparency off, our rain and clouds behind stained or tinted glass aren't seen, as vanilla's aren't
    (seen in game, 2026-11-04): that glass leaves its depth; through clear glass they are.
  - Point lights and streaks are drawn after the translucent particles, not before them; rain over the world border.
  - With Improved Transparency on, the main target's depth holds only solid things until its composite, which writes
    the nearest translucent thing wherever anything was drawn, a particle's faintest edge included: seen with
    RenderDoc, those edges lit up as rings of specks under the point lights, and ground fog stopped at them. So a depth
    copy is made before the stages, or a particle fades out against where it just was. All three stages
    must drop the same pixels: one only the depth bounds stage drops (under 0.01 alpha) gets the far plane from the
    composite, a hole in the depth that clouds show through (seen as rings at the edge of mist). Particle layers need a set for the stages (or `drawLayers`
    throws): `RenderPipelines.OIT_PARTICLE`, or ours in `SoftParticles`, the same shader with `OIT` and, for added
    light, `OIT_ADDITIVE`. Vanilla's weather and clouds have theirs (`renderOit`, canceled for ours).
- Shaders: every stage is compiled to SPIR-V, so `#version 330`, `#extension GL_ARB_separate_shader_objects : require`,
  `#include` (not `#moj_import`), `layout(location = N)` on every input and output (a vertex shader's attributes in the
  vertex format's order), `gl_VertexIndex`. An include has no `#version`. Pipelines are compiled on demand through
  `RenderSystem.getCompiledPipeline`, and `RenderPass.setPipeline` takes the compiled one.
  - Blend alpha as "over" (`ONE, ONE_MINUS_SRC_ALPHA`), not the default's `ONE, ZERO`, or piled-up color outgrows its
    alpha.
- Enhanced sounds: a `SourceContext` per playing source, built from an `ISoundEmitter` (a game sound's
  `InstanceEmitter`, or a voice) and its OpenAL context's `Efx`. Uploads happen on the thread where that context is
  current (`Efx.isCurrent`, asserted). The worker recalculates whatever the `ISourceProvider`s registered with
  `SourceProviders` hand it. Worker threads read the world only through `WorldContext.blocks`
  (`lib/block/NoBlockEntityView`), or, for the traces, each thread's `lib/block/ChunkCachingView` (keeps the chunk in
  hand between reads; a quarter of a trace was the level looking chunks up): on the client `Level.getBlockEntity` can
  create a block entity from any thread, and mod blocks call it from shape methods, which corrupted chunk maps (#166).
  Nor may they call a `SoundInstance`'s
  `getVolume()`/`getPitch()`: each draws on the instance's `LegacyRandomSource`, which the sound engine also uses on the
  client thread and which throws when two threads meet in it; take what the worker needs when the sound starts
  (`InstanceEmitter.hearingRangeSq`). A sound's first calculation runs as it starts, on the thread playing it (the
  sound engine's, which waits), within `StartBudget` (2 ms every 50 ms, in `SourceContext`); past it the sound starts
  without effects and the workers' next pass calculates it. `sound.json`'s `burst` scene goes past it.
- Simple Voice Chat (`runtime/audio/voicechat`): voices play in its own OpenAL context, so they get their own `Efx`
  (made in its `CreateOpenALContextEvent`). `VoiceSounds` keeps the channels (tested with a fake `Backend`);
  `OpenALBackend` does the OpenAL side. `VoiceChatPlugin` is the only class using its API (compile-only; loaded by the
  `voicechat` entrypoint / NeoForge annotation), so nothing else may reference it.
- Our particles (`effects/particles`) stay the game's own particles, not a system like the weather's drops: the
  package's `package-info.java` says why, and what to measure before converting one (waterfall mist the likely one).
  A particle's `tick` isn't always on the client thread: Particle Core ticks particles on several threads at once, and
  Async Particles on a worker. So `tick` touches no shared state unguarded, and no `level.getRandom()` (fine in a
  constructor): an unguarded map in `WaterFoam` crashed with Particle Core (#213).
- Target portrait (`gui/overlay/TargetPortraitOverlay`): framed by height and width (`headBlocks`), with horses set by hand. A mob's effects aren't sent to clients, only the particles of the visible ones, so `TargetEffects` matches those back by color or type.
- Random block effects: one `effects/systems/RandomBlockEffectSystem` (one effect per block), sampling where
  `RandomBlockSampler` says: vanilla's near (16) and far (32) triangular spreads, 667 each a tick, which also play the
  blocks' sounds; past 32, an effects-only tail out to `blockEffectRange`, kept only where the inner spreads fall short
  of it, so the rate a block falls smoothly with distance (`RandomBlockSamplerTests`). `/dsprofile` recordings carry
  the rate by distance for `jfr-summary.py` to compare.
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
  `"sound"`/`"loop"`: 26.2 added `music.overworld.sulfur_caves`, 26.3 the `dappled_forest` biome); `BiomeNameFallbackAnalyzer` any new naming words;
  the `dsconfigs/tags` `is_*` files any vanilla tag changes, and every vanilla tag a `dsconfigs/tags` file names (26.2
  emptied `#minecraft:dirt` of grass, podzol, mycelium, mud and moss, into `#grass_blocks`, `#mud`, `#moss_blocks`;
  `#substrate_overworld` holds them all). 26.3 moved `worldgen/configured_feature` to `worldgen/feature`, flat with no
  `config`, added weighted selectors, and moved spawns into the `minecraft:gameplay/natural_mob_spawns` attribute, all of
  which `BiomeDefinitions` reads. Also check how the client gets music and climate:
  `BiomeEvidence.of` reads music from environment attributes on 26.2 (it was `BiomeSpecialEffects` on 1.21.1).
  `everySoundInTheTableIsVanillas` catches only removed ids, not new ones.
- Weather. `runtime/weather` is what the weather is (nothing there draws); `effects/precipitation` draws it; each has
  a `package-info.java` map.
  - `WeatherSimulation.rainLevel(vanilla)` is the storm's strength: use it, not the world's rain level, for anything
    that should follow the storm (fog, sounds, scripts, particles, aurora). Mixins feed it into vanilla's weather:
    drawn (`MixinWeatherEffectRenderer`), ticked with its splashes and sounds (`ClientLevel.tickWeatherEffects`), and
    its rain fog (`MixinAtmosphericFogEnvironment`). The mod's weather fog adds only a dust storm's. The tick is hooked
    around the method (`@WrapMethod`, `MixinHelpers.rainTickThread`) and on what it calls (`Level.getRainLevel`,
    `ClientLevel.addParticle` and `playLocalSound`), not inside it: a mod wrapping the method (as Iris 1.10 does to
    1.21.11's `tickRainParticles`) leaves hooks inside it nothing to attach to. The wrapper has an early `order` so that
    it is the innermost. Wrappers chain, the first applied inside, and Async Particles' `tickWeatherAsync` hands the
    method on to a worker from a `@WrapMethod` of its own: with ours outside it, or around the call, the mark was made on
    the client thread and gone by then, so vanilla's splashes came through on grass, with vanilla's rain sounds and
    level, and `OptionalHook` saw nothing wrong. That setting takes effect at once, no world restart, so it's a quick
    way to compare. To see the order, run with `-Dmixin.debug.export=true` and read `.mixin.out` in the run folder.
  - `WindService` ticks `Wind` and publishes it via static `SceneWind` for particles to drift; `WindSounds` scales
    sound factories marked `followsWind`. `DustStorms` decides where storms raise dust.
  - `Shelter.exposure` is how open to the weather a place is (sky light, and a roof from the `MOTION_BLOCKING`
    heightmap: glass counts, leaves and fluids don't). Wind, wind sounds, the weather's fog and ground fog all use it;
    don't gate weather on sky light alone, which leaks through windows and glass roofs.
  - On 1.21.1 our weather and ground fog replaced `renderSnowAndRain` only when `renderLevel` called it, as other mods
    call it themselves (Ars Nouveau, into its sky texture). The same holds: particle weather cancels
    `WeatherEffectRenderer.render` and `renderOit` only inside the level's main pass (`MixinLevelRenderer`), and draws in
    `IWorldRender.WEATHER`.
  - `processing/PrecipitationHandler` runs particle weather (`PrecipitationField`, `LevelColumns`, `Landings`) and
    stands aside for shader packs, a failed shader, mods that draw weather as particles (`FeatureConflict`, the table of features that stand aside for other mods) and dimensions that draw their
    own weather. The drops' tick runs on the `dsurround-Precipitation` thread, a tick behind: `PrecipitationField.finish`
    at the start of the handler's tick, `start` at its end, with two sets of the drops (`Drops`). Each frame's quads (`DropMeshes.Kind`: drops, dust, snow flecks, window streaks, damp spots) are
    written into `QuadWriter`s on a thread of their own (`dsurround-PrecipitationQuads`, apart from the step's, which a frame's build waited behind
    at High's range) as the level starts to be drawn (`DropMeshes`, from
    `MixinGameRenderer` at the head of `GameRenderer.renderLevel`, `OptionalHook.PRECIPITATION_AHEAD`), for that camera
    and partial tick; the `IWorldRender` handler uploads and draws them, or writes them on the
    render thread when the camera differs (a mod drawing the level again) or the build isn't done after
    `DropMeshes.WAIT_NANOS` (2 ms; a starved thread made frames wait 10-30 ms; timed as "- Waiting for Quads"). The tick discards a build before the drops,
    or what they left, change. The step may read only
    the columns and `Wind.Snapshot`; what needs the world it leaves as questions that `finish` answers. Tests use
    `tick`, which is both at once.
  - Drops stop at `LevelColumns.groundHeight`: the `MOTION_BLOCKING` heightmap, raised where a block above it is solid as
    it stands but not marked solid (a mod's dynamic-shape block without `forceSolidOn`, e.g. Mekanism's energy cube, which
    the heightmap leaves out). Shelter, ground fog and frost still read the plain heightmap.
  - Ground fog (`effects/groundfog`, run by `processing/GroundFogHandler`): mist in biomes with `groundFog` in
    `biomes.json`, drawn by a ray-marched screen pass (`GroundFogVolume`) over a per-column grid (`GroundFogField`),
    scaled by `GroundFogConditions` (time of day, wind, rain, shelter). Ground heights come from the client's
    `MOTION_BLOCKING` heightmap: the server never sends `MOTION_BLOCKING_NO_LEAVES`, so on the client it is empty.
    Noise positions wrap at `GroundFogVolume.NOISE_WRAP`, a whole number of every noise's cells (a test checks), or a
    wrap shows as a jump. Three fullscreen passes (march, smooth, composite) share `shaders/include/ground_fog.glsl`;
    the matrices the world was drawn with (view bobbing is in the projection) reach them through their block. Drawn in
    `IWorldRender.GROUND_FOG`, before the rain.
  - Dynamic clouds (`effects/clouds`, run by `processing/CloudHandler`): blocky columns on vanilla's 12-block cells,
    each with its own base and top, in place of vanilla's (`MixinCloudRenderer` cancels `CloudRenderer.render` when
    ours draw; `OptionalHook.DYNAMIC_CLOUDS`; off for Distant Horizons, which turns vanilla's clouds off and draws its
    own: `FeatureConflict.DYNAMIC_CLOUDS`). Three pipelines in one pass to the main target, in `IWorldRender.CLOUDS`, vanilla's `prepare`, `render` and `renderOit` canceled:
    the settled part's depth, then its color, then the changing part fading, no depth. The grid follows the Cloud
    Distance option; the light on them is vanilla's cloud color (the environment's). `runtime/weather/CloudCover` is
    what the weather calls for: fair puffs that build up in the afternoon, a storm's lowered, thickened deck, thunder
    towers, high streaks. Lightning strikes start from `CloudHandler.cloudBase()` when ours are drawn. Every player sees
    the same clouds: they drift with `runtime/weather/UpperWind` and change shape by the game time alone (never a
    session's ticks or the player's wind), and each cell's cover follows the biome under it. Quads are kept in 4x4-cell
    `CloudSections`, rebuilt only where cells changed (at most every `SECTION_TICKS`), positions from an anchor cell so
    they survive the grid moving; uploaded as they are (`POSITION_TEX_COLOR`). 1.21.1's Cloud Distance option
    (`weatherOptions.cloudDistance`) isn't here: vanilla's is.
  - Per dimension (`dimensions.json`): `particleWeather`, `simulateWind`, `groundFog` and `dynamicClouds`, guessed when
    absent (`DimensionInfo.drawsOwnWeather`/`drawsOwnClouds`: NeoForge's custom weather and clouds renderers for the
    dimension; sky light). The client always reports sea level 63, so a modded dimension
    with another needs a `seaLevel` rule.
  - `/dsweather` holds the storm strength, the wind's speed and direction, a ground fog thickness, and the clouds'
    coverage and thickness (`clouds 0-100 [thickness]`) for testing;
    disconnecting clears them.
  - Weather script variables live in `runtime/variables/WeatherVariables` (`weather.*`).
  - Lightning (`effects/lightning`, run by `processing/LightningHandler`): branching bolts in place of vanilla's (from
    `ClientLevel.addEntity`, shaped from the bolt's seed; vanilla's submit skipped for those seeds, both
    `OptionalHook.LIGHTNING_BOLTS`) and the sky's own strikes through a thunderstorm, mostly upwind
    (`Strikes.strikeAngle`, from `WindService`), with distance-delayed thunder. Drawn in `IWorldRender.LIGHTNING`,
    after the weather and the ground fog. Gated by `Configuration.ScaryContent` (`allowScaryContent` && `enableLightning`; script
    `global.allowLightning()`) and vanilla's Hide Lightning Flashes (no sky strikes, no flicker).
    `/dsweather lightning [ground|spider|sheet]`.
    With dynamic clouds drawn, a strike's cloud glow lights the clouds from inside (`CloudLights`, up to 8 points a
    frame, passed by `LightningHandler` to `CloudHandler`) instead of being drawn as patches. Strikes dim with distance
    against the fog's reach (`Strikes.visibility`, with `FogHandler.viewReach`: the nearer of the weather's fog and the
    render distance's).
  - Window frost (`effects/frost`, run by `processing/WindowFrostHandler`): on the outside of glass
    (`effects/frosted_windows` tag; `effects/windows` is all glass, for rain streaks), independent of particle weather.
    Drawn in `IWorldRender.WINDOW_FROST`, its depth tested in the shader against a copy of what the main pass left.
    "Outside" uses the `MOTION_BLOCKING` heightmap, then sky light on either side (`canSeeSky` is only sky light 15,
    which a glass roof doesn't reduce).

## Environment gotchas
- Git Bash mangles backslash escapes inside heredocs passed to Python (`'\\'`, `§`, `\s`): use the Edit/Write
  tools for code containing backslashes, `chr()`/`os.sep` in scripts, and avoid two heredocs in one command.
- Language files: edit as text, line by line. Re-serializing the JSON turns `§` escapes into literal `§`.
  Only `en_us` is maintained here; other languages come through Crowdin (`crowdin.yml`) and must stay a subset of
  `en_us` (`LanguageFileTests`).
- `powercfg` runs from PowerShell only: Git Bash mangles `/change` ("Invalid Parameters").
- Git Bash's `/tmp` isn't a path to Windows programs, Python among them. Give a script a Windows path (`cygpath -m`) in
  the scratchpad.
- Findings worth keeping go in a file as they are made (`MIGRATION.md`, here, a memory note): a conversation's summary
  isn't a record, and what is only there is lost with it.
- The user commits; don't commit unless asked.

## Branches
- `main`: the 26.x line, on 26.3 (NeoForge 26.3.0.101, Architectury 22.0.5, Cloth Config 26.3.159; NeoForge's
  client launch class is `net.neoforged.fml.startup.Client`). 5.1.0 is released (tag `26.3-5.1.0`); it has the
  realistic aurora, which 1.21.11 doesn't (see Versions). Never rebase it.
  - It came from `OreCruncher/26.2`, the 26.2 port, started from 0.4.5 and carrying 0.4.6 through 0.4.9, moved to 26.3
    and merged into `main` for 5.1.0. Up to then `main` was the 1.21.1 line, which is why its history holds 0.4.9's
    squash (`df0c23ef`), recorded on the port branch with `git merge -s ours`.
- `OreCruncher/1.21.11` (5.0.0 released, tag `1.21.11-5.0.0`): a long-lived 1.21.11 line backported from the 26.x line,
  branched from `OreCruncher/26.2` rather than from `main`. It takes no features from `main`, only fixes, so it stays on
  5.0.x and skips the minors `main` makes. Don't merge between the two (that would bring `main`'s features there, or the
  backport's downgrades here): cherry-pick a fix either way.
- 1.21.1 is frozen at 0.4.9 (tag `1.21.1-0.4.9`); a hotfix there would be 0.4.10, the old numbering. Branch it from
  the tag (`git switch -c OreCruncher/0.4.10 1.21.1-0.4.9`), release and tag it from that branch, and never merge it
  into `main`: cherry-pick fixes across. Its changelog heading and `versions.json` entry still go into `main` by hand
  (see Versions).
  - `python scripts/port-merge.py convert --to 1.21.1 FILE...` renames 26.x names in code copied to 1.21.1, and
    `--to 26.2` the other way (`scripts/port-renames.txt`); compile afterwards, as a rename can't see types. Its
    `merge` mode was for merging 1.21.1 release branches into the port branch, which no longer happens.
- Biome dumps: `python scripts/dump-diff.py OLD NEW [--sounds]` lists only what changed (traits, their sources, the
  "Biomes to check" list); `--keep DUMP` saves a copy to `build/dumps` before the next dump overwrites it.

## Versions
- `MAJOR.MINOR.PATCH` from 5.0.0, the first 1.21.11 release. The 0.x releases used the third number for every release,
  leaving no room for hotfixes.
- Patch: bug fixes only. No new config options or lang keys (they would sit untranslated until Crowdin catches up), no
  change to the `dsconfigs` or script formats.
- Minor: feature releases. A config property that moves keeps players' values through `@MovedFrom`.
- Major: only when something packs or players rely on breaks: a config that can't migrate, a `dsconfigs` or script
  format change, a loader dropped.
- One number means one set of features across Minecraft versions: 1.21.11 and 26.x release the same minor when they
  carry the same features, and a line skips a minor it gets nothing from. Pre-releases are `5.0.0-beta.1` (sorted
  before `5.0.0`, so the update check tells testers about the final).
- A release sets `mod_version` in `gradle.properties`, adds a changelog heading `DynamicSurroundings-<mc>-<version>`
  and a `versions.json` entry, and `recommend`, under its Minecraft version (the update check compares only within a
  Minecraft version, so 0.4.8 and 5.0.0 never meet), and is tagged `<mc>-<version>` (`1.21.11-5.0.1`). The jar's name
  already carries the Minecraft version.
- The update check reads `versions.json` from `main`, so upload the jars before that entry reaches `main`, or players
  are told about a version they can't download.
- Every line's changelog links in `versions.json` point at `main`'s one `CHANGELOG.md` (`blob/main/CHANGELOG.md#...`),
  whichever branch the release came from, so a heading has to be in `main`'s copy for its link to work.
