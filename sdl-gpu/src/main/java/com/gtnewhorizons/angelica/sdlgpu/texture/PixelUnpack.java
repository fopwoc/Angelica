package com.gtnewhorizons.angelica.sdlgpu.texture;

import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState.PixelStoreState;
import com.gtnewhorizons.angelica.sdlgpu.resource.PixelOps;
import java.nio.ByteBuffer;

/** Validates the complete client-memory span before unpack conversion allocates or reads native memory. */
public final class PixelUnpack {
    private PixelUnpack() {}

    public static ByteBuffer bufferView(
            ByteBuffer staging, long offset, int width, int height, int format, int type, PixelStoreState state) {
        if (offset < 0 || offset > staging.capacity())
            throw new IllegalArgumentException("Pixel unpack buffer offset is outside storage: offset=" + offset
                    + ", capacity=" + staging.capacity());
        var layout = UnpackLayout.of(width, height, 1, PixelOps.glPixelSize(format, type), state, 0, 0);
        var source = staging.duplicate().order(staging.order()).clear().position(Math.toIntExact(offset));
        requireSource(source, width, height, format, type, state);
        source.limit(Math.addExact(source.position(), layout.requiredBytes()));
        return source.slice().order(staging.order());
    }

    public static boolean isTightlyPacked(int width, int height, int pixelBytes, PixelStoreState state) {
        if (width == 0 || height == 0) return true;
        var layout = UnpackLayout.of(width, height, 1, pixelBytes, state, 0, 0);
        return layout.start() == 0
                && (height == 1 || layout.rowStride() == Math.multiplyExact((long) width, pixelBytes));
    }

    public static void requireSource(
            ByteBuffer source, int width, int height, int format, int type, PixelStoreState state) {
        var layout = UnpackLayout.of(width, height, 1, PixelOps.glPixelSize(format, type), state, 0, 0);
        if (source.remaining() < layout.requiredBytes())
            throw new IllegalArgumentException("Pixel unpack source is too small: width=" + width + ", height=" + height
                    + ", format=0x" + Integer.toHexString(format) + ", type=0x" + Integer.toHexString(type)
                    + ", required=" + layout.requiredBytes() + ", available=" + source.remaining() + ", position="
                    + source.position()
                    + ", limit=" + source.limit() + ", capacity=" + source.capacity() + ", direct=" + source.isDirect()
                    + ", unpackAlignment=" + state.unpackAlignment + ", unpackRowLength=" + state.unpackRowLength
                    + ", unpackSkipRows=" + state.unpackSkipRows + ", unpackSkipPixels=" + state.unpackSkipPixels
                    + ", rowStride=" + layout.rowStride() + ", packAlignment=" + state.packAlignment);
    }
}
