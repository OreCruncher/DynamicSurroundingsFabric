# Moving to the next Minecraft version

Notes for the next move, 26.3 → 26.4 as this is written. `AGENTS.md` has what the code must keep to on the current
version. The merge diary this file used to hold (0.4.6 through 0.4.8 onto 26.2) is in git:
`git show 658ae710:MIGRATION.md`.

## How to size one

- `python scripts/version-compat.py BASE.jar OTHER.jar` lists what no longer links. It says nothing of behavior, game
  data or how a frame is drawn, and 26.2 → 26.3 looked like renames until the weather pass was found gone. Read
  `LevelRenderer` in the decompiled sources (`AGENTS.md`, "Build and test") before believing the list.
- Bring the biome traits and the `dsconfigs/tags` files up to date (`AGENTS.md`, "Biome traits").
- A mixin that doesn't match crashes when its target loads, and some load only in a world, so join one on both
  platforms, then with Improved Transparency on, then on Vulkan.
- Wait for Fabric API, NeoForge and Architectury builds. `-PgraphicsBackend=vulkan` and the bench (`compat.json`) show
  what a move broke faster than looking around by hand.

## 26.2 → 26.3

The branch moved to 26.3 without a release on 26.2, on what Modrinth's numbers said about where players are. 26.3 was
more than the rename from `com.mojang.blaze3d` to `com.mojang.renderpearl`. What changed:

- **The frame.** No weather pass, no separate targets, translucent things drawn inside the solid world's open pass, and
  Improved Transparency is now order-independent transparency. Everything of ours that opened a pass there moved to the
  new `eventing/IWorldRender`, after the main pass closes. Fabulous's code (depth writing pipelines, `WeatherDepth`, two
  draws of a soft layer, the stained glass frost, the damp spots' dark color, ground fog's depth) had no use and went.
- **The sky.** Its pass is open as the sun and moon are drawn, so `ISkyRender` comes after it closes.
- **Shaders** are compiled to SPIR-V: `#include`, explicit locations, `gl_VertexIndex`.
- **Pipelines** are compiled on demand; `setPipeline` takes the compiled one; `bindTexture` is `setUniform`; layouts name
  samplers as uniforms of `COMBINED_IMAGE_SAMPLER`.
- **Game data.** `dappled_forest`, poplar leaf particles; features in `worldgen/feature`, flat, with weighted selectors;
  spawns under the biome's attributes; sky colors are vectors. `BlockState.blocksMotion()` is gone (a tag now); `Shelter`
  keeps its old rule.
- **NeoForge** doesn't call `CreativeModeTabs.searchTab` in `handleUpdateTags`, so the tag hook is at the method's tail.

## 26.3 → 26.4: what's known

Checked 2026-11-05, snapshot 7 against 26.3, both on Java 25, with 26.4 not released. About 40 of the mod's 916 classes
don't link.

- **Packages move back.** `RenderPipeline` and `VertexFormat` to `com.mojang.blaze3d.pipeline` and `.vertex`,
  `RenderPipelines` to `net.minecraft.client.renderer.pipeline`: 18 files.
- **Textures.** `AbstractTexture` and `DynamicTexture` are gone, for `TextureHandle` and `TextureProvider*`: 6 files.
- **Block sounds.** `SoundType` is gone, for `BlockSoundSet`, with a codec and tags: `BlockInfoBuilder`, `SoundLibrary`.
- **Order-independent transparency.** `OitPipelineSet` is gone, for pipeline variants (`ParticleRenderPipelineVariant`
  and others), and its constants changed (wavelets to depth bins). This reaches `SoftParticles` and probably
  `soft_particle.fsh`.
- **The frame.** `addCloudsPass` is new, and the sky is drawn inside the main pass with `blitSky` and `blitClouds`.
  Weather's `render` and `renderOit` are merged. The aurora's `ISkyRender` and the clouds' and weather's cancels depend
  on all of this, so start here.
- **Mixins flagged:** `MixinSoundEngine`, `MixinCloudRenderer`, `MixinQuadParticleFeatureRenderer`, `MixinShaderManager`,
  `MixinSkyRenderer`, `MixinWeatherEffectRenderer`, Fabric's `MixinHud`.
- **Game data.** `ice_caves` is a new biome.
- **Not confirmed:** Vulkan as the default from snapshot 1 is from the web, not from the jar. Comparing vanilla's shader
  includes failed (the extraction came back empty), so they are unchecked. (Settled at snapshot 10, below.)

