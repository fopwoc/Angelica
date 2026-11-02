package com.gtnewhorizons.angelica.sdlgpu.shader;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL32.GL_GEOMETRY_SHADER;

import java.util.List;
import org.junit.jupiter.api.Test;

class ShaderFrontendTest {
    @Test
    void retainsGeometryForTheComputeExecutionPath() {
        try (var frontend = new ShaderFrontend()) {
            var geometry = frontend.compile(GL_GEOMETRY_SHADER, """
                    #version 460 core
                    layout(triangles) in;
                    layout(triangle_strip, max_vertices=3) out;
                    void main() {
                        for (int i=0;i<3;i++) {
                            gl_Position=gl_in[i].gl_Position;
                            EmitVertex();
                        }
                        EndPrimitive();
                    }
                    """);
            assertTrue(geometry.valid(), geometry.log());
            var vertex = frontend.compile(GL_VERTEX_SHADER, """
                    #version 460 core
                    void main() { gl_Position=vec4(0,0,0,1); }
                    """);
            var fragment = frontend.compile(GL_FRAGMENT_SHADER, """
                    #version 460 core
                    out vec4 color;
                    void main() { color=vec4(1); }
                    """);
            assertTrue(vertex.valid(), vertex.log());
            assertTrue(fragment.valid(), fragment.log());
            var linked = frontend.link(List.of(vertex, geometry, fragment));
            assertTrue(linked.valid(), linked.log());
            assertTrue(linked.stages().containsKey(GL_GEOMETRY_SHADER));
            assertTrue(frontend.link(List.of(vertex, fragment)).valid());
        }
    }
}
