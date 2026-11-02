package com.gtnewhorizons.angelica.sdlgpu.geometry;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL43.*;

import com.gtnewhorizons.angelica.sdlgpu.Upstream;
import java.nio.ByteBuffer;
import java.util.*;
import org.lwjgl.system.MemoryUtil;

/** Per-draw vertex fetch descriptors. Vertex/index payload stays in the application's GPU buffers. */
final class GeometryBuffers {
    static final long MAX_BYTES = 64L * 1024 * 1024;

    private GeometryBuffers() {}

    record Inputs(ByteBuffer metadata, List<Integer> streams, int indexBuffer) implements AutoCloseable {
        @Override
        public void close() {
            MemoryUtil.memFree(metadata);
        }
    }

    static Inputs inputs(GeometryVertex.Plan plan, int first, int count, int indexType, long indices, int baseVertex) {
        var state = Upstream.context();
        var vao = state.currentVao;
        var resources = Upstream.resources();
        var data = MemoryUtil.memCalloc(GeometryVertex.META_WORDS * 4);
        try {
            var streams = new ArrayList<Integer>();
            int indexBytes = switch (indexType) {
                case 0 -> 0;
                case GL_UNSIGNED_BYTE -> 1;
                case GL_UNSIGNED_SHORT -> 2;
                case GL_UNSIGNED_INT -> 4;
                default -> throw new UnsupportedOperationException("Geometry index type: " + indexType);
            };
            if (first < 0 || count < 0 || indices < 0 || indices > Integer.MAX_VALUE)
                throw new IllegalArgumentException("Geometry draw range");
            data.putInt(0, first).putInt(4, count).putInt(8, baseVertex).putInt(12, indexBytes);
            int indexBuffer = indexBytes == 0 ? 0 : state.currentVao.elementBuffer;
            if (indexBytes != 0 && indexBuffer == 0)
                throw new UnsupportedOperationException("Geometry client-memory index arrays are not implemented");
            long indexSize = indexBuffer == 0 ? 0 : resources.getBufferSize(indexBuffer);
            if (indexSize > Integer.MAX_VALUE
                    || indexSize % 4 != 0
                    || indexBytes != 0 && indices + (long) count * indexBytes > indexSize)
                throw new IllegalArgumentException("Geometry index range exceeds storage");
            data.putInt(16, (int) indices).putInt(20, (int) indexSize);
            for (var input : plan.attributes())
                for (int column = 0; column < input.columns(); column++) {
                    int location = input.location() + column, at = (3 + location * 2) * 16;
                    for (int c = 0; c < 4; c++)
                        data.putInt(at + 16 + c * 4, Float.floatToRawIntBits(state.attribDefaults[location * 4 + c]));
                    if (!vao.attribEnabled[location]) continue;
                    int binding = vao.attribBinding[location], buffer = vao.bindingBuffer[binding];
                    if (buffer == 0)
                        throw new UnsupportedOperationException("Geometry client vertex arrays are not implemented");
                    int stream = streams.indexOf(buffer);
                    if (stream < 0) {
                        stream = streams.size();
                        streams.add(buffer);
                    }
                    if (stream >= GeometryVertex.MAX_STREAMS)
                        throw new UnsupportedOperationException("Geometry vertex fetch exceeds four GPU streams");
                    long size = resources.getBufferSize(buffer),
                            offset =
                                    vao.bindingOffset[binding]
                                            + Integer.toUnsignedLong(vao.attribRelativeOffset[location]);
                    if (size > Integer.MAX_VALUE || size % 4 != 0 || offset > size || offset < 0)
                        throw new IllegalArgumentException("Geometry vertex storage must be word aligned and in range");
                    int kind = switch (vao.attribType[location]) {
                        case GL_FLOAT -> 0;
                        case GL_BYTE -> 1;
                        case GL_UNSIGNED_BYTE -> 2;
                        case GL_SHORT -> 3;
                        case GL_UNSIGNED_SHORT -> 4;
                        case GL_INT -> 5;
                        case GL_UNSIGNED_INT -> 6;
                        case GL_HALF_FLOAT -> 7;
                        default ->
                            throw new UnsupportedOperationException(
                                    "Geometry vertex format: " + vao.attribType[location]);
                    };
                    int components = vao.attribSize[location];
                    if (components < 1 || components > 4)
                        throw new UnsupportedOperationException("Geometry vertex component count: " + components);
                    int stride = vao.bindingStride[binding];
                    if (stride < 0) throw new IllegalArgumentException("Negative geometry vertex stride");
                    int format = 0x80000000
                            | components
                            | kind << 3
                            | (vao.attribNormalized[location] ? 128 : 0)
                            | (vao.attribIsInteger[location] ? 256 : 0)
                            | stream << 16;
                    data.putInt(at, (int) offset)
                            .putInt(at + 4, stride)
                            .putInt(at + 8, vao.bindingDivisor[binding])
                            .putInt(at + 12, format);
                    data.putInt(35 * 16 + stream * 4, (int) size);
                }
            return new Inputs(data, streams, indexBuffer);
        } catch (RuntimeException | Error failure) {
            MemoryUtil.memFree(data);
            throw failure;
        }
    }
}
