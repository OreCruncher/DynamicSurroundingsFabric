# 0.4.6 → 26.2 port

Branch `OreCruncher/26.2`: the 26.2 port of 0.4.5 with `OreCruncher/0.4.6` merged in, and `OreCruncher/0.4.7` merged
in after it (see "0.4.7 merge" below). Game APIs follow the 26.2
branch; behaviour follows 0.4.6 unless noted. Checked so far: everything compiles on common, Fabric and NeoForge; the
common tests pass; both clients start with all three shaders compiled and join a world without errors.

## 0.4.7 merge

`OreCruncher/0.4.7` (through `f7ddfdb`) merged in, staged: everything compiles on common, Fabric and NeoForge, the
common tests pass, and both clients start with every pipeline compiled and every optional hook applied. Four of 0.4.7's
new systems needed a renderer of their own on 26.2 and stood aside until rebuilt, one at a time; all four are rebuilt
(the light level overlay, particle weather, window frost and ground fog). 0.4.7's code is the reference:
`git show origin/OreCruncher/0.4.7:<path>`.

What changed to fit 26.2:
- **Point lights**: 0.4.7's `PointLights` (any light: fireflies, flame jets) replaces `FireflyLights`, on its 26.2
  pass. Each light carries its radius in its corner UVs; the shader gives light already scaled by its strength, with
  next to no alpha, and one pipeline adds both (`ONE, ONE`), which also suits Fabulous graphics' particle target.
- **Flames**: a soft, additive particle layer (`SoftParticles.flameLayer()`, `pipeline/flame`, fade 0.3) beside the
  mist's. Steam, smoke, mist and flame tongues share `SoftPuffParticle` (a `SingleQuadParticle`).
