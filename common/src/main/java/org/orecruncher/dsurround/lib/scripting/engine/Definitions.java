// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.lib.scripting.engine;

import java.util.*;

import static org.orecruncher.dsurround.lib.scripting.engine.TokenType.FALSE;
import static org.orecruncher.dsurround.lib.scripting.engine.TokenType.TRUE;

final class Definitions {
    static final Map<String, TokenType> KEYWORDS = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    static {
        KEYWORDS.put("false", FALSE);
        KEYWORDS.put("true", TRUE);
    }
}
