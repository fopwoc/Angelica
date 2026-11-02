package com.gtnewhorizons.angelica.sdlgpu.geometry;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.util.shaderc.Shaderc.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class GeometryAssemblyTest {
    @Test
    void compilesAllPrimitiveAssemblyTopologiesAndRasterInterfaces() {
        for (var output : GeometryLayout.Output.values()) {
            var layout = new GeometryLayout(
                    GeometryLayout.Input.TRIANGLES,
                    output,
                    6,
                    1,
                    List.of(),
                    List.of(
                            new GeometryField("vec2", "uv"),
                            new GeometryField("int", "tag", GeometryField.Interpolation.FLAT)));
            GeometryComputeTest.compile(GeometryAssembly.source(layout, new GeometryAssembly.Bindings(0, 1, 2, 4)));
            String vertex = GeometryRaster.source(layout, new GeometryRaster.Bindings(0, 1, 4));
            long compiler = shaderc_compiler_initialize(), options = shaderc_compile_options_initialize();
            shaderc_compile_options_set_target_env(options, shaderc_target_env_opengl, shaderc_env_version_opengl_4_5);
            shaderc_compile_options_set_auto_map_locations(options, true);
            long result = shaderc_compile_into_spv(
                    compiler, vertex, shaderc_glsl_vertex_shader, "geometry.vert", "main", options);
            try {
                assertEquals(
                        shaderc_compilation_status_success,
                        shaderc_result_get_compilation_status(result),
                        shaderc_result_get_error_message(result));
            } finally {
                shaderc_result_release(result);
                shaderc_compile_options_release(options);
                shaderc_compiler_release(compiler);
            }
        }
    }

    @Test
    void reservesWorstCaseCornersAndRejectsOverflowBeforeAllocation() {
        var triangle = new GeometryLayout(
                GeometryLayout.Input.TRIANGLES, GeometryLayout.Output.TRIANGLE_STRIP, 6, 6, List.of(), List.of());
        assertEquals(12, GeometryAssembly.cornersPerJob(triangle));
        assertEquals(2304, GeometryAssembly.cornerBytes(triangle, 12, 4096));
        assertThrows(IllegalArgumentException.class, () -> GeometryAssembly.cornerBytes(triangle, 12, 2000));
        var lines = new GeometryLayout(
                GeometryLayout.Input.LINES, GeometryLayout.Output.LINE_STRIP, 6, 1, List.of(), List.of());
        assertEquals(10, GeometryAssembly.cornersPerJob(lines));
        var points = new GeometryLayout(
                GeometryLayout.Input.POINTS, GeometryLayout.Output.POINTS, 6, 1, List.of(), List.of());
        assertEquals(6, GeometryAssembly.cornersPerJob(points));
    }
}
