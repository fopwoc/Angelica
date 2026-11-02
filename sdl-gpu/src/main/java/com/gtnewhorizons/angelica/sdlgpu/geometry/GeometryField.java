package com.gtnewhorizons.angelica.sdlgpu.geometry;

/** Scalar/vector/matrix stage values, with the std430 layout used by the compute transport buffers. */
public record GeometryField(String type, String name, Interpolation interpolation) {
    public enum Interpolation {
        SMOOTH,
        FLAT,
        NOPERSPECTIVE
    }

    public GeometryField(String type, String name) {
        this(type, name, Interpolation.SMOOTH);
    }

    public GeometryField {
        if (interpolation == null) throw new IllegalArgumentException("Missing geometry interpolation");
        if (!type.matches("(?:float|int|uint|[iu]?vec[234]|mat[234](?:x[234])?)"))
            throw new UnsupportedOperationException("Geometry transport type: " + type);
        if (!name.matches("[A-Za-z_][A-Za-z_0-9]*") || name.startsWith("_geometry_"))
            throw new IllegalArgumentException("Invalid geometry interface name: " + name);
    }

    public int alignment() {
        int rows = rows();
        return rows == 1 ? 4 : rows == 2 ? 8 : 16;
    }

    public int size() {
        if (!type.startsWith("mat")) return rows() * 4;
        return Math.multiplyExact(type.charAt(3) - '0', align(rows() * 4, alignment()));
    }

    public int arrayStride() {
        return align(size(), alignment());
    }

    private int rows() {
        if (type.startsWith("mat")) return type.charAt(type.length() - 1) - '0';
        return type.contains("vec") ? type.charAt(type.length() - 1) - '0' : 1;
    }

    static int align(int value, int alignment) {
        return Math.addExact(value, alignment - 1) / alignment * alignment;
    }
}
