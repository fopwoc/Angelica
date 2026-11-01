package com.gtnewhorizons.angelica.sdlgpu.framebuffer;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_SCISSOR_TEST;

import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;

/** Validation shared by indexed-capability entry points and backend execution. */
public final class IndexedCapabilities {
    private IndexedCapabilities() {}

    public static void require(int capability, int index) {
        int limit = switch (capability) {
            case GL_BLEND -> ContextState.MAX_COLOR_ATTACHMENTS;
            case GL_SCISSOR_TEST -> 1;
            default -> throw new UnsupportedOperationException("Indexed capability not implemented: " + capability);
        };
        if (index < 0 || index >= limit)
            throw new IllegalArgumentException("Indexed capability index out of range: " + index);
    }
}
