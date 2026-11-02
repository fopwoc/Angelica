package com.gtnewhorizons.angelica.sdlgpu.texture;

import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState.PixelStoreState;
import com.gtnewhorizons.angelica.sdlgpu.resource.PixelOps;
import java.nio.ByteBuffer;

/** Validates the complete client-memory span before unpack conversion allocates or reads native memory. */
public final class PixelUnpack {
    private PixelUnpack() {}

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
