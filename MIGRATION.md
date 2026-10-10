# 0.4.6 → 26.2 port

Branch `OreCruncher/26.2`: the 26.2 port of 0.4.5 with `OreCruncher/0.4.6` merged in. Game APIs follow the 26.2
branch; behaviour follows 0.4.6 unless noted. Checked so far: everything compiles on common, Fabric and NeoForge; the
common tests pass; both clients start with all three shaders compiled and join a world without errors.

## Later 0.4.6 merges

Through `ceb6cd3` (tag sync on remote servers):
- Custom sprite sets (`58333c5`): `AtlasSpriteSet` reads the particle atlas through
  `Minecraft.getAtlasManager().getAtlasOrThrow(AtlasIds.PARTICLES)` and implements 26.2's `SpriteSet.first()`.
  `MixinParticleResources`, `MixinParticleTypes` and `SpriteOnlyProvider` are gone; `ParticleUtils.getSpriteProvider`
  reads `ParticleResources.spriteSets`.
- Tag sync (`ceb6cd3`): 26.2 has no `TagCollector`. Login tags fire `ITagSync` from `MixinRegistryDataCollector`
  (`collectGameRegistries` RETURN, for remote and integrated servers); `/reload` still goes through
  `MixinClientPacketListener.handleUpdateTags`. Checked: "Tag sync event received" logs on joining a world.
