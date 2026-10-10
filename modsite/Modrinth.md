As an FYI there are other versions of my mod floating around the various mod distribution sites. They appear to be forks
from various versions of my mod, and have had AI extensively used to rework to fit modern Minecraft/loaders, and extend the
feature set. I cannot attest to the quality, and I have no involvement with those efforts. The source of my mod is licensed
MIT so they are free to do this with proper attribution. Unfortunately my mod ID is being reused, and the JAR files on
disk are very much similar to what I produce. At this moment the best I can do is make you aware. My hope is that any
quality issue those mods have will not tarnish my efforts. :\

### Features
* Biome sounds - Various atmospheric sounds that play based on biomes in the area. Seamless blending of sounds as the player moves throughout the world. (This does not replace the Minecraft feature of a singular biome background sound. Currently, the various biomes in the Nether use this capability, and Dynamic Surroundings does not have sound configurations for that dimension.)
* Enhanced Sound processing - sounds playing in 3d space will have a reverb effect based on the surrounding materials.
* Individual Sound Control - Set a key bind and activate in-game. Use this feature to block, cull, and control the volume at which sounds play. And as a bonus you can play the sound to hear it.
* Fog density changes driven by biome, time of day, and weather.
* Hot block effects such as flame jets over lava, and steam where water hits a hot block. Hot blocks are things Lava, Magma, campfires, and a cauldron containing lava.
* Waterfall sound and visual effects - will trigger when flowing water is detected nearby.
* Aurora display in the northern sky when standing in a cold/icy/taiga biome (1.21.1-0.4.6+).
* Firefly particle effect around flowers. Particles blink and glow with a soft light (1.21.1-0.4.6+).
* Replace Minecraft's thunder sound with improved versions.
* Remap of block step sounds to "improved" versions (basically a lighter weight Presence Footsteps). Other plays and humanoid mobs will also have these step sound remaps!
* Various "DS" client side commands for dumping configuration information. Useful for authors wanting to see how things are configured at runtime.
* Compatibility with Serene Seasons - variations in seasons and temperatures can influence effects.
* Custom debug HUD that can be accessed by key bind. Moves the Dynamic Surroundings clutter out of the traditional F3 display.
* Dynamic Surroundings is compatible when connecting to a Vanilla servers (a feature of both Fabric and NeoForge loaders).

### Optional Dependencies
* [Mod Menu](https://modrinth.com/mod/modmenu)
* [ClothConfig API](https://modrinth.com/mod/cloth-config)
* Want more detailed footsteps sounds?  Check out [Presence Footsteps](https://modrinth.com/mod/presence-footsteps).

### Links
* [Documentation on ReadTheDocs](https://dynamic-surroundings.readthedocs.io/en/latest/index.html)

### AI Disclosure
Starting with 1.21.1-0.4.6, AI is being used to help with:

* Code review: finding bugs, performance problems, and places that didn't follow best practices
* Refactoring: restructuring code so it can be unit tested (isolation, mocking), removing duplicate code, etc
* Testing: writing extensive and thorough unit tests
* GUIs: making the mod's screens consistent with how Minecraft's own screens work
* Mod compatibility: analyzing how the mod interacts with other mods and making improvements
* Visual effects: rework existing features, assist with shaders, etc.
* Planning ahead: working out the migration path and facilitating migration to subsequent Minecraft versions so it is easier and less error-prone

### Videos
<iframe width="560" height="315" src="https://www.youtube-nocookie.com/embed/guMuLeG3lck" title="YouTube video player" frameborder="0" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" allowfullscreen></iframe>
<iframe width="560" height="315" src="https://www.youtube-nocookie.com/embed/KGFZ1zf9R2s" title="YouTube video player" frameborder="0" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" allowfullscreen></iframe>
<iframe width="560" height="315" src="https://www.youtube-nocookie.com/embed/GbwaGX3JWeM" title="YouTube video player" frameborder="0" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" allowfullscreen></iframe>