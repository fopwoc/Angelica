package com.gtnewhorizons.angelica.sdlgpu.geometry;

/** Raster vertex stage for GPU-emitted corners. Viewport/layer selection is performed by the draw executor. */
public final class GeometryRaster {
    private GeometryRaster() {}

    public record Bindings(int vertices, int corners, int control) {
        public Bindings {
            if (vertices < 0
                    || corners < 0
                    || control < 0
                    || vertices == corners
                    || vertices == control
                    || corners == control)
                throw new IllegalArgumentException("Geometry raster storage bindings must be distinct");
        }
    }

    public static String source(GeometryLayout layout, Bindings bindings) {
        var source = new StringBuilder("#version 460 core\n").append(GeometryTransport.output(layout));
        source.append("""
                layout(std430,binding=%d) readonly buffer _GeometryVertices { _GeometryOutput _geometry_vertices[]; };
                layout(std430,binding=%d) readonly buffer _GeometryCorners { uvec4 _geometry_corners[]; };
                layout(std430,binding=%d) readonly buffer _GeometryRasterControl { uint _geometry_viewport; uint _geometry_layer; uint _geometry_corner_first; };
                flat out int _geometry_primitive_id;
                """.formatted(bindings.vertices(), bindings.corners(), bindings.control()));
        for (var field : layout.outputs()) {
            String interpolation = switch (field.interpolation()) {
                case FLAT -> "flat ";
                case NOPERSPECTIVE -> "noperspective ";
                case SMOOTH -> "";
            };
            source.append(interpolation)
                    .append("out ")
                    .append(field.type())
                    .append(' ')
                    .append(field.name())
                    .append(";\n");
        }
        int arity = switch (layout.output()) {
            case POINTS -> 1;
            case LINE_STRIP -> 2;
            case TRIANGLE_STRIP -> 3;
        };
        source.append("""
                void main() {
                    uvec4 corner=_geometry_corners[_geometry_corner_first+uint(gl_VertexID)];
                    if (corner.x==0xffffffffu || corner.y!=_geometry_viewport || corner.z!=_geometry_layer) {
                        gl_Position=vec4(2,2,2,1);
                        return;
                    }
                    gl_Position=_geometry_vertices[corner.x]._geometry_position;
                    _geometry_primitive_id=int(corner.w);
                """);
        if (layout.output() == GeometryLayout.Output.POINTS)
            source.append("gl_PointSize=_geometry_vertices[corner.x]._geometry_point.x;\n");
        source.append("uint provoking=(_geometry_corner_first+uint(gl_VertexID)/")
                .append(arity)
                .append("u*")
                .append(arity)
                .append("u)+")
                .append(arity - 1)
                .append("u;\nuvec4 flatCorner=_geometry_corners[provoking];\n");
        for (var field : layout.outputs())
            source.append(field.name())
                    .append(
                            field.interpolation() == GeometryField.Interpolation.FLAT
                                    ? "=_geometry_vertices[flatCorner.x]."
                                    : "=_geometry_vertices[corner.x].")
                    .append(field.name())
                    .append(";\n");
        return source.append("}\n").toString();
    }
}
