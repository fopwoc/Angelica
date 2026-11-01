package com.gtnewhorizons.angelica.sdlgpu.framebuffer;

import com.gtnewhorizons.angelica.sdlgpu.Subsystems;
import com.gtnewhorizons.angelica.sdlgpu.pipeline.Hashing;

/** Pipeline blend-op lookup and pipeline-key mixing while per-output equations differ. */
public final class BlendEquationHooks {
    private BlendEquationHooks() {}

    public static int color(int upstream, int output) {
        return Subsystems.current().blendEquations().color(output, upstream);
    }

    public static int alpha(int upstream, int output) {
        return Subsystems.current().blendEquations().alpha(output, upstream);
    }

    public static long hash(long upstream) {
        var equations = Subsystems.current().blendEquations();
        return equations.indexed() ? Hashing.fmix64(upstream, equations.packed()) : upstream;
    }
}
