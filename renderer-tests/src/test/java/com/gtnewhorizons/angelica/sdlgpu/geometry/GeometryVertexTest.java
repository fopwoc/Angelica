package com.gtnewhorizons.angelica.sdlgpu.geometry;

import static org.junit.jupiter.api.Assertions.*;

import com.gtnewhorizons.angelica.glsm.shader.SpirvCompiler;
import com.gtnewhorizons.angelica.sdlgpu.shader.ShaderManager;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.shaderc.Shaderc;

class GeometryVertexTest {
    static final String GEOMETRY = """
            #version 460 core
            layout(triangles,invocations=6) in;
            layout(triangle_strip,max_vertices=3) out;
            in vec3 incoming[];
            out vec3 outgoing;
            void main() {
                for(int i=0;i<3;i++) {
                    outgoing=incoming[i];
                    gl_Position=gl_in[i].gl_Position;
                    gl_ViewportIndex=gl_InvocationID;
                    EmitVertex();
                }
                EndPrimitive();
            }
            """;
    static final String VERTEX = """
            #version 460 core
            layout(location=0) in vec3 position;
            layout(location=3) in ivec4 joints;
            layout(location=4) in mat3 basis;
            out vec3 incoming;
            vec3 helper(in vec3 value) { return value; }
            void main() {
                incoming=helper(basis*position)+vec3(joints.xyz)*0.01;
                gl_Position=vec4(position+vec3(float(gl_VertexID)*0.001),1);
            }
            """;

    @Test
    void lowersPointAmplificationWithVertexAndGeometryBufferSamplers() {
        var geometry = GeometryCompute.lower("""
                #version 460 core
                layout(points) in;
                layout(points,max_vertices=2) out;
                in vec4 incoming[];
                out vec4 shade;
                uniform samplerBuffer tint;
                void main(){
                    for(int i=0;i<2;i++){
                        shade=incoming[0]*texelFetch(tint,i);
                        gl_Position=gl_in[0].gl_Position;
                        gl_PointSize=float(i+1);
                        EmitVertex();EndPrimitive();
                    }
                }
                """, GeometryComputeTest.BINDINGS);
        var plan = GeometryVertex.lower("""
                #version 460 core
                uniform samplerBuffer positions;
                out vec4 incoming;
                void main(){incoming=vec4(.5);gl_Position=texelFetch(positions,gl_VertexID);}
                """, geometry, Map.of());
        assertEquals(64, geometry.layout().outputStride());
        assertEquals(48, geometry.layout().outputOffset("shade"));
        var compiled = SpirvCompiler.compile(
                plan.source(),
                Shaderc.shaderc_glsl_compute_shader,
                "geometry-point-samplers",
                SpirvCompiler.Options.vulkanForced460Core(),
                false);
        assertNotNull(compiled.spirv(), compiled.error());
        try {
            var map = ShaderManager.remapSpirvForComputeSDLGPU(compiled.spirv());
            assertTrue(List.of(map.samplerNames()).containsAll(List.of("tint", "_vertex_uniform_positions")));
        } finally {
            MemoryUtil.memFree(compiled.spirv());
        }
    }

    @Test
    void compilesGpuVertexFetchAndGeometryInOneComputeKernel() {
        var plan =
                GeometryVertex.lower(VERTEX, GeometryCompute.lower(GEOMETRY, GeometryComputeTest.BINDINGS), Map.of());
        assertEquals(3, plan.attributes().size());
        assertEquals(3, plan.attributes().get(2).columns());
        GeometryComputeTest.compile(plan.source());
    }

    @Test
    void retainsDefaultUniformIdentityAfterComputeBindingsAreRemapped() {
        String vertex = VERTEX.replace("void main()", "uniform float gain; void main()")
                .replace("incoming=helper", "incoming=vec3(gain)+helper");
        String geometry = GEOMETRY.replace("void main()", "uniform mat4 faces[6]; uniform float gain; void main()")
                .replace(
                        "gl_Position=gl_in[i].gl_Position",
                        "gl_Position=faces[gl_InvocationID]*gl_in[i].gl_Position+vec4(gain)");
        var plan =
                GeometryVertex.lower(vertex, GeometryCompute.lower(geometry, GeometryComputeTest.BINDINGS), Map.of());
        var compiled = SpirvCompiler.compile(
                plan.source(),
                Shaderc.shaderc_glsl_compute_shader,
                "geometry-uniform-test",
                SpirvCompiler.Options.vulkanForced460Core(),
                false);
        assertNotNull(compiled.spirv(), compiled.error());
        try {
            ShaderManager.remapSpirvForComputeSDLGPU(compiled.spirv());
            var reflection = ShaderManager.reflectStage(compiled.spirv(), false);
            var names = reflection.uboMembers().stream()
                    .map(member -> member.name())
                    .toList();
            assertTrue(names.containsAll(List.of("faces", "gain", "_vertex_uniform_gain")), names.toString());
            assertFalse(names.contains("_geometry_primitives"));
        } finally {
            MemoryUtil.memFree(compiled.spirv());
        }
    }

    @Test
    void compilesInstancedVertexIdsWithoutChangingGeometryInvocationIds() {
        String source = VERTEX.replace(
                "float(gl_VertexID)", "float(gl_VertexID+gl_InstanceID+gl_BaseInstance+gl_DrawID+gl_BaseVertex)");
        var plan =
                GeometryVertex.lower(source, GeometryCompute.lower(GEOMETRY, GeometryComputeTest.BINDINGS), Map.of());
        GeometryComputeTest.compile(plan.source());
    }

    @Test
    void reservesExplicitAndBoundMatrixLocationsBeforeAutomaticAssignment() {
        var source = VERTEX.replace("layout(location=0) ", "").replace("layout(location=4) ", "");
        var plan = GeometryVertex.lower(
                source, GeometryCompute.lower(GEOMETRY, GeometryComputeTest.BINDINGS), Map.of("basis", 0));
        assertEquals(4, plan.attributes().get(0).location());
        assertEquals(0, plan.attributes().get(2).location());
        assertThrows(
                IllegalArgumentException.class,
                () -> GeometryVertex.lower(
                        VERTEX.replace("location=4", "location=3"),
                        GeometryCompute.lower(GEOMETRY, GeometryComputeTest.BINDINGS),
                        Map.of()));
    }

    @Test
    void rejectsVertexEffectsAndUntransportableInterfaces() {
        var kernel = GeometryCompute.lower(GEOMETRY, GeometryComputeTest.BINDINGS);
        assertThrows(
                UnsupportedOperationException.class,
                () -> GeometryVertex.lower(
                        VERTEX.replace("void main()", "layout(rgba32f,binding=1) uniform image2D texture; void main()"),
                        kernel,
                        Map.of()));
        assertThrows(
                UnsupportedOperationException.class,
                () -> GeometryVertex.lower(VERTEX.replace("gl_VertexID", "gl_ViewIndex"), kernel, Map.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> GeometryVertex.lower(VERTEX.replace("out vec3 incoming", "out vec2 incoming"), kernel, Map.of()));
    }
}
