package com.gtnewhorizons.angelica.sdlgpu.geometry;

/** Shared GLSL record declarations; all stages must agree with the Java std430 sizes. */
final class GeometryTransport {
    private GeometryTransport() {}

    static String output(GeometryLayout layout) {
        var source = new StringBuilder("struct _GeometryOutput { vec4 _geometry_position; ivec4 _geometry_routing;\n");
        for (var field : layout.outputs())
            source.append(field.type()).append(' ').append(field.name()).append(";\n");
        return source.append("};\n").toString();
    }
}
