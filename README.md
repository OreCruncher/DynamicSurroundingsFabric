> ### Dynamic Surroundings: Fabric Edition
A Minecraft Fabric Mod that alters the fabric of Minecraft experience by weaving a tapestry of sound and visual effects.

<a href="https://www.curseforge.com/minecraft/mc-mods/dynamic-surroundings-fabric-edition"><img src="http://cf.way2muchnoise.eu/versions/dynamic-surroundings-fabric-edition.svg" alt="CurseForge Project"/></a>
</br></br>
<a href="https://www.curseforge.com/minecraft/mc-mods/dynamic-surroundings-fabric-edition"><img src="http://cf.way2muchnoise.eu/full_dynamic-surroundings-fabric-edition_downloads.svg" alt="CurseForge Project"/></a>
</br></br>

This mod is a spiritual successor to the Forge based Dynamic Surroundings series.  Though a lot of the functionality is similar the ultimate feature set will be different.  My focus is on extending the Vanilla aspect of gameplay rather than introducing anything radical.  Besides, Microsoft is adding features to Minecraft that fill the gaps that Dynamic Surroundings has historically tried to address.  I expect this trend to continue.

**Requirements**
* JAVA 16+ (I am using Adoptium https://adoptium.net/)
* Fabric Loader >=0.11.7
* Fabric API 0.40.6+1.17
* 100% client side; no server side deployment needed

**Recommended Additions**
* Mod Menu to get in-game mod configuration menu.

**Features**
* Individual Sound Control - Set key bind and activate in-game.  Use this feature to block, cull, and control the volume at which sounds play.  And as a bonus you can play the sound to hear it.
* Biome sounds - Various atmospheric sounds that play based on biomes in the area.  Seamless blending of sounds as the player moves throughout the world.
    * This does not replace the Minecraft feature of a singular biome background sound.  Currently, the various biomes in the Nether use this capability, and Dynamic Surroundings does not have sound configurations for that dimension.
* Hot block effects such as flame jets over lava, and steam where water hits a hot block.
    * Hot blocks are things like Lava and Magma.
* Replace Minecraft's thunder sound with improved versions.
* Various "DS" client side commands for dumping configuration information.  (These are currently used by me while developing.  I do plan on documenting at some point for general use.)
* Custom debug HUD that can be accessed by key bind.  Moves the Dynamic Surroundings clutter out of the traditional F3 display.

**Embedded Jars**
* Cloth Config API (Fabric) for configuration menu support.  Add Mod menu to your pack, and you can activate from the Mod's screen.
* OpenJDK Nashorn JavaScript Engine 15.3 (https://github.com/openjdk/nashorn)

**FAQ**
* Will there be releases for earlier versions of Minecraft?
    * No.
* Will additional features from the Forge version make it's way into the Fabric version?
    * See below.
* Will will there be updates to the Forge version of Dynamic Surroundings?
    * At the moment I am not sure.  I have limited time, so I need to be a bit picky about what I do.  I have found that writing Fabric mods is much easier than on Forge, though it could just be the nature of what my mod does.  Take this as a biased assessment.

**What's Being Dropped**
* Aurora.  Good at turning a computer into a space heater, and I do not know enough about shaders to improve.
* Mob Effects in its entirety.  Still thinking about carrying some of this functionality over.  (Mob effects are the things that give enhanced footsteps and other acoustics related to mobs.)
* Sound processing (reverb, though folks commonly confuse this with echo - they are two different things).
* Waterfalls.  They are tricky to maintain and can bring a potato CPU to its knees.
* Specialized fog effects.  Minecraft has made some improvements in this area, and I expect it to continue.  I don't want to be in a position of overriding (or managing the problem) of when Microsoft adds more dynamic content.
* Weather effects.  Again, there have been improvements to Minecraft.  I may add some additional processing around weather, but I do not expect to make major changes.

As I indicated these features are not planned.  Based on time commitments I may change my mind. :) 

**Planned Features**
* Hunger grumble.  This was removed in Forge versions, but I am missing it a bit.  Will need to find some better sounds though.
* Rustling sounds when walking through plants and the like.  This is a feature of footstep acoustics that I am planning on reusing.  Call it the "walk through grass" sound feature.
* Making the config system publicly available so that pack authors can configure things.  This is possible with this release, but I may change things.  Besides, it has to be documented so that someone would know what to do.
* Notification of mod updates.  Need to see if there is a mechanism to do that already.

> ### License
The MIT License (MIT)

Copyright (c) 2021 OreCruncher

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