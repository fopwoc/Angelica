package com.gtnewhorizons.angelica.sdlgpu.shader;

import java.util.List;

/** Restores GL image-unit assignments from the layout bindings saved before backend slot remapping. */
final class ImageBindings {
    private ImageBindings() {}

    static void initialize(ShaderManager.ProgramObject program) {
        program.imageTextureUnits.clear();
        if (program.computeSpirv != null) {
            var map = program.computeBindingMap;
            assign(program, List.of(map.roStorageTextureNames()), map.roStorageTextureGlSlots(), null);
            assign(program, List.of(map.rwStorageTextureNames()), map.rwStorageTextureGlSlots(), null);
        } else {
            assign(
                    program,
                    program.vertexImageNames,
                    program.vertexGraphicsBindingMap.roStorageTextureGlSlots(),
                    program.vertexImageIsMovedSampler);
            assign(
                    program,
                    program.fragmentImageNames,
                    program.fragmentGraphicsBindingMap.roStorageTextureGlSlots(),
                    program.fragmentImageIsMovedSampler);
        }
    }

    private static void assign(ShaderManager.ProgramObject program, List<String> names, int[] units, boolean[] moved) {
        if (names.size() != units.length || moved != null && moved.length != names.size())
            throw new IllegalStateException("Image names and GL binding slots disagree");
        for (int i = 0; i < units.length; i++) {
            if (moved != null && moved[i] || SamplerOverflow.isMoved(names.get(i))) continue;
            String name = names.get(i);
            if (program.imageTextureUnits.containsKey(name) && program.imageTextureUnits.getInt(name) != units[i])
                throw new IllegalStateException("Image layout bindings disagree across stages: " + name);
            program.imageTextureUnits.put(name, units[i]);
        }
    }
}
