package com.gtnewhorizons.angelica.sdlgpu.framebuffer;

import com.gtnewhorizons.angelica.sdlgpu.Subsystems;
import com.gtnewhorizons.angelica.sdlgpu.pipeline.Hashing;

/** Pipeline mask lookup and pipeline-key mixing for indexed output masks. */
public final class ColorMaskHooks {
    private ColorMaskHooks() {}

    public static int mask(int output) {
        return Subsystems.current().colorMasks().mask(output);
    }

    public static long hash(long upstream) {
        return Hashing.fmix64(upstream, Subsystems.current().colorMasks().packed());
    }

}
