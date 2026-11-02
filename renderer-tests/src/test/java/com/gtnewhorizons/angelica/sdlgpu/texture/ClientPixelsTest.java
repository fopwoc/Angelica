package com.gtnewhorizons.angelica.sdlgpu.texture;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL11.*;

import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState.PixelStoreState;
import com.gtnewhorizons.angelica.sdlgpu.resource.PixelOps;
import java.nio.ByteOrder;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

class ClientPixelsTest {
    @Test
    void computesLastReadableByteWithAlignmentAndSkips() {
        var store = new PixelStoreState();
        assertEquals(24, ClientPixels.requiredBytes(3, 2, 4, store));
        assertEquals(21, ClientPixels.requiredBytes(3, 2, 3, store));
        store.unpackRowLength = 5;
        store.unpackSkipRows = 1;
        store.unpackSkipPixels = 2;
        assertEquals(47, ClientPixels.requiredBytes(3, 2, 3, store));
        assertEquals(0, ClientPixels.requiredBytes(3, 0, 3, store));
    }

    @Test
    void rejectsUnaddressableSpansAndInvalidStateBeforeBorrowingMemory() {
        var store = new PixelStoreState();
        assertThrows(ArithmeticException.class, () -> ClientPixels.requiredBytes(Integer.MAX_VALUE, 2, 4, store));
        assertThrows(IllegalArgumentException.class, () -> ClientPixels.requiredBytes(-1, 2, 4, store));
        store.unpackAlignment = 3;
        assertThrows(IllegalArgumentException.class, () -> ClientPixels.requiredBytes(3, 2, 4, store));
    }

    @Test
    void exposesTheNativeAllocationForAZeroLimitPreviewTexture() {
        var state = new PixelStoreState();
        state.unpackAlignment = 1;
        var data = MemoryUtil.memCalloc(512 * 512 * 4);
        data.limit(0);
        try {
            var view = ClientPixels.nativeView(data, 512, 512, GL_RGBA, GL_UNSIGNED_BYTE, state);
            assertEquals(1048576, view.remaining());
            assertEquals(MemoryUtil.memAddress(data), MemoryUtil.memAddress(view));
            assertEquals(0, data.position());
            assertEquals(0, data.limit());
            var packed = PixelOps.applyUnpackPixelStore(view, 512, 512, GL_RGBA, GL_UNSIGNED_BYTE, state);
            assertSame(view, packed);
        } finally {
            MemoryUtil.memFree(data);
        }
    }

    @Test
    void boundsNativeViewsAtThePositionAndUnpacksSkipsWithoutChangingReadOnlySources() {
        var state = new PixelStoreState();
        state.unpackRowLength = 4;
        state.unpackSkipRows = 1;
        state.unpackSkipPixels = 1;
        var data = MemoryUtil.memAlloc(64);
        for (int i = 0; i < 64; i++) data.put(i, (byte) i);
        data.position(7).limit(7);
        var readOnly = data.asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN);
        try {
            var view = ClientPixels.nativeView(readOnly, 2, 2, GL_RGBA, GL_UNSIGNED_BYTE, state);
            assertEquals(7, view.position());
            assertEquals(51, view.limit());
            assertTrue(view.isReadOnly());
            assertEquals(ByteOrder.LITTLE_ENDIAN, view.order());
            assertEquals(7, readOnly.limit());
            var packed = PixelOps.applyUnpackPixelStore(view, 2, 2, GL_RGBA, GL_UNSIGNED_BYTE, state);
            try {
                for (int row = 0; row < 2; row++)
                    for (int i = 0; i < 8; i++) assertEquals((byte) (27 + row * 16 + i), packed.get(row * 8 + i));
            } finally {
                MemoryUtil.memFree(packed);
            }
        } finally {
            MemoryUtil.memFree(data);
        }
    }

    @Test
    void refusesNativeTransfersBeyondTheActualAllocation() {
        var data = MemoryUtil.memAlloc(16);
        data.position(1).limit(1);
        try {
            var failure = assertThrows(
                    IllegalArgumentException.class,
                    () -> ClientPixels.nativeView(data, 2, 2, GL_RGBA, GL_UNSIGNED_BYTE, new PixelStoreState()));
            assertTrue(failure.getMessage().contains("required=16, available=15"));
            assertEquals(1, data.position());
            assertEquals(1, data.limit());
        } finally {
            MemoryUtil.memFree(data);
        }
    }

    @Test
    void unpacksDefaultAlignmentPaddingExactlyOnceForRawClientPointers() {
        var state = new PixelStoreState();
        var data = MemoryUtil.memAlloc(7);
        for (int i = 0; i < 7; i++) data.put(i, (byte) i);
        try (var source = ClientPixels.borrow(MemoryUtil.memAddress(data), 3, 2, GL_RED, GL_UNSIGNED_BYTE, state)) {
            var view = ClientPixels.nativeView(source.data(), 3, 2, GL_RED, GL_UNSIGNED_BYTE, state);
            var packed = PixelOps.applyUnpackPixelStore(view, 3, 2, GL_RED, GL_UNSIGNED_BYTE, state);
            try {
                assertEquals(6, packed.remaining());
                for (int i = 0; i < 6; i++) assertEquals((byte) (i + (i >= 3 ? 1 : 0)), packed.get(i));
            } finally {
                MemoryUtil.memFree(packed);
            }
        } finally {
            MemoryUtil.memFree(data);
        }
    }
}