- **Dependencies** on that date: Fabric API had 26.4 builds (0.165.4+26.4); none found for NeoForge or Architectury.
- **Vulkan buffer writes (check on 26.4).** 26.3's `VulkanCommandEncoder.writeToBuffer` stages the data, copies it
  with `vkCmdCopyBuffer`, then records a memory barrier `ALL_COMMANDS → ALL_COMMANDS` for every read and write, which
  drains the GPU's queue at every upload. Measured 2026-11-09 (`BENCHMARKS.md`, "The drops' upload on Vulkan"): the
  drops' upload is about half their GPU time at Medium and up. Not worked around on 26.3 (opt-in there, 0.15 ms of
  precipitation at rain High). On 26.4, run `precipitation.json` with `-PgraphicsBackend=vulkan` first: if the barrier
  is still that broad (and more so if Vulkan is the default), do every mesh's upload before any drawing (one drain, not
  one an upload), or fewer bytes a drop. Instanced drops would help either backend, mostly the CPU's "Waiting for Quads".


### Snapshot 10 (checked 2026-11-17)

Snapshots 8 (2026-11-10), 9 and 10 (both 2026-11-17) since; still no pre-release or release date. `version-compat.py`
against snapshot 10's `client.jar` (from Mojang's version manifest, SHA-1 checked): 45 of the mod's 999 classes don't
link. Everything above still holds; new since snapshot 7:

- **Vulkan is the default** (from the jar): `PreferredGraphicsApi.getBackendsToTry` tries Vulkan then OpenGL for
  `DEFAULT` and `VULKAN`, OpenGL first only for `OPENGL` (26.3: OpenGL first for `DEFAULT`), and Vulkan's option is no
  longer labeled experimental (`options.graphicsApi.vulkanNonExperimental`). So most players will be on Vulkan: the
  Vulkan buffer-write item above comes first, and every Vulkan rule in `AGENTS.md` matters for everyone. Open Vulkan
  bugs: resizing the window crashes (MC-312444), windowed mode distorts (MC-312375).
- **Improved Fog** (snapshot 9), a setting apart from Improved Transparency, on by default (off on Fast): with it on and
  Improved Transparency off, the sky blends into the render distance fog; with Improved Transparency on, the sky and
  clouds both do. `FogRenderer.setupFog`, `LevelRenderer.addMainPass` and `executeOit` each gain a `boolean` for it,
  and `core/terrain.fsh` changed. `MixinFogRenderer` and `MixinLevelRenderer`'s hook before the transparency stages no
  longer match; the aurora (drawn after the sky), the dynamic clouds (which would need to blend as vanilla's do) and
  `FogHandler`/`ExtendedViewFog` need a look with it on and off.
- **Order-independent transparency** (snapshot 8): its shaders use subpasses (`oit_composite.fsh`,
  `oit_depth_bounds_cull.fsh`, `include/oit_depth_sample.glsl`, `include/oit_sample.glsl` changed;
  `blit_depth_bounds.fsh` new). Besides `SoftParticles`, check the depth copy before the stages
  (`SoftParticles.captureBeforeTransparency`), which everything reading the copy relies on.
- **Terrain and depth** (snapshot 8): terrain vertices are read from a texel buffer; the boat water mask draws to a
  target of its own, sampled by `terrain.fsh`, not into the terrain's depth (`blit_depth.fsh` removed). Check what
  reads depth near water, and the parked "boats on the pool floor" oddity.
- **Commands:** `SharedSuggestionProvider` is gone, for `SuggestionBox`/`SuggestionContext` (`ConfigCommand` calls
  `SharedSuggestionProvider.suggest`).
- **GUI:** `CycleButton$OnValueChange` moved to `AbstractCycleButton$OnValueChange`, a new base of `CycleButton`
  (`IndividualSoundControlListEntry`).
- **Sounds:** `block.ice.ambient` moves to Ambient/Environment; new tags `#triggers_ambient_ice_block_sounds` and
  `#triggers_ambient_ice_crystal_sounds`; fire breaks with the extinguish sound; dispensers launch with
  `block.dispenser.launch`, and their fire charge and firework sounds move to Blocks; new `entity.frostbite.throw`.
- **Particles:** `bubble_column_up` takes `pop_chance` (a pop and its sound at the end), `current_down` takes `radius`.
- **Formats:** resource pack 104, data pack 128; Java 25 still.
- `MixinGameRenderer`'s start of the weather's build in `GameRenderer.extract` (5.3.0) still links.
- Dependencies (NeoForge, Architectury, Fabric API for snapshot 10) weren't looked at this time.

Plan: ship on 26.3, wait for NeoForge and Architectury, run `version-compat.py` again on the release, and start with
the frame's order.
