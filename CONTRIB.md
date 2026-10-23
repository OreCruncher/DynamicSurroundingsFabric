> # Contributing to Dynamic Surroundings

Thank you for helping out. These are the ground rules for pull requests. They are about keeping the mod's content
honest about where it came from, and keeping its code tested.

> ### Language Files

* Language updates must be written by a human, not by AI.
* Only `assets/dsurround/lang/en_us.json` is edited in this repository. The other languages are translated through
  [Crowdin](crowdin.yml) and must stay a subset of `en_us`; a test checks this.
* Edit the language files as text, line by line. Loading and saving the JSON with a tool turns `§` escapes into
  literal characters.

> ### Code

* Using AI to write code is fine. If you did, say so in the pull request:
  * Which AI model was used
  * What the AI produced or did, in a sentence or two, for the **AI Disclosure** section of the
    [CHANGELOG](CHANGELOG.md)
* Code changes need unit tests wherever it is at all possible. Run them with `./gradlew :common:test`. Logic that
  touches the game world or rendering can often be split out into a plain method that can be tested on its own;
  look at the existing tests for examples, including the `testing/Fakes` helper for standing in for interfaces.
* The mod is client side and supports both Fabric and NeoForge. Check that your change builds for both:
  `./gradlew :common:compileJava :fabric:compileJava :neoforge:compileJava`

> ### Textures

* Significant textures must be created by a human.
* AI is fine for noise textures.
* A texture obtained from a third-party site needs attribution in [CREDITS.md](CREDITS.md), even if it is in the
  public domain: its name with a link to where it came from, the author, the license, and what was changed.
* A texture you created yourself must be under a license compatible with
  [CC BY-NC 4.0](https://creativecommons.org/licenses/by-nc/4.0/), the license of the mod's own textures.

> ### Sounds

* Nature sounds must come from live recordings.
* Foley effects must not be made by AI.
* Sounds can be sampled, cleaned up, trimmed, converted to mono, and so on.
* A sound obtained from a third-party site needs attribution in [CREDITS.md](CREDITS.md), even if it is in the public
  domain: its name with a link to where it came from, the author, and the license. Add the same credit to the sound's
  `ds_credits` entry in `assets/dsurround/sounds.json`, which is what the in-game *Sound Credit* list shows.
* A sound you created yourself must be under a license compatible with
  [CC BY-NC 4.0](https://creativecommons.org/licenses/by-nc/4.0/).
