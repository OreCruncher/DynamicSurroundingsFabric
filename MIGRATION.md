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
  includes failed (the extraction came back empty), so they are unchecked.
- **Dependencies** on that date: Fabric API had 26.4 builds (0.165.4+26.4); none found for NeoForge or Architectury.

Plan: ship on 26.3, wait for NeoForge and Architectury, run `version-compat.py` again on the release, and start with
the frame's order.
