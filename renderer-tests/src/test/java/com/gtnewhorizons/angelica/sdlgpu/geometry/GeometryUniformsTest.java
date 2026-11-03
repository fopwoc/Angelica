package com.gtnewhorizons.angelica.sdlgpu.geometry;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.util.spvc.Spvc.SPVC_BASETYPE_FP32;

import com.gtnewhorizons.angelica.sdlgpu.shader.ShaderManager.ProgramObject;
import com.gtnewhorizons.angelica.sdlgpu.shader.ShaderManager.UniformMemberInfo;
import java.util.List;
import org.junit.jupiter.api.Test;

class GeometryUniformsTest {
    @Test
    void mergesComputeSamplerAssignmentsIntoThePublicNamespace() {
        var logical = new ProgramObject();
        var compute = new ProgramObject();
        var raster = new ProgramObject();
        add(compute, "_vertex_uniform_positions", null);
        add(compute, "tint", null);
        add(raster, "tint", null);
        compute.allSamplerNames.addAll(List.of("_vertex_uniform_positions", "tint"));
        raster.allSamplerNames.add("tint");
        raster.fragmentSamplerNames = List.of("tint");
        GeometryUniforms.merge(logical, compute, raster);
        assertEquals(2, logical.uniformSlotCount);
        for (String name : List.of("positions", "tint")) {
            int slot = logical.nameToLocation.getInt(name);
            assertTrue(slot >= 0);
            assertEquals(ProgramObject.LOCATION_KIND_SAMPLER, logical.locationKind[slot]);
            assertEquals(0, logical.locationSamplerUnit[slot]);
        }
        assertEquals(-1, logical.nameToLocation.getInt("_vertex_uniform_positions"));
    }

    @Test
    void mergesUserUniformsAcrossComputeAndRasterWithoutExposingTransport() {
        var compute = new ProgramObject();
        var raster = new ProgramObject();
        var logical = new ProgramObject();
        var matrix = new UniformMemberInfo(0, 384, 64, true, 4, 4, SPVC_BASETYPE_FP32, 6);
        var scalar = new UniformMemberInfo(384, 4, 0, false, 1, 1, SPVC_BASETYPE_FP32, 0);
        add(compute, "_vertex_uniform_gain", scalar);
        add(compute, "gain", scalar);
        add(compute, "faces", matrix);
        add(compute, "_geometry_primitives", scalar);
        add(raster, "gain", scalar);
        add(raster, "diffuse", null);
        raster.allSamplerNames.add("diffuse");
        raster.fragmentSamplerNames = List.of("diffuse");
        GeometryUniforms.merge(logical, compute, raster);
        assertEquals(3, logical.uniformSlotCount);
        int gain = logical.nameToLocation.getInt("gain");
        assertNotNull(logical.vsInfoBySlot[gain]);
        assertNotNull(logical.fsInfoBySlot[gain]);
        assertEquals(
                6,
                logical.locationToMemberInfo
                        .get(logical.nameToLocation.getInt("faces"))
                        .arrayLen());
        assertEquals(-1, logical.nameToLocation.getInt("_geometry_primitives"));
        int sampler = logical.nameToLocation.getInt("diffuse");
        assertEquals(ProgramObject.LOCATION_KIND_SAMPLER, logical.locationKind[sampler]);
        assertEquals(0, logical.locationSamplerUnit[sampler]);
    }

    private static void add(ProgramObject object, String name, UniformMemberInfo info) {
        int slot = object.nextUniformLocation++;
        object.nameToLocation.put(name, slot);
        object.locationToName.put(slot, name);
        if (info != null) object.fragmentMemberInfo.put(slot, info);
    }
}
