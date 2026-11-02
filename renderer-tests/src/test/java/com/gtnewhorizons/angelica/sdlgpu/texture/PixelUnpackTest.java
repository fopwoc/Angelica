package com.gtnewhorizons.angelica.sdlgpu.texture;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL11.*;

import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState.PixelStoreState;
import com.gtnewhorizons.angelica.sdlgpu.resource.PixelOps;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

class PixelUnpackTest {
    @Test
    void rejectsShortUnpackSourcesBeforeAllocationAndExplainsTheFullAddressingState() {
        var state = new PixelStoreState();
        state.unpackRowLength = 4;
        state.unpackSkipRows = 1;
        state.unpackSkipPixels = 1;
        var data = MemoryUtil.memAlloc(64);
        data.position(3).limit(46);
        try {
            var failure = assertThrows(
                    IllegalArgumentException.class,
                    () -> PixelOps.applyUnpackPixelStore(data, 2, 2, GL_RGBA, GL_UNSIGNED_BYTE, state));
            for (String part : new String[] {
                "required=44",
                "available=43",
                "position=3",
                "limit=46",
                "capacity=64",
                "unpackRowLength=4",
                "unpackSkipRows=1",
                "unpackSkipPixels=1",
                "rowStride=16"
            }) assertTrue(failure.getMessage().contains(part), failure.getMessage());
            assertEquals(3, data.position());
            assertEquals(46, data.limit());
        } finally {
            MemoryUtil.memFree(data);
        }
    }

    @Test
    void acceptsExactlyTheLastReadableByteAndPreservesSourceBounds() {
        var state = new PixelStoreState();
        state.unpackRowLength = 4;
        state.unpackSkipRows = 1;
        state.unpackSkipPixels = 1;
        var data = MemoryUtil.memAlloc(64);
        for (int i = 0; i < 64; i++) data.put(i, (byte) i);
        data.position(3).limit(47);
        try {
            var packed = PixelOps.applyUnpackPixelStore(data, 2, 2, GL_RGBA, GL_UNSIGNED_BYTE, state);
            try {
                assertEquals(16, packed.remaining());
                for (int row = 0; row < 2; row++)
                    for (int i = 0; i < 8; i++) assertEquals((byte) (23 + row * 16 + i), packed.get(row * 8 + i));
                assertEquals(3, data.position());
                assertEquals(47, data.limit());
            } finally {
                if (packed != data) MemoryUtil.memFree(packed);
            }
        } finally {
            MemoryUtil.memFree(data);
        }
    }

    @Test
    void rejectsUnaddressableUnpackSpansWithoutAllocatingTheDestination() {
        var state = new PixelStoreState();
        state.unpackRowLength = Integer.MAX_VALUE;
        var data = MemoryUtil.memAlloc(4);
        try {
            assertThrows(
                    ArithmeticException.class,
                    () -> PixelOps.applyUnpackPixelStore(data, Integer.MAX_VALUE, 2, GL_RGBA, GL_UNSIGNED_BYTE, state));
        } finally {
            MemoryUtil.memFree(data);
        }
    }
}
