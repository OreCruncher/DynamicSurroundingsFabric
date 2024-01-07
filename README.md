> ### Dynamic Surroundings: Fabric Edition
A Minecraft Fabric Mod that alters the fabric of Minecraft experience by weaving a tapestry of sound and visual effects.

<a href="https://www.curseforge.com/minecraft/mc-mods/dynamic-surroundings-fabric-edition"><img src="http://cf.way2muchnoise.eu/versions/535715.svg" alt="CurseForge Project"/></a>
<a href="https://www.curseforge.com/minecraft/mc-mods/dynamic-surroundings-fabric-edition"><img src="http://cf.way2muchnoise.eu/full_535715_downloads.svg" alt="CurseForge Project"/></a>
<a href="https://modrinth.com/mod/dynamicsurroundingsfabric"><img src="https://img.shields.io/badge/Mod-rinth-brightgreen" alt="Modrinth"></a>

This mod is a spiritual successor to the Forge-based Dynamic Surroundings series.
Though a lot of the functionality is similar, the ultimate feature set will be different.
My focus is on extending the Vanilla aspect of gameplay rather than introducing anything radical.
Microsoft is adding features to Minecraft filling the gaps that Dynamic Surroundings have tried to address.
I expect this trend to continue.

**Requirements**
* JAVA 17+ (I am using Adoptium https://adoptium.net/)
* Fabric Loader >=0.15.1
* Fabric API 0.91.2+1.20.4
* 100% client side; no server side deployment needed

**Recommended Additions**
* [Mod Menu (CurseForge)](https://www.curseforge.com/minecraft/mc-mods/modmenu) or [ModMenu (Modrinth)](https://modrinth.com/mod/modmenu) to get an in-game mod configuration menu.
* [Presence Footsteps (CurseForge)](https://www.curseforge.com/minecraft/mc-mods/presence-footsteps) or [Presence Foosteps (Modrinth)](https://modrinth.com/mod/presence-footsteps) for footstep acoustics

**Features**
* Enhanced Sound processing - performs calculations in the background, adding a reverb effect to spacial sounds.
* Individual Sound Control - Set key bind and activate in-game.  Use this feature to block, cull, and control the volume at which sounds play.  And as a bonus you can play the sound to hear it.
* Biome sounds - Atmospheric sounds that play based on biomes in the area.  Seamless blending of sounds as the player moves throughout the world.
    * This does not replace the Minecraft feature of a singular biome background sound.  Currently, the various biomes in the Nether use this capability, and Dynamic Surroundings does not have sound configurations for that dimension.
* Hot block effects such as flame jets over lava, and steam where water hits a hot block.
    * Hot blocks are things like Lava and Magma.
* Waterfall sound and visual effect - will trigger when flowing water is detected nearby.
* Replace Minecraft's thunder sound with improved versions.
* Various "DS" client side commands for dumping configuration information.  (I currently use these while developing. I do plan to document at some point for general use.)
* Custom debug HUD that can be accessed by key bind.  Moves the Dynamic Surroundings clutter out of the traditional F3 display.

**Embedded Jars**
* Yet Another Configuration Library for configuration menu support.
* OpenJDK Nashorn JavaScript Engine 15.4 (https://github.com/openjdk/nashorn)

**FAQ**
* Will there be releases for earlier versions of Minecraft?
    * No.  Minecraft experiences large number of internal changes between releases and it would become a chore.
* Will additional features from the Forge version be integrated into the Fabric version?
    * See below.
* Will will there be updates to the Forge/Neoforge version of Dynamic Surroundings?
  * Forge - highly unlikely.  I do not disagree with the perspective of the Neoforge team regarding Forge. 
  * Neoforge, I am not sure.  Looking into hybrid development so that the mod can be built for Fabric and Neoforge at the same time.

**What's Being Dropped**
* Aurora.  Good at turning a computer into a space heater, and I do not know enough about shaders to improve.
* Specialized fog effects.  Minecraft has made some improvements in this area, and I expect it to continue.  I don't want to be in a position of overriding (or managing the problem) of when Microsoft adds more dynamic content.
* Weather effects.  Again, there have been improvements to Minecraft.  I may add some additional processing around weather, but I do not expect to make major changes.

As I indicated, these features are not planned.  Based on time commitments, I may change my mind. :) 

**Planned Features**
* Making the config system publicly available so that pack authors can configure things.  This is possible with this release, but I may change things.  Besides, it has to be documented so that someone would know what to do.

> ### License
The MIT License (MIT)

Copyright (c) 2023-2024 OreCruncher

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