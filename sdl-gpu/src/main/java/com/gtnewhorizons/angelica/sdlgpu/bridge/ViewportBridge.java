package com.gtnewhorizons.angelica.sdlgpu.bridge;

import static org.lwjgl.opengl.GL11.GL_DEPTH_RANGE;
import static org.lwjgl.opengl.GL11.GL_SCISSOR_BOX;
import static org.lwjgl.opengl.GL11.GL_VIEWPORT;

import com.gtnewhorizons.angelica.glsm.GLSMRasterizer;
import com.gtnewhorizons.angelica.sdlgpu.Subsystems;
import com.gtnewhorizons.angelica.sdlgpu.Upstream;
import com.gtnewhorizons.angelica.sdlgpu.geometry.GeometryViewports;

/** Logical indexed viewport state; the geometry executor replays each selected viewport over SDL GPU. */
public final class ViewportBridge {
    private ViewportBridge() {}

    public static void glViewportIndexedf(int index, float x, float y, float width, float height) {
        Subsystems.current()
                .geometryViewports()
                .viewport(index, Math.round(x), Math.round(y), Math.round(width), Math.round(height));
        if (index != 0) return;
        Subsystems.current()
                .geometryViewports()
                .indexed(() ->
                        GLSMRasterizer.glViewport(Math.round(x), Math.round(y), Math.round(width), Math.round(height)));
    }

    public static void glScissorIndexed(int index, int x, int y, int width, int height) {
        Subsystems.current().geometryViewports().scissor(index, x, y, width, height);
        if (index != 0) return;
        Subsystems.current().geometryViewports().indexed(() -> GLSMRasterizer.glScissor(x, y, width, height));
    }

    public static void glDepthRangeIndexed(int index, double near, double far) {
        Subsystems.current().geometryViewports().depth(index, near, far);
        if (index != 0) return;
        Subsystems.current().geometryViewports().indexed(() -> DepthRangeBridge.glDepthRange(near, far));
    }

    public static void glGetFloati_v(int parameter, int index, float[] values) {
        GeometryViewports.validate(index);
        double[] state = indexed(parameter, index);
        for (int i = 0; i < Math.min(values.length, state.length); i++) values[i] = (float) state[i];
    }

    public static void glGetDoublei_v(int parameter, int index, double[] values) {
        GeometryViewports.validate(index);
        double[] state = indexed(parameter, index);
        System.arraycopy(state, 0, values, 0, Math.min(values.length, state.length));
    }

    /** GLSM's indexed integer query stores one value; viewport and scissor boxes need all four. */
    public static boolean answersIntegers(int parameter) {
        return parameter == GL_VIEWPORT || parameter == GL_SCISSOR_BOX;
    }

    public static void glGetIntegeri_v(int parameter, int index, int[] values) {
        GeometryViewports.validate(index);
        double[] state = indexed(parameter, index);
        for (int i = 0; i < Math.min(values.length, state.length); i++) values[i] = (int) state[i];
    }

    // Upstream's scalar queries do not cover viewport state, so the SDL context answers directly.
    private static double[] indexed(int parameter, int index) {
        if (index != 0) {
            var v = Subsystems.current().geometryViewports().get(index);
            return switch (parameter) {
                case GL_VIEWPORT -> new double[] {v.x(), v.y(), v.width(), v.height()};
                case GL_SCISSOR_BOX -> new double[] {v.scissorX(), v.scissorY(), v.scissorWidth(), v.scissorHeight()};
                case GL_DEPTH_RANGE -> new double[] {v.near(), v.far()};
                default -> throw new UnsupportedOperationException("Indexed state query not implemented: " + parameter);
            };
        }
        var state = Upstream.context();
        return switch (parameter) {
            case GL_VIEWPORT -> new double[] {state.viewportX, state.viewportY, state.viewportW, state.viewportH};
            case GL_SCISSOR_BOX -> new double[] {state.scissorX, state.scissorY, state.scissorW, state.scissorH};
            case GL_DEPTH_RANGE -> new double[] {state.viewportDepthNear, state.viewportDepthFar};
            default -> throw new UnsupportedOperationException("Indexed state query not implemented: " + parameter);
        };
    }
}
