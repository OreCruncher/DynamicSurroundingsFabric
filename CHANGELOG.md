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