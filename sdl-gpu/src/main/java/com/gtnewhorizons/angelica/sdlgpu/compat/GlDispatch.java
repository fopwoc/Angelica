package com.gtnewhorizons.angelica.sdlgpu.compat;

import org.lwjgl.opengl.GL;

/** Releases fabricated GL dispatch and function-provider state before the host resumes. */
public final class GlDispatch {
    private GlDispatch() {}

    public static void release() {
        try {
            GL.setCapabilities(null);
        } finally {
            GL.destroy();
        }
    }
}
