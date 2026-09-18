package org.orecruncher.dsurround.lib.collections;

import java.io.Serializable;
import java.util.Comparator;

public record Pair<F, S>(F first, S second) {

    public static <F, S>  Pair<F, S> of(F first, S second) {
        return new Pair<>(first, second);
    }

    public static <F extends Comparable<? super F>, S> Comparator<Pair<F, S>> comparingByFirst() {
        return (Comparator<Pair<F, S>> & Serializable)
                (c1, c2) -> c1.first().compareTo(c2.first());
    }

}
