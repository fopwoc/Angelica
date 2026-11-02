package com.gtnewhorizons.angelica.sdlgpu.geometry;

import static org.lwjgl.opengl.GL31.GL_UNIFORM_BUFFER;
import static org.lwjgl.opengl.GL43.*;

import com.gtnewhorizons.angelica.sdlgpu.Upstream;
import java.util.Arrays;

/** Restores actual backend state without changing the caller's GLSM cache during internal passes. */
final class GeometryDrawState implements AutoCloseable {
    private final int program, vao, ssbo, ubo, control, offset, size;
    private final int[] bindings;
    private final GeometryViewports.View viewport;
    private final boolean deleted;

    GeometryDrawState() {
        var state = Upstream.context();
        program = state.boundProgram;
        vao = state.boundVAO;
        ssbo = state.getBoundBuffer(GL_SHADER_STORAGE_BUFFER);
        ubo = state.getBoundBuffer(GL_UNIFORM_BUFFER);
        control = state.boundUboByIndex[4];
        offset = state.uboRangeOffset[4];
        size = state.uboRangeSize[4];
        bindings = Arrays.copyOf(state.boundSsboByIndex, 9);
        viewport = Upstream.ownedBackend().subsystems().geometryViewports().get(0);
        deleted = state.boundProgramObj.deletePending;
        state.boundProgramObj.deletePending = false;
    }

    @Override
    public void close() {
        var backend = Upstream.ownedBackend();
        for (int i = 0; i < bindings.length; i++) backend.bindBufferBase(GL_SHADER_STORAGE_BUFFER, i, bindings[i]);
        if (size > 0) backend.bindBufferRange(GL_UNIFORM_BUFFER, 4, control, offset, size);
        else backend.bindBufferBase(GL_UNIFORM_BUFFER, 4, control);
        backend.bindBuffer(GL_SHADER_STORAGE_BUFFER, ssbo);
        backend.bindBuffer(GL_UNIFORM_BUFFER, ubo);
        backend.bindVertexArray(vao);
        backend.applyGeometryViewport(viewport);
        backend.useProgram(program);
        var object = Upstream.shaders().getProgram(program);
        if (object != null) object.deletePending = deleted;
    }
}
