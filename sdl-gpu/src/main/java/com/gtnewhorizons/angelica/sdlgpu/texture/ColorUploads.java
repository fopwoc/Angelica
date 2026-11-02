package com.gtnewhorizons.angelica.sdlgpu.texture;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.sdl.SDLGPU.*;

import java.nio.ByteBuffer;
import org.lwjgl.system.MemoryUtil;

/** Converts scalar color components numerically; GL source types do not describe physical GPU storage. */
public final class ColorUploads {
    private ColorUploads() {}

    private record Storage(int channels, int type, boolean bgra) {}

    public static final class Conversion {
        private final int format, type, components, componentBytes;
        private final Storage storage;
        private final boolean swapBytes;

        private Conversion(int format, int type, int components, Storage storage, boolean swapBytes) {
            this.format = format;
            this.type = type;
            this.components = components;
            this.componentBytes = bytes(type);
            this.storage = storage;
            this.swapBytes = swapBytes;
        }

        public int sourceBytes() {
            return components * componentBytes;
        }

        public int destinationBytes() {
            return storage.channels * bytes(storage.type);
        }

        private boolean direct() {
            return !swapBytes
                    && components == storage.channels
                    && type == storage.type
                    && (format == GL_RED
                            || format == GL_RG
                            || format == GL_RGB
                            || format == GL_RGBA
                            || format == GL_BGRA)
                    && (format == GL_BGRA) == storage.bgra;
        }

        public void copy(
                ByteBuffer source, int sourceOffset, ByteBuffer destination, int destinationOffset, int pixels) {
            if (pixels < 0
                    || sourceOffset < 0
                    || destinationOffset < 0
                    || (long) sourceOffset + (long) pixels * sourceBytes() > source.limit()
                    || (long) destinationOffset + (long) pixels * destinationBytes() > destination.limit())
                throw new IllegalArgumentException("Color conversion region exceeds buffer bounds");
            if (pixels == 0) return;
            if (direct()) {
                MemoryUtil.memCopy(
                        MemoryUtil.memAddress(source, sourceOffset),
                        MemoryUtil.memAddress(destination, destinationOffset),
                        (long) pixels * sourceBytes());
                return;
            }
            long input = MemoryUtil.memAddress(source, sourceOffset);
            long output = MemoryUtil.memAddress(destination, destinationOffset);
            for (int pixel = 0; pixel < pixels; pixel++) {
                for (int channel = 0; channel < storage.channels; channel++) {
                    int rgba = storage.bgra && channel < 3 ? 2 - channel : channel;
                    int component = component(rgba);
                    double value = component < 0
                            ? (rgba == 3 || format == GL_ALPHA ? 1 : 0)
                            : read(input + (long) component * componentBytes);
                    write(output + (long) channel * bytes(storage.type), value);
                }
                input += sourceBytes();
                output += destinationBytes();
            }
        }

        private int component(int rgba) {
            if (format == GL_ALPHA) return rgba == 3 ? 0 : -1;
            if (format == GL_LUMINANCE) return rgba == 3 ? -1 : 0;
            if (format == GL_LUMINANCE_ALPHA) return rgba == 3 ? 1 : 0;
            int component = (format == GL_BGR || format == GL_BGRA) && rgba < 3 ? 2 - rgba : rgba;
            return component < components ? component : -1;
        }

        private double read(long address) {
            int word = switch (componentBytes) {
                case 1 -> MemoryUtil.memGetByte(address) & 255;
                case 2 -> MemoryUtil.memGetShort(address) & 65535;
                case 4 -> MemoryUtil.memGetInt(address);
                default -> throw new AssertionError();
            };
            if (swapBytes)
                word = componentBytes == 2
                        ? Short.reverseBytes((short) word) & 65535
                        : componentBytes == 4 ? Integer.reverseBytes(word) : word;
            return switch (type) {
                case GL_UNSIGNED_BYTE -> word / 255.0;
                case GL_BYTE -> Math.max(-1, (byte) word / 127.0);
                case GL_UNSIGNED_SHORT -> word / 65535.0;
                case GL_SHORT -> Math.max(-1, (short) word / 32767.0);
                case GL_UNSIGNED_INT -> Integer.toUnsignedLong(word) / 4294967295.0;
                case GL_INT -> Math.max(-1, word / 2147483647.0);
                case GL_HALF_FLOAT -> Float.float16ToFloat((short) word);
                case GL_FLOAT -> Float.intBitsToFloat(word);
                default -> throw new AssertionError();
            };
        }

