package com.gtnewhorizons.angelica.sdlgpu.geometry;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.util.shaderc.Shaderc.*;
import static org.lwjgl.util.spvc.Spvc.*;

import java.nio.ByteBuffer;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryStack;

class GeometryComputeTest {
    static final GeometryCompute.Bindings BINDINGS = new GeometryCompute.Bindings(0, 1, 2, 4);
    static final String TRIANGLE = """
            #version 460 core
            layout(triangles, invocations=6) in;
            layout(triangle_strip, max_vertices=3) out;
            layout(std140,binding=3) uniform Projection { mat4 faces[6]; };
            in vec3 incoming[];
            out vec3 outgoing;
            flat out int face;
            void main() {
                for (int i=0;i<3;i++) {
                    outgoing=incoming[i];
                    face=gl_InvocationID;
                    gl_ViewportIndex=gl_InvocationID;
                    gl_Layer=2;
                    gl_PrimitiveID=gl_PrimitiveIDIn;
                    gl_Position=faces[gl_InvocationID]*gl_in[i].gl_Position;
                    EmitVertex();
                }
                EndPrimitive();
            }
            """;

    @Test
    void lowersInvokedTriangleGeometryToRealComputeSpirvAndMetal() {
        var kernel = GeometryCompute.lower(TRIANGLE, BINDINGS);
        assertEquals(6, kernel.layout().invocations());
        assertEquals(3, kernel.layout().maxVertices());
        assertEquals(
                GeometryField.Interpolation.FLAT,
                kernel.layout().outputs().get(1).interpolation());
        var bytes = compile(kernel.source());
        try (var stack = MemoryStack.stackPush()) {
            var pointer = stack.callocPointer(1);
            assertEquals(SPVC_SUCCESS, spvc_context_create(pointer));
            long context = pointer.get(0);
            try {
                assertEquals(
                        SPVC_SUCCESS,
                        spvc_context_parse_spirv(context, bytes.asIntBuffer(), bytes.remaining() / 4, pointer));
                long parsed = pointer.get(0);
                assertEquals(
                        SPVC_SUCCESS,
                        spvc_context_create_compiler(
                                context, SPVC_BACKEND_MSL, parsed, SPVC_CAPTURE_MODE_TAKE_OWNERSHIP, pointer));
                long compiler = pointer.get(0);
                assertEquals(
                        SPVC_SUCCESS,
                        spvc_compiler_compile(compiler, pointer),
                        spvc_context_get_last_error_string(context));
                String metal = pointer.getStringUTF8(0);
                assertTrue(metal.contains("kernel void"), metal);
                assertFalse(metal.contains("EmitVertex"));
            } finally {
                spvc_context_destroy(context);
            }
        }
    }

    @Test
    void preservesEmitOrderStripBreaksAndEarlyReturnInGeneratedKernel() {
        var source = TRIANGLE.replace(
                "outgoing=incoming[i];", "outgoing=incoming[i]; if (i==2) { EndPrimitive(); return; }");
        var kernel = GeometryCompute.lower(source, BINDINGS);
        compile(kernel.source());
        assertTrue(kernel.source().contains("_geometry_stage();"));
        assertTrue(kernel.source().contains("_geometry_counts[_geometry_job]"));
        assertTrue(kernel.source().contains("_geometry_overflow=1u; return;"));
    }

    @Test
    void computesStd430TransportAndBoundedDispatchSizes() {
        var layout = GeometryCompute.lower(TRIANGLE, BINDINGS).layout();
        assertEquals(96, layout.inputStride());
        assertEquals(48, layout.outputStride());
        assertEquals(32, layout.outputOffset("outgoing"));
        assertEquals(44, layout.outputOffset("face"));
        assertEquals(new GeometryLayout.Dispatch(12, 1, 192, 1728, 192), layout.dispatch(2, 4096));
        assertEquals(0, layout.dispatch(0, 0).jobs());
        assertThrows(IllegalArgumentException.class, () -> layout.dispatch(2, 1000));
        assertThrows(ArithmeticException.class, () -> layout.dispatch(Integer.MAX_VALUE, Long.MAX_VALUE));
        assertEquals(48, new GeometryField("mat3", "transform").size());
        assertEquals(32, new GeometryField("mat2x3", "transform").size());
        assertEquals(16, new GeometryField("vec3", "normal").arrayStride());
    }

    @Test
    void rejectsInterfacesAndResourcesThatCannotBeTransportedCorrectly() {
        for (var source : List.of(
                TRIANGLE.replace("in vec3 incoming[];", "in Payload { vec3 value; } incoming[];"),
                TRIANGLE.replace("out vec3 outgoing;", "out vec3 outgoing[2];"),
                TRIANGLE.replace("out vec3 outgoing;", "out dvec3 outgoing;"),
                TRIANGLE.replace("out vec3 outgoing;", "layout(location=3) out vec3 outgoing;"),
                TRIANGLE.replace("out vec3 outgoing;", "precise out vec3 outgoing;"),
                TRIANGLE.replace("EmitVertex();", "EmitStreamVertex(0);"),
                TRIANGLE.replace("max_vertices=3", "max_vertices=2048"),
                TRIANGLE.replace("invocations=6", "invocations=64"),
                TRIANGLE.replace("gl_in[i].gl_Position", "gl_in[i].gl_ClipDistance[0]"),
                TRIANGLE.replace("binding=3", "binding=4"),
                TRIANGLE.replace("void main()", "layout(std430,binding=1) buffer User {uint data[];}; void main()"),
                "#define BAD 1\n" + TRIANGLE)) {
            assertThrows(RuntimeException.class, () -> GeometryCompute.lower(source, BINDINGS), source);
        }
    }

    @Test
    void handlesHelperFunctionsCommentsAndSeparateLayoutDeclarations() {
        var source = TRIANGLE.replace(
                        "layout(triangles, invocations=6) in;", "layout(triangles) in; layout(invocations=6) in;")
                .replace(
                        "void main()",
                        "// layout(points) in; EmitVertex();\nvec3 helper(in vec3 v) { return v; }\nvoid main()")
                .replace("outgoing=incoming[i]", "outgoing=helper(incoming[i])");
        compile(GeometryCompute.lower(source, BINDINGS).source());
    }

    static ByteBuffer compile(String source) {
        long compiler = shaderc_compiler_initialize();
        long result =
                shaderc_compile_into_spv(compiler, source, shaderc_glsl_compute_shader, "geometry.comp", "main", 0);
        try {
            assertEquals(
                    shaderc_compilation_status_success,
                    shaderc_result_get_compilation_status(result),
                    () -> shaderc_result_get_error_message(result) + "\n" + source);
            var bytes = shaderc_result_get_bytes(result);
            return ByteBuffer.allocateDirect(bytes.remaining())
                    .order(java.nio.ByteOrder.nativeOrder())
                    .put(bytes)
                    .flip();
        } finally {
            shaderc_result_release(result);
            shaderc_compiler_release(compiler);
        }
    }
}
