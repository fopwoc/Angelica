package com.gtnewhorizons.angelica.sdlgpu.texture;

import com.gtnewhorizons.angelica.sdlgpu.Subsystems;
import com.gtnewhorizons.angelica.sdlgpu.Upstream;

/** Compute sampler overrides suspend per-target logical sampler bindings while they are active. */
public final class TextureBindingHooks {
    private TextureBindingHooks() {}

    public static void samplerOverride(long[] overrides) {
        Subsystems.current().textureBindings().samplerOverride(Upstream.context(), overrides != null);
    }
}
