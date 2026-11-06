package com.gtnewhorizons.angelica.sdlgpu.vertex;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL30.GL_MAP_WRITE_BIT;
import static org.lwjgl.opengl.GL44.GL_MAP_COHERENT_BIT;
import static org.lwjgl.opengl.GL44.GL_MAP_PERSISTENT_BIT;

import com.gtnewhorizons.angelica.sdlgpu.resource.PersistentMapping;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.Test;

class CoherentIndirectTest {
    private static final int COHERENT = GL_MAP_WRITE_BIT | GL_MAP_PERSISTENT_BIT | GL_MAP_COHERENT_BIT;

    private static PersistentMapping mapping(int flags) {
        var staging = ByteBuffer.allocateDirect(64).order(ByteOrder.nativeOrder());
        return new PersistentMapping(staging, 32, 64, flags);
    }

    @Test
    void readsTheLatestRecordsAtTheirBufferOffset() {
        var mapping = mapping(COHERENT);
        var commands = CoherentIndirect.view(mapping, 48, 16);
        mapping.staging.putInt(16, 36);
        mapping.staging.putInt(20, 3);
        assertEquals(36, commands.getInt(0));
        assertEquals(3, commands.getInt(4));
    }

    @Test
    void leavesFlushedMappingsToTheGpuCopy() {
        assertNull(CoherentIndirect.view(null, 0, 16));
        assertNull(CoherentIndirect.view(mapping(GL_MAP_WRITE_BIT | GL_MAP_PERSISTENT_BIT), 32, 16));
    }

    @Test
    void rejectsCommandsOutsideTheMappedRange() {
        var mapping = mapping(COHERENT);
        assertThrows(IllegalArgumentException.class, () -> CoherentIndirect.view(mapping, 16, 16));
        assertThrows(IllegalArgumentException.class, () -> CoherentIndirect.view(mapping, 88, 16));
    }
}
