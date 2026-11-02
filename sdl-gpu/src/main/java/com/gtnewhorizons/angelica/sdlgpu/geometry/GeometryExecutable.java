package com.gtnewhorizons.angelica.sdlgpu.geometry;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL31.GL_UNIFORM_BUFFER;
import static org.lwjgl.opengl.GL43.*;

import com.gtnewhorizons.angelica.sdlgpu.Upstream;
import com.gtnewhorizons.angelica.sdlgpu.shader.ShaderManager.ProgramObject;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.List;
import org.antlr.v4.runtime.TokenStreamRewriter;
import org.lwjgl.system.MemoryUtil;

/** Compute execution and raster replay for one linked geometry program. */
final class GeometryExecutable implements AutoCloseable {
    private final GeometryLayout layout;
    private final GeometryVertex.Plan vertex;
    private final String fragment;
    private int compute, raster, assembly, metadata, vertices, counts, corners, control, zero, vao;

    GeometryExecutable(GeometryLayout layout, GeometryVertex.Plan vertex, String fragment) {
        this.layout = layout;
        this.vertex = vertex;
        this.fragment = fragment;
    }

    List<GeometryVertex.Attribute> attributes() {
        return vertex.attributes();
    }

    GeometryLayout layout() {
        return layout;
    }

    ProgramObject computeObject() {
        return Upstream.shaders().getProgram(compute);
    }

    ProgramObject rasterObject() {
        return Upstream.shaders().getProgram(raster);
    }

    void create() {
        compute = program(Map.of(GL_COMPUTE_SHADER, vertex.source()));
        String fs = fragment;
        // The fragment primitive ID comes from the geometry stage, transported as a flat integer.
        var parsed = GeometrySyntax.parse(fs);
        var rewrite = new TokenStreamRewriter(parsed.stream());
        boolean primitive = false;
        for (var token : parsed.tokens())
            if (token.getText().equals("gl_PrimitiveID")) {
                rewrite.replace(token.getTokenIndex(), "_geometry_primitive_id");
                primitive = true;
            }
        fs = rewrite.getText();
        if (primitive) fs = "#version 460 core\nflat in int _geometry_primitive_id;\n" + fs;
        else fs = "#version 460 core\n" + fs;
        raster = program(Map.of(
                GL_VERTEX_SHADER,
                GeometryRaster.source(layout, new GeometryRaster.Bindings(1, 3, 4)),
                GL_FRAGMENT_SHADER,
                fs));
        assembly = program(
                Map.of(GL_COMPUTE_SHADER, GeometryAssembly.source(layout, new GeometryAssembly.Bindings(1, 2, 3, 4))));
        var backend = Upstream.ownedBackend();
        metadata = backend.genBuffers();
        vertices = backend.genBuffers();
        counts = backend.genBuffers();
        corners = backend.genBuffers();
        control = backend.genBuffers();
        zero = backend.genBuffers();
        vao = backend.genVertexArrays();
    }

    private static int program(Map<Integer, String> stages) {
        var manager = Upstream.shaders();
        int program = manager.createProgram();
        var shaders = new ArrayList<Integer>();
        try {
            for (var entry : stages.entrySet()) {
                int shader = manager.createShader(entry.getKey());
                shaders.add(shader);
                manager.shaderSource(shader, entry.getValue());
                manager.compileShader(shader);
                if (!manager.isCompiled(shader))
                    throw new UnsupportedOperationException(
                            "Internal geometry stage: " + manager.getShaderInfoLog(shader));
                manager.attachShader(program, shader);
            }
            manager.linkProgram(program);
            if (!manager.getProgram(program).linked)
                throw new UnsupportedOperationException(
                        "Internal geometry program: " + manager.getProgram(program).infoLog);
            return program;
        } catch (RuntimeException | Error failure) {
            manager.releaseProgramFinal(program);
            throw failure;
        } finally {
            for (int shader : shaders) manager.deleteShader(shader);
        }
    }

