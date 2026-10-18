package org.orecruncher.dsurround.config.biome.biometraits;

/**
 * Where a biome's trait came from, in the order they are looked at. Tags, the vanilla table and config rules are
 * taken as given; the rest are guesses, which only fill in what those leave out (see {@link TraitGroup}).
 */
public enum TraitSource {
    /**
     * The biome's tags: the server's, and the mod's client side ones
     */
    TAG("tags", false),
    /**
     * The mod's table of vanilla biomes
     */
    VANILLA("vanilla table", false),
    /**
     * Follows from traits taken as given (snowy is cold)
     */
    DERIVED("derived", false),
    /**
     * The biome's definition file: how many trees it grows (see {@link BiomeDefinitions})
     */
    DEFINITION("definition", true),
    /**
     * The biome's effects: the vanilla music it plays, its grass color modifier, its ambient loop
     */
    EFFECTS("effects", true),
    /**
     * The biome's climate: temperature, rain, downfall
     */
    CLIMATE("climate", true),
    /**
     * Words in the biome's id
     */
    NAME("name", true),
    /**
     * Follows from guessed traits
     */
    DERIVED_FROM_GUESS("derived from guesses", true),
    /**
     * A config rule (biomes.json), or a synthetic biome's own
     */
    CONFIG("config", false);

    private final String label;
    private final boolean guess;

    TraitSource(String label, boolean guess) {
        this.label = label;
        this.guess = guess;
    }

    public String label() {
        return this.label;
    }

    /**
     * Whether a trait from here is a guess, made where nothing more certain said
     */
    public boolean isGuess() {
        return this.guess;
    }
}
