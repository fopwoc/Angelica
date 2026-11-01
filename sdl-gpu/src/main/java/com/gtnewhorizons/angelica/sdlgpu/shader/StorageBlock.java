package com.gtnewhorizons.angelica.sdlgpu.shader;

import java.util.List;

public record StorageBlock(String name, int binding, int bytes, List<ResourceVariable> variables, int stages) {
    public static final int VERTEX = 1, FRAGMENT = 2, COMPUTE = 4;

    public StorageBlock {
        variables = List.copyOf(variables);
    }
}