    void draw(GeometryDraw draw) {
        int primitives = draw.primitives();
        if (primitives == 0) return;
        var size = layout.dispatch(primitives, GeometryBuffers.MAX_BYTES);
        int cornerCount = Math.multiplyExact(size.jobs(), GeometryAssembly.cornersPerJob(layout));
        long cornerBytes = GeometryAssembly.cornerBytes(layout, size.jobs(), GeometryBuffers.MAX_BYTES);
        var backend = Upstream.ownedBackend();
        var logical = Upstream.context().boundProgramObj;
        var viewportState = backend.subsystems().geometryViewports();
        GeometryViewports.View[] views = new GeometryViewports.View[GeometryViewports.LIMIT];
        for (int i = 0; i < views.length; i++) views[i] = viewportState.get(i);
        var parameters = MemoryUtil.memCalloc(16);
        var status = MemoryUtil.memAlloc((int) size.countsBytes());
        try (var inputs = GeometryBuffers.inputs(vertex, draw);
                var saved = new GeometryDrawState()) {
            allocate(metadata, 3, inputs.metadata());
            allocate(vertices, 1, size.outputBytes());
            allocate(counts, 2, size.countsBytes());
            allocate(corners, 3, cornerBytes);
            var zeros = MemoryUtil.memCalloc(4);
            try {
                allocate(zero, 0, zeros);
            } finally {
                MemoryUtil.memFree(zeros);
            }
            // Metadata was displaced while allocating the corner buffer.
            backend.bindBufferBase(GL_SHADER_STORAGE_BUFFER, 3, metadata);
            for (int i = 0; i < GeometryVertex.MAX_STREAMS; i++)
                backend.bindBufferBase(
                        GL_SHADER_STORAGE_BUFFER,
                        GeometryVertex.FIRST_STREAM_BINDING + i,
                        i < inputs.streams().size() ? inputs.streams().get(i) : zero);
            backend.bindBufferBase(
                    GL_SHADER_STORAGE_BUFFER,
                    GeometryVertex.INDEX_BINDING,
                    inputs.indexBuffer() == 0 ? zero : inputs.indexBuffer());
            backend.useProgram(compute);
            copyUniforms(logical, computeObject());
            parameters.putInt(0, primitives).putInt(4, 0).putInt(8, 0).putInt(12, 0);
            parameters(control, parameters);
            backend.dispatchCompute(size.groups(), 1, 1);
            backend.memoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT | GL_BUFFER_UPDATE_BARRIER_BIT);
            backend.bindBuffer(GL_SHADER_STORAGE_BUFFER, counts);
            backend.getBufferSubData(GL_SHADER_STORAGE_BUFFER, 0, status);

            // Fail before rasterization on overflow/bounds faults. This first path synchronizes for validation.
            for (int i = 0; i < size.jobs(); i++) {
                int flag = status.getInt(i * 16 + 8);
                if (flag != 0)
                    throw new IllegalStateException("Geometry GPU job " + i + " failed: "
                            + (flag == 1 ? "max_vertices exceeded" : "vertex/index fetch out of bounds"));
            }
            backend.useProgram(assembly);
            backend.bindBufferBase(GL_SHADER_STORAGE_BUFFER, 3, corners);
            parameters.putInt(0, size.jobs());
            parameters(control, parameters);
            backend.dispatchCompute(size.groups(), 1, 1);
            backend.memoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
            backend.useProgram(raster);
            copyUniforms(logical, rasterObject());
            backend.bindVertexArray(vao);
            for (int index = 0; index < views.length; index++) {
                var view = views[index];
                if (view.width() == 0 || view.height() == 0) continue;
                backend.applyGeometryViewport(view);
                parameters.putInt(0, index).putInt(4, 0).putInt(8, 0);
                allocate(control, 4, parameters);
                backend.drawArrays(GL_TRIANGLES, 0, cornerCount);
            }
        } finally {
            MemoryUtil.memFree(parameters);
            MemoryUtil.memFree(status);
        }
    }

    private static void copyUniforms(ProgramObject logical, ProgramObject target) {
        var state = Upstream.context();
        var source = state.uniformStaging(logical);
        for (var entry : target.nameToLocation.object2IntEntrySet()) {
            String name = entry.getKey();
            if (GeometryUniforms.internal(name)) continue;
            int slot = logical.nameToLocation.getInt(GeometryUniforms.publicName(name));
            if (slot < 0) continue;
            if (logical.locationKind[slot] == ProgramObject.LOCATION_KIND_SAMPLER) {
                int unit = logical.locationSamplerUnit[slot];
                target.locationSamplerUnit[entry.getIntValue()] = unit;
                target.samplerTextureUnits.put(name, unit);
                target.samplerUnitsDirty = true;
                state.samplerBindGen++;
            } else {
                float[] values = source.uniformDataBySlot[slot];
                if (values != null) Upstream.pipeline().putUniform(state, entry.getIntValue(), values);
            }
        }
    }

    private static void parameters(int buffer, ByteBuffer data) {
        var backend = Upstream.ownedBackend();
        backend.bindBuffer(GL_UNIFORM_BUFFER, buffer);
        backend.bufferData(GL_UNIFORM_BUFFER, data, GL_STREAM_DRAW);
        backend.bindBufferBase(GL_UNIFORM_BUFFER, 4, buffer);
    }

    private static void allocate(int buffer, int binding, long bytes) {
        var backend = Upstream.ownedBackend();
        backend.bindBuffer(GL_SHADER_STORAGE_BUFFER, buffer);
        backend.bufferData(GL_SHADER_STORAGE_BUFFER, Math.max(4, bytes), GL_STREAM_DRAW);
        backend.bindBufferBase(GL_SHADER_STORAGE_BUFFER, binding, buffer);
    }

    private static void allocate(int buffer, int binding, ByteBuffer data) {
        var backend = Upstream.ownedBackend();
        backend.bindBuffer(GL_SHADER_STORAGE_BUFFER, buffer);
        backend.bufferData(GL_SHADER_STORAGE_BUFFER, data, GL_STREAM_DRAW);
        backend.bindBufferBase(GL_SHADER_STORAGE_BUFFER, binding, buffer);
    }

    @Override
    public void close() {
        var backend = Upstream.ownedBackend();
        for (int program : new int[] {compute, raster, assembly})
            if (program != 0) Upstream.shaders().releaseProgramFinal(program);
        for (int buffer : new int[] {metadata, vertices, counts, corners, control, zero})
            if (buffer != 0) backend.deleteBuffers(buffer);
        if (vao != 0) backend.deleteVertexArrays(vao);
    }
}
