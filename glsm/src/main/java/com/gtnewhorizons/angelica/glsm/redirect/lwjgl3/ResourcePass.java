package com.gtnewhorizons.angelica.glsm.redirect.lwjgl3;

import java.util.Map;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** Only the exact observed resource overloads enter the bounded bridge implementations. */
public final class ResourcePass {
    // CORE keys match the function under any GLnn or GLnnC owner; extension and GLSM owners stay exact.
    private static final String CORE = CoreOwners.core("");
    private static final String LWJGL = CoreOwners.LWJGL;
    private static final Map<String, RendererEndpoint> ROUTES = Map.ofEntries(
            Map.entry(CORE + "glTexParameterfv(II[F)V", RendererEndpoint.BOUND_TEXTURE_BRIDGE),
            Map.entry(CORE + "glGetInteger64i(II)J", RendererEndpoint.BUFFER_BINDING_BRIDGE),
            Map.entry(CORE + "glUniform1ui(II)V", RendererEndpoint.UNIFORM_UPLOAD_BRIDGE),
            Map.entry(CORE + "glUniform2ui(III)V", RendererEndpoint.UNIFORM_UPLOAD_BRIDGE),
            Map.entry(CORE + "glUniform3ui(IIII)V", RendererEndpoint.UNIFORM_UPLOAD_BRIDGE),
            Map.entry(CORE + "glUniform4ui(IIIII)V", RendererEndpoint.UNIFORM_UPLOAD_BRIDGE),
            Map.entry(CORE + "glClearBufferuiv(II[I)V", RendererEndpoint.FRAMEBUFFER_CLEAR_BRIDGE),
            Map.entry(CORE + "glClearBufferiv(II[I)V", RendererEndpoint.FRAMEBUFFER_CLEAR_BRIDGE),
            Map.entry(CORE + "glGetTextureSubImage(IIIIIIIIIIIJ)V", RendererEndpoint.TEXTURE_READBACK_BRIDGE),
            Map.entry(CORE + "glViewportIndexedf(IFFFF)V", RendererEndpoint.VIEWPORT_BRIDGE),
            Map.entry(CORE + "glScissorIndexed(IIIII)V", RendererEndpoint.VIEWPORT_BRIDGE),
            Map.entry(CORE + "glDepthRangeIndexed(IDD)V", RendererEndpoint.VIEWPORT_BRIDGE),
            Map.entry(CORE + "glGetFloati_v(II[F)V", RendererEndpoint.VIEWPORT_BRIDGE),
            Map.entry(CORE + "glGetDoublei_v(II[D)V", RendererEndpoint.VIEWPORT_BRIDGE),
            Map.entry(CORE + "glProgramUniform1f(IIF)V", RendererEndpoint.UNIFORM_BRIDGE),
            Map.entry(CORE + "glProgramUniform2f(IIFF)V", RendererEndpoint.UNIFORM_BRIDGE),
            Map.entry(CORE + "glProgramUniform4f(IIFFFF)V", RendererEndpoint.UNIFORM_BRIDGE),
            Map.entry(CORE + "glProgramUniform1i(III)V", RendererEndpoint.UNIFORM_BRIDGE),
            Map.entry(CORE + "glProgramUniform4fv(II[F)V", RendererEndpoint.UNIFORM_BRIDGE),
            Map.entry(CORE + "glGetQueryi(II)I", RendererEndpoint.QUERY_BRIDGE),
            Map.entry(CORE + "glGetNamedBufferParameteri(II)I", RendererEndpoint.BUFFER_STORAGE_BRIDGE),
            Map.entry(CORE + "glGetVertexAttribi(II)I", RendererEndpoint.VERTEX_BINDING_BRIDGE),
            Map.entry(CORE + "glGetVertexAttribPointer(II)J", RendererEndpoint.VERTEX_BINDING_BRIDGE),
            Map.entry(CORE + "glDrawElementsIndirect(IIJ)V", RendererEndpoint.INDIRECT_DRAWS),
            Map.entry(
                    "com/gtnewhorizons/angelica/glsm/GLSMQueries.glGetIntegeri_v(II[I)V",
                    RendererEndpoint.RESOURCE_ARRAY_BRIDGE),
            Map.entry(
                    "com/gtnewhorizons/angelica/glsm/GLSMPrograms.glGetAttachedShaders(I[I[I)V",
                    RendererEndpoint.RESOURCE_ARRAY_BRIDGE),
            Map.entry(
                    "com/gtnewhorizons/angelica/glsm/GLSMPrograms.glGetActiveAttrib(IIILjava/nio/IntBuffer;Ljava/nio/IntBuffer;)Ljava/lang/String;",
                    RendererEndpoint.PROGRAM_RESOURCE_BRIDGE),
            Map.entry(
                    "com/gtnewhorizons/angelica/glsm/GLSMTextures.glTexImage2D(IIIIIIII[F)V",
                    RendererEndpoint.BOUND_TEXTURE_BRIDGE),
            Map.entry(
                    "com/gtnewhorizons/angelica/glsm/GLSMTextures.glTexImage2D(IIIIIIII[I)V",
                    RendererEndpoint.BOUND_TEXTURE_BRIDGE),
            Map.entry(
                    "com/gtnewhorizons/angelica/glsm/GLSMTextures.glTexImage3D(IIIIIIIII[F)V",
                    RendererEndpoint.BOUND_TEXTURE_BRIDGE),
            Map.entry(
                    "com/gtnewhorizons/angelica/glsm/GLSMTextures.glTexSubImage3D(IIIIIIIIIILjava/nio/FloatBuffer;)V",
                    RendererEndpoint.BOUND_TEXTURE_BRIDGE),
            Map.entry(
                    "com/gtnewhorizons/angelica/glsm/GLSMTextures.glGetTexImage(IIIILjava/nio/FloatBuffer;)V",
                    RendererEndpoint.BOUND_TEXTURE_BRIDGE),
            Map.entry(CORE + "glEnablei(II)V", RendererEndpoint.BLEND_BRIDGE),
            Map.entry(CORE + "glDisablei(II)V", RendererEndpoint.BLEND_BRIDGE),
            Map.entry(CORE + "glIsEnabledi(II)Z", RendererEndpoint.BLEND_BRIDGE),
            Map.entry(CORE + "glBlendFuncSeparatei(IIIII)V", RendererEndpoint.BLEND_BRIDGE),
            Map.entry(
                    "com/gtnewhorizons/angelica/glsm/GLSMQueries.glGetBooleani_v(IILjava/nio/ByteBuffer;)V",
                    RendererEndpoint.COLOR_MASK_BRIDGE),
            Map.entry(CORE + "glColorMaski(IZZZZ)V", RendererEndpoint.COLOR_MASK_BRIDGE),
            Map.entry(CORE + "glGetBooleani_v(IILjava/nio/ByteBuffer;)V", RendererEndpoint.COLOR_MASK_BRIDGE),
            Map.entry(CORE + "glBlendFunci(III)V", RendererEndpoint.BLEND_BRIDGE),
            Map.entry(CORE + "glBlendEquationi(II)V", RendererEndpoint.BLEND_BRIDGE),
            Map.entry(CORE + "glBlendEquationSeparatei(III)V", RendererEndpoint.BLEND_BRIDGE),
            Map.entry(CORE + "glClearBufferfv(II[F)V", RendererEndpoint.FRAMEBUFFER_CLEAR_BRIDGE),
            Map.entry(CORE + "glClearBufferfv(IILjava/nio/FloatBuffer;)V", RendererEndpoint.FRAMEBUFFER_CLEAR_BRIDGE),
            Map.entry(CORE + "glMultiDrawArraysIndirect(IJII)V", RendererEndpoint.INDIRECT_DRAWS),
            Map.entry(LWJGL + "ARBMultiDrawIndirect.glMultiDrawArraysIndirect(IJII)V", RendererEndpoint.INDIRECT_DRAWS),
            Map.entry(
                    "com/gtnewhorizons/angelica/glsm/GLStateManager.glMultiDrawArraysIndirect(IJII)V",
                    RendererEndpoint.INDIRECT_DRAWS),
            Map.entry(CORE + "glDrawArraysInstancedBaseInstance(IIIII)V", RendererEndpoint.BASE_INSTANCE_DRAWS),
            Map.entry(
                    LWJGL + "ARBBaseInstance.glDrawArraysInstancedBaseInstance(IIIII)V",
                    RendererEndpoint.BASE_INSTANCE_DRAWS),
            Map.entry(
                    "com/gtnewhorizons/angelica/glsm/GLStateManager.glDrawArraysInstancedBaseInstance(IIIII)V",
                    RendererEndpoint.BASE_INSTANCE_DRAWS),
            Map.entry(CORE + "glUniform1iv(ILjava/nio/IntBuffer;)V", RendererEndpoint.UNIFORM_UPLOAD_BRIDGE),
            Map.entry(CORE + "glFenceSync(II)J", RendererEndpoint.SYNC_BRIDGE),
            Map.entry(CORE + "glClientWaitSync(JIJ)I", RendererEndpoint.SYNC_BRIDGE),
            Map.entry(CORE + "glDeleteSync(J)V", RendererEndpoint.SYNC_BRIDGE),
            Map.entry(CORE + "glIsSync(J)Z", RendererEndpoint.SYNC_BRIDGE),
            Map.entry(LWJGL + "ARBSync.glFenceSync(II)J", RendererEndpoint.SYNC_BRIDGE),
            Map.entry(LWJGL + "ARBSync.glClientWaitSync(JIJ)I", RendererEndpoint.SYNC_BRIDGE),
            Map.entry(LWJGL + "ARBSync.glDeleteSync(J)V", RendererEndpoint.SYNC_BRIDGE),
            Map.entry(LWJGL + "ARBSync.glIsSync(J)Z", RendererEndpoint.SYNC_BRIDGE),
            Map.entry("com/gtnewhorizons/angelica/glsm/GLSMBuffers.glFenceSync(II)J", RendererEndpoint.SYNC_BRIDGE),
            Map.entry(
                    "com/gtnewhorizons/angelica/glsm/GLSMBuffers.glClientWaitSync(JIJ)I", RendererEndpoint.SYNC_BRIDGE),
            Map.entry("com/gtnewhorizons/angelica/glsm/GLSMBuffers.glDeleteSync(J)V", RendererEndpoint.SYNC_BRIDGE),
            Map.entry("com/gtnewhorizons/angelica/glsm/GLStateManager.glIsSync(J)Z", RendererEndpoint.SYNC_BRIDGE),
            Map.entry(CORE + "glTexStorage3D(IIIIII)V", RendererEndpoint.BOUND_TEXTURE_BRIDGE),
            Map.entry(LWJGL + "ARBTextureStorage.glTexStorage3D(IIIIII)V", RendererEndpoint.BOUND_TEXTURE_BRIDGE),
            Map.entry(
                    "com/gtnewhorizons/angelica/glsm/GLStateManager.glTexStorage3D(IIIIII)V",
                    RendererEndpoint.BOUND_TEXTURE_BRIDGE),
            Map.entry(CORE + "glTexSubImage3D(IIIIIIIIIILjava/nio/IntBuffer;)V", RendererEndpoint.BOUND_TEXTURE_BRIDGE),
            Map.entry(
                    "com/gtnewhorizons/angelica/glsm/GLSMTextures.glTexSubImage3D(IIIIIIIIIILjava/nio/IntBuffer;)V",
                    RendererEndpoint.BOUND_TEXTURE_BRIDGE),
            Map.entry(CORE + "glTexSubImage2D(IIIIIIIILjava/nio/FloatBuffer;)V", RendererEndpoint.BOUND_TEXTURE_BRIDGE),
            Map.entry(
                    "com/gtnewhorizons/angelica/glsm/GLSMTextures.glTexSubImage2D(IIIIIIIILjava/nio/FloatBuffer;)V",
                    RendererEndpoint.BOUND_TEXTURE_BRIDGE),
            Map.entry(CORE + "glTexSubImage2D(IIIIIIIILjava/nio/ShortBuffer;)V", RendererEndpoint.BOUND_TEXTURE_BRIDGE),
            Map.entry(
                    "com/gtnewhorizons/angelica/glsm/GLSMTextures.glTexSubImage2D(IIIIIIIILjava/nio/ShortBuffer;)V",
                    RendererEndpoint.BOUND_TEXTURE_BRIDGE),
            Map.entry(CORE + "glGetProgramInterfacei(III)I", RendererEndpoint.PROGRAM_RESOURCE_BRIDGE),
            Map.entry(CORE + "glGetProgramInterfaceiv(III[I)V", RendererEndpoint.PROGRAM_RESOURCE_BRIDGE),
            Map.entry(
                    CORE + "glGetProgramInterfaceiv(IIILjava/nio/IntBuffer;)V",
                    RendererEndpoint.PROGRAM_RESOURCE_BRIDGE),
            Map.entry(
                    CORE + "glGetProgramResourceiv(IIILjava/nio/IntBuffer;Ljava/nio/IntBuffer;Ljava/nio/IntBuffer;)V",
                    RendererEndpoint.PROGRAM_RESOURCE_BRIDGE),
            Map.entry(CORE + "glCreateFramebuffers()I", RendererEndpoint.FRAMEBUFFER_BRIDGE),
            Map.entry(CORE + "glCreateFramebuffers([I)V", RendererEndpoint.FRAMEBUFFER_BRIDGE),
            Map.entry(CORE + "glCreateFramebuffers(Ljava/nio/IntBuffer;)V", RendererEndpoint.FRAMEBUFFER_BRIDGE),
            Map.entry(CORE + "glNamedFramebufferTexture(IIII)V", RendererEndpoint.FRAMEBUFFER_BRIDGE),
            Map.entry(CORE + "glNamedFramebufferTextureLayer(IIIII)V", RendererEndpoint.FRAMEBUFFER_BRIDGE),
            Map.entry(CORE + "glNamedFramebufferDrawBuffer(II)V", RendererEndpoint.FRAMEBUFFER_BRIDGE),
            Map.entry(CORE + "glNamedFramebufferDrawBuffers(I[I)V", RendererEndpoint.FRAMEBUFFER_BRIDGE),
            Map.entry(
                    CORE + "glNamedFramebufferDrawBuffers(ILjava/nio/IntBuffer;)V",
                    RendererEndpoint.FRAMEBUFFER_BRIDGE),
            Map.entry(CORE + "glNamedFramebufferReadBuffer(II)V", RendererEndpoint.FRAMEBUFFER_BRIDGE),
            Map.entry(CORE + "glCheckNamedFramebufferStatus(II)I", RendererEndpoint.FRAMEBUFFER_BRIDGE),
            Map.entry(CORE + "glFramebufferTextureLayer(IIIII)V", RendererEndpoint.FRAMEBUFFER_BRIDGE),
            Map.entry(
                    "com/gtnewhorizons/angelica/glsm/GLSMFramebuffer.glFramebufferTextureLayer(IIIII)V",
                    RendererEndpoint.FRAMEBUFFER_BRIDGE),
            Map.entry(CORE + "glGetTextureLevelParameteri(III)I", RendererEndpoint.NAMED_TEXTURE_BRIDGE),
            Map.entry(CORE + "glGetTextureLevelParameterf(III)F", RendererEndpoint.NAMED_TEXTURE_BRIDGE),
            Map.entry(
                    CORE + "glGetTextureLevelParameteriv(IIILjava/nio/IntBuffer;)V",
                    RendererEndpoint.NAMED_TEXTURE_BRIDGE),
            Map.entry(CORE + "glGetTextureLevelParameteriv(III[I)V", RendererEndpoint.NAMED_TEXTURE_BRIDGE),
            Map.entry(
                    CORE + "glGetTextureLevelParameterfv(IIILjava/nio/FloatBuffer;)V",
                    RendererEndpoint.NAMED_TEXTURE_BRIDGE),
            Map.entry(CORE + "glGetTextureLevelParameterfv(III[F)V", RendererEndpoint.NAMED_TEXTURE_BRIDGE),
            Map.entry(
                    LWJGL + "ARBDirectStateAccess.glGetTextureLevelParameteri(III)I",
                    RendererEndpoint.NAMED_TEXTURE_BRIDGE),
            Map.entry(
                    LWJGL + "ARBDirectStateAccess.glGetTextureLevelParameterf(III)F",
                    RendererEndpoint.NAMED_TEXTURE_BRIDGE),
            Map.entry(
                    LWJGL + "ARBDirectStateAccess.glGetTextureLevelParameteriv(IIILjava/nio/IntBuffer;)V",
                    RendererEndpoint.NAMED_TEXTURE_BRIDGE),
            Map.entry(
                    LWJGL + "ARBDirectStateAccess.glGetTextureLevelParameteriv(III[I)V",
                    RendererEndpoint.NAMED_TEXTURE_BRIDGE),
            Map.entry(
                    LWJGL + "ARBDirectStateAccess.glGetTextureLevelParameterfv(IIILjava/nio/FloatBuffer;)V",
                    RendererEndpoint.NAMED_TEXTURE_BRIDGE),
            Map.entry(
                    LWJGL + "ARBDirectStateAccess.glGetTextureLevelParameterfv(III[F)V",
                    RendererEndpoint.NAMED_TEXTURE_BRIDGE),
            Map.entry(CORE + "glCreateTextures(I)I", RendererEndpoint.NAMED_TEXTURE_BRIDGE),
            Map.entry(CORE + "glCreateTextures(ILjava/nio/IntBuffer;)V", RendererEndpoint.NAMED_TEXTURE_BRIDGE),
            Map.entry(CORE + "glCreateTextures(I[I)V", RendererEndpoint.NAMED_TEXTURE_BRIDGE),
            Map.entry(CORE + "glTextureParameteri(III)V", RendererEndpoint.NAMED_TEXTURE_BRIDGE),
            Map.entry(CORE + "glTextureParameterf(IIF)V", RendererEndpoint.NAMED_TEXTURE_BRIDGE),
            Map.entry(CORE + "glTextureStorage2D(IIIII)V", RendererEndpoint.NAMED_TEXTURE_BRIDGE),
            Map.entry(CORE + "glTextureStorage3D(IIIIII)V", RendererEndpoint.NAMED_TEXTURE_BRIDGE),
            Map.entry(
                    CORE + "glTextureSubImage2D(IIIIIIIILjava/nio/ByteBuffer;)V",
                    RendererEndpoint.NAMED_TEXTURE_BRIDGE),
            Map.entry(
                    CORE + "glTextureSubImage2D(IIIIIIIILjava/nio/IntBuffer;)V", RendererEndpoint.NAMED_TEXTURE_BRIDGE),
            Map.entry(
                    CORE + "glTextureSubImage2D(IIIIIIIILjava/nio/FloatBuffer;)V",
                    RendererEndpoint.NAMED_TEXTURE_BRIDGE),
            Map.entry(
                    CORE + "glTextureSubImage2D(IIIIIIIILjava/nio/ShortBuffer;)V",
                    RendererEndpoint.NAMED_TEXTURE_BRIDGE),
            Map.entry(
                    CORE + "glTextureSubImage3D(IIIIIIIIIILjava/nio/ByteBuffer;)V",
                    RendererEndpoint.NAMED_TEXTURE_BRIDGE),
            Map.entry(
                    CORE + "glTextureSubImage3D(IIIIIIIIIILjava/nio/IntBuffer;)V",
                    RendererEndpoint.NAMED_TEXTURE_BRIDGE),
            Map.entry(
                    CORE + "glTextureSubImage3D(IIIIIIIIIILjava/nio/FloatBuffer;)V",
                    RendererEndpoint.NAMED_TEXTURE_BRIDGE),
            Map.entry(
                    CORE + "glTextureSubImage3D(IIIIIIIIIILjava/nio/ShortBuffer;)V",
                    RendererEndpoint.NAMED_TEXTURE_BRIDGE),
            Map.entry(CORE + "glVertexBindingDivisor(II)V", RendererEndpoint.VERTEX_BINDING_BRIDGE),
            Map.entry(
                    "com/gtnewhorizons/angelica/glsm/GLSMVertexInput.glVertexAttribDivisor(II)V",
                    RendererEndpoint.VERTEX_BINDING_BRIDGE),
            Map.entry(
                    "com/gtnewhorizons/angelica/glsm/GLSMVertexInput.glVertexAttribDivisorARB(II)V",
                    RendererEndpoint.VERTEX_BINDING_BRIDGE),
            Map.entry(
                    "com/gtnewhorizons/angelica/glsm/GLSMTextures.glBindTexture(II)V",
                    RendererEndpoint.BUFFER_TEXTURE_BRIDGE),
            Map.entry(CORE + "glTexBuffer(III)V", RendererEndpoint.BUFFER_TEXTURE_BRIDGE),
            Map.entry(CORE + "glTexBufferRange(IIIJJ)V", RendererEndpoint.BUFFER_TEXTURE_BRIDGE),
            Map.entry(
                    "com/gtnewhorizons/angelica/glsm/GLSMTextures.glGenTextures([I)V",
                    RendererEndpoint.RESOURCE_ARRAY_BRIDGE),
            Map.entry(
                    "com/gtnewhorizons/angelica/glsm/GLSMTextures.glDeleteTextures([I)V",
                    RendererEndpoint.RESOURCE_ARRAY_BRIDGE),
            Map.entry(
                    "com/gtnewhorizons/angelica/glsm/GLSMFramebuffer.glDeleteFramebuffers([I)V",
                    RendererEndpoint.RESOURCE_ARRAY_BRIDGE),
            Map.entry(
                    "com/gtnewhorizons/angelica/glsm/GLSMFramebuffer.glDrawBuffers([I)V",
                    RendererEndpoint.RESOURCE_ARRAY_BRIDGE),
            Map.entry(CORE + "glUniform1iv(I[I)V", RendererEndpoint.UNIFORM_UPLOAD_BRIDGE),
            Map.entry("com/gtnewhorizons/angelica/glsm/GLSMQueries.glGenQueries([I)V", RendererEndpoint.QUERY_BRIDGE),
            Map.entry(
                    "com/gtnewhorizons/angelica/glsm/GLSMQueries.glDeleteQueries([I)V", RendererEndpoint.QUERY_BRIDGE),
            Map.entry(CORE + "glGetQueryObjecti64(II)J", RendererEndpoint.QUERY_BRIDGE),
            Map.entry(
                    "com/gtnewhorizons/angelica/glsm/GLSMDraws.glDrawRangeElements(IIIIIJ)V",
                    RendererEndpoint.DRAW_RANGE_BRIDGE),
            Map.entry(
                    CORE + "glGetProgramResourceIndex(IILjava/lang/CharSequence;)I",
                    RendererEndpoint.PROGRAM_RESOURCE_BRIDGE),
            Map.entry(
                    CORE + "glGetProgramResourceName(IIII)Ljava/lang/String;",
                    RendererEndpoint.PROGRAM_RESOURCE_BRIDGE),
            Map.entry(CORE + "glGetProgramResourceiv(III[I[I[I)V", RendererEndpoint.PROGRAM_RESOURCE_BRIDGE),
            Map.entry(CORE + "glTextureView(IIIIIIII)V", RendererEndpoint.TEXTURE_VIEW_BRIDGE),
            Map.entry(LWJGL + "ARBTextureView.glTextureView(IIIIIIII)V", RendererEndpoint.TEXTURE_VIEW_BRIDGE),
            Map.entry(CORE + "glNamedBufferStorage(ILjava/nio/ByteBuffer;I)V", RendererEndpoint.BUFFER_STORAGE_BRIDGE),
            Map.entry(CORE + "glNamedBufferStorage(IJI)V", RendererEndpoint.BUFFER_STORAGE_BRIDGE),
            Map.entry(
                    LWJGL + "ARBDirectStateAccess.glNamedBufferStorage(IJI)V", RendererEndpoint.BUFFER_STORAGE_BRIDGE),
            Map.entry(
                    LWJGL + "ARBDirectStateAccess.glNamedBufferStorage(ILjava/nio/ByteBuffer;I)V",
                    RendererEndpoint.BUFFER_STORAGE_BRIDGE),
            Map.entry(CORE + "glCopyNamedBufferSubData(IIJJJ)V", RendererEndpoint.BUFFER_TRANSFER_BRIDGE),
            Map.entry(
                    CORE + "glGetNamedBufferSubData(IJLjava/nio/ByteBuffer;)V",
                    RendererEndpoint.BUFFER_TRANSFER_BRIDGE),
            Map.entry(
                    LWJGL + "ARBDirectStateAccess.glCopyNamedBufferSubData(IIJJJ)V",
                    RendererEndpoint.BUFFER_TRANSFER_BRIDGE),
            Map.entry(
                    LWJGL + "ARBDirectStateAccess.glGetNamedBufferSubData(IJLjava/nio/ByteBuffer;)V",
                    RendererEndpoint.BUFFER_TRANSFER_BRIDGE),
            Map.entry(CORE + "glGetTextureImage(IIIIIJ)V", RendererEndpoint.TEXTURE_READBACK_BRIDGE),
            Map.entry(CORE + "glGetTextureImage(IIIILjava/nio/ByteBuffer;)V", RendererEndpoint.TEXTURE_READBACK_BRIDGE),
            Map.entry(
                    LWJGL + "ARBDirectStateAccess.glGetTextureImage(IIIIIJ)V",
                    RendererEndpoint.TEXTURE_READBACK_BRIDGE),
            Map.entry(
                    LWJGL + "ARBDirectStateAccess.glGetTextureImage(IIIILjava/nio/ByteBuffer;)V",
                    RendererEndpoint.TEXTURE_READBACK_BRIDGE),
            Map.entry(CORE + "glCopyImageSubData(IIIIIIIIIIIIIII)V", RendererEndpoint.TEXTURE_COPY_BRIDGE),
            Map.entry(
                    LWJGL + "ARBCopyImage.glCopyImageSubData(IIIIIIIIIIIIIII)V", RendererEndpoint.TEXTURE_COPY_BRIDGE),
            Map.entry(
                    "com/gtnewhorizons/angelica/glsm/GLSMTextures.glCopyImageSubData(IIIIIIIIIIIIIII)V",
                    RendererEndpoint.TEXTURE_COPY_BRIDGE),
            Map.entry(CORE + "glClearTexSubImage(IIIIIIIIII[F)V", RendererEndpoint.TEXTURE_CLEAR_BRIDGE));

