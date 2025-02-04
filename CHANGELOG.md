> ### DynamicSurroundings-1.21.1-0.4.2
**All Loaders**
* JAVA 21+
* Architectury 13.0.8+

**Fabric**
* Fabric Loader >= 0.16.9
* Fabric API >= 0.110.0+1.21.

**NeoForge**
* NeoForge 21.1.84+

**What's New**
* Experimental: Toolbar sounds will play the "block step" sound for block items. It is off by default, and can be turned on in settings.
* Added whatsplaying subcommand to /dsmm. Will report on the music that is playing in the Music Manager.
* Work in Progress: Brought back a variety of block step sounds. Dynamic Surroundings will perform remapping of sound plays to get an updated sound, which is entirely different from the prior implementation. This is not intended to be full-featured as with prior step simulations. Can be disabled in the configuration (Sound Options -> Sound Remapping).

**Changes**
* Do not tint biome fog if biome fog effect is disabled.
* Compass overlay will spin wildly if the dimension is not natural, like the Nether. This mirrors vanilla compass behavior.
* Internal modifications and restructure to facilitate porting to MC 1.21.4. Mojang started its refactor for how component data is encoded, not to mention the always welcome "find out what was renamed and where it was moved to" game.

**Fixes**
* Disabling fog effect actually works
* Compatibility with Nostalgia Tweaks and Distant Horizons world fog effect

> ### DynamicSurroundings-1.21.1-0.4.1
**All Loaders**
* JAVA 21+
* Architectury 13.0.8+

**Fabric**
* Fabric Loader >= 0.16.9
* Fabric API >= 0.110.0+1.21.

**NeoForge**
* NeoForge 21.1.84+

**What's New**
* Added capability to pause/unpause music manager using the /dsmm command.
* Fog effects - Several different types of fog
  * Morning fog Occurs early in the AM and eventually burns off
  * Weather fog when raining
  * Biome fog rendered in the fog color of the biome
* Turtledove sound effect for most forests
* Silent forest biome sound for forests that are snowy/cold (like snowy taiga). It's fairly quiet - light wind blowing through.
* Powered redstone has a chance of emitting an electric arc sound effect.

**Fixes**
* Individual Sound Configuration menu crash when rendering sounds from resource pack.
* Badlands have sound again. (Mesa -> Badlands transition had some challenges.)

> ### DynamicSurroundings-1.21.1-0.4.0
**All Loaders**
* JAVA 21+
* Architectury 13.0.8+ **<===== LOOK HERE**

**Fabric**
* Fabric Loader >= 0.16.9
* Fabric API >= 0.110.0+1.21.

**NeoForge**
* NeoForge 21.1.84+

**What's New**
* Mod versions for both Fabric and NeoForge. To achieve this, I am using the mod Architectury. Architectury is a required dependent.
* Forge support is dependent on Architectury. Once Architectury supports Forge, I will update.
* Support for Minecraft 1.21.1. No plans to support prior versions. Support for later versions based on availability of Fabric and NeoForge.
* Both Fabric and NeoForge versions support connection to Vanilla servers.

**Changes**
* Removed player particle suppression. Mojang changed how effects are associated with a player and made my hack incompatible.
* Changed firefly particle speed, sizing, etc. to be less "energetic."
* Removed dimension sound for The End. On the balance, people disliked more than liked.

**Fixes**
* Occasional incorrect access to randomizer associated with the render thread.
* Fixup tagging for flower forest biome.
* Disable rain ripple effect if the mod Particle Rain is installed.

