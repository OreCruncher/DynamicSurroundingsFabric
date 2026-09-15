package org.orecruncher.dsurround.lib.collections;

import java.io.Serializable;
import java.util.Comparator;

public record Triplet<F, S, T>(F first, S second, T third) {

    public static <F, S, T> Triplet<F, S, T> of(F first, S second, T third) {
        return new Triplet<>(first, second, third);
    }

    public static <F extends Comparable<? super F>, S, T> Comparator<Triplet<F, S, T>> comparingByFirst() {
        return (Comparator<Triplet<F, S, T>> & Serializable)
                (c1, c2) -> c1.first().compareTo(c2.first());
    }

}
