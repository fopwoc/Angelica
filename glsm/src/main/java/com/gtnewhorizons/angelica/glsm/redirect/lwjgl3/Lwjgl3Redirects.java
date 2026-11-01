package com.gtnewhorizons.angelica.glsm.redirect.lwjgl3;

import org.objectweb.asm.tree.ClassNode;

/** Completes GLSM redirection with the renderer's implemented LWJGL3 overloads and aliases. */
public final class Lwjgl3Redirects {
    private Lwjgl3Redirects() {}

    /** Run after GLSMRedirector and before consumer-specific isolation adapters. */
    public static void apply(ClassNode node) {
        VanillaAliasPass.adapt(node);
        UniformOverloadPass.adapt(node);
        UniformAccessPass.adapt(node);
        ResourcePass.adapt(node);
        TextureAllocationPass.adapt(node);
    }
}
