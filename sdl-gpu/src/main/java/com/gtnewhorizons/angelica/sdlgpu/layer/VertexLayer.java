package com.gtnewhorizons.angelica.sdlgpu.layer;

import static org.lwjgl.opengl.GL12.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL43.GL_VERTEX_BINDING_DIVISOR;

import com.gtnewhorizons.angelica.sdlgpu.Upstream;
import com.gtnewhorizons.angelica.sdlgpu.geometry.GeometryViewports;
import com.gtnewhorizons.angelica.sdlgpu.vertex.BaseInstanceDraws;
import com.gtnewhorizons.angelica.sdlgpu.vertex.VertexInputs;
import java.nio.IntBuffer;

/** Vertex attribute layout, full-width divisors, and base-instance offsets for instanced draws. */
public abstract class VertexLayer extends QueryLayer {
    public void applyGeometryViewport(GeometryViewports.View view) {
        super.viewport(view.x(), view.y(), view.width(), view.height());
        super.scissor(view.scissorX(), view.scissorY(), view.scissorWidth(), view.scissorHeight());
        super.depthRange(view.near(), view.far());
        if (view.scissorEnabled()) super.enable(GL_SCISSOR_TEST);
        else super.disable(GL_SCISSOR_TEST);
    }

    @Override
    public void viewport(int x, int y, int width, int height) {
        var views = subsystems().geometryViewports();
        if (!views.indexed()) for (int i = 0; i < GeometryViewports.LIMIT; i++) views.viewport(i, x, y, width, height);
        super.viewport(x, y, width, height);
    }

    @Override
    public void scissor(int x, int y, int width, int height) {
        var views = subsystems().geometryViewports();
        if (!views.indexed()) for (int i = 0; i < GeometryViewports.LIMIT; i++) views.scissor(i, x, y, width, height);
        super.scissor(x, y, width, height);
    }

    @Override
    public void depthRange(double near, double far) {
        var views = subsystems().geometryViewports();
        if (!views.indexed()) for (int i = 0; i < GeometryViewports.LIMIT; i++) views.depth(i, near, far);
        super.depthRange(near, far);
    }

    @Override
    public void enable(int capability) {
        if (capability == GL_SCISSOR_TEST)
            for (int i = 0; i < GeometryViewports.LIMIT; i++)
                subsystems().geometryViewports().scissorEnabled(i, true);
        super.enable(capability);
    }

    @Override
    public void disable(int capability) {
        if (capability == GL_SCISSOR_TEST)
            for (int i = 0; i < GeometryViewports.LIMIT; i++)
                subsystems().geometryViewports().scissorEnabled(i, false);
        super.disable(capability);
    }

    @Override
    public void enablei(int capability, int index) {
        if (capability != GL_SCISSOR_TEST) {
            super.enablei(capability, index);
            return;
        }
        subsystems().geometryViewports().scissorEnabled(index, true);
        if (index == 0) super.enable(capability);
    }

    @Override
    public void disablei(int capability, int index) {
        if (capability != GL_SCISSOR_TEST) {
            super.disablei(capability, index);
            return;
        }
        subsystems().geometryViewports().scissorEnabled(index, false);
        if (index == 0) super.disable(capability);
    }

    @Override
    public void drawArrays(int mode, int first, int count) {
        if (!subsystems().geometryPrograms().draw(mode, first, count, 0, 0, 0, 1)) super.drawArrays(mode, first, count);
    }

    @Override
    public void drawElements(int mode, int count, int type, long indices) {
        if (!subsystems().geometryPrograms().draw(mode, 0, count, type, indices, 0, 1))
            super.drawElements(mode, count, type, indices);
    }

    @Override
    public void drawElementsBaseVertex(int mode, int count, int type, long indices, int base) {
        if (!subsystems().geometryPrograms().draw(mode, 0, count, type, indices, base, 1))
            super.drawElementsBaseVertex(mode, count, type, indices, base);
    }

    @Override
    public void multiDrawArrays(int mode, IntBuffer firsts, IntBuffer counts) {
        subsystems().geometryPrograms().requireIntercepted();
        super.multiDrawArrays(mode, firsts, counts);
    }

    @Override
    public boolean preservesVertexLayout(int attribute) {
        return VertexInputs.preserveLayout(attribute);
    }

    @Override
    public void drawArraysInstanced(int mode, int first, int count, int instances) {
        if (count <= 0 || instances <= 0) return;
        if (subsystems().geometryPrograms().draw(mode, first, count, 0, 0, 0, instances)) return;
        try (var _ = BaseInstanceDraws.offsets(Upstream.context())) {
            super.drawArraysInstanced(mode, first, count, instances);
        }
    }

    @Override
    public int getIntegerIndexed(int parameter, int index) {
        return parameter == GL_VERTEX_BINDING_DIVISOR
                ? VertexInputs.divisor(index)
                : super.getIntegerIndexed(parameter, index);
    }

    @Override
    public void vertexAttribFormat(int attribute, int size, int type, boolean normalized, int offset) {
        VertexInputs.modernAttribute(attribute);
        super.vertexAttribFormat(attribute, size, type, normalized, offset);
    }

    @Override
    public void vertexAttribIFormat(int attribute, int size, int type, int offset) {
        VertexInputs.modernAttribute(attribute);
        super.vertexAttribIFormat(attribute, size, type, offset);
    }

    @Override
    public void vertexAttribBinding(int attribute, int binding) {
        VertexInputs.validateBinding(binding);
        VertexInputs.modernAttribute(attribute);
        super.vertexAttribBinding(attribute, binding);
    }

    @Override
    public void bindVertexBuffer(int binding, int buffer, long offset, int stride) {
        VertexInputs.modernBinding(binding);
        super.bindVertexBuffer(binding, buffer, offset, stride);
    }

    @Override
    public void vertexAttribPointer(int attribute, int size, int type, boolean normalized, int stride, long offset) {
        VertexInputs.legacyPointer(attribute);
        super.vertexAttribPointer(attribute, size, type, normalized, stride, offset);
    }

    @Override
    public void vertexAttribIPointer(int attribute, int size, int type, int stride, long offset) {
        VertexInputs.legacyPointer(attribute);
        super.vertexAttribIPointer(attribute, size, type, stride, offset);
    }

    @Override
    public void vertexAttribDivisor(int attribute, int divisor) {
        VertexInputs.attributeDivisor(attribute, divisor);
    }

    @Override
    public void drawElementsInstanced(int mode, int count, int type, long indices, int instances) {
        if (count <= 0 || instances <= 0) return;
        if (subsystems().geometryPrograms().draw(mode, 0, count, type, indices, 0, instances)) return;
        try (var _ = BaseInstanceDraws.offsets(Upstream.context())) {
            super.drawElementsInstanced(mode, count, type, indices, instances);
        }
    }
}
