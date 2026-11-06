package com.gtnewhorizons.angelica.sdlgpu.shader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Literal source replacements read from the file named by {@code angelica.debug.shaderPatches}, one
 * {@code from ==> to} per line. It lets a real game run answer what a shader decided per pixel, which CPU traces cannot
 * see. Patches apply before the shader caches are keyed, so a patched run never reuses or poisons unpatched entries.
 */
final class DebugShaderPatches {
    private static final String SEPARATOR = " ==> ";
    private static final List<String[]> PATCHES = load(System.getProperty("angelica.debug.shaderPatches", ""));

    private DebugShaderPatches() {}

    static String apply(String source) {
        return apply(PATCHES, source);
    }

    static String apply(List<String[]> patches, String source) {
        for (String[] patch : patches) source = source.replace(patch[0], patch[1]);
        return source;
    }

    static List<String[]> parse(List<String> lines) {
        var patches = new ArrayList<String[]>();
        for (String line : lines) {
            if (line.isBlank()) continue;
            int separator = line.indexOf(SEPARATOR);
            if (separator <= 0) throw new IllegalArgumentException("Expected 'from ==> to': " + line);
            patches.add(new String[] {line.substring(0, separator), line.substring(separator + SEPARATOR.length())});
        }
        return List.copyOf(patches);
    }

    private static List<String[]> load(String file) {
        if (file.isEmpty()) return List.of();
        try {
            return parse(Files.readAllLines(Path.of(file)));
        } catch (IOException failure) {
            throw new UncheckedIOException("Cannot read shader patches: " + file, failure);
        }
    }
}
