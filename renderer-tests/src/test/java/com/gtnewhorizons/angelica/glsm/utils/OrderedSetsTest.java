package com.gtnewhorizons.angelica.glsm.utils;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class OrderedSetsTest {
    @Test
    void preservesFirstOccurrenceOrderWhenValuesRepeat() {
        var set = OrderedSets.of("depth", "blend", "depth", "texture", "blend");
        assertEquals(List.of("depth", "blend", "texture"), List.copyOf(set));
        assertThrows(UnsupportedOperationException.class, () -> set.add("stencil"));
        assertThrows(UnsupportedOperationException.class, () -> set.remove("depth"));
    }

    @Test
    void rejectsNullAndDoesNotRetainTheCallerArray() {
        assertThrows(NullPointerException.class, () -> OrderedSets.of("depth", null));
        String[] source = {"depth", "blend"};
        var set = OrderedSets.of(source);
        source[0] = "stencil";
        assertEquals(List.of("depth", "blend"), List.copyOf(set));
    }
}
