package com.gtnewhorizons.angelica.sdlgpu.geometry;

/** GPU strip assembly with fixed per-invocation storage, deterministic ordering and no CPU readback. */
public final class GeometryAssembly {
    private GeometryAssembly() {}

    public record Bindings(int vertices, int counts, int corners, int control) {
        public Bindings {
            if (vertices < 0
                    || counts < 0
                    || corners < 0
                    || control < 0
                    || vertices == counts
                    || vertices == corners
                    || counts == corners)
                throw new IllegalArgumentException("Geometry assembly storage bindings must be distinct");
        }
    }

    public static int cornersPerJob(GeometryLayout layout) {
        return switch (layout.output()) {
            case POINTS -> layout.maxVertices();
            case LINE_STRIP -> Math.multiplyExact(Math.max(0, layout.maxVertices() - 1), 2);
            case TRIANGLE_STRIP -> Math.multiplyExact(Math.max(0, layout.maxVertices() - 2), 3);
        };
    }

    public static long cornerBytes(GeometryLayout layout, int jobs, long maxBufferBytes) {
        if (jobs < 0 || maxBufferBytes < 0) throw new IllegalArgumentException("Negative geometry allocation");
        long bytes = Math.multiplyExact(Math.multiplyExact((long) jobs, cornersPerJob(layout)), 16);
        if (bytes > maxBufferBytes)
            throw new IllegalArgumentException("Geometry corner transport exceeds the device buffer-size limit");
        return bytes;
    }

    public static String source(GeometryLayout layout, Bindings bindings) {
        int maxCorners = cornersPerJob(layout);
        String primitive = switch (layout.output()) {
            case POINTS -> "_geometry_corner(source+i,route);";
            case LINE_STRIP ->
                "if (stripVertices>=2u) { _geometry_corner(source+i-1u,route); _geometry_corner(source+i,route); }";
            case TRIANGLE_STRIP -> """
                    if (stripVertices>=3u) {
                        bool odd=((stripVertices-3u)&1u)!=0u;
                        _geometry_corner(source+i-(odd?1u:2u),route);
                        _geometry_corner(source+i-(odd?2u:1u),route);
                        _geometry_corner(source+i,route);
                    }
                    """;
        };
        return "#version 460 core\nlayout(local_size_x=64) in;\n" + GeometryTransport.output(layout)
                + """
                layout(std430,binding=%d) readonly buffer _GeometryVertices { _GeometryOutput _geometry_vertices[]; };
                layout(std430,binding=%d) readonly buffer _GeometryCounts { uvec4 _geometry_counts[]; };
                layout(std430,binding=%d) writeonly buffer _GeometryCorners { uvec4 _geometry_corners[]; };
                layout(std140,binding=%d) uniform _GeometryAssemblyControl { uint _geometry_jobs; uint _geometry_vertex_first; uint _geometry_corner_first; };
                uint _geometry_target;
                void _geometry_corner(uint vertex,ivec4 route) {
                    _geometry_corners[_geometry_target++]=uvec4(vertex,uint(route.x),uint(route.y),uint(route.z));
                }
                void main() {
                    uint job=gl_GlobalInvocationID.x;
                    if (job>=_geometry_jobs) return;
                    _geometry_target=_geometry_corner_first+job*%du;
                    for (uint i=0u;i<%du;i++) _geometry_corners[_geometry_target+i]=uvec4(0xffffffffu);
                    uvec4 counts=_geometry_counts[job];
                    if (counts.z!=0u || counts.x>%du) return;
                    uint source=_geometry_vertex_first+job*%du;
                    uint stripVertices=0u;
                    int previousStrip=-1;
                    for (uint i=0u;i<counts.x;i++) {
                        ivec4 route=_geometry_vertices[source+i]._geometry_routing;
                        if (route.w!=previousStrip) stripVertices=0u;
                        previousStrip=route.w;
                        stripVertices++;
                        %s
                    }
                }
                """.formatted(
                                bindings.vertices(),
                                bindings.counts(),
                                bindings.corners(),
                                bindings.control(),
                                maxCorners,
                                maxCorners,
                                layout.maxVertices(),
                                layout.maxVertices(),
                                primitive);
    }
}
