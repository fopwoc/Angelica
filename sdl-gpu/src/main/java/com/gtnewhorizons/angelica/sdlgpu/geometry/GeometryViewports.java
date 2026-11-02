package com.gtnewhorizons.angelica.sdlgpu.geometry;

import com.gtnewhorizons.angelica.sdlgpu.Upstream;
import java.util.Arrays;

/** Logical indexed viewports replayed one at a time over SDL GPU's single raster viewport. */
public final class GeometryViewports {
    public static final int LIMIT = 16;

    public record View(
            int x,
            int y,
            int width,
            int height,
            int scissorX,
            int scissorY,
            int scissorWidth,
            int scissorHeight,
            double near,
            double far,
            boolean scissorEnabled) {}

    private boolean indexed;

    public boolean indexed() {
        return indexed;
    }

    public void indexed(Runnable action) {
        boolean previous = indexed;
        indexed = true;
        try {
            action.run();
        } finally {
            indexed = previous;
        }
    }

    public void scissorEnabled(int index, boolean enabled) {
        validate(index);
        var v = values[index];
        values[index] = new View(
                v.x,
                v.y,
                v.width,
                v.height,
                v.scissorX,
                v.scissorY,
                v.scissorWidth,
                v.scissorHeight,
                v.near,
                v.far,
                enabled);
    }

    private final View[] values = new View[LIMIT];

    public GeometryViewports() {
        Arrays.fill(values, new View(0, 0, 0, 0, 0, 0, 0, 0, 0, 1, false));
    }

    public static void validate(int index) {
        if (index < 0 || index >= LIMIT)
            throw new IllegalArgumentException("Viewport index exceeds GL_MAX_VIEWPORTS: " + index);
    }

    public View get(int index) {
        validate(index);
        if (index != 0) return values[index];
        var state = Upstream.context();
        return new View(
                (int) state.viewportX,
                (int) state.viewportY,
                (int) state.viewportW,
                (int) state.viewportH,
                state.scissorX,
                state.scissorY,
                state.scissorW,
                state.scissorH,
                state.viewportDepthNear,
                state.viewportDepthFar,
                state.scissorEnabled);
    }

    public void viewport(int index, int x, int y, int width, int height) {
        validate(index);
        if (width < 0 || height < 0) throw new IllegalArgumentException("Negative viewport dimensions");
        var v = values[index];
        values[index] = new View(
                x,
                y,
                width,
                height,
                v.scissorX,
                v.scissorY,
                v.scissorWidth,
                v.scissorHeight,
                v.near,
                v.far,
                v.scissorEnabled);
    }

    public void scissor(int index, int x, int y, int width, int height) {
        validate(index);
        if (width < 0 || height < 0) throw new IllegalArgumentException("Negative scissor dimensions");
        var v = values[index];
        values[index] = new View(v.x, v.y, v.width, v.height, x, y, width, height, v.near, v.far, v.scissorEnabled);
    }

    public void depth(int index, double near, double far) {
        validate(index);
        var v = values[index];
        values[index] = new View(
                v.x,
                v.y,
                v.width,
                v.height,
                v.scissorX,
                v.scissorY,
                v.scissorWidth,
                v.scissorHeight,
                Math.clamp(near, 0, 1),
                Math.clamp(far, 0, 1),
                v.scissorEnabled);
    }
}
