package com.gtnewhorizons.angelica.sdlgpu.shader;

/** One active default-block uniform; an opaque array is one resource with its full array size. */
public record UniformVariable(String name, int type, int size, int location, int stages) {}
