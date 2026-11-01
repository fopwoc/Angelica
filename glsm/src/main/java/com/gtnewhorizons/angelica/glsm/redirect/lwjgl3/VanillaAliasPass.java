package com.gtnewhorizons.angelica.glsm.redirect.lwjgl3;

import com.gtnewhorizons.angelica.glsm.redirect.GLSMRedirector;
import java.util.Map;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** OpenGL entry points with implemented GLSM/backend equivalents. */
public final class VanillaAliasPass {
    // Keyed by function, so every core version class a caller imports from reaches the same alias.
    private static final Map<String, String> ALIASES = Map.of(
            CoreOwners.core("glLightfv(IILjava/nio/FloatBuffer;)V"), "glLight",
            CoreOwners.core("glMaterialfv(IILjava/nio/FloatBuffer;)V"), "glMaterial",
            CoreOwners.core("glMultMatrixf(Ljava/nio/FloatBuffer;)V"), "glMultMatrix",
            CoreOwners.core("glUniform2fv(ILjava/nio/FloatBuffer;)V"), "glUniform2",
            CoreOwners.core("glGetBooleanv(ILjava/nio/ByteBuffer;)V"), "glGetBoolean");

    private VanillaAliasPass() {}

    public static void adapt(ClassNode node) {
        for (var method : node.methods) {
            for (var instruction : method.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode call) || call.getOpcode() != Opcodes.INVOKESTATIC) continue;
                String key = CoreOwners.key(call.owner, call.name, call.desc);
                if (key.equals(RendererEndpoint.GLSM_DEPTH_STENCIL_ALPHA.owner() + ".glDepthRange(DD)V")) {
                    call.owner = RendererEndpoint.DEPTH_RANGE_BRIDGE.owner();
                } else if (key.equals(CoreOwners.core("glClearDepthf(F)V"))) {
                    method.instructions.insertBefore(call, new InsnNode(Opcodes.F2D));
                    call.owner = RendererEndpoint.GLSM_DEPTH_STENCIL_ALPHA.owner();
                    call.name = "glClearDepth";
                    call.desc = "(D)V";
                } else {
                    String name = ALIASES.get(key);
                    if (name != null) {
                        call.owner = GLSMRedirector.getTargetOwner(name);
                        call.name = name;
                    }
                }
            }
        }
    }
}
