package com.gtnewhorizons.angelica.sdlgpu.texture;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.sdl.SDLGPU.*;

import com.gtnewhorizons.angelica.sdlgpu.resource.PixelOps;
import com.gtnewhorizons.angelica.sdlgpu.resource.ResourceManager;
import java.nio.ByteOrder;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

class ColorUploadsTest {
    @Test
    void rgbFloatAtmospherePixelsBecomeHalfPrecisionRgbaWithoutLosingHdr() {
        var data = MemoryUtil.memAlloc(28).order(ByteOrder.nativeOrder());
        data.position(4);
        float[] values = {.125f, .5f, 4f, -.25f, 1.5f, 16f};
        for (int i = 0; i < values.length; i++) data.putFloat(4 + i * 4, values[i]);
        int storage = ResourceManager.mapTextureFormat(
                GL_RGB16F, SDL_GPU_TEXTUREFORMAT_D24_UNORM, SDL_GPU_TEXTUREFORMAT_D24_UNORM_S8_UINT);
        assertEquals(SDL_GPU_TEXTUREFORMAT_R16G16B16A16_FLOAT, storage);
        try {
            var pixels = PixelOps.prepareUploadBuffer(GL_RGB, GL_FLOAT, storage, data, 2, 1);
            try {
                assertEquals(16, pixels.remaining());
                for (int p = 0; p < 2; p++) {
                    for (int c = 0; c < 3; c++)
                        assertEquals(values[p * 3 + c], Float.float16ToFloat(pixels.getShort(p * 8 + c * 2)));
                    assertEquals(1f, Float.float16ToFloat(pixels.getShort(p * 8 + 6)));
                }
                assertEquals(4, data.position());
                assertEquals(28, data.limit());
            } finally {
                MemoryUtil.memFree(pixels);
            }
        } finally {
            MemoryUtil.memFree(data);
        }
    }

    @Test
    void expandsRgbBytesByPixelInsteadOfReinterpretingTheirGroupBoundaries() {
        var data = MemoryUtil.memAlloc(6);
        for (int i = 0; i < 6; i++) data.put(i, (byte) (i * 31));
        try {
            var pixels = PixelOps.prepareUploadBuffer(
                    GL_RGB, GL_UNSIGNED_BYTE, SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM, data, 2, 1);
            try {
                assertEquals(8, pixels.remaining());
                for (int p = 0; p < 2; p++) {
                    for (int c = 0; c < 3; c++) assertEquals(data.get(p * 3 + c), pixels.get(p * 4 + c));
                    assertEquals((byte) 255, pixels.get(p * 4 + 3));
                }
            } finally {
                MemoryUtil.memFree(pixels);
            }
        } finally {
            MemoryUtil.memFree(data);
        }
    }

    @Test
    void clampsNormalizedStorageWhileFloatingStorageRetainsNegativeAndLargeComponents() {
        var data = MemoryUtil.memAlloc(16).order(ByteOrder.nativeOrder());
        data.putFloat(0, -.5f).putFloat(4, .5f).putFloat(8, 8f).putFloat(12, Float.NaN);
        try {
            var normalized =
                    PixelOps.prepareUploadBuffer(GL_RGBA, GL_FLOAT, SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM, data, 1, 1);
            try {
                assertArrayEquals(
                        new byte[] {0, (byte) 128, (byte) 255, 0},
                        new byte[] {normalized.get(0), normalized.get(1), normalized.get(2), normalized.get(3)});
            } finally {
                MemoryUtil.memFree(normalized);
            }
            assertSame(
                    data,
                    PixelOps.prepareUploadBuffer(
                            GL_RGBA, GL_FLOAT, SDL_GPU_TEXTUREFORMAT_R32G32B32A32_FLOAT, data, 1, 1));
        } finally {
            MemoryUtil.memFree(data);
        }
    }

    @Test
    void bgraUsesWholeFloatComponentsAndNotIndividualBytes() {
        var data = MemoryUtil.memAlloc(16).order(ByteOrder.nativeOrder());
        data.putFloat(0, .25f).putFloat(4, .5f).putFloat(8, 2f).putFloat(12, 1f);
        try {
            var pixels = PixelOps.prepareUploadBuffer(
                    GL_BGRA, GL_FLOAT, SDL_GPU_TEXTUREFORMAT_R16G16B16A16_FLOAT, data, 1, 1);
            try {
                assertEquals(2f, Float.float16ToFloat(pixels.getShort(0)));
                assertEquals(.5f, Float.float16ToFloat(pixels.getShort(2)));
                assertEquals(.25f, Float.float16ToFloat(pixels.getShort(4)));
                assertEquals(1f, Float.float16ToFloat(pixels.getShort(6)));
            } finally {
                MemoryUtil.memFree(pixels);
            }
        } finally {
            MemoryUtil.memFree(data);
        }
    }

    @Test
    void convertsSwappedHalfComponentsAndChecksBothMemoryRegions() {
        var data = MemoryUtil.memAlloc(6).order(ByteOrder.nativeOrder());
        var destination = MemoryUtil.memAlloc(16).order(ByteOrder.nativeOrder());
        for (int i = 0; i < 3; i++) data.putShort(i * 2, Short.reverseBytes(Float.floatToFloat16(i + .5f)));
        var conversion = ColorUploads.conversion(GL_RGB, GL_HALF_FLOAT, SDL_GPU_TEXTUREFORMAT_R32G32B32A32_FLOAT, true);
        try {
            conversion.copy(data, 0, destination, 0, 1);
            for (int i = 0; i < 4; i++) assertEquals(i == 3 ? 1f : i + .5f, destination.getFloat(i * 4));
            assertThrows(IllegalArgumentException.class, () -> conversion.copy(data, 1, destination, 0, 1));
            assertThrows(IllegalArgumentException.class, () -> conversion.copy(data, 0, destination, 1, 1));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> ColorUploads.prepare(GL_RGB, GL_FLOAT, SDL_GPU_TEXTUREFORMAT_R16G16B16A16_FLOAT, data, 1, 1));
        } finally {
            MemoryUtil.memFree(data);
            MemoryUtil.memFree(destination);
        }
    }

    @Test
    void exactBytesAvoidCopiesAndPackedOrIntegerFormatsKeepTheirDedicatedPaths() {
        var data = MemoryUtil.memAlloc(4);
        try {
            assertSame(
                    data,
                    PixelOps.prepareUploadBuffer(
                            GL_RGBA, GL_UNSIGNED_BYTE, SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM, data, 1, 1));
            assertNull(ColorUploads.conversion(
                    GL_RGBA_INTEGER, GL_UNSIGNED_BYTE, SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UINT, false));
            assertNull(ColorUploads.conversion(
                    GL_RGBA, GL_UNSIGNED_INT_2_10_10_10_REV, SDL_GPU_TEXTUREFORMAT_R10G10B10A2_UNORM, false));
        } finally {
            MemoryUtil.memFree(data);
        }
    }
}