- **Hooks** (0.4.7's `MixinLevelRenderer`, `MixinFogRenderer` and `MixinParticleEngine` targets are gone): storm strength
  in drawn rain (`MixinWeatherEffectRenderer`), in ticked rain, rain sounds and vanilla splash hiding
  (`ClientLevel.tickWeatherEffects`, `MixinClientLevel`), in vanilla's rain fog (`MixinAtmosphericFogEnvironment`); dust
  tint of the sky (`SkyRenderer.extractRenderState`) and fog (`FogRenderer.computeFogColor`, which the clear colour
  follows); wind-blown marking (`ParticleEngine.makeParticle`). All in `OptionalHook`.
- **Weather fog**: rain's fog is vanilla's own on 26.2 (given the storm's strength); `WeatherFogRangeCalculator` adds
  only a dust storm's, and is on again (it was left out of the 26.2 port).
- **Dimensions**: `DimensionInformation` is gone; its 0.4.7 values (`particleWeather`, `simulateWind`, `groundFog`) are
  on `IDimensionOracle`. Whether a dimension draws its own weather is NeoForge's custom weather renderer
  (`DimensionInfo.drawsOwnWeather`, set by `NeoForgeMod`); nothing replaces it on Fabric. Looked up once, at the
  origin: NeoForge reads it from an environment attribute, which a biome could set, so custom weather in some biomes
  only would be missed (none known). Checking at the player as the weather is drawn would catch it.
- **GPU timing**: `RenderTimer` takes its timestamps through the device's query pool, so it works on either backend.
- **Aurora**: reads 0.4.7's noise texture (`NoiseSampler`, linear and repeating) in place of hashing.
- **Seasons**: `ISeasonalInformation` keeps 26.2's names (`getTemperatureAt`); 0.4.7 adds `SNOW_TEMPERATURE` and
  `hasPrecipitationAt` (Serene Seasons: `SeasonHooks.hasPrecipitationSeasonal`).
- **Light level overlay**: rebuilt. `LightLevelAtlas` draws the font's digits straight into a GPU texture (no read
  back), with the GUI text pipelines the glyphs name (`TextRenderable.guiPipeline`), outlines then numbers, and
  registers it with the texture manager. `MixinLevelRenderer` (`submitFeatures` TAIL) has `LightLevelRenderer` submit
  one quad per label with `submitCustomGeometry` and vanilla's `textPolygonOffset`, culled by the camera state's
  frustum (0.4.7's frustum accessor is gone). The atlas is drawn there, on the render thread, not from the tick. A
  texture drawn into is stored bottom row first, so the labels sample `1 - v` (`LightLevelAtlas.sampledV`).
- **Particle weather**: rebuilt. Six pipelines in `PrecipitationShaders` (in `ModShaders.SHADERS`), each a pair, as
  vanilla's weather writes depth only with Fabulous graphics: the drops (rain, snow, snow flecks, window streaks), the
  soft dust (the `precipitation` shader with `SOFT_DISTANCE`, reading `SoftParticles`' depth copy, made again in the
  weather pass by `SoftParticles.captureDepth` from the translucent target with Fabulous, the main one without), and
  damp spots (darkening blend; with Fabulous, translucent with depth writes). `MixinWeatherEffectRenderer` cancels
  `render` (matched by name: NeoForge's takes the level render state too) when `PrecipitationHandler.render` draws,
  only inside the level renderer's weather pass (`MixinLevelRenderer` flags `lambda$addWeatherPass$0`), so a mod
  calling the weather renderer itself gets vanilla's. Each kind of mesh has its own vertex buffer (`MeshDraw`).
  Optional hook `PRECIPITATION`: if it doesn't apply, particle weather stands aside. Dust storms (fog, sky tint) come
  back with it.
- **Window frost**: rebuilt. Two pipelines in `FrostRenderer` (depth written only with Fabulous graphics), drawn with
  `MeshDraw` (moved to `effects`, shared with the weather). Without Fabulous, `MixinLevelRenderer` draws it in the main
  pass after the translucent terrain, before `executeTranslucentAfterTerrain` (optional hook `WINDOW_FROST`), so water
  and stained glass behind a window are under its frost. Its depth is tested in the shader (`DEPTH_COPY`) against a
  copy of the scene's depth taken before `executeTranslucent` (`effects/DepthCopy`, only while there is frost), not
  against the depth buffer, which by then has the water and stained glass in it: those in front of the frost don't
  hide it, but are drawn under it rather than over it (untinted). 26.2 draws particles before that; frost breath now
  uses the soft mist layer, which writes no depth, so it no longer cuts the frost out (vanilla particles that write
  depth, such as smoke, still can).
  With Fabulous, frost on clear glass at the start of the weather pass, to the weather target. The shader's fade
  distance is a define.
  Frost is on all glass now (`frosted_windows`: `#c:glass_blocks`, `#c:glass_panes`, as rain streaks), stained and
  tinted included. Fabulous keeps one depth per target and pixel, so the water, ice and stained glass at a pixel are
  one layer, at the nearest one's depth. Seen through the stained glass it is on, frost in the weather target sorted
  behind that layer, and water or ice behind the window covered it. So with Fabulous, frost on stained glass
  (`FrostFace.translucent`: the block's model has `BakedQuad.FLAG_TRANSLUCENT`) is drawn as without Fabulous: after the
  translucent terrain, into the translucent target, tested against the main target's depth (which has no translucent
  things). The glass is always in that layer there, so its depth stays right. Seen from inside, it is untinted, on
  both settings (accepted).
  Known limit, clear glass with Fabulous: through stained glass at a frosted clear window with water behind it, the
  water is laid over the frost. Drawing clear glass's frost into the translucent target too would lose its depth where
  nothing translucent is in front (rain behind a window would be laid over it), so it stays.
- **Ground fog**: rebuilt. Four pipelines in `GroundFogVolume` (march, smooth, composite, and the composite writing
  depth for Fabulous), each a triangle from the vertex index (`ground_fog_screen.vsh`, no vertex buffer), sharing a
  `GroundFogInfo` uniform block (`shaders/include/ground_fog.glsl`). The vertex shader inverts `ProjMat`/`ModelViewMat`
  itself, as 26.2 adds view bobbing to the projection on the GPU side only. Depth is reversed: the sky (depth 0) is
  treated as far away (`FAR_AWAY`), and depth is turned into NDC by `DepthZeroToOne`. A line of sight's direction is
  taken from a point far off (`DIRECTION_DEPTH`): view bobbing shifts the eye in the projection by about as much as the
  near plane is from it, so a near point turned the lines wildly while walking (the mist blinked). The depth copy is `SoftParticles.captureDepth` (translucent target with Fabulous). Drawn at the start
  of the weather pass without Fabulous; with it, after the weather and before the world border (optional hook
  `GROUND_FOG`). The march also writes each pixel's distance (`distance_at`) to an `R32_FLOAT` target, a second colour
  attachment, for the smooth (no depth reads) and composite (its own pixel only); it writes every pixel, returning
  clear colour instead of discarding, as pixels without mist still count when blending. 3D noise was dropped (no banding
  found on 26.2). `GroundFogField.prepare` reads the obstacle check's neighbours in area order rather than through the
  ring's slots.
- **Precipitation columns**: `PrecipitationColumns` keeps each column in a slot keyed by its world position, as
  `GroundFogField` does: moving looks up only the columns that came into the area, a few rows are looked at again each
  tick (all within `REFRESH_TICKS`) instead of the whole area every 10 ticks, and the rain, snow and dust shares and the
  regional dust colour are running totals.
- **Drop vertices**: the falling drops (rain, snow, dust) are written straight into native memory by `ParticleQuads`
  (vanilla's particle layout, packed as BufferBuilder packs it; `ParticleQuadsTests` compares the bytes) and drawn with
  `MeshDraw.drawQuads`, instead of four BufferBuilder calls a corner. Snow flecks, window streaks and damp spots,
  hundreds rather than thousands, still use BufferBuilder.
- **Far from the origin**: the falling drops (`PrecipitationField`: positions and landings), damp spots, snow flecks
  and drops on windows keep x and z as doubles (y stays a float),
  and window frost keeps its faces' plane and extent across as doubles; each becomes a float only once the camera is
  taken away. Point lights are stored relative to the camera when added (drawn the same frame). `FarFromOriginTests`
  checks a quarter block survives at 8,000,000, and `PrecipitationFieldTests` that snow still drifts there (as floats
  it fell straight down and jumped half a block now and then).
- **Soft particles with Fabulous graphics**: 0.4.7 wrote the translucent target's depth (the water's surface) from a
  shader of its own; on 26.2 the particles are drawn before the water, so that depth doesn't exist yet. Instead the
  mist and flame layers are drawn twice with Fabulous (`SoftParticles.choosePipeline`, `depthPassFor`, and
  `MixinQuadParticleFeatureRenderer` wrapping `drawIndexed`): first only their depth (`*_depth`: no colour, the usual
  test and writes, `WRITE_DEPTH` dropping what has faded to nothing), leaving the nearest puff's at each pixel; then
  their colour (`*_fabulous`: no test, no writes). Writing depth in a single untested pass left whichever puff came
  last, as particles aren't drawn in order, and showed as patches over a waterfall. Mist above water then sorts in front
  of it in the transparency pass. `soft_particle_scene_depth` is gone.
- **Spark and droplet streaks**: a 26.2 particle hands the engine a square facing the camera; a streak is a long quad
  along its path, its head bright and its tail clear. `FlameSpark` and `LavaDroplet` add theirs to `Streaks` as they
  are extracted (camera-relative, into `ParticleQuads`), and `Streaks.draw` draws them all just before the translucent
  particles, beside the point lights, with the flame layer's pipeline for the frame (soft and additive; with Fabulous,
  its depth pass first). Each particle also hands the engine a clear quad of its own: a group with no quads isn't
  submitted, and the streaks are drawn when it is. Leftovers are dropped at the end of the frame (`finishExecute`).
  Two traps found in game: the streaks and point lights take the particle renderer's own `dynamicTransforms` (by
  then RenderSystem's model view isn't the camera's, which put everything behind it), and the streak corners wind
  counter-clockwise (the flame pipeline culls back faces, which dropped every streak). The overlay's Render section
  now starts with the graphics mode (`Graphics:`), and has a `Streaks` timer.
- **Vanilla tags**: 26.2's `minecraft:dirt` no longer holds grass blocks, podzol, mycelium, moss or mud; they are in
  `grass_blocks`, `mud` and `moss_blocks`, all under `substrate_overworld`, which `rain_absorbing` (damp spots) and
  `BlockLibrary`'s acoustic seed tags now use. `minecraft:tall_flowers` is gone; `brush_step` lists its five flowers.
  `ModTagFilesTests` checks every `#minecraft:` reference names a tag that exists (a shrunken tag still needs a look
  when porting: compare the version's tag files).
- **Clock, compass and light level legend**: 0.4.7's overlays on `GuiGraphicsExtractor`; the watch reads the day clock
  and the moon's phase from the `MOON_PHASE` attribute.
- **Simple Voice Chat**: the plugin as in 0.4.7; the dev clients get voice chat 2.6.24 for 26.2 from Modrinth's Maven.

To check in game (both platforms), besides the list below: particle rain, snow and dust (with and without Fabulous
graphics; dust fading into the ground, fog and sky tinted), damp spots darkening the ground, snow flecks, drops on
windows, and vanilla's weather back when particle weather is turned off; window frost (`/dsweather` can set it) with
and without Fabulous graphics, breath in front of a frosted window; flame-jet sparks and lava droplets as streaks, with and without Fabulous; ground fog (`/dsweather` can set it) with and
without Fabulous, against the sky, water and trees, at each quality, while bobbing and with nausea; storm rain and snow with the storm's strength and sounds,
wind drifting smoke and leaves, flame jets (soft, additive, lighting their surroundings),
steam and smoke puffs, the pocket watch and compass roses (and in the margin), the aurora, and voice chat processing.

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

## New on 26.2: the realistic aurora

Not a port: a second aurora, modelled on real ones, and the default style (`auroraOptions.style`: `REALISTIC` or
`CLASSIC`; `maxBands` moved to `auroraOptions.classic`, `stormChance`, `quality` and `moonWashout` are in
`auroraOptions.realistic`). The config screen shows groups within groups for it (`ClothAPIFactory`).

- **Model** (`effects/aurora/RealisticAurora`): one night's arcs, in kilometres around a viewer on a round Earth, from
  the night's number. Arcs run along magnetic east-west (`declination` from true north), bow at their ends, fold three
  ways (large drifting folds, a travelling wave, small curls) and lean south up the magnetic field. A night has an
  `AuroraActivity` (quiet to storm, storms at `stormChance`) and an `AuroraColour` character. `at(progress)` gives a
  `Moment` for how far through the night it is (`DayCycle.getNightProgress`): growth, then a breakup (brighter, closer,
  faster; a storm comes overhead), its height, and recovery (pulsing patches). Active nights and storms have a ray field.
- **Motion** (`RealisticAuroraMotion`): waves, curls, rays and the ray field move frame by frame at the moment's speed,
  wrapped where their patterns repeat; not `time × speed`, which leaps when the speed changes.
- **Drawing** (`RealisticAuroraRenderer`, `shaders/core/aurora_realistic.*`): one screen pass after the stars, at
  `ISkyRender`, adding light (no depth). Each pixel's line of sight goes through 80 to 450 km up; for each arc it steps
  only through the band the sheet can be in, as many steps as the folds need, finds sign changes of the offset from the
  sheet and narrows each crossing (regula falsi), and adds the light there at once: thickness over how squarely it is
  crossed, so edge on is brighter. Light by height (green, red above the edge, a pink fringe, blue-violet above the
  Earth's shadow), rays from noise along the arc, ragged ray tops and lower edge. The ray field is sampled along the
  line, as many samples as show its rays. Brought into range by the brightest channel; a bright moon washes out the
  faint parts. At `FAST` it is drawn at half size and laid over the sky (`aurora_composite`).
- **Direction from the screen**: taken between two unprojected points, not from the camera, as 26.2's view bobbing in
  the projection moves the camera too.
- **`/dsaurora`**: status; `activity`, `phase` and `colour` hold one until `clear` (or disconnecting).
- **`/dsprofile [scene] [seconds]`** (`profiling/`), for players too, to send when something is slow: sets up a
  scene (`general`, the default, changes nothing; `aurora`, `ground_fog`, `weather`, `frost` hold an effect at its
  costliest and send time/weather commands in single player only), records with Java Flight Recorder from inside the
  game (2 minutes by default) and writes `dsurround-<scene>-<date-time>.jfr` to `config/dsurround/profiles`, with the
  mod's render timers as `dsurround.RenderTimer` events once a second. An indicator at the top of the screen shows the
  time left; the player is told when it is done (the file's name opens it). Only the newest 10 are kept: older ones
  are deleted as the client starts. Without the `jdk.jfr` module the player is told so; only `ProfileRecording` and
  `RenderTimerEvent` use it (a test checks). Nothing private: the events that carry it (command line, environment,
  system properties, processes, paths, addresses, exception messages, the game's network events) are off and filtered
  out again, and the home folder's path is redacted (`Redaction`).
- Measured on Fabric at 2560×1369, Fancy: one arc looking north about 0.6 ms of GPU; a storm overhead about 1.2 to
  1.35 ms; CPU about 0.03 ms.

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
- Realistic aurora on NeoForge (checked on Fabric): `/dsaurora activity storm`, `phase` and `colour` for each look;
  Fast quality; moonlight washout under a full moon.
- Waterfall mist fading into water and terrain (soft), with and without Fabulous graphics; firefly lights on grass.
- Fog effects (biome, morning) now actually change the fog.
- Sound configuration screen: layout, filter, play/stop, tooltips; credits/markdown screen links and hover text.
- Compass scale, clock colour, diagnostics overlay modes including the in-world effect boxes.
- Biome music (`/dsmm whatsplaying`): configured tracks in their biomes, underwater music in oceans, creative music
  with and without `playBiomeMusicWhileCreative`.
