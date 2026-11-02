package com.gtnewhorizons.angelica.sdlgpu.shader;

import static org.lwjgl.util.spvc.Spv.SpvDecorationBinding;
import static org.lwjgl.util.spvc.Spvc.*;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.spvc.SpvcReflectedResource;

/** Default uniform storage follows its block identity after resource bindings have been remapped. */
public final class DefaultUniforms {
    private DefaultUniforms() {}

    public static boolean reflect(long compiler, long resources, SpvcReflectedResource candidate, MemoryStack stack) {
        if (named(compiler, candidate)) return true;
        var list = stack.mallocPointer(1);
        var count = stack.mallocPointer(1);
        if (spvc_resources_get_resource_list_for_type(resources, SPVC_RESOURCE_TYPE_UNIFORM_BUFFER, list, count)
                != SPVC_SUCCESS) throw new IllegalStateException("Cannot inspect shader uniform buffers");
        var buffers = SpvcReflectedResource.create(list.get(0), (int) count.get(0));
        for (int i = 0; i < buffers.capacity(); i++) if (named(compiler, buffers.get(i))) return false;
        return spvc_compiler_get_decoration(compiler, candidate.id(), SpvDecorationBinding) == 0;
    }

    private static boolean named(long compiler, SpvcReflectedResource resource) {
        String name = resource.nameString(), type = spvc_compiler_get_name(compiler, resource.base_type_id());
        return name != null && name.endsWith("DefaultUniformBlock")
                || type != null && type.endsWith("DefaultUniformBlock");
    }
}
