package com.gtnewhorizons.angelica.sdlgpu.geometry;

/** One original GL draw; base instance is an unsigned bit pattern, separate from the zero-based instance ID. */
record GeometryDraw(
        int first,
        int count,
        int indexType,
        long indices,
        int baseVertex,
        int instances,
        int baseInstance,
        int drawId) {
    GeometryDraw {
        if (first < 0 || count < 0 || instances < 0 || drawId < 0 || indices < 0)
            throw new IllegalArgumentException("Invalid geometry draw range");
    }

    int primitivesPerInstance(GeometryLayout.Input input) {
        return count / input.vertices();
    }

    int primitives(GeometryLayout.Input input) {
        return Math.multiplyExact(primitivesPerInstance(input), instances);
    }
}
