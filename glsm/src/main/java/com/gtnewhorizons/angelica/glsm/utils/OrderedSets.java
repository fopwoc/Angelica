package com.gtnewhorizons.angelica.glsm.utils;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Immutable membership with first-occurrence iteration order, duplicate elimination, and no null elements. */
public final class OrderedSets {
    private OrderedSets() {}

    @SafeVarargs
    public static <E> Set<E> of(E... elements) {
        var set = new LinkedHashSet<E>();
        for (E element : elements) set.add(Objects.requireNonNull(element));
        return Collections.unmodifiableSet(set);
    }
}