- `ReflectionHelper` casts in `MixinClientLevel` became plain casts.
- Biome background music: 26.2 has no `Biome.getBackgroundMusic()`; music is the `BACKGROUND_MUSIC` environment
  attribute with default, creative and underwater tracks. `MixinMinecraftClient` wraps `BackgroundMusic.select(ZZ)`
  in `getSituationalMusic` (0.4.6 wraps `Biome.getBackgroundMusic()` there). The game's underwater and creative tracks
  still play; configured music is chosen with the default one, which is folded into the choices only when the biome
  sets the attribute itself (otherwise it is the dimension's `Musics.GAME`, the fallback, as in 1.21.1). Biome at the
  camera, where the attribute is sampled. `playBiomeMusicWhileCreative` passes `isCreative = false` to `select`
  instead of faking `Abilities`.

## Structure: which side was kept

| Area | Kept | Notes |
|---|---|---|
| Events | 0.4.6 (`IClientTickStart.EVENT` and friends) | 26.2's `ClientState.*_EVENT`, `ClientEventHooks.FOG_RENDER_EVENT` and `AssetLibraryEvent.RELOAD` callers moved over. New `IClientLevelLoad` for 26.2's oracles. |
| World/dimension access | 26.2 (`runtime/oracle`: `ILevelOracle`, `IDimensionOracle`, `IMinecraftClock`) | 0.4.6's `DimensionInformation`, `IDimensionInformation` and `lib/time/MinecraftClock` dropped. `DimensionInfo` has 0.4.6's `finish()` (cloud and space heights) and 26.2's `natural`. `DimensionLibrary` no longer caches; `DimensionOracle` does. |
| Block entity search | 0.4.6 (`LevelCompat.doesBlockEntityExistNear`) | `ILevelOracle.doesBlockEntityExist(predicate)` became `doesBlockEntityExistNear(center, range, predicate)`. The ClientChunkCache access entries are gone from the classtweaker, as in 0.4.6. |
| Sound config screens, toast, markdown viewer | 0.4.6 | Ported to `GuiGraphicsExtractor`, `extractRenderState`, `KeyEvent`/`CharacterEvent`, `AbstractTextAreaWidget`. |
| Fog | 0.4.6 calculators on 26.2's `FogData` | See "Fixes to the 26.2 branch". The weather fog calculator stays switched off, as 26.2 had it. |
| Mixins | 26.2's targets with 0.4.6's hooks | `MixinClientLevel` has both the block update hook (26.2) and the chunk load hook (0.4.6). `MixinSoundEngine` takes only the channel handle (0.4.6) from 26.2's `play` returning `PlayResult`. |

## Rendering (0.4.6 features rebuilt for 26.2)

1.21.1's `ShaderInstance`, JSON shader definitions, `RenderSystem` state, `Tesselator`/`BufferUploader` and particle
render types are gone. What replaced them:

- **`ModShader`** holds a `RenderPipeline`. Pipelines are not added to `RenderPipelines`: those are all compiled on
  every reload and one failure rejects the reload. `MixinShaderManager` compiles the mod's pipelines after vanilla's
  (`ShaderManager.apply` TAIL) and each `ModShader` records whether it can be drawn, logging `Loaded the
  dsurround:pipeline/<name> shader` or `Unable to load ...`. No platform registration (Fabric's
  `CoreShaderRegistrationCallback` and NeoForge's `RegisterShadersEvent` hooks were removed).
- **Shaders** (`assets/dsurround/shaders/core/*.vsh/.fsh`) use GLSL 330 with uniform blocks and `#moj_import` of
  vanilla's includes (`dynamictransforms`, `projection`, `fog`, `sample_lightmap`). Each effect's own values are in a
  std140 block (`AuroraInfo`, `SoftParticleInfo`, `FireflyLightInfo`) written through a `MappableRingBuffer`.
- **Depth is reverse-Z** (cleared to 0, near is 1) and clip-space depth is 0..1 only when the driver has clip control,
  so the depth-reading shaders get `DepthZeroToOne` and use `distance = P[3][2] / (ndc + P[2][2])`; the sky is depth 0.
- **Aurora**: `MixinSkyRenderer` (`SkyRenderer.renderSunMoonAndStars` TAIL, overworld-type skies only) raises
  `ISkyRender` with the view matrix, partial tick, star brightness and rain brightness. `AuroraRenderer` builds the
  mesh each frame, uploads it to its own vertex buffer and draws in its own render pass on the main target (additive,
  no depth, no culling). Nights are numbered from the day clock (`getDefaultClockTime`).
- **Soft particles / firefly lights**: `MixinQuadParticleFeatureRenderer`. Before the translucent particle group
  (outside its render pass) the scene depth is copied (`SoftParticles.captureSceneDepth`) and the firefly lights are
  drawn in their own pass. As each layer's pipeline is set, a soft pipeline gets its `DepthSampler` and uniforms, or
  is swapped for its vanilla equivalent if there is no depth copy this frame. The mist's fade distance is a shader
  define (`SOFT_DISTANCE`) on its pipeline. The NeoForge stencil match is now by reflection on `useStencil` and the
  6-argument `TextureTarget` constructor. Firefly lights are recorded during extraction and drawn the same frame (no
  longer one frame late).
- **Particles**: `WaterfallMist`, `WaterFoam` extend `SingleQuadParticle`; `extract` replaces `render`, `getLayer`
  replaces `getRenderType`. Fireflies have their own layer (vanilla particle shader, translucent, no depth writes).
- **Effect diagnostics** (in-world boxes and labels) are gizmos emitted from `DebugRenderer.emitGizmos`.

## Fixes to the 26.2 branch found while porting

- Fog had no effect: the calculators' result was never written back to the game's `FogData`.
  `FogCompat.applyRange` now copies the environmental range into it.
- `MixinAtmosphericFogEnvironment`'s handler was `static` for an instance target; Mixin rejects that when the class
  loads (in a world).
- `FrostBreathParticle.quadSize(float)` didn't override `getQuadSize(float)`, so breath never grew.
- `LevelOracle.worldTime()` returned game time; it is now the day clock, as `getDayTime()` was in 1.21.1, so the
  clock overlay and morning fog follow `/time set` and sleeping.
- `diurnal.getCelestialAngle` was the sun angle in degrees; it is the 0..1 fraction of the day again, as in 1.21.1.
- On NeoForge none of the mod's own sounds were known (`Unable to locate sound 'dsurround:...'`): sounds.json was
  found by listing it as if it were a folder, which NeoForge's mod packs don't do for a file. `ClientResourceFinder`
  now looks it up in each namespace, as vanilla's sound manager does.

## To check in game (both platforms)

- Mixins that apply only in a world: sky, fog, quad particle renderer, debug renderer, client level, sound engine.
- Aurora: cold biome at night (`/dsurround` diagnostics shows its status); colours, strength, behind the world.
- Waterfall mist fading into water and terrain (soft), with and without Fabulous graphics; firefly lights on grass.
- Fog effects (biome, morning) now actually change the fog.
- Sound configuration screen: layout, filter, play/stop, tooltips; credits/markdown screen links and hover text.
- Compass scale, clock colour, diagnostics overlay modes including the in-world effect boxes.
- Biome music (`/dsmm whatsplaying`): configured tracks in their biomes, underwater music in oceans, creative music
  with and without `playBiomeMusicWhileCreative`.
