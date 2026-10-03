package org.orecruncher.dsurround.processing;

import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.config.libraries.IBiomeLibrary;
import org.orecruncher.dsurround.config.libraries.ISoundLibrary;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.effects.entity.BowUseEffect;
import org.orecruncher.dsurround.effects.entity.BreathEffect;
import org.orecruncher.dsurround.effects.entity.StepThroughBrushEffect;
import org.orecruncher.dsurround.lib.di.internal.DependencyContainer;
import org.orecruncher.dsurround.lib.seasons.ISeasonalInformation;
import org.orecruncher.dsurround.processing.accents.FootstepAccents;
import org.orecruncher.dsurround.processing.fog.HolisticFogRangeCalculator;
import org.orecruncher.dsurround.processing.scanner.CeilingScanner;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The handlers' dependencies come in through their constructors, so validation can see them. Run against an empty
 * container, every library a handler needs shows up as missing.
 */
public class DependencyWiringTests {

    private static List<String> problemsFor(Class<?> root) {
        return new DependencyContainer("test").validate(root);
    }

    private static void assertNeeds(List<String> problems, Class<?> owner, Class<?> dependency) {
        var expected = "'" + owner.getName() + "' needs '" + dependency.getName() + "'";
        assertTrue(problems.stream().anyMatch(p -> p.contains(expected)), () -> "expected " + expected + " in " + problems);
    }

    @Test
    void fogHandlerUsesTheContainersCalculator() {
        // Regression: the fog handler built its own calculator, which looked its libraries up itself, while the
        // container built a second one that was never used
        var problems = problemsFor(FogHandler.class);

        assertNeeds(problems, HolisticFogRangeCalculator.class, IBiomeLibrary.class);
        assertNeeds(problems, HolisticFogRangeCalculator.class, ISeasonalInformation.class);
    }

    @Test
    void footstepAccentsNeedTheirLibraries() {
        var problems = problemsFor(FootstepAccents.class);

        assertNeeds(problems, FootstepAccents.class, ISoundLibrary.class);
        assertNeeds(problems, FootstepAccents.class, ITagLibrary.class);
    }

    @Test
    void entityEffectsNeedTheirLibraries() {
        // They used to look these up in static fields, which validation couldn't see and which kept the classes
        // from loading without a container
        assertNeeds(problemsFor(BowUseEffect.class), BowUseEffect.class, ISoundLibrary.class);
        assertNeeds(problemsFor(BowUseEffect.class), BowUseEffect.class, ITagLibrary.class);
        assertNeeds(problemsFor(BreathEffect.class), BreathEffect.class, ISeasonalInformation.class);
        assertNeeds(problemsFor(StepThroughBrushEffect.class), StepThroughBrushEffect.class, ISoundLibrary.class);
    }

    @Test
    void ceilingScannerNeedsTheTagLibrary() {
        assertNeeds(problemsFor(CeilingScanner.class), CeilingScanner.class, ITagLibrary.class);
    }
}
