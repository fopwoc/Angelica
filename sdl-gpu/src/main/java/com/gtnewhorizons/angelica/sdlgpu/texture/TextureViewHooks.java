package com.gtnewhorizons.angelica.sdlgpu.texture;

import com.gtnewhorizons.angelica.sdlgpu.Subsystems;
import com.gtnewhorizons.angelica.sdlgpu.Upstream;
import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import com.gtnewhorizons.angelica.sdlgpu.resource.ResourceManager;

/** Texture views, buffer textures and sampler fallbacks at the upstream texture lifecycle and sampling points. */
public final class TextureViewHooks {
    private TextureViewHooks() {}

    public static boolean referenced(ResourceManager resources, long handle) {
        return Upstream.textureReferenced(resources, handle);
    }

    public static void sharedAccess(long handle) {
        Subsystems.current().textureViews().sharedAccess(handle);
    }

    public static void beforeAllocation(int name) {
        Subsystems.current().textureViews().beforeAllocation(name);
    }

    public static void prepare(ContextState state) {
        var subsystems = Subsystems.current();
        subsystems.bufferTextures().prepare(state);
        subsystems.textureViews().prepare(state);
    }

    public static long samplingHandle(ResourceManager resources, int name) {
        var subsystems = Subsystems.current();
        var buffers = subsystems.bufferTextures();
        return buffers.contains(name)
                ? buffers.samplingHandle(name)
                : subsystems.textureViews().samplingHandle(resources, name);
    }
}
