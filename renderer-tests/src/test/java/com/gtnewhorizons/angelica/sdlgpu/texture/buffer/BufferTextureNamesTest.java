package com.gtnewhorizons.angelica.sdlgpu.texture.buffer;

import static org.junit.jupiter.api.Assertions.*;

import com.gtnewhorizons.angelica.sdlgpu.shader.LinkedPrograms;
import com.gtnewhorizons.angelica.sdlgpu.texture.TextureBindings;
import com.gtnewhorizons.angelica.sdlgpu.texture.TextureTable;
import org.junit.jupiter.api.Test;

class BufferTextureNamesTest {
    @Test
    void placeholdersNeverOccupyApplicationTextureNames() {
        var table = new TextureTable();
        var buffers = new BufferTextures(
                () -> {
                    throw new AssertionError("No native driver expected");
                },
                new TextureBindings(),
                table,
                new LinkedPrograms());
        for (int name : new int[] {-1, -2, -3, -4, -16, -35, Integer.MIN_VALUE}) {
            assertFalse(buffers.contains(name));
            var texture = table.getOrCreate(name);
            assertFalse(buffers.contains(name));
            texture.buffer(new BufferTextureView());
            assertTrue(buffers.contains(name));
            table.deleted(name);
            assertFalse(buffers.contains(name));
        }
    }
}
