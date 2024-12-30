> # Dynamic Surroundings for Fabric and NeoForge
A Minecraft Mod that alters the fabric of Minecraft experience by weaving a tapestry of sound and visual effects.

 <div style="text-align: center;">
    <a href="https://www.curseforge.com/minecraft/mc-mods/dynamic-surroundings"><img src="http://cf.way2muchnoise.eu/versions/238891.svg" alt="CurseForge Project"/></a>
    <a href="https://www.curseforge.com/minecraft/mc-mods/dynamic-surroundings"><img src="http://cf.way2muchnoise.eu/full_238891_downloads.svg" alt="CurseForge Project"/></a>
 </div>

 <div style="text-align: center;">
    <a href="https://modrinth.com/mod/dynamicsurroundingsfabric"><img src="https://img.shields.io/modrinth/game-versions/H7fshfpD?style=flat&label=Available%20for&color=%2300AF5C" alt="Modrinth"></a>
    <a href="https://modrinth.com/mod/dynamicsurroundingsfabric"><img src="https://img.shields.io/modrinth/dt/H7fshfpD?style=flat&logo=modrinth&label=downloads" alt="Modrinth"></a>
 </div>

This mod is a successor to the Forge-based Dynamic Surroundings series.
Though a lot of the functionality is similar, the ultimate feature set will be different.
My focus is on extending the Vanilla aspect of gameplay rather than introducing anything radical.
Mojang is adding features to Minecraft filling the gaps that Dynamic Surroundings have tried to address.
I expect this trend to continue.

The mod is 100% client side. You can add to any mod pack, whether you play standalone or multiplayer.

Starting with Minecraft 1.21.1, Dynamic Surroundings is supported on Fabric and NeoForge.

Online documentation: https://dynamic-surroundings.readthedocs.io/en/latest/index.html

Documentation repository: https://github.com/OreCruncher/DynamicSurroundingsDocs

### Minecraft 1.21.1 Requirements
* JAVA 21+
* Architectury 13.0.8+

**Fabric**
* Fabric Loader >= 0.16.9
* Fabric API >= 0.110.0+1.21.

**NeoForge**
* NeoForge 21.1.84+

### Minecraft 1.20.4 Requirements
* JAVA 17+ (I am using Adoptium https://adoptium.net/)
* Fabric Loader >=0.15.1
* Fabric API 0.91.2+1.20.4
* 100% client side; no server side deployment needed

### Minecraft 1.20.1 Requirements
* JAVA 17+ (I am using Adoptium https://adoptium.net/)
* Fabric Loader >=0.14.22
* Fabric API >=0.91.0+1.20.1
* 100% client side; no server side deployment needed

## Recommended Additions
* For Config UI support, Cloth Config API [(CurseForge)](https://www.curseforge.com/minecraft/mc-mods/cloth-config) or [(Modrinth)](https://modrinth.com/mod/cloth-config) is supported. If not present, the in-game mod configuration pages will not be available.
* On Fabric, Mod Menu [(Modrinth)](https://modrinth.com/mod/modmenu) to get in-game mod configuration menu listings.
* Presence Footsteps [(Modrinth)](https://modrinth.com/mod/presence-footsteps) for footstep acoustics

## Videos
* Steam, Fire Jets, and Waterfalls [(YouTube)](https://youtu.be/guMuLeG3lck)
* Cave Reverb [(YouTube)](https://youtu.be/KGFZ1zf9R2s)
* Having a wander [(YouTube)](https://youtu.be/GbwaGX3JWeM)

## Features
* Enhanced Sound processing - performs calculations in the background, adding a reverb effect to spacial sounds.
* Individual Sound Control - Set key bind and activate in-game.  Use this feature to block, cull, and control the volume at which sounds play.  And as a bonus, you can play the sound to hear it.
* Biome sounds - Atmospheric sounds that play based on biomes in the area.  Seamless blending of sounds as the player moves throughout the world.
    * This does not replace the Minecraft feature of a singular biome background sound.  Currently, the various biomes in the Nether use this capability. Dynamic Surroundings does not provide sound configurations for that dimension.
* Hot block effects such as flame jets over lava, and steam where water hits a hot block.
    * Hot blocks are things Lava, Magma, campfires, and a cauldron containing lava.
* Waterfall sound and visual effect - will trigger when flowing water is detected nearby.
* Replace Minecraft's thunder sound with improved versions.
* Various "DS" client side commands for dumping configuration information.  (I currently use these while developing. I do plan to document at some point for general use.)
* Custom debug HUD that can be accessed by key bind.  Moves the Dynamic Surroundings clutter out of the traditional F3 display.
* Compatibility with Serene Seasons - variations in seasons and temperatures can influence effects.
* Dynamic Surroundings is compatible when connecting to a Vanilla servers. Both Fabric and NeoForge loaders can do this.

## Embedded Jars
* Fabric version has OpenJDK Nashorn JavaScript Engine 15.4 (https://github.com/openjdk/nashorn)

## FAQ 
* Will there be updates for the Forge loaders?
  * Not until Architectury has support for Forge 1.21.1+

## What's Being Dropped
* Aurora.  Good at turning a computer into a space heater, and I do not know enough about shaders to improve.
* Specialized fog effects.  Minecraft has made some improvements in this area, and I expect it to continue.  I don't want to be in a position of overriding (or managing the problem) of when Microsoft adds more dynamic content.
* Weather effects.  Again, there have been improvements to Minecraft.  I may add some additional processing around weather, but I do not expect to make major changes.
* Footstep sound effects. Trimmed to reduce sound fatigue. There are some effects that were preserved, such as walking through brush and low-key armor sounds. If you are looking for that old experience, I recommend the mod Presence Footsteps.
* Player particle suppression. Mojang changed how player particle effects are handled and there isn't a clean way to suppress.

As I indicated, these features are not planned.  Based on time commitments, I may change my mind. :) 

## Planned Features
* Making the config system publicly available so that pack authors can configure things.  This is possible with this release, but I may change things.  Besides, it has to be documented so that someone would know what to do.

> # License
The MIT License (MIT)

Copyright (c) 2023-2025 OreCruncher

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.