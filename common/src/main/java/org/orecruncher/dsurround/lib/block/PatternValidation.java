// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.lib.block;

import java.util.regex.Pattern;

public final class PatternValidation {

    /**
     * BlockState Property names
     */
    public static final Pattern BLOCKSTATE_PROPERTY_NAME = Pattern.compile("^[a-z0-9_]+$");
}
