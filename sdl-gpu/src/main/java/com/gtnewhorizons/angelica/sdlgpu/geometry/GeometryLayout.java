package com.gtnewhorizons.angelica.sdlgpu.geometry;

import java.util.List;

/** Bounded primitive amplification contract shared by generated kernels and the draw executor. */
public record GeometryLayout(
        Input input,
        Output output,
        int maxVertices,
        int invocations,
        List<GeometryField> inputs,
        List<GeometryField> outputs) {
    public enum Input {
        POINTS(1),
        LINES(2),
        LINES_ADJACENCY(4),
        TRIANGLES(3),
        TRIANGLES_ADJACENCY(6);
        private final int vertices;

        Input(int vertices) {
            this.vertices = vertices;
        }

        public int vertices() {
            return vertices;
        }
    }

    public enum Output {
        POINTS,
        LINE_STRIP,
        TRIANGLE_STRIP
    }

    public GeometryLayout {
        if (input == null
                || output == null
                || maxVertices < 1
                || maxVertices > 1024
                || invocations < 1
                || invocations > 32)
            throw new IllegalArgumentException("Geometry emission limits are outside the supported bounds");
        inputs = List.copyOf(inputs);
        outputs = List.copyOf(outputs);
    }

    public int inputStride() {
        int offset = input.vertices() * 16; // gl_in[].gl_Position
        for (var field : inputs) {
            offset = GeometryField.align(offset, field.alignment());
            offset = Math.addExact(offset, Math.multiplyExact(field.arrayStride(), input.vertices()));
        }
        return GeometryField.align(offset, 16);
    }

    public int outputOffset(String name) {
        int offset = outputHeaderSize();
        for (var field : outputs) {
            offset = GeometryField.align(offset, field.alignment());
            if (field.name().equals(name)) return offset;
            offset = Math.addExact(offset, field.size());
        }
        throw new IllegalArgumentException("Unknown geometry output: " + name);
    }

    public int outputStride() {
        int offset = outputHeaderSize();
        for (var field : outputs) offset = Math.addExact(GeometryField.align(offset, field.alignment()), field.size());
        return GeometryField.align(offset, 16);
    }

    private int outputHeaderSize() {
        // Point rasterization also carries the emitted size; triangle record layouts stay unchanged.
        return output == Output.POINTS ? 48 : 32;
    }

    public Dispatch dispatch(int primitives, long maxBufferBytes) {
        if (primitives < 0 || maxBufferBytes < 0) throw new IllegalArgumentException("Negative geometry dispatch size");
        int jobs = Math.multiplyExact(primitives, invocations);
        long inputBytes = Math.multiplyExact((long) primitives, inputStride());
        long outputBytes = Math.multiplyExact(Math.multiplyExact((long) jobs, maxVertices), outputStride());
        long countsBytes = Math.multiplyExact((long) jobs, 16);
        if (Math.max(inputBytes, Math.max(outputBytes, countsBytes)) > maxBufferBytes)
            throw new IllegalArgumentException("Geometry transport exceeds the device buffer-size limit");
        return new Dispatch(jobs, (int) ((jobs + 63L) / 64), inputBytes, outputBytes, countsBytes);
    }

    public record Dispatch(int jobs, int groups, long inputBytes, long outputBytes, long countsBytes) {}
}
