package com.gtnewhorizons.angelica.sdlgpu.geometry;

import static org.lwjgl.opengl.GL40.GL_DRAW_INDIRECT_BUFFER;

import com.gtnewhorizons.angelica.sdlgpu.Upstream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import org.lwjgl.system.MemoryUtil;

/** Bounded indirect-command readback for geometry replay; vertex/index payload remains on the GPU. */
public final class GeometryIndirect {
    private static final int MAX_COMMANDS = 65536;

    private GeometryIndirect() {}

    public static void arrays(int mode, long offset, int count, int stride) {
        int bytes = bytes(count, stride);
        if (bytes == 0) return;
        var data = MemoryUtil.memAlloc(bytes);
        try {
            Upstream.ownedBackend().getBufferSubData(GL_DRAW_INDIRECT_BUFFER, offset, data);
            data.clear();
            for (var draw : decode(data, count, stride))
                Upstream.ownedBackend().subsystems().geometryPrograms().draw(mode, draw);
        } finally {
            MemoryUtil.memFree(data);
        }
    }

    static int bytes(int count, int stride) {
        if (count > MAX_COMMANDS)
            throw new UnsupportedOperationException("Geometry indirect command count exceeds " + MAX_COMMANDS);
        if (count < 0 || stride < 0 || (stride & 3) != 0)
            throw new IllegalArgumentException("Invalid geometry indirect range");
        long bytes =
                count == 0 ? 0 : Math.addExact(Math.multiplyExact((long) count - 1, stride == 0 ? 16 : stride), 16);
        if (bytes > GeometryBuffers.MAX_BYTES)
            throw new UnsupportedOperationException("Geometry indirect commands exceed the readback limit");
        return Math.toIntExact(bytes);
    }

    static List<GeometryDraw> decode(ByteBuffer data, int count, int stride) {
        int bytes = bytes(count, stride);
        if (data.remaining() < bytes) throw new IllegalArgumentException("Incomplete geometry indirect commands");
        var input = data.duplicate().order(ByteOrder.nativeOrder());
        int step = stride == 0 ? 16 : stride;
        var draws = new ArrayList<GeometryDraw>(count);
        for (int id = 0; id < count; id++) {
            int at = Math.addExact(input.position(), Math.multiplyExact(id, step));
            draws.add(new GeometryDraw(
                    input.getInt(at + 8), input.getInt(at), 0, 0, 0, input.getInt(at + 4), input.getInt(at + 12), id));
        }
        return List.copyOf(draws);
    }
}
