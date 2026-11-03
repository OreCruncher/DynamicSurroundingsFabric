// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.lib.collections;

public record Pair<F, S>(F first, S second) {

    public static <F, S>  Pair<F, S> of(F first, S second) {
        return new Pair<>(first, second);
    }

}