    private ResourcePass() {}

    public static void adapt(ClassNode node) {
        for (var method : node.methods) {
            for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESTATIC) {
                    var route = ROUTES.get(CoreOwners.key(call.owner, call.name, call.desc));
                    if (route != null) call.owner = route.owner();
                } else if (instruction instanceof LdcInsnNode constant) {
                    constant.cst = constant(constant.cst);
                } else if (instruction instanceof InvokeDynamicInsnNode dynamic) {
                    dynamic.bsm = (Handle) constant(dynamic.bsm);
                    for (int i = 0; i < dynamic.bsmArgs.length; i++) dynamic.bsmArgs[i] = constant(dynamic.bsmArgs[i]);
                }
            }
        }
    }

    private static Object constant(Object value) {
        if (value instanceof Handle handle && handle.getTag() == Opcodes.H_INVOKESTATIC) {
            var route = ROUTES.get(CoreOwners.key(handle.getOwner(), handle.getName(), handle.getDesc()));
            if (route != null)
                return new Handle(handle.getTag(), route.owner(), handle.getName(), handle.getDesc(), false);
        }
        if (value instanceof ConstantDynamic dynamic) {
            Object[] arguments = new Object[dynamic.getBootstrapMethodArgumentCount()];
            for (int i = 0; i < arguments.length; i++) arguments[i] = constant(dynamic.getBootstrapMethodArgument(i));
            return new ConstantDynamic(
                    dynamic.getName(),
                    dynamic.getDescriptor(),
                    (Handle) constant(dynamic.getBootstrapMethod()),
                    arguments);
        }
        return value;
    }
}
