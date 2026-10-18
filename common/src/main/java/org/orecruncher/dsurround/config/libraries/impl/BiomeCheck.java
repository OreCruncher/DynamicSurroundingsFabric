package org.orecruncher.dsurround.config.libraries.impl;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.dimension.LevelStem;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.config.BiomeTrait;
import org.orecruncher.dsurround.config.biome.biometraits.BiomeTraits;
import org.orecruncher.dsurround.config.biome.biometraits.TraitGroup;

import java.util.*;

/**
 * What the biome dump asks to have checked about a biome's traits: those guessed from its music, grass or name, and
 * a dimension or kind it has none of. Where the world's generation is known (single player), a biome's dimension is
 * checked against the dimensions it generates in rather than guessed at.
 */
final class BiomeCheck {

    private static final Map<Identifier, BiomeTrait> DIMENSION_TRAITS = Map.of(
            LevelStem.OVERWORLD.identifier(), BiomeTrait.OVERWORLD,
            LevelStem.NETHER.identifier(), BiomeTrait.NETHER,
            LevelStem.END.identifier(), BiomeTrait.END);

    private BiomeCheck() {
    }

    /**
     * The notes on a biome, none if there is nothing to check
     *
     * @param generatedIn       the dimensions the biome generates in, or null if that isn't known (multiplayer)
     * @param modHasDimensions whether the biome's mod has dimensions of its own, for when generation isn't known
     */
    static List<String> notes(BiomeTraits traits, @Nullable Set<Identifier> generatedIn, boolean modHasDimensions) {
        var notes = new ArrayList<String>();
        var doubtful = traits.doubtfulGroups();
        if (generatedIn != null)
            // Its generation says which dimension it is in, so a guessed one is checked against that below
            doubtful.remove(TraitGroup.DIMENSION);

        doubtful.forEach((group, members) -> {
            var each = new StringJoiner(", ");
            members.forEach(trait -> each.add("%s (%s)".formatted(trait.getName(), traits.sourceOf(trait).label())));
            notes.add(group.name().toLowerCase(Locale.ROOT) + " " + each);
        });

        var missing = traits.missingGroups();
        if (generatedIn != null) {
            notes.addAll(dimensionNotes(traits, generatedIn));
        } else if (missing.contains(TraitGroup.DIMENSION) && !modHasDimensions) {
            // A biome of a mod with a dimension of its own is likely in it, which none of the dimension traits
            // describe (the Twilight Forest, the Undergarden, the Bumblezone)
            notes.add("no dimension");
        }
        if (missing.contains(TraitGroup.KIND))
            notes.add("no kind");
        return notes;
    }

    /**
     * A biome's dimension against where it generates: a dimension trait it has that none of its dimensions give, or
     * none at all where one of them gives one. A biome only in modded dimensions rightly has none.
     */
    static List<String> dimensionNotes(BiomeTraits traits, Set<Identifier> generatedIn) {
        // A void is a void wherever it is: vanilla's is used for superflat worlds and by mods for dimensions of their
        // own (Iron's Spellbooks' pocket dimension)
        if (traits.contains(BiomeTrait.VOID))
            return List.of();
        var expected = EnumSet.noneOf(BiomeTrait.class);
        for (var dimension : generatedIn) {
            var trait = DIMENSION_TRAITS.get(dimension);
            if (trait != null)
                expected.add(trait);
        }
        var has = EnumSet.noneOf(BiomeTrait.class);
        traits.forEach(trait -> {
            if (TraitGroup.DIMENSION.members().contains(trait))
                has.add(trait);
        });

        var where = generatedIn.stream().map(Identifier::toString).sorted().reduce((a, b) -> a + ", " + b).orElse("");
        if (!has.isEmpty() && !expected.containsAll(has)) {
            var names = new StringJoiner(", ");
            has.forEach(trait -> names.add("%s (%s)".formatted(trait.getName(), traits.sourceOf(trait).label())));
            return List.of("dimension " + names + ", but generates in " + where);
        }
        if (has.isEmpty() && !expected.isEmpty())
            return List.of("no dimension, but generates in " + where);
        return List.of();
    }
}
