package org.orecruncher.dsurround.config;

import org.orecruncher.dsurround.config.libraries.ISoundLibrary;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.runtime.IConditionEvaluator;

/**
 * What the configuration's data objects (BlockInfo, BiomeInfo, and the sound entries they hold) use. The library that
 * builds them hands it over, rather than each looking these up for itself, so they can also be built in tests.
 *
 * @param logger             for problems found in the configuration
 * @param soundLibrary       turns the configuration's sound names into sounds
 * @param tagLibrary         answers which tags a block or biome has
 * @param conditionEvaluator evaluates the configuration's scripts (conditions and chances)
 */
public record ConfigServices(IModLog logger, ISoundLibrary soundLibrary, ITagLibrary tagLibrary,
                             IConditionEvaluator conditionEvaluator) {
}
