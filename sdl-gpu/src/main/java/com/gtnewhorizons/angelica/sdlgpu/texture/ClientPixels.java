package com.gtnewhorizons.angelica.sdlgpu.texture;

import static org.lwjgl.opengl.GL11.GL_RGB;
import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL12.GL_BGR;

import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState.PixelStoreState;
import com.gtnewhorizons.angelica.sdlgpu.resource.PixelOps;
import java.nio.ByteBuffer;
import org.lwjgl.system.MemoryUtil;

/** Borrows a client pointer for one synchronous upload; never owns or retains the caller's native memory. */
public final class ClientPixels implements AutoCloseable {
    private final ByteBuffer data;
    private final boolean owned;
    private final int format;

    private ClientPixels(ByteBuffer data, boolean owned, int format) {
        this.data = data;
        this.owned = owned;
        this.format = format;
    }

    public static ClientPixels transparent(int width, int height, PixelStoreState store) {
        return new ClientPixels(MemoryUtil.memCalloc(requiredBytes(width, height, 4, store)), true, GL_RGBA);
    }

    public static ClientPixels borrow(
            long address, int width, int height, int format, int type, PixelStoreState store) {
        int pixelBytes = PixelOps.glPixelSize(format, type);
        int span = requiredBytes(width, height, pixelBytes, store);
        var source = MemoryUtil.memByteBuffer(address, span);
        int stride = Math.toIntExact(rowStride(width, pixelBytes, store));
        if ((format == GL_RGB || format == GL_BGR) && type == GL_UNSIGNED_BYTE) {
            var expanded = MemoryUtil.memAlloc(requiredBytes(width, height, 4, store));
            int expandedStride = Math.toIntExact(rowStride(width, 4, store));
            int srcBase = store.unpackSkipRows * stride + store.unpackSkipPixels * 3;
            int dstBase = store.unpackSkipRows * expandedStride + store.unpackSkipPixels * 4;
            for (int y = 0; y < height; y++)
                for (int x = 0; x < width; x++) {
                    int src = srcBase + y * stride + x * 3;
                    int dst = dstBase + y * expandedStride + x * 4;
                    expanded.put(dst, source.get(src + (format == GL_BGR ? 2 : 0)))
                            .put(dst + 1, source.get(src + 1))
                            .put(dst + 2, source.get(src + (format == GL_BGR ? 0 : 2)))
                            .put(dst + 3, (byte) 255);
                }
            return new ClientPixels(expanded, true, GL_RGBA);
        }
        return new ClientPixels(source, false, format);
    }

    /** Native GL sees an address and transfer span, not a direct buffer's Java limit. */
    public static ByteBuffer nativeView(
            ByteBuffer source, int width, int height, int format, int type, PixelStoreState store) {
        if (source == null || !source.isDirect()) return source;
        int span = requiredBytes(width, height, PixelOps.glPixelSize(format, type), store);
        var view = source.duplicate().order(source.order());
        view.limit(source.capacity());
        PixelUnpack.requireSource(view, width, height, format, type, store);
        int end = Math.addExact(source.position(), span);
        if (source.limit() == end) return source;
        return view.limit(end);
    }

    static int requiredBytes(int width, int height, int pixelBytes, PixelStoreState store) {
        if (width < 0
                || height < 0
                || pixelBytes <= 0
                || store.unpackRowLength < 0
                || store.unpackSkipPixels < 0
                || store.unpackSkipRows < 0)
            throw new IllegalArgumentException("Invalid client texture dimensions or unpack state");
        if (width == 0 || height == 0) return 0;
        long stride = rowStride(width, pixelBytes, store);
        long skipped = Math.addExact(
                Math.multiplyExact((long) store.unpackSkipRows, stride),
                Math.multiplyExact((long) store.unpackSkipPixels, pixelBytes));
        return Math.toIntExact(Math.addExact(
                skipped,
                Math.addExact(
                        Math.multiplyExact((long) height - 1, stride), Math.multiplyExact((long) width, pixelBytes))));
    }

    private static long rowStride(int width, int pixelBytes, PixelStoreState store) {
        int alignment = store.unpackAlignment;
        if (alignment != 1 && alignment != 2 && alignment != 4 && alignment != 8)
            throw new IllegalArgumentException("Invalid pixel unpack alignment");
        long bytes = Math.multiplyExact((long) (store.unpackRowLength > 0 ? store.unpackRowLength : width), pixelBytes);
        return Math.multiplyExact((bytes + alignment - 1) / alignment, alignment);
    }

    public int format() {
        return format;
    }

    public ByteBuffer data() {
        return data;
    }

    @Override
    public void close() {
        if (owned) MemoryUtil.memFree(data);
    }
}
