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

This mod is a successor to the Forge-based Dynamic Surroundings series. The mod is 100% client side. You can add to any
mod pack, whether you play standalone or multiplayer.

NeoForge is supported, in addition to Fabric, with versions 1.21.1+.

Online documentation: https://dynamic-surroundings.readthedocs.io/en/latest/index.html. This documentation is comprehensive
and covers settings for players as well as modpack/resource pack authors. The repository for documentation is located at
https://github.com/OreCruncher/DynamicSurroundingsDocs.

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
* Presence Footsteps [(Modrinth)](https://modrinth.com/mod/presence-footsteps) for detailed footstep acoustics.
* Simple Voice Chat [(CurseForge)](https://www.curseforge.com/minecraft/mc-mods/simple-voice-chat) or [(Modrinth)](https://modrinth.com/plugin/simple-voice-chat) to have nearby players' voices processed like other sounds (reverb, occlusion).

## Videos
* Steam, Fire Jets, and Waterfalls [(YouTube)](https://youtu.be/guMuLeG3lck)
* Cave Reverb [(YouTube)](https://youtu.be/KGFZ1zf9R2s)
* Having a wander [(YouTube)](https://youtu.be/GbwaGX3JWeM)

## Features
* Biome sounds - Various atmospheric sounds that play based on biomes in the area
  * Seamless blending of sounds as the player moves throughout the world
  * Handles Minecraft biomes as well as many modded ones
* Client side wind simulation (1.21.1-0.4.7+)
  * Varying direction and speed over time
  * Influences the drift of particles of the mod, such as steam and fire jets
* Client side storm simulation (1.21.1-0.4.7+)
  * Rain, snow, and dust particle like effects in applicable biomes
  * Varying storm intensity which drives the number of particles
  * Simulated wind affects direction and velocity of precipitation particles
  * Rain, snow, and dust blown in under overhangs drift a little way in and fade (1.21.1-0.4.8+)
  * Rain and snow stop on top of blocks from other mods that Minecraft's own rain falls through, such as Mekanism's energy cube (1.21.1-0.4.8+)
* Lightning that branches and forks, more the stronger the thunderstorm, flickering with each stroke (1.21.1-0.4.8+)
  * Replaces Minecraft's bolts, and adds bolts, spider lightning, and flashes in the clouds through a thunderstorm, mostly upwind
  * Thunder from each strike arrives later the farther away it is
  * Can be turned off in the Scary Content options
* Dynamic clouds that change with the weather (experimental) (1.21.1-0.4.8+)
  * Fair-weather clouds build up through the afternoon; a storm closes them into a low, dark deck
  * Cloudier over wet biomes than dry, and the same for every player in a world
  * Lit from inside by lightning
* Ground fog in swamps and eerie biomes (experimental) (1.21.1-0.4.7+)
  * Hugs the ground, and drifts a bit based on wind
  * Intense rain will diminish the effect, but will come back once rain has finished
* Frost on the outside of windows in cold weather (1.21.1-0.4.7+)
  * Grows in from the window's edges on clear glass blocks and panes, thickest before sunrise
  * The sun melts it from the windows it shines on
* Sky fog density changes driven by biome, time of day, and weather.
* Waterfall sound and visual effects - will trigger when flowing water is detected nearby
  * Misting where waterfalls hit a full block surface
  * Foam generated by 1 block high falls which follow the flow of the water
* Aurora display in the northern sky when standing in a cold/icy/taiga biome (1.21.1-0.4.6+)
* Firefly particle effect around flowers (1.21.1-0.4.6+)
  * Particles blink like real fireflies
  * A faint glow lights up a small area around the particle
* Hot block effects such as flame jets over lava, and steam where water hits a hot block
  * Hot blocks are things Lava, Magma, campfires, and a cauldron containing lava
* Skeleton skulls placed in the world burn with little flames in their eyes, blue soul fire for wither skeleton skulls (1.21.1-0.4.8+)
* Block effects are found in a sphere around the player, nearest first; the range can reach past Minecraft's particle distance, up to 64 blocks (1.21.1-0.4.8+)
* Burning mobs are wreathed in flame jet style flames, sparks, and smoke instead of flat fire sheets (1.21.1-0.4.8+)
  * Sized to the mob, keeps up when it runs, and lights the area around it
* Dark mist where a mob's body vanishes when it dies, in place of the white puffs (1.21.1-0.4.8+)
* Damage pop-offs: numbers pop off mobs and players when their health changes, with comic book words for heavy blows (1.21.1-0.4.8+)
* Footprints left by players and mobs in soft ground such as dirt, sand, gravel, snow, and mud (1.21.1-0.4.8+)
  * Square, paw, hoof, and bird prints depending on the feet; players can pick their style
  * Prints fade over time, last longer in snow, and wash away in the rain
* Enhanced Sound processing - sounds playing in 3d space will have a reverb effect based on the surrounding materials
  * Simple Voice Chat support (experimental) (1.21.1-0.4.7+) - nearby players' voices get the same reverb and occlusion
* Individual Sound Control - Set a key bind and activate in-game
  * Use this feature to block, cull, and control the volume at which sounds play
* Replace Minecraft's thunder sound with improved versions.
* Remap of block step sounds to "improved" versions (basically a lighter weight Presence Footsteps)
  * Other players and humanoid mobs will also have these step sound remaps!
* Various "DS" client side commands for dumping configuration information. Useful for authors wanting to see how things are configured at runtime.
* Compatibility with Serene Seasons - variations in seasons and temperatures can influence effects.
* Compass and clock display when holding a compass or clock
  * Compass shown as a band that slides with the heading, or as a compass rose laid flat in front of the player (1.21.1-0.4.7+)
  * Clock shown as text, or as a pocket watch (worn and round, or square in pixel art) whose hands tick from minute to minute, with windows for day or night and the season or moon phase (1.21.1-0.4.7+)
  * Optionally shown in a side or corner of the screen while a compass or clock is in the hot bar (1.21.1-0.4.7+)
  * Either can be turned off, and their textures replaced by resource packs (1.21.1-0.4.7+)
* Light level overlay, turned on and off by key bind, showing the light on the blocks around the player colored by whether monsters can spawn there, with an on screen legend (1.21.1-0.4.7+)
* Guards that repair problems other mods cause, such as particles drawn as squares, naming the mod responsible in the log; each can be turned off (1.21.1-0.4.8+)
* Custom debug HUD that can be accessed by key bind. Moves the Dynamic Surroundings clutter out of the traditional F3 display.
* Dynamic Surroundings is compatible when connecting to a Vanilla servers (a feature of both Fabric and NeoForge loaders).

## Embedded Jars
* Fabric 0.4.2 and earlier has OpenJDK Nashorn JavaScript Engine 15.4 (https://github.com/openjdk/nashorn). For 0.4.3+ there is no JavaScript engine dependency.

## FAQ 
* Do you use AI for features in the mod?
  * AI is used for a variety of tasks, including code creation, review, bug fixing, performance analysis, and mod conflict analysis.

## Contributing
Pull requests are welcome. See [CONTRIB.md](CONTRIB.md) for the ground rules, including what to say when AI was used,
and where textures and sounds may come from.

> # License
The MIT License (MIT)

Copyright (c) 2023-2026 OreCruncher

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