> ### DynamicSurroundings-Fabric-1.20.4-0.3.3
**Requirements**
* JAVA 17+ (I am using Adoptium https://adoptium.net/)
* Fabric Loader >=0.15.1
* Fabric API >=0.91.2+1.20.4
* 100% client side; no server side deployment needed

**Fixes**
* Better support for BungeeCord server operations, specifically server transfer
* Fix concurrent access exception on randomizer instances

> ### DynamicSurroundings-Fabric-1.20.4-0.3.2
**Requirements**
* JAVA 17+ (I am using Adoptium https://adoptium.net/)
* Fabric Loader >=0.15.1
* Fabric API >=0.91.2+1.20.4
* 100% client side; no server side deployment needed

**Changes**
* Detect Quilt loader at runtime and automatically disable client command registration if present.

**Fixes**
* Biome sounds will play correctly when connected to a Paper branded server.
* Fix playing the elytra flying sound.

> ### DynamicSurroundings-Fabric-1.20.4-0.3.1
**Requirements**
* JAVA 17+ (I am using Adoptium https://adoptium.net/)
* Fabric Loader >=0.15.1
* Fabric API >=0.91.2+1.20.4
* 100% client side; no server side deployment needed

**What's New**
* Added a button to Minecraft's Sound Configuration Options screen for accessing the Individual Sound Configuration menu of the mod. It can be found in the lower left corner of the screen.
* Added option to enable playing of other situational music (as in biome background music) rather than creative music when the player is in creative mode. It is off by default and needs to be enabled. Makes testing resource/mod packs easier.
* Added a client side command /dsmm to force a reset of Minecraft's music manager. This will clear any music being played and set the timers so that a new selection will be made within a few seconds.
* Added option to disable client side command registration. Useful when trying to run under Quilt, or if a pack developer just wants to disable as a default.

**Changes**
* Reworked the display of the individual sound configuration UI. The goal is to slim things down so the information fits on smaller displays. WIP - the icons suck.
* Preload tag cache when connecting to a world. Before, the mod would load entries into the cache in a lazy fashion, and as a result, the player would experience a lag-stutters when faulted in.
* Delay processing resource packs/external configurations until later in the startup process.

**Fixes**
* Startup crash when ModernUI is installed with its "ding at startup" enabled.

> ### DynamicSurroundings-Fabric-1.20.4-0.3.0
**Requirements**
* JAVA 17+ (I am using Adoptium https://adoptium.net/)
* Fabric Loader >=0.15.1
* Fabric API >=0.91.2+1.20.4
* 100% client side; no server side deployment needed

**What's New**
* Support for Serene Seasons. Seasonal changes will affect temperature-dependent effects (like frost breath and some biome sounds). Supporting other season mods is dependent on whether the season mod provides a formal API for obtaining temperature and precipitation information.
* Added a global option to disable scary sounds. Currently, this applies to the wolf and the underground monster growl. Scary is subjective, so I would be glad to hear opinions about other sounds in the mod. This only applies to sounds generated by Dynamic Surroundings. If another mod has a scary sound, you can block it using the Individual Sound Control GUI in game (assuming the keybind is mapped).
* Added a global volume scale setting for ambient sounds. This applies only to Dynamic Surroundings sound effects. Intended to give a simple knob to increase/decrease the volume of the mod's environmental sounds.

**Changes**
* Reworked the internal tagging for items, blocks, and biomes. The mod no longer declares tags in /data. The mod's tag configuration has been moved into client side resources. (I have my own tag scanning code for loading tag information as the mod is 100% client side.)
* Added resource support for Biomes O'Plenty, Nature's Spirit, Profundis, and Promenade.

**Fixes**
* Minimal particle settings can cause mod to crash with waterfall effects.
* Disconnecting and reconnecting can result in the loss of biome sound effects.
* Footstep sound effects would not play when connected to a remote server.

> ### DynamicSurroundings-Fabric-1.20.4-0.2.2
**Requirements**
* JAVA 17+ (I am using Adoptium https://adoptium.net/)
* Fabric Loader >=0.15.1
* Fabric API >=0.91.2+1.20.4
* 100% client side; no server side deployment needed

**Fixes**
* Stack overflow when circular dependencies in tag files encountered

> ### DynamicSurroundings-Fabric-1.20.4-0.2.1
**Requirements**
* JAVA 17+ (I am using Adoptium https://adoptium.net/)
* Fabric Loader >=0.15.1
* Fabric API >=0.91.2+1.20.4
* 100% client side; no server side deployment needed

**What's New**
* Added biome sound for coniferous forests, like Taiga. I finally found one I kinda like.
* Added step sound on leaf blocks.  It will auto-disable if Presence Footsteps is installed.
* Reed like sound when walking through sugar cane and other "stiff" plants. The regular brush sound didn't fit right for these types of plants.
* Biome scanning algorithm now accounts for Minecraft's 3D biome structure, like caves. Cave biomes can have sound effects tied to being underground. Additionally, underground biome sounds will fade in the lower the player's Y.
* If enabled, the clock overlay will render if the player is looking at item frame containing a clock.

**Changes**
* Waterfall particles are more splashy.
* Reworked internal eventing implementation. Gained a bit of performance and decoupled entirely from loader environment.
* Reworked effects configuration system to be more data driven.

**Fixes**
* Mod config sporadically isn't present in ModMenu.
* Water ripple handler was removing rain impact particle effect for non-liquid blocks.
* Accumulated updates for forge data tags as well as Biomes O'Plenty assets.

> ### DynamicSurroundings-Fabric-1.20.4-0.2.0
**Requirements**
* JAVA 17+ (I am using Adoptium https://adoptium.net/)
* Fabric Loader >=0.15.1
* Fabric API >=0.91.2+1.20.4
* 100% client side; no server side deployment needed

**What's New**
* Cloth-Config is no longer embedded in the jar. Mod will use either Cloth-Config or Yet Another Config Library if installed.
  * If both are installed Cloth-Config will be used
  * If neither are installed, the mod will still work—you just won't have access to the mod configuration menu and will have to make hand edits to the dsurround.json config file.
* Added Forge biome tags. Enables the broadest out-of-the-box support for cross-loader biome mods.

**Changes**
* Moved clock overlay display to above the hot bar. Seemed dorky to have it above the cross-hair. Rendering is similar to a tooltip, and the text color is a gradient between midnight (Dark Violet) and noon (Sun Glow) colors.
* Auto-disable footstep brush effect at runtime if Presence Footsteps is installed
* Reworked debug overlay screen for better organization. Added information about entities as well.

**Fixes**
* Rendering of compass overlay is smoother. I forgot to lerp.
* Null reference exception when player spawns outside of build height
* Sometimes the RandomGenerator algorithms for modern Java are not available (for some unknown reason) and will cause Dynamic Surroundings to crash at startup.  If it is not possible to create this generator, logic will fall back to using the Minecraft random generator. Reinstalling the modpack from scratch may help.

> ### DynamicSurroundings-Fabric-1.20.4-0.1.0
**Requirements**
* JAVA 17+ (I am using Adoptium https://adoptium.net/)
* Fabric Loader >=0.15.1
* Fabric API >=0.91.2+1.20.4
* 100% client side; no server side deployment needed

**What's New**
* Added key bind to activate the mod menu while in-game.
  * If ModMenu is present, the key bind is not set in favor of using the various "Mod" configuration entry points.
  * If ModMenu is not present, will default to the '=' key and can be accessed in game.

**Changes**
* Added more randomness to steam effects. In large steam areas, the particle spawns seemed a bit uniform.
* Increased reverb decay time of sounds.  Needed more reverb.  FYI - reverb and echo are two different things.
* Updated randomizers to use the newer random number generators in Java.  Performance increased a small amount.
* Improve handling of tags when connected to remote server.  Should be more performant.
* Short circuit some checks/operations for a set of blocks that should always be ignored, like air and command_block.
* Attributions in sounds.json are now structured.  (Attributions for sounds show up in the tooltips while in the individual sound config menu.)
* Added subtitle information to sound config entries tooltip.  Subtitles are text shown when a sound plays if the option is enabled in the sound menu.  (Not all sounds have subtitles defined—it's optional.)
* Use official Mojang mappings rather than Yarn.  Should be transparent but let me know if anything strange happens.
* Misc clean up such as removing loader-specific info from language file, recoloring of version update notification, etc.

**Fixes**
* Bow-use sound when connected to a vanilla server will now play.
* Fixed concurrency issue when the background sound operations triggered tag access in the TagLibrary. The exception was an index range exception generated in a fastutil collection. The times I have experienced this problem are when joining a world where the location was sound dense, like large waterfalls.

> ### DynamicSurroundings-Fabric-1.20.4-0.0.9
**Requirements**
* JAVA 17+ (I am using Adoptium https://adoptium.net/)
* Fabric Loader >=0.15.1
* Fabric API 0.91.2+1.20.4
* 100% client side; no server side deployment needed

**What's New**
* Overhauled the waterfall sound effect system.  The number of sound instances played is significantly reduced around large falls.
* Lava cauldrons have effects:
  * Count as a hot block for steam production.
  * Chance of spawning small flame jets similar to fully grown nether wart crops.
* Kelp plants and tall seagrass will trigger brush step sounds.

**Changes**
* Adjusted item swing sounds for bows and crossbows. Now less hefty.
* Changes to block effect settings will no longer require a restart.
  * Adjusting while in game will cause the currently playing effects to reset.
* Default for block effect range is now 32 from 24.
  * The 32 range matches the Minecraft particle render range as well as the larger random display tick range.
  * Higher ranges will consume more of the client tick for processing and may not add any additional experience. YMMV.
  * Existing configurations will require manual changes of the block effect range to get the range of 32.
* Diagnostic commands and HUDs will report local tag information even when connected to a remote server.

**Fixes**
* No more button click sound when pressing the PLAY button in individual sound config UI.
* Fixed step brush sounds when connected to a vanilla server.
* Dark Forest should now play the deep forest sounds.

> ### DynamicSurroundings-Fabric-1.20.4-0.0.8
**Requirements**
* JAVA 17+ (I am using Adoptium https://adoptium.net/)
* Fabric Loader >=0.15.1
* Fabric API 0.91.2+1.20.4
* 100% client side; no server side deployment needed

**What's New**
* Added footstep accents when the player steps.  These can be independently disabled in the config.
  * Armor rustling based on worn armor.  Derived from the items equip sound.
  * Water splash when raining, walking on waterlogged blocks, or blocks like lily pads
  * Floor squeaks when walking on squeaky floors (like wood planks)
  * Brush sound when walking through dense brush or climbing vines
* Added Firefly particle effect.  Spawn around flowers at night when it is not raining.
* Added a compass and clock overlay that displays when a compass and/or clock is held.  Disabled by default.

**Changes**
* Config setting for particle trail effect on projectiles has changed.  By default, suppression is not enabled.  This can be changed in config settings.
* Tweaked diurnal processing - sunrise and day start should occur a little bit earlier.
* Removed time of day (/dstod) command

**Fixes**
* Fixed mixin conflict with SoundPhysics Remastered.  New behavior is to Auto-disable enhanced sound processing if SoundPhysics Remastered is present.

> ### DynamicSurroundings-Fabric-1.20.4-0.0.7
**Requirements**
* JAVA 17+ (I am using Adoptium https://adoptium.net/)
* Fabric Loader >=0.15.1
* Fabric API 0.91.2+1.20.4
* 100% client side; no server side deployment needed

**What's New**
* Removed frog sound effects since Minecraft has frogs.  I think they need a fez.
* Detection of "built in" toolbar effect sounds, such as armor and buckets if a dsurround effect tag is not already supplied.  (These guys have item equip sounds.)
* Works when connecting to Vanilla servers - yay!  Includes handling impact of tag sync between server and client.

**Fixes**
* Using a bow/crossbow will no longer trigger the swing sound
* Cleaned up first person potion particle suppression.  Config setting change will no longer require a restart of the client.
* Null ref exception triggered as a result of a race condition when connecting to a server.
* Fixed rendering of the in-game individual sound config screen
* Cleaned up new version detection and messaging
* Fixed /reload and /dsreload effects on cached data

> ### DynamicSurroundings-Fabric-1.20.4-0.0.6
**Requirements**
* JAVA 17+ (I am using Adoptium https://adoptium.net/)
* Fabric Loader >=0.15.1 (*<-- Note the change*)
* Fabric API 0.91.2+1.20.4
* 100% client side; no server side deployment needed

**Special Thanks**
* Thanks to ThexXTURBOXx, HarvelsX, and jmattingley23 for compatility changes for 1.18 and 1.19 - made my life easier getting to 1.20.x

**What's New**
* Minecraft 1.20.4 compatibility
  * Updated Nashorn script engine to 15.4
  * When will Microsoft stop boiling the ocean? :D
* Support for client side tags - should ease addition of new blocks and providing Dynamic Surround support via data packs.
  * Block reflectance and occlusions for sound reverb processing
  * EntityType tags for entity effects (bow pull, frost breath, etc.)
  * Item tags for Item sound effects (toolbar and swing)
  * Support of Biome tags for biome conditions

**Fixes**
* Bunch of small fixes too numerous to list :)

> ### DynamicSurroundings-Fabric-1.17.1-0.0.4
**Requirements**
* JAVA 16+ (I am using Adoptium https://adoptium.net/)
* Fabric Loader >=0.11.7 (*<-- Note the change*)
* Fabric API 0.40.8+1.17
* 100% client side; no server side deployment needed

**What's New**
* Volume and pitch ranges for acoustic configurations.  What this means, practically, is that some sounds that play will have variation in pitch and volume, like frog croaks and soul sand laughter.  These variations give a bit more texture especially if there are several instances of a sound playing simultaneously.  It also helps avoid harmonics if there are a large number of similar sounds playing at the same time.

**Fixes**
* Speculative fix for client crash when exiting world.

**Changes**
* Reworked a piece of code so that it can be compatible with the 0.11.7 loader
* Tweaked waterfall splash to be a bit more "splashy"

> ### DynamicSurroundings-Fabric-1.17.1-0.0.3
**Requirements**
* JAVA 16+ (I am using Adoptium https://adoptium.net/)
* Fabric Loader >=0.12.1
* Fabric API 0.40.8+1.17
* 100% client side; no server side deployment needed

**What's New**
* Stomach grumble when a player's food level is < 4 or has the hunger debuff.  You can block the sound "durround:player.tummy" if you don't like.
* Particle breath effect in cold biomes.  On by default; can be disabled in the config.
* Sound Pruning - do not play sounds if they are too far away from the player.  Reduces load on Minecraft's sound engine, and in turn will improve enhanced sound processing.  Will not apply to global, repeating, or WEATHER sounds.  On by default; can be disabled in the config.
* Waterfall block effect
  * Can occur when water flows downward
  * On by default.  Can be turned off in the config.
  * Two other independent controls for modifying behavior:
    * Disable sounds.  This will permit the particle effects to appear without the audio.  Sound lag could occur with enhanced processing, with larger falls, on systems that do not have a strong CPU.
    * Disable particles.  This will permit the sounds to play without the visuals.  Render lag could happen on systems that do not have a more recent vintage of video card.
* Tuning options for enhanced sound processing.  Gives some knobs to twist when the computer has a potato CPU or something much better.  Options can be found in the enhanced sound processing config section.

**Fixes**
* NRE crashes when processing sound loop logging.  This could show up in different ways, such as quiting a world or teleporting.
* Don't do client ticking until player chunk is loaded.  This primarily affects area block scanning.
* Area block scanning and effects for height < 0.
* Client side block update detection failing for some modpacks.

**Changes**
* Changed the underpinnings of how the mod config menu is generated.  More colorful and the tooltips are better.
* Additional biome trait detection rules improving support of modded biomes.
* 3D biome scanning.  Allows for vertical biomes as well as the classic horizontal biome mix.

> ### DynamicSurroundings-Fabric-1.17.1-0.0.2
**Requirements**
* JAVA 16+ (I am using Adoptium https://adoptium.net/)
* Fabric Loader >=0.12.1 (*<-- Note the bump*)
* Fabric API 0.40.8+1.17 (*<-- Note the bump*)
* 100% client side; no server side deployment needed

**What's New**
* Enhanced Sound Processing.  You guys apparently liked it enough, so I added it back.
  * Reverb (not echo) in caves and the like.  Reverb and echo are two different things.
  * Block occlusion processing.  Disabled by default.
  * Conversion of sound buffers on the fly to mono if needed.  This will happen if a stereo sound is played in 3d space rather than global.
  * Though using background threads it can be intensive on lower end CPUs.  Feature can be disabled in the configs.
* Bow pull sound effect.  Applies to players and skeleton like mobs.
* [WIP] Online version checking message to chat window to get notified of mod updates.  On by default.
* Optional tweaks to some particle behaviors
  * Disable player potion swirls in first person.  Off by default.  (Not sure about you guys but these particles can cause me a bit of vertigo.)
  * Disable projectile particle effects.  On by default.
* /dstod Time of Day client command for displaying current in game Minecraft date
  * Experimental - need feedback whether it would be immersion breaking

> ### DynamicSurroundings-Fabric-1.17.1-0.0.1
**Requirements**
* JAVA 16+ (I am using Adoptium https://adoptium.net/)
* Fabric Loader >=0.11.7
* Fabric API 0.40.6+1.17
* 100% client side; no server side deployment needed

**Recommended Additions**
* Mod Menu to get in-game mod configuration menu.

Initial release.