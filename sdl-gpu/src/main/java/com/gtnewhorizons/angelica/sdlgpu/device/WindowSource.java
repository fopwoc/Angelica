package com.gtnewhorizons.angelica.sdlgpu.device;

/**
 * The application-owned SDL window the device presents into. The application keeps the window and its event loop; the
 * renderer only queries it, on the threads the application allows.
 */
public interface WindowSource {
    WindowSource NONE = new WindowSource() {
        @Override public long window() { return 0L; }
        @Override public boolean wasResized() { return false; }
    };

    /** SDL window handle, or 0 while there is none. */
    long window();

    /** Whether the size changed since the application last reported it. */
    boolean wasResized();

    /** Size the renderer draws at, given the native pixel size packed as {@code width << 32 | height}. */
    default long renderSize(long nativePixels) { return nativePixels; }
}
