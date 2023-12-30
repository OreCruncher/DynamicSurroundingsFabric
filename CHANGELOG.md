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