package com.gtnewhorizons.angelica.sdlgpu.geometry;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class GeometryViewportsTest {
    @Test
    void keepsIndexedRasterStateIndependentAndClampsDepthEndpoints() {
        var views = new GeometryViewports();
        views.viewport(2, 10, 20, 30, 40);
        views.scissor(2, 11, 21, 28, 38);
        views.scissorEnabled(2, true);
        views.depth(2, 2, -1);
        assertEquals(new GeometryViewports.View(10, 20, 30, 40, 11, 21, 28, 38, 1, 0, true), views.get(2));
        assertEquals(0, views.get(1).width());
        assertFalse(views.get(1).scissorEnabled());
        assertThrows(IllegalArgumentException.class, () -> views.viewport(16, 0, 0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> views.scissor(2, 0, 0, -1, 1));
    }

    @Test
    void indexedUpdateScopeRestoresItsNestingStateOnFailure() {
        var views = new GeometryViewports();
        assertThrows(
                IllegalStateException.class,
                () -> views.indexed(() -> {
                    assertTrue(views.indexed());
                    views.indexed(() -> assertTrue(views.indexed()));
                    assertTrue(views.indexed());
                    throw new IllegalStateException("expected");
                }));
        assertFalse(views.indexed());
    }
}
