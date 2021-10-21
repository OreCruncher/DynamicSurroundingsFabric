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