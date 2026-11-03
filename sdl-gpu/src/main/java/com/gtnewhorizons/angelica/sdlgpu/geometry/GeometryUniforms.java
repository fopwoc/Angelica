package com.gtnewhorizons.angelica.sdlgpu.geometry;

import com.gtnewhorizons.angelica.sdlgpu.shader.ProgramLinkState;
import com.gtnewhorizons.angelica.sdlgpu.shader.ShaderManager.ProgramObject;
import com.gtnewhorizons.angelica.sdlgpu.shader.ShaderManager.UniformMemberInfo;
import java.util.*;

/** One public uniform namespace over compute vertex/geometry values and raster fragment values. */
final class GeometryUniforms {
    private GeometryUniforms() {}

    static String publicName(String name) {
        return name.startsWith("_vertex_uniform_") ? name.substring("_vertex_uniform_".length()) : name;
    }

    static boolean internal(String name) {
        return name.startsWith("_geometry_") || name.startsWith("_vertex_") && !name.startsWith("_vertex_uniform_");
    }

    static void merge(ProgramObject logical, ProgramObject compute, ProgramObject raster) {
        ProgramLinkState.reset(logical);
        logical.allSamplerNames.clear();
        logical.allImageNames.clear();
        logical.boolUniforms.clear();
        logical.vertexUboSize = compute.fragmentUboSize;
        logical.fragmentUboSize = raster.fragmentUboSize;
        copy(logical, compute, true);
        copy(logical, raster, false);
        logical.vertexSamplerNames = List.of();
        logical.fragmentSamplerNames = raster.fragmentSamplerNames;
        logical.fragmentImageNames = List.of();
        logical.vertexImageNames = List.of();
        for (String name : compute.allSamplerNames) logical.allSamplerNames.add(publicName(name));
        logical.allSamplerNames.addAll(raster.allSamplerNames);
        for (String name : compute.boolUniforms) logical.boolUniforms.add(publicName(name));
        logical.boolUniforms.addAll(raster.boolUniforms);
        logical.buildUniformSlotArrays();
        for (int i = 0; i < logical.uniformSlotCount; i++)
            if (logical.locationKind[i] == ProgramObject.LOCATION_KIND_SAMPLER) logical.locationSamplerUnit[i] = 0;
        logical.linked = true;
        logical.infoLog = "";
    }

    private static void copy(ProgramObject logical, ProgramObject source, boolean vertex) {
        for (var entry : source.nameToLocation.object2IntEntrySet()) {
            String original = entry.getKey();
            if (internal(original)) continue;
            String name = publicName(original);
            int slot = logical.nameToLocation.getInt(name);
            if (slot < 0) {
                slot = logical.nextUniformLocation++;
                logical.nameToLocation.put(name, slot);
                logical.locationToName.put(slot, name);
            }
            UniformMemberInfo info = source.fragmentMemberInfo.get(entry.getIntValue());
            if (info != null) {
                (vertex ? logical.vertexMemberInfo : logical.fragmentMemberInfo).put(slot, info);
                logical.locationToMemberInfo.putIfAbsent(slot, info);
            }
        }
    }
}
