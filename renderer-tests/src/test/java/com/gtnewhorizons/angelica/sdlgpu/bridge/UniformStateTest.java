package com.gtnewhorizons.angelica.sdlgpu.bridge;

import static org.junit.jupiter.api.Assertions.*;

import com.gtnewhorizons.angelica.sdlgpu.shader.ShaderManager;
import com.gtnewhorizons.angelica.sdlgpu.shader.UniformStaging;
import java.nio.IntBuffer;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.lwjgl.util.spvc.Spvc;

class UniformStateTest {
    @Test
    void readsOneArrayElementAndMatrixColumnOrderFromActualStagingShape() {
        var program = program(2, 1, 2);
        var staging = staging(new float[] {0.25f, 0.75f, 9, 8});
        assertArrayEquals(
                new float[] {0.25f, 0.75f},
                UniformState.read(program, staging, 0).raw());
        program.locationToMemberInfo.put(
                0, new ShaderManager.UniformMemberInfo(0, 64, 0, false, 4, 4, Spvc.SPVC_BASETYPE_FP32, 0));
        float[] matrix = new float[16];
        for (int i = 0; i < matrix.length; i++) matrix[i] = i;
        assertArrayEquals(matrix, UniformState.read(program, staging(matrix), 0).raw());
    }

    @Test
    void distinguishesDefaultZeroAndInvalidOrIncompleteUploads() {
        var program = program(2, 1, 0);
        assertArrayEquals(
                new float[] {0, 0}, UniformState.read(program, staging(null), 0).raw());
        assertThrows(IllegalStateException.class, () -> UniformState.read(program, staging(new float[] {1}), 0));
        assertThrows(IllegalArgumentException.class, () -> UniformState.read(program, staging(null), 1));
        program.linked = false;
        assertThrows(IllegalArgumentException.class, () -> UniformState.read(program, staging(null), 0));
    }

    @Test
    void readsSamplerAssignmentsOutsideOrdinaryUniformStorage() {
        var program = program(1, 1, 0);
        program.locationKind = new byte[] {ShaderManager.ProgramObject.LOCATION_KIND_SAMPLER};
        program.locationSamplerUnit = new int[] {7};
        assertEquals(7, UniformState.read(program, staging(new float[] {99}), 0).scalarInt());
    }

    @Test
    void readsOpaqueUniformAssignmentsWhenTheProgramAlsoUsesAnExternalUniformBuffer() {
        var program = program(1, 1, 0);
        program.externalUboBinding = 3;
        program.locationKind = new byte[] {ShaderManager.ProgramObject.LOCATION_KIND_SAMPLER};
        program.locationSamplerUnit = new int[] {0};
        int[] result = {-1, 99};
        UniformState.read(program, staging(new float[] {123}), 0).write(result);
        assertArrayEquals(new int[] {0, 99}, result);
        program.locationSamplerUnit[0] = 7;
        var integers = IntBuffer.allocate(3);
        integers.position(1);
        UniformState.read(program, staging(null), 0).write(integers);
        assertEquals(7, integers.get(1));
        assertEquals(1, integers.position());
        program.locationKind[0] = ShaderManager.ProgramObject.LOCATION_KIND_IMAGE;
        assertEquals(0, UniformState.read(program, staging(null), 0).scalarInt());
        program.imageTextureUnits.put("value", 9);
        assertEquals(9, UniformState.read(program, staging(null), 0).scalarInt());
        assertThrows(IllegalArgumentException.class, () -> UniformState.read(program, staging(null), -1));
    }

    @Test
    void readsDefaultMatrixUniformWithoutTreatingANamedBlockBindingAsItsStorage() {
        var program = program(4, 4, 0);
        program.locationToName.put(0, "actorShadowProjection");
        program.externalUboBinding = 7;
        assertArrayEquals(
                new float[16], UniformState.read(program, staging(null), 0).raw());
        float[] matrix = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
        var values = staging(matrix);
        float[] result = new float[17];
        result[16] = 99;
        UniformState.read(program, values, 0).write(result);
        assertArrayEquals(matrix, Arrays.copyOf(result, 16));
        assertEquals(99, result[16]);
        program.externalUboBinding = 3;
        assertArrayEquals(matrix, UniformState.read(program, values, 0).raw());
        assertEquals(3, program.externalUboBinding);
        assertThrows(IllegalArgumentException.class, () -> UniformState.read(program, values, -1));
    }

    private static ShaderManager.ProgramObject program(int vector, int columns, int arrayLength) {
        var program = new ShaderManager.ProgramObject();
        program.linked = true;
        program.uniformSlotCount = 1;
        program.locationToName.put(0, "value");
        program.locationToMemberInfo.put(
                0,
                new ShaderManager.UniformMemberInfo(
                        0, 64, 0, false, vector, columns, Spvc.SPVC_BASETYPE_FP32, arrayLength));
        return program;
    }

    private static UniformStaging staging(float[] values) {
        var staging = new UniformStaging();
        staging.uniformDataBySlot = new float[][] {values};
        return staging;
    }
}
