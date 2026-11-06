package com.gtnewhorizons.angelica.sdlgpu.shader;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class DebugShaderPatchesTest {
    @Test
    void replacesEveryLiteralOccurrenceInOrder() {
        var patches = DebugShaderPatches.parse(List.of("a(b) ==> c", "", "c ==> d ==> e"));
        assertEquals("d ==> e + d ==> e", DebugShaderPatches.apply(patches, "a(b) + a(b)"));
    }

    @Test
    void rejectsLinesWithoutASeparator() {
        assertThrows(IllegalArgumentException.class, () -> DebugShaderPatches.parse(List.of("no separator")));
    }
}
