package com.gtnewhorizons.angelica.sdlgpu.shader;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.ByteBuffer;
import java.util.List;
import org.junit.jupiter.api.Test;

class ImageBindingsTest {
    @Test
    void linkingRestoresDeclaredBindingsAndKeepsMovedSamplersInTheirOwnNamespace() {
        var program = graphics();
        program.imageTextureUnits.put("input", 11);
        program.imageTextureUnits.put("old", 3);
        ImageBindings.initialize(program);
        assertEquals(5, program.imageTextureUnits.getInt("input"));
        assertEquals(2, program.imageTextureUnits.getInt("vertexOnly"));
        assertFalse(program.imageTextureUnits.containsKey("moved"));
        assertFalse(program.imageTextureUnits.containsKey("old"));
        program.imageTextureUnits.put("input", 9);
        ImageBindings.initialize(program);
        assertEquals(5, program.imageTextureUnits.getInt("input"));
    }

    @Test
    void conflictingStageBindingsAndIncompleteReflectionFailBeforeDrawing() {
        var program = graphics();
        program.fragmentGraphicsBindingMap =
                new ShaderManager.GraphicsBindingMap(new int[] {6, 7}, new int[0], new int[0]);
        assertThrows(IllegalStateException.class, () -> ImageBindings.initialize(program));
        program.fragmentGraphicsBindingMap = ShaderManager.GraphicsBindingMap.EMPTY;
        assertThrows(IllegalStateException.class, () -> ImageBindings.initialize(program));
    }

    @Test
    void computeReadAndWriteImagesPublishTheirSavedUnitsToo() {
        var program = new ShaderManager.ProgramObject();
        program.computeSpirv = ByteBuffer.allocate(0);
        program.computeBindingMap = new ShaderManager.ComputeBindingMap(
                new int[0],
                new int[] {2},
                new int[] {5},
                new int[0],
                new int[0],
                new int[0],
                new String[0],
                new String[] {"input"},
                new String[] {"output"},
                new boolean[0],
                new int[0],
                new int[0],
                new int[0],
                new int[0],
                new int[0]);
        ImageBindings.initialize(program);
        assertEquals(2, program.imageTextureUnits.getInt("input"));
        assertEquals(5, program.imageTextureUnits.getInt("output"));
    }

    private static ShaderManager.ProgramObject graphics() {
        var program = new ShaderManager.ProgramObject();
        program.vertexImageNames = List.of("input", "vertexOnly");
        program.vertexImageIsMovedSampler = new boolean[2];
        program.vertexGraphicsBindingMap =
                new ShaderManager.GraphicsBindingMap(new int[] {5, 2}, new int[0], new int[0]);
        program.fragmentImageNames = List.of("input", "moved");
        program.fragmentImageIsMovedSampler = new boolean[] {false, true};
        program.fragmentGraphicsBindingMap =
                new ShaderManager.GraphicsBindingMap(new int[] {5, 7}, new int[0], new int[0]);
        return program;
    }
}