        private void write(long address, double value) {
            switch (storage.type) {
                case GL_UNSIGNED_BYTE -> MemoryUtil.memPutByte(address, (byte) normalized(value, 255));
                case GL_UNSIGNED_SHORT -> MemoryUtil.memPutShort(address, (short) normalized(value, 65535));
                case GL_HALF_FLOAT -> MemoryUtil.memPutShort(address, Float.floatToFloat16((float) value));
                case GL_FLOAT -> MemoryUtil.memPutFloat(address, (float) value);
                default -> throw new AssertionError();
            }
        }
    }

    public static Conversion conversion(int format, int type, int destination, boolean swapBytes) {
        Storage storage = switch (destination) {
            case SDL_GPU_TEXTUREFORMAT_R8_UNORM -> new Storage(1, GL_UNSIGNED_BYTE, false);
            case SDL_GPU_TEXTUREFORMAT_R8G8_UNORM -> new Storage(2, GL_UNSIGNED_BYTE, false);
            case SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM, SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM_SRGB ->
                new Storage(4, GL_UNSIGNED_BYTE, false);
            case SDL_GPU_TEXTUREFORMAT_B8G8R8A8_UNORM, SDL_GPU_TEXTUREFORMAT_B8G8R8A8_UNORM_SRGB ->
                new Storage(4, GL_UNSIGNED_BYTE, true);
            case SDL_GPU_TEXTUREFORMAT_R16_UNORM -> new Storage(1, GL_UNSIGNED_SHORT, false);
            case SDL_GPU_TEXTUREFORMAT_R16G16_UNORM -> new Storage(2, GL_UNSIGNED_SHORT, false);
            case SDL_GPU_TEXTUREFORMAT_R16G16B16A16_UNORM -> new Storage(4, GL_UNSIGNED_SHORT, false);
            case SDL_GPU_TEXTUREFORMAT_R16_FLOAT -> new Storage(1, GL_HALF_FLOAT, false);
            case SDL_GPU_TEXTUREFORMAT_R16G16_FLOAT -> new Storage(2, GL_HALF_FLOAT, false);
            case SDL_GPU_TEXTUREFORMAT_R16G16B16A16_FLOAT -> new Storage(4, GL_HALF_FLOAT, false);
            case SDL_GPU_TEXTUREFORMAT_R32_FLOAT -> new Storage(1, GL_FLOAT, false);
            case SDL_GPU_TEXTUREFORMAT_R32G32_FLOAT -> new Storage(2, GL_FLOAT, false);
            case SDL_GPU_TEXTUREFORMAT_R32G32B32A32_FLOAT -> new Storage(4, GL_FLOAT, false);
            default -> null;
        };
        if (storage == null) return null;
        int components = switch (format) {
            case GL_RED, GL_ALPHA, GL_LUMINANCE -> 1;
            case GL_RG, GL_LUMINANCE_ALPHA -> 2;
            case GL_RGB, GL_BGR -> 3;
            case GL_RGBA, GL_BGRA -> 4;
            default -> 0;
        };
        if (components == 0 || bytes(type) == 0) return null;
        return new Conversion(format, type, components, storage, swapBytes);
    }

    /** Returns null for packed, depth and integer formats handled by the existing upload paths. */
    public static ByteBuffer prepare(int format, int type, int destination, ByteBuffer source, int width, int height) {
        var conversion = conversion(format, type, destination, false);
        if (conversion == null) return null;
        int pixels = Math.multiplyExact(width, height);
        int required = Math.multiplyExact(pixels, conversion.sourceBytes());
        if (source.remaining() < required) throw new IllegalArgumentException("Color upload source is too small");
        if (conversion.direct()) return source;
        var result = MemoryUtil.memAlloc(Math.multiplyExact(pixels, conversion.destinationBytes()));
        conversion.copy(source, source.position(), result, 0, pixels);
        return result;
    }

    private static int bytes(int type) {
        return switch (type) {
            case GL_UNSIGNED_BYTE, GL_BYTE -> 1;
            case GL_UNSIGNED_SHORT, GL_SHORT, GL_HALF_FLOAT -> 2;
            case GL_UNSIGNED_INT, GL_INT, GL_FLOAT -> 4;
            default -> 0;
        };
    }

    private static long normalized(double value, long maximum) {
        return Math.round((value > 0 ? Math.min(value, 1) : 0) * maximum);
    }
}
