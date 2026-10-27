> ### DynamicSurroundings-1.21.1-0.4.8

Documentation can be found at [ReadTheDocs](https://dynamic-surroundings.readthedocs.io/en/latest/index.html) website.

**All Loaders**
* JAVA 21+
* Architectury 13.0.8+

**Fabric**
* Fabric Loader >= 0.16.9
* Fabric API >= 0.110.0+1.21.

**NeoForge**
* NeoForge 21.1.84+

**AI Disclosure**
* Used to build the burning mob flames and death mist from existing logic and assets
* Ported footprints from older versions, and expanded them to include mobs
* Ported the damage pop-offs from older versions, drawing them as batched text rather than particles
* Reworked block effect scanning into a sphere, and let precipitation drift under blocks
* Reviewed performance profiles for the new features, and cut the work waterfall foam does each tick
* Analyzed OGG sound files and normalized where necessary
* Tracked down particles drawing as squares in a large modpack to another mod's particle, and built the guard that repairs it
* Built the branching lightning, its timing and its thunder, and gathered the scary options into one place
* Added the conditional operator "? :" to the scripting engine
* Built the dynamic clouds, their fading and sharing between players, and lightning lighting them; reviewed their performance profiles
* Measured the tick handlers in recordings, moved the precipitation's work to its own thread, and ported the jump sound from 1.12.2
* Placed animals' breath at their mouths from Minecraft's mob models
* Placed the flames in skulls' eyes from Minecraft's skull model and textures

**What's New**
* Burning mobs are wreathed in flames like a flame jet's (tongues of flame, sparks, and smoke) in place of Minecraft's flat fire sheets
  * The fire fits the mob: more flames for a bigger body, larger ones for a broader body
  * Flames keep up with a running mob, and burn on through its death until the body is gone
  * In lava, flames only rise from the part above the surface
  * Fewer flames farther away, and burning mobs share a budget, so a crowd of undead burning at dawn thins out evenly
  * One flickering light per mob lights up the surrounding area (one light for the whole fire, so a crowd stays affordable to draw), unless LambDynamicLights or RyoamicLights is installed, which already does
  * Minecraft's fire is kept beyond the entity effect range, with Particles on Minimal, while a shader pack is in use, and for your own body in first person
  * Burning Mob Flames option in Entity Effects; mobs tagged `dsurround:effects/vanilla_fire` keep Minecraft's fire
* A mob's body vanishes into dark mist when it dies, in place of Minecraft's white puffs
  * Death Mist option in Entity Effects
* Damage pop-offs are back from older versions: numbers pop off mobs and players when their health changes, red for damage and green for healing
  * A heavy blow (40% or more of the mob's health) gets a comic book word over the number, such as KAPOW!
  * Drawn as text rather than particles, so a busy fight costs little
  * Damage Pop-offs and Crit Words options in Entity Effects
* Footprints: players and mobs leave prints in soft ground (dirt, grass, sand, gravel, snow, mud, soul sand)
  * Players' prints are square by default; Footprint Style in Entity Effects picks another
  * Mobs leave prints that fit their feet: square for humanoids, paws for wolves, cats, foxes and bears, hooves for horses, cows, sheep, pigs and goats, and bird tracks for chickens; tags set which, and modpacks can add their own mobs
  * Prints fade after 10 seconds, or a minute in snow, and wash away faster in the rain where they are out in the open
  * No prints while sneaking
  * Footprints option in Entity Effects
* Frog sounds are back from older versions: lily pads croak, with the odd bullfrog call, twice as often at night as by day; not while indoors, or when it's cold enough for water to freeze
  * Includes the lily pads of Biomes O' Plenty, Twilight Forest, Oh The Biomes We've Gone, and Regions Unexplored; the `dsurround:effects/frog_croaks` block tag sets which blocks croak
* The heartbeat is back from older versions: the player's heart pounds when their health is at 30% or less (not in creative); Allow Scary Content turns it off
* The jump sound is back from older versions: a breath when you jump, heard by you only; off by default, turned on with Jump Sound in Entity Effects
* The bowstring's release is back from older versions: arrows fired from a bow, by players and skeletons, are heard with it in place of Minecraft's sound; Remap Sounds in Sound Options turns it off
* Animals breathe in the cold too: cows, mooshrooms, sheep, goats, pigs, horses, donkeys, mules, llamas, camels, wolves, foxes, cats, ocelots, polar bears, pandas, sniffers, armadillos, hoglins, and ravagers
  * The breath comes from the mouth, out in front of the body, and follows the head as it turns and nods; under water they breathe bubbles
  * Bigger animals breathe bigger puffs; a sheep eating grass, a goat about to ram, a rearing horse, or a sleeping fox holds its breath until it's done
  * Within 16 blocks of you; Animal Breath option in Entity Effects, along with Breath Particle Effect; mobs tagged `dsurround:effects/animal_breath` breathe this way
  * Villagers', illagers', and players' breath now follows where their head faces
* Skeleton skulls placed in the world burn with little flickering flames in their eyes, and wither skeleton skulls with blue soul fire
  * The flames light the floor and wall around the skull (Flame jet light in Flame Jet Options turns it off)
  * Skull Flames option in Block Effect Options; the block tags `dsurround:effects/skull_flames` and `dsurround:effects/soul_skull_flames` set which skulls burn
* In a dust storm the glow of sunrise and sunset turns dusty and dims, as the sky and fog already do
* Rain, snow, and dust blown in under an overhang drift a little way in out of the wind and fade, instead of stopping at the roof's edge; snowflakes that reach the ground under the overhang lie there, and rain splashes on a porch floor
* Lightning that branches and forks, in place of Minecraft's bolts
  * The stronger the thunderstorm, the more branches, the more often they fork again, and the more strokes a flash has
  * Each flash has a faint leader feeling its way down, then strokes that light the whole channel and flicker as they die away; later strokes barely light the branches, as in real lightning
  * Minecraft's lightning bolts are drawn this way too, each shaped from the bolt itself, so every player sees the same bolt
  * Through a thunderstorm the sky has lightning of its own: bolts to the ground, spider lightning crawling under the cloud base, and flashes hidden in the cloud that light it up in patches, moving about inside it from stroke to stroke
  * With the dynamic clouds, the flashes light the clouds themselves from inside: their undersides, the towers they hide in, and not the gaps between
  * Far strikes are dimmer, the more so the thicker the weather between: rain hides a distant storm's flashes a good deal more than clear air
  * Most of it comes from upwind, where the storm is coming from
  * Each strike in the sky has its thunder, later the farther away it is (about a second for every 343 blocks), and quieter and lower when far off; a close one lights up the sky, as Minecraft's own bolts do
  * Lightning option in the new Scary Content section; not drawn while a shader pack is in use
  * With Minecraft's Hide Lightning Flashes accessibility option on, there is no lightning in the sky and bolts don't flicker
  * `/dsweather lightning` strikes ahead of the player for testing; `ground`, `spider`, or `sheet` picks the kind
  * Scripts can check whether lightning is shown with `global.allowLightning()`
* **[Experimental]** Dynamic clouds: blocky clouds that change with the weather, in place of Minecraft's
  * On fair days, scattered clouds build up into stepped towers through the afternoon and flatten out at night, with thin streaks high above; some days are cloudier than others
  * Cloudier over wet biomes than dry ones: they build over jungles and swamps and thin over deserts as they drift
  * A storm closes them into a low deck that lowers and thickens with its strength, darker underneath the thicker it is; a thunderstorm's towers rise far above it
  * They change a little at a time, fading in and out, rather than all at once
  * Every player in a world sees the same clouds, drifting with the wind up high
  * Far off they fade into the fog, and in rain the deck overhead stays dark rather than turning to haze
  * Cloud Distance in Weather Options sets how far they reach, 8 to 32 chunks, whatever the render distance
  * Dynamic Clouds option in Weather Options; off with a shader pack, with Minecraft's clouds turned off, and in dimensions that draw their own clouds (`dynamicClouds` in a dimension config sets it)
  * `/dsweather clouds 0-100 [thickness]` holds how much of the sky they cover, and how thick the thickest are, for testing; `/dsweather clouds clear` goes back to the weather's
  * `/dsprofile clouds` records them in a thunderstorm
* Scripts have a conditional operator: `diurnal.isNight() ? 0.04 : 0.02`
  * Only the chosen value is worked out, as with `lib.iif`, which still works the same
  * It binds more loosely than any other operator, so `'is ' + (weather.isRaining() ? 'wet' : 'dry')` needs the parentheses

**Changes**
* Sound file maintenance
  * Normalized the volume of a bunch of sounds
  * Removed unused sounds from the JAR to reduce size
* Block effects (waterfalls, steam, flame jets, and other random block effects) are now found in a sphere around the player rather than a cube
  * Block Effect Range is now a radius, and the scan finds the nearest blocks first, so effects close by show up first after a teleport
  * Setting it past 32 shows effects farther away than Minecraft shows particles, at more cost; waterfalls thin their splashes past 32 blocks
  * The default is now 40, which scans about as many blocks as the old 32 block cube, and shows block effects a little past Minecraft's particle distance
  * Past 32 blocks, random block effects thin out evenly toward the edge of the range
* The default Entity Effect Range is now 32, up from 24, as the new entity effects are cheap and still visible that far out
* The default Precipitation Range is now 14, up from 12, so rain, snow, and dust reach a little farther
  * The area around the player that precipitation looks at is now round, matching where drops fall, rather than square, which saves work at any range
* Existing configurations keep the values already saved in them; set the new defaults in the options to use them
* Removed the option for sound effects when selecting blocks on the hotbar
* Removed the random sound played when the game finishes loading, and its Play Random Sound On Startup option
* soundconfig.json now saves each sound's setting as one `action` (default, cull, or block) in place of the separate block and cull flags; older files are converted when loaded
* Waterfall foam, mist, steam, and smoke do less work: each block's water surface is worked out once every half second and shared by all the foam on it, and foam and the soft puffs read their light every few ticks instead of every frame; noticeable in large modpacks with many waterfalls nearby
* A new General Options section, first in the list, holds Mod Update Chat Messages and Register Client Side Commands, moved out of Diagnostic Options; values already set carry over
  * Diagnostic Options is saved under `diagnostics` in dsurround.json, in place of `logging`; values already set carry over
* A new Guards section in Diagnostic Options holds fixes for problems other mods cause, each of which can be turned off should it cause a problem of its own
* A new Scary Content section gathers the options for content some players find frightening
  * Allow Scary Sounds in Sound Options is now Allow Scary Content there, and also covers the lightning; a value already set carries over
  * The Lightning option turns the lightning off on its own
* Rain landing on snow soaks in and leaves a damp spot, as on dirt and sand, instead of splashing
* Dynamic Surroundings music manager will start music when Minecraft's main menu is shown rather than wait 5 seconds
* A hungry player's stomach grumbles more often
* Particle rain, snow, and dust move on a thread of their own while frames are drawn, taking most of their work off the game's main thread; standing still, the area they fall in is looked over a few rows a tick rather than all at once
* `/dsprofile` recordings also hold how long each of Dynamic Surroundings' tick handlers took every tick, how often random block effects looked, started effects and played sounds at each distance, and which optional hooks applied, which features stand aside for another mod, and the settings (text settings left out)

**Fixes**
* A block within 16 blocks of the player could run two random block effects at once, one over the other; it now runs one at a time
* Particle rain and snow fell through blocks from other mods whose shape depends on the world, such as Mekanism's energy cube (Minecraft's own rain does too: such blocks are left out of the height map it stops at); they now stop on top of them
* Minecraft's particles (such as campfire smoke) and the mod's waterfall foam could draw as white squares when another mod's particle changed the drawing state while particles were being drawn; the state is now put back before they are drawn, the log names the particle responsible once, and that particle is drawn on its own from then on so it looks right too (seen with the Rainbows mod); Repair Particle Batches, in a new Guards section of Diagnostic Options, turns this off
* With Fabulous graphics in some modpacks, rain drops cut the water out where they crossed it, showing the bottom through each drop; another mod left the wrong render target in place for the weather. The mod now checks the target before drawing the weather, lightning, soft particles and particle lights, puts the right one back when it is found replaced, and says once in the log what was there instead; the particle batch guard also catches another mod's particle switching targets
* Frost breath on a dark night with Astral Sorcery showed the blocks behind it through the dark; breath is now drawn soft, as mist is, and fades where it meets blocks

> ### DynamicSurroundings-1.21.1-0.4.7

Documentation can be found at [ReadTheDocs](https://dynamic-surroundings.readthedocs.io/en/latest/index.html) website.

**All Loaders**
* JAVA 21+
* Architectury 13.0.8+

**Fabric**
* Fabric Loader >= 0.16.9
* Fabric API >= 0.110.0+1.21.

**NeoForge**
* NeoForge 21.1.84+

**AI Disclosure**
* Used to extend the storm simulation system for rendering and handling
* Assisted in the creation of the ground fog feature
* Helped with integrating Simple Voice Chat
* Used to extend steam and flame jet effects, adding additional particles, shader, and lighting where appropriate
* Rebuilt the clock overlay to use an old pocket watch texture with embedded indicators for diurnal cycle, seasons or moon phase (depending on if Serene Seasons is installed)
  * Added the option to show the compass and pocket watch in a side or corner of the screen
* Integrated older compass rose textures as possible selections for the compass, and cleaning up the textures
* Ported the light level HUD from older versions as a light level overlay, drawn from a texture for speed
* Identified root causes of several issues and help with the fix

**What's New**
* Simulated wind affects movement and behavior of Dynamic Surroundings particles
  * Includes mist, steam, flame jets, breath, and precipitation (see below)
  * Also includes a select group of particles such as Minecraft's smoke, ash, and falling leaves
* Windy biome sound effects have volumes dynamically adjusted based on wind speed
  * Becomes louder or quieter as time progresses
  * Enhanced when it is storming
* Client side storm simulation, based on older Dynamic Surroundings versions
  * Only works in dimensions where there are no modded hooks for weather rendering
  * Random seeds tied to the Minecraft day so all players using Dynamic Surroundings will experience the same effects
  * When precipitation starts, the storm simulator generates a strength value independent of Minecraft's rain strength
  * During a storm the strength surges and eases off below its peak, changing every half minute or so; how far it can ease is the Storm variation option in Weather Options
  * The precipitation effects are "particle" like rather than rendered texture sheets, though they are not true Minecraft particles
    * Rain drops with a slight slant, and will change direction based on wind
      * Rain tint option in Weather Options sets how blue the rain is, from a nearly colorless gray to a deep blue
    * Snow drops more slowly, and the impact of wind is more visible
      * Textures are sampled from Minecraft's snow.png texture at runtime, meaning the logic will sample any modded replacement
    * Sleet (a mix of rain and snow) can occur in transition zones (like elevation)
    * Dust storms will occur in deserts when raining, and particles will drift along the ground in the direction of wind
      * The dust particles will climb small inclines as well giving the impression of flowing over elevations
    * These particles are affected by the wind simulator
  * Serene Seasons will affect whether rain or dust blows in some biomes, such as Biomes OPlenty's Lush Desert
  * Various parts of the system can be enabled/disabled in the Weather Options section
  * Snowflakes lie where they land for a moment, then shrink and fade as they melt; blown by the wind into the side of a wall or tree trunk, they stick to it (Snow sticks to sides option in Weather Options)
  * Rain blown onto windows (glass blocks and panes) clings, then runs down the glass in short rushes, leaving a wet trail that dries, and drips off a bottom edge over open air; windows facing the wind and open to the sky get the most. Rain on windows option in Weather Options
  * For a while after rain stops, water drips from the lowest leaves of trees and from overhanging edges like eaves, less and less as things dry; edges drip while it rains too. Drips after rain option in Weather Options; works even with particle rain off
  * In the cold, frost grows on the outside of clear glass windows, in from the window's edges, thickest before sunrise; the sun melts it from east facing windows in the morning and west facing ones in the evening. Frost on windows option in Weather Options; `/dsweather frost 0-100` sets it for testing
  * The weather system will auto disable if Particle Rain or Simple Weather is installed
* Brought back the compass rose textures from older mod versions
  * There are 3 rose styles to choose from
  * Textures were cleaned up from the originals
* New pocket watch clock display, styled after Minecraft's clock
  * Shown above the crosshair (beside the compass if both are shown), with hour and minute hands that tick from minute to minute
  * Has some wear like an heirloom handed down: tarnish, scratches, a dent or two, and a hairline crack in the dial
  * A window under XII shows day or night, like Minecraft's clock, to tell noon from midnight
  * A window above VI shows the season when Serene Seasons is installed, or the moon's phase without it
  * Spins where the compass does (the Nether, the End), as Minecraft's clock does there; the clock text leaves out the time (`dsurround:effects/clock_wobble` picks which clocks)
  * Clock style option in Compass and Clock Options: text, pocket watch, or both; the Render scaling option applies to it as well
  * Pocket watch style option picks the look: CLASSIC (the worn round watch) or SQUARE (a square brass watch in pixel art)
* The compass and clock on/off switches are now a NONE choice in their styles; a switch that was off carries over as NONE
* Option to show the compass and pocket watch in a side or corner of the screen while one is in the hot bar or off hand, rather than above the crosshair while held
  * Show in screen margin option in Compass and Clock Options turns it on; Screen margin position picks one of the four sides or four corners (left by default)
  * Shown together, they sit one above the other on the sides and corners, and side by side at the top and bottom
  * The compass rose is upright there, turning with your heading; the compass band is drawn at half size
  * Clock text is still shown only while a clock is held
* The compass band texture is now smoothed, so it no longer flickers as it slides at small sizes
* Light level overlay, back from older versions: numbers on the blocks around the player that monsters could stand on, colored by whether they can spawn there
  * Turned on and off with the Light Level Overlay key (unbound to start with, under Dynamic Surroundings in the controls)
  * Red where monsters can spawn now, yellow where they can at night, green where it is too bright, aqua where they can never spawn; DARK colors also available
  * Spawning follows the dimension's own rules: in the Overworld any block light keeps monsters away, in the Nether it goes by light level 7
  * Shows block light, skylight, or the brighter of the two; range of 8 to 32 blocks; can hide the safe blocks
  * A legend in the bottom right corner of the screen says what the colors mean, and can be turned off
  * The numbers are drawn with the game's font, so a resource pack that changes the font changes them too
  * Light Level Options in the configuration
* **[Experimental]** Ground fog in swamps and spooky biomes like Dark Forest and BOPs Ominous Forest
  * Heavy rains can thin it out, higher density between midnight and morning
  * Wind can shift the drift direction and speed
  * Should clear when inside
  * There is a quality setting in Weather Options that allow you to control the ground fog rendering parameters
    * OFF, Fast, Fancy, and Fabulous; defaults to Fancy
    * Fast has some artifacting; Fabulous looks much better but will be computationally intensive (of course NONE is the most performant :D)
* **[Experimental]** Support for Simple Voice Chat with enhanced sound processing
  * On by default; setting can be found in the Enhanced Sounds configuration options
  * Hear Self diagnostics setting can be found in the Diagnostics Settings config section; disabled by default
  * Will auto disable if enhanced sound processing is not available (ex, Sound Physics Remastered is installed)
* New /dsweather command to control aspects of simulated wind and storm
  * I use for testing but if you want to play around with it go ahead - doesn't affect game mechanics
* New /dsconflicts command lists in chat the Dynamic Surroundings features turned off because another mod is installed, and which mod
  * For example, Particle Rain turns off the particle weather and water ripples; Presence Footsteps turns off the wet surface accents and brush step effect
* New /dsprofile command records a performance profile of the game, to send to the mod's authors when something seems slow
  * Just `/dsprofile` records two minutes of play as usual; `/dsprofile <scene> [seconds]` picks a scene to record instead: `ground_fog`, `weather` or `frost` shows that effect at its heaviest
  * A note at the top of the screen shows the time left, and chat says where the recording was saved when it is done (click the name to open it)
  * Recordings are saved zipped, ready to attach to an issue on GitHub, in the dsurround config folder under `profiles`; only the newest 10 are kept
  * Recordings note what they were made on: the graphics card and driver, the graphics settings, render distance, and the Java and Windows/Mac/Linux versions
  * Personal information is left out of the recordings: no account names, folders, command line or network addresses
  * Uses Java Flight Recorder, which comes with most Java installs; if yours doesn't have it, the command says so

**Changes**
* Updated steam effect to utilize the core logic and rendering of waterfall mist
* Reworked the flame jet effect, reusing the mist particle with color tinting and smoke
  * Configuration options for flame jet were moved into its own configuration section
* Updated aurora rendering to utilize the simulated rain strength and weather fog effects, meaning that for light precipitation the aurora will still render though a bit muted
* Config settings for experimental features are colored "orchid" to distinguish from other options
  * Experimental options/features are subject to change and may be removed in a future release
* The config file (dsurround.json) now notes settings that only take effect after restarting Minecraft or rejoining the world
* Aurora shader was modified to use a noise map rather than hashing in the shader. Result is that the style of aurora could change between computers that are running different versions of Dynamic Surroundings.
* Reuse reverb calculations between sounds that have their source close to each other
* Features turned off because of another mod are now listed together: in the log at startup, and in the diagnostics overlay
* Less work for ground fog, random block effects and waterfall foam (found by profiling a large modpack)
* Tooltips on the config screen and the individual sound screen wait until the mouse rests for half a second, and stay hidden while a slider is dragged
* Better biome traits for modded biomes missing tags
  * A biome's tags are no longer overridden by guesses: a modded biome tagged cold stays cold even when its temperature looks temperate
  * Guesses come from the biome's vanilla music, grass, and ambient sounds first, then its climate, then its name; names match whole words, so "alpine" isn't a pine forest
  * Before guessing, the biome's definition in its mod's jar is read for how many trees it grows: dense or sparse vegetation, and a forest, without a guess (works on servers too, as the mod is installed on the client)
    * A biome playing swamp music or named a bog or swamp stays a swamp however many trees it has; caves and water biomes don't count trees
    * Trees count in whichever generation step the mod places them (No Man's Land puts most of its forests' outside the vegetation step)
  * Vanilla biomes have the same traits on every server: the mod's own table settles their temperature, moisture and vegetation where Fabric's or NeoForge's tags disagree
  * A biome named a shore is a beach but not wet, like vanilla's stony shore
  * More biome names understood: fields, plains, meadows and prairies; seas; streams and lakes; savannas; plateaus; highlands; tundra and glaciers
  * `/dsdump biomes` shows where each trait came from, and ends with a list of biomes to check: traits guessed from music, grass or name, or no dimension or kind at all, to help write tag files for them
    * In single player it checks each biome's dimension against where the world generates it, and lists each dimension's biomes
    * A biome only in modded dimensions (or, on a server, of a mod with dimensions of its own) isn't listed for having no kind: it is often its dimension's only biome
  * The traits shipped for other mods' biomes are now tag files in each mod's folder (`assets/<mod>/dsconfigs/tags/worldgen/biome/is_<trait>.json`), read only when that mod is installed
    * Nature's Spirit's alpine and highlands biomes are windswept, as intended: their tag file was misnamed and never read
    * Tags for Spectrum's Deeper Down biomes its own tags leave out (deep dripstone caves, noxshroom forest), and No Man's Land's mud beach (a beach, not a swamp) and tropical beach
    * `traits` and `clearTraits` in `biomes.json` are deprecated (a warning is logged): they are added after the guesses, where a tag file settles a trait before any guess

**Fixes**
* Individual sound volumes and the ambient volume option now hold for sounds that tick, the mod's own biome loops among them (they fell back to the game's volume after their first tick, and every playing sound did when a volume slider moved); turning down a loud sound, such as an explosion, now makes it quieter
* Possible crash with Enhanced Sounds ("Accessed LegacyRandomSource from multiple threads"): its worker threads read a playing sound's volume, which draws on the same random source the game uses for it each tick; each sound's hearing range is now taken once, on the client thread, when it starts
* Sound pruning no longer drops sounds that could still be heard: looping, moving and streamed ones (a music disc started out of range, a machine's hum) play as the game would, and fade in as you come closer; a sound louder than 1 is given the game's longer range rather than let through
* With a mod that turns the game's fog off (Distant Horizons), the log no longer fills with invalid fog range warnings, and the fog is left off: rain, morning and biome fog would only cover the game's own terrain, not the far terrain that mod draws, so they stand aside, and their work stops, while it is off (#201)
* Particle rain, snow and damp spots turned themselves off in the overworld with Stellar View installed (NeoForge): it replaces the overworld's sky effects, which was taken to mean it draws its own weather. Now only a dimension whose effects draw or tick their own weather is left to them
* Water ripples are visible when looking from underwater. During the day they are faint rings; at night they are more visible.
* Added Twilight Forest dimension configuration to set sea level.
* Moved aurora render hook to avoid conflict with Astral Sorcery's sky render
* Fog being applied to SPOOKY underground biomes, like BOPs spider nest
* Rendering issues when Fabulous! graphics enabled
* [MAYBE] Infrequent crash in fastutil collection when rendering block entities, seen with Create belts
* Underwater in vanilla's oceans (other than deep ones) used the plain underwater sounds rather than the oceans when Fabric's or NeoForge's biome tags were present
* Ground fog didn't work with Mesa graphics drivers (common on Linux): its shader used a word GLSL reserves
* The log filled with "Fog calculator ... reporting invalid fog range" warnings underwater, where vanilla's fog starts behind the camera
* With the audio device gone (headphones turned off) and nothing else to play on, the game tried to restart sound every tick and logged a stack trace each time, growing the log by gigabytes overnight. It now tries every few seconds and logs the failure once; sound comes back as before once a device is there (Sound System option "Throttle Audio Device Retries", on by default)
* With Veil installed, the log showed "Couldn't load vanilla shader from dsurround:shaders/core/point_light.vsh" with a stack trace at each resource reload: Veil's shader parser can't read a return without a value. The lights always worked; the shader no longer uses one

> ### DynamicSurroundings-1.21.1-0.4.6

Documentation can be found at [ReadTheDocs](https://dynamic-surroundings.readthedocs.io/en/latest/index.html) website.

**All Loaders**
* JAVA 21+
* Architectury 13.0.8+

**Fabric**
* Fabric Loader >= 0.16.9
* Fabric API >= 0.110.0+1.21.

**NeoForge**
* NeoForge 21.1.84+

**AI Disclosure**

This section is a bit long since this is the first time I am writing this. In subsequent releases it should be shorter. :)

Starting with **1.21.1-0.4.6** I am using AI to assist with modding (Claude Code Pro if you are interested). To establish
a bit more context, I am a software engineer with almost 40 years industry experience (50 if you include high school and other
personal projects), primarily working on backend systems. I personally believe that AI has a role in modding, but the challenge
is having the "wisdom" and "experience" of applying it "properly" (notice all the quotes). Further, there are qualitative
differences in work product between the different AI models, and a lack of software experience on the users part runs the risk
of releasing something that is low quality. Unfortunately there isn't much I can do about this particular situation, other
than be transparent about my use of AI. (I am aware of the broader discussion/issues of AI.)

If you have constructive feedback or questions related to my AI experience feel free to comment [here](https://github.com/OreCruncher/DynamicSurroundingsFabric/discussions).

These are the details of my use and the result it had on my mod so you can make an informed decision. My hope is to demonstrate how
AI can be applied in ways other than "generate me a texture" or "create a mod that looks like that mod so I can publish it as my own".

Summary:

* Code review: finding bugs, performance problems, and places that didn't follow best practices
* Refactoring: restructuring code so it can be unit tested (isolation, mocking), removing duplicate code, and cleaning up config processing
* Testing: writing an extensive set of unit tests
* GUIs: making the mod's screens consistent with how Minecraft's own screens work
* Mod compatibility: analyzing how the mod interacts with other mods and making improvements
* Visual effects: reworked noise textures, added particle shaders, and integrated the shader based aurora from older versions
* Planning ahead: working out the migration path to Minecraft 26.2 so it is easier and less error-prone   

Results:

*Testing and maintainability*                                                                                                                                                                                                                                                                                    
  * Added 1400+ unit tests to validate internal logic                                                                                                                                                                                                                                                              
  * Refactored, eliminating static references in favor of injection
  * Found duplicate code and refactored to remove the redundancy
  * Removed dead code, or code that wasn't actually providing any value (always return true, didn't contribute anything meaningful, etc.)
  * Produced comments for undocumented methods where clarification was needed

*Performance*                                                                                                                                                                                                                                                                                                    
  * Script engine performance improved by roughly 40%                                                                                                                                                                                                                                                              
  * Fixed performance issues in the area block event scanner, making it about 50% faster
    * There were a lot of cases where it would do a needless double scan
  * Identified a Minecraft biome blend behavior and tuned the area biome scanner to take advantage, reducing the number of samples by 85%
  * Improved performance and resource usage of the waterfall and steam producer effect systems                                                                                                                                                                                                                     
  * Reduced calculations in the sound effect engine (reverb) where possible 
    * Basically, it only does calculations if any of the critical factors changed, like player block position, block state changes in the area, etc.                                                                                                                                                                                                                                        
  * Added an annotation processor to generate event loop implementations instead of using reflection (less overhead, no allocations)
    * This is for the mod's internal event system, not Architectury/Fabric/NeoForge
  * Fixed GUI render performance issues 

*Reliability*                                                                                                                                                                                                                                                                                                    
  * Fixed edge cases where caches weren't flushed or data held a reference to an object that could go out of scope (like Entities)                                                                                                                                                                                 
  * Added throttling where repeated errors could spam the log                                                                                                                                                                                                                                                      
  * Improved error detection and reporting for scripts and json configurations                                                                                                                                                                                                                               
  * Mod configuration logic handles bad input from hand edits, and writes comments into the config file                                                                                                                                                                                                                  
  * Improved Markdown parsing robustness and performance (Sound Credits dialog)
  * Validation of dependency injection entries to detect circular dependencies (cycles) earlier in the startup process (a development aid rather than a player facing issue)
                                                                                                                                                                                                                                                                                                                   
*Features*                                                                                                                                                                                                                                                                                                       
  * Waterfall mist and foam, firefly glow and lighting, and the return of the aurora (see What's New)

**What's New**
* Added /dsversion command that will query version status from my GitHub and report back in the local chat. Response will have clickable links so you can navigate to various information sources. This command will work regardless if the chat report on login is disabled. (Some modpack authors disable chat reporting on client login.)
* Added "Sound Credits" button to the Sound Options dialog. Clicking will display credit and attribution information about the sounds in the mod. (This information is also buried in the tool tips when hovering over sounds in the sound configuration menu.)
* Humanoid mobs gain brush and straw step effect (skeletons, zombies, raiders, etc.) Did not do creepers, sorry.
* Cleaned up mod config processing
  * Added comments to fields when writing out the configuration file. User comments will not be preserved.
  * Added mechanisms to migrate existing settings to new fields when the config is loaded
* The aurora is back. There are a set of options in the configuration to enable/disable, adjust frequency, as well as the max number of bands.
  * New version takes about 1/10th the compute to render (no more computer space heater)
  * Shows in the northern sky while standing in cold/icy/taiga biomes
  * There is a new Aurora configuration section where it can be enabled/disabled, and other properties modified
  * Will auto disable if Iris or Oculus is installed.
* Waterfall mist (replaces the relatively new cascade) and water foam.
  * Mist - swirls at the bottom of a waterfall drops (falling water hitting a surface)
  * Foam - generates when water drops down a block, or when water hits a surface. Foam will flow with the direction of water and eventually fade.
  * Options are in the Waterfall Options section of the configuration. (Previous settings, if any, are migrated.)
  * Shader blending will auto disable if Iris or Oculus is installed.
* Refreshed firefly effect so that they behave more like fireflies; added a glow around them that lights up the area a bit.
  * Options are in the Firefly Options section. (Previous settings, if any, are migrated.)
  * Will auto disable shader lighting if Iris or Oculus is installed.
* Extended debug hud for Dynamic Surroundings. If the key is bound, hitting it will toggle between the normal HUD, information about the player biome, and the effect system placements in world.

**Changes**
* Search box in the Individual Sound Configuration menu will accept regular expressions for filtering
* Reduced the default reverb ray distance from 256 to 128
  * Existing configs will still have 256 (because the new upper bound is 256) so you can reduce if you want
  * Math models and testing showed anything more than 110 would give marginal improvements to sound
* Ensured all Dynamic Surroundings footstep sounds are mono, and removed on the fly mono-conversion support
* Removed the waterfall cascade effect as it is replaced by mist and foam
* Backported fog calculator changes from 26.2 to 1.21.1
  * Biome fog should be softer
  * Sky fog is taken into account
* Configuration in player resource packs is read whatever its namespace, so a pack can bring its own sounds and the configuration that plays them (such as Dynamic Surroundings Extended under `dsurround_ex`)

**Fixes**
* Some caches weren't properly flushed when the level changes (as when changing dimensions)
* Edge cases where caches were holding direct references to things longer than they should (like Entities)
* Cases related to validation and handling of configuration data coming from external sources (better error reporting, etc.)
* Sound effects when the player is underwater are properly dampened
* Some of the math in reverb calculations were lossy, and as a result sounds were muted a bit (about 8 - 20% depending on environment)
  * I do not directly hear the difference, but it seems like the listening experience is improved
* Breath effect underwater is back (bubbles being emitted)
  * Will be disabled if breath effect is disabled.
* Fixed issue when joining a remote server where a tag sync is not detected and Dynamic Surroundings tag cache is not cleared

> ### DynamicSurroundings-1.21.1-0.4.5

Documentation can be found at [ReadTheDocs](https://dynamic-surroundings.readthedocs.io/en/latest/index.html) website.

**All Loaders**
* JAVA 21+
* Architectury 13.0.8+

**Fabric**
* Fabric Loader >= 0.16.9
* Fabric API >= 0.110.0+1.21.

**NeoForge**
* NeoForge 21.1.84+

**Fixes**
* Configure sound button on Sound Options screen will now work when Music Manager replacement is toggled off.

**Changes**
* Consolidated the Block and Cull buttons in the Individual Sound configuration menu into a single tri-state button. The icons didn't grow on me.
* Converted play button to text button.

> ### DynamicSurroundings-1.21.1-0.4.4

Documentation can be found at [ReadTheDocs](https://dynamic-surroundings.readthedocs.io/en/latest/index.html) website.

**All Loaders**
* JAVA 21+
* Architectury 13.0.8+

**Fabric**
* Fabric Loader >= 0.16.9
* Fabric API >= 0.110.0+1.21.

**NeoForge**
* NeoForge 21.1.84+

**Fixes**
* Fixed compatibility with Enderscape mod. Client should no longer crash during startup.
* Dynamically disable sound discarding feature if Raise Sound Limit Simplified (RSLS) is installed.
 
**Changes**
* Added isEarly(), isMiddle(), and isLate() functions to seasonal script namespace to detect sub-seasons. For modpacks without seasonal mods (ie, Serene Season), each will return true.
* Added configuration options (under Sound Options) to control how Dynamic Surroundings asserts a sound play is originating from the client thread.
  * discardNonClientSoundPlays (default true): If true, a sound play will be discarded with a corresponding message emitted into the log whenever it detects the play is originating from a non-client thread
  * logStacktraceWhenDiscarding (default false): If true, a stack trace is emitted to the log with the goal of giving as many clues as to which mod is making the call

> ### DynamicSurroundings-1.21.1-0.4.3

Documentation can be found at [ReadTheDocs](https://dynamic-surroundings.readthedocs.io/en/latest/index.html) website.

**All Loaders**
* JAVA 21+
* Architectury 13.0.8+

**Fabric**
* Fabric Loader >= 0.16.9
* Fabric API >= 0.110.0+1.21.

**NeoForge**
* NeoForge 21.1.84+

**What's New**
* Biome configuration setting to reset fog color for biomes.
* Biome configuration setting to clear existing traits before applying new ones.
* Replaced Nashorn JavaScript engine with my own simple syntax tree implementation. Reduces issues related to maintenance and future compatibility. Fabric JARs should be thinner.
* Trident and Mace toolbar sound effects.
* Waterfall cascade effect. Simulates turbulence and misting when water hits a surface. WIP - current implementation is passable (NeoForge looks better IMO). :)
* Replace Minecraft's music manager with a modded version. This enables the use of music manager commands (/dsmm \[pause|unpause|reset|whatsplaying\]). Replacement can be disabled via the configuration.
* Configuration option to reduce the wait times between music plays. By default, there will be no reduction in wait time. This feature is independent of music manager replacement.

**Changes**
* Updated Dynamic Surroundings tag names to match the conventional tag naming scheme (ex., "dsurround:is_axe" rather than "dsurround:axes"). Old tag names will continue to work though dependencies should be updated to use the new naming scheme.
* Updated biome trait detection which should improve "out of the box" experience with modded biomes.
* A bunch of changes related to mixin with the goal of simplifying and reduce chances of mixin related failures.

**Fixes**
* Added missing sound subtitles for mapped step sounds.
* Fixed underground sounds playing with superflat worlds.
* Defensive code for mods that create fake worlds as part of their feature set (The Aether's "quick join" for example).
* Changed randomizer to use Xoroshiro from the Minecraft library (rather than JDK), and added thread safety checks. This should mitigate potential issues (or at least take Dynamic Surroundings out of the equation) with other mods performing cross-thread random number generation as well as Java runtimes that do not support Xoroshiro natively.
* Fix rain ripple rendering issue when picking up items (it would flicker to black and back). This would show up frequently when fishing in the rain and picking up drops.
* Fix null pointer exception with Cobblemon when the player utilizes an item while mounted (thanks Memori!).
* Fix incorrect biome sound playing when underwater in Deep Ocean biome.
* Fix sporadic null pointer exceptions when logging due to modpack mod load order and mixin wild west dependencies (thanks SylvanSerenity!).
* Reworked block update handling and added defensive code to ensure proper client side thread handling.

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