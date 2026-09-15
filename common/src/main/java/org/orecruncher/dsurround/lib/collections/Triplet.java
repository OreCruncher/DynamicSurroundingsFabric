package org.orecruncher.dsurround.lib.collections;

public record Triplet<F, S, T>(F first, S second, T third) {

    public static <F, S, T> Triplet<F, S, T> of(F first, S second, T third) {
        return new Triplet<>(first, second, third);
    }
}
