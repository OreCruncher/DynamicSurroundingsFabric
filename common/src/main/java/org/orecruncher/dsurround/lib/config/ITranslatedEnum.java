// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.lib.config;

/**
 * An enum whose values are shown by a translation of their own in the config screen and its tooltips, rather than
 * their names. The keys may be vanilla's ("options.graphics.fancy").
 */
public interface ITranslatedEnum {

    /**
     * The translation key of this value's name
     */
    String translationKey();
}
