package com.gtnewhorizons.angelica.glsm.redirect.lwjgl3;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** Static renderer entry points named by transformed consumers, without loading execution classes. */
public enum RendererEndpoint {
    GLSM_ATTRIB_STACK("com/gtnewhorizons/angelica/glsm/GLSMAttribStack"),
    GLSM_BLEND("com/gtnewhorizons/angelica/glsm/GLSMBlend"),
    GLSM_BUFFERS("com/gtnewhorizons/angelica/glsm/GLSMBuffers"),
    GLSM_CURRENT_ATTRIBS("com/gtnewhorizons/angelica/glsm/GLSMCurrentAttribs"),
    GLSM_DEPTH_STENCIL_ALPHA("com/gtnewhorizons/angelica/glsm/GLSMDepthStencilAlpha"),
    GLSM_DRAWS("com/gtnewhorizons/angelica/glsm/GLSMDraws"),
    GLSM_ENABLE("com/gtnewhorizons/angelica/glsm/GLSMEnable"),
    GLSM_FOG("com/gtnewhorizons/angelica/glsm/GLSMFog"),
    GLSM_FRAMEBUFFER("com/gtnewhorizons/angelica/glsm/GLSMFramebuffer"),
    GLSM_LIGHTING("com/gtnewhorizons/angelica/glsm/GLSMLighting"),
    GLSM_MATRICES("com/gtnewhorizons/angelica/glsm/GLSMMatrices"),
    GLSM_PROGRAMS("com/gtnewhorizons/angelica/glsm/GLSMPrograms"),
    GLSM_QUERIES("com/gtnewhorizons/angelica/glsm/GLSMQueries"),
    GLSM_RASTERIZER("com/gtnewhorizons/angelica/glsm/GLSMRasterizer"),
    GLSM_TEX_ENV_GEN("com/gtnewhorizons/angelica/glsm/GLSMTexEnvGen"),
    GLSM_TEXTURE_LIFETIME("com/gtnewhorizons/angelica/glsm/GLSMTextureLifetime"),
    GLSM_TEXTURES("com/gtnewhorizons/angelica/glsm/GLSMTextures"),
    GLSM_VERTEX_INPUT("com/gtnewhorizons/angelica/glsm/GLSMVertexInput"),
    STATE_MANAGER("com/gtnewhorizons/angelica/glsm/GLStateManager"),
    DEBUG_SHIM("com/gtnewhorizons/angelica/sdlgpu/compat/AwareGLDebugShim"),
    DEPTH_RANGE_BRIDGE("com/gtnewhorizons/angelica/sdlgpu/bridge/DepthRangeBridge"),
    VIEWPORT_BRIDGE("com/gtnewhorizons/angelica/sdlgpu/bridge/ViewportBridge"),
    DRAW_RANGE_BRIDGE("com/gtnewhorizons/angelica/sdlgpu/bridge/DrawRangeBridge"),
    DEBUG_CALLBACK_BRIDGE("com/gtnewhorizons/angelica/sdlgpu/bridge/DebugCallbackBridge"),
    PROGRAM_RESOURCE_BRIDGE("com/gtnewhorizons/angelica/sdlgpu/bridge/ProgramResourceBridge"),
    UNIFORM_BRIDGE("com/gtnewhorizons/angelica/sdlgpu/bridge/UniformBridge"),
    RESOURCE_ARRAY_BRIDGE("com/gtnewhorizons/angelica/sdlgpu/bridge/ResourceArrayBridge"),
    SYNC_BRIDGE("com/gtnewhorizons/angelica/sdlgpu/bridge/SyncBridge"),
    QUERY_BRIDGE("com/gtnewhorizons/angelica/sdlgpu/bridge/QueryBridge"),
    UNIFORM_QUERY_BRIDGE("com/gtnewhorizons/angelica/sdlgpu/bridge/UniformQueryBridge"),
    UNIFORM_UPLOAD_BRIDGE("com/gtnewhorizons/angelica/sdlgpu/bridge/UniformUploadBridge"),
    COLOR_MASK_BRIDGE("com/gtnewhorizons/angelica/sdlgpu/bridge/ColorMaskBridge"),
    BLEND_BRIDGE("com/gtnewhorizons/angelica/sdlgpu/bridge/BlendBridge"),
    FRAMEBUFFER_CLEAR_BRIDGE("com/gtnewhorizons/angelica/sdlgpu/bridge/FramebufferClearBridge"),
    FRAMEBUFFER_BRIDGE("com/gtnewhorizons/angelica/sdlgpu/bridge/FramebufferBridge"),
    BOUND_TEXTURE_BRIDGE("com/gtnewhorizons/angelica/sdlgpu/bridge/BoundTextureBridge"),
    NAMED_TEXTURE_BRIDGE("com/gtnewhorizons/angelica/sdlgpu/bridge/NamedTextureBridge"),
    TEXTURE_ALLOCATION_BRIDGE("com/gtnewhorizons/angelica/sdlgpu/bridge/TextureAllocationBridge"),
    BUFFER_TRANSFER_BRIDGE("com/gtnewhorizons/angelica/sdlgpu/bridge/BufferTransferBridge"),
    BUFFER_STORAGE_BRIDGE("com/gtnewhorizons/angelica/sdlgpu/bridge/BufferStorageBridge"),
    BUFFER_BINDING_BRIDGE("com/gtnewhorizons/angelica/sdlgpu/bridge/BufferBindingBridge"),
    TEXTURE_CLEAR_BRIDGE("com/gtnewhorizons/angelica/sdlgpu/bridge/TextureClearBridge"),
    TEXTURE_VIEW_BRIDGE("com/gtnewhorizons/angelica/sdlgpu/bridge/TextureViewBridge"),
    VERTEX_BINDING_BRIDGE("com/gtnewhorizons/angelica/sdlgpu/bridge/VertexBindingBridge"),
    INDIRECT_DRAWS("com/gtnewhorizons/angelica/sdlgpu/vertex/IndirectDraws"),
    BASE_INSTANCE_DRAWS("com/gtnewhorizons/angelica/sdlgpu/vertex/BaseInstanceDraws"),
    BUFFER_TEXTURE_BRIDGE("com/gtnewhorizons/angelica/sdlgpu/bridge/BufferTextureBridge"),
    TEXTURE_READBACK_BRIDGE("com/gtnewhorizons/angelica/sdlgpu/bridge/TextureReadbackBridge"),
    TEXTURE_COPY_BRIDGE("com/gtnewhorizons/angelica/sdlgpu/bridge/TextureCopyBridge");

    private static final Set<String> OWNERS =
            Arrays.stream(values()).map(RendererEndpoint::owner).collect(Collectors.toUnmodifiableSet());
    private final String owner;

    RendererEndpoint(String owner) {
        this.owner = owner;
    }

    public String owner() {
        return owner;
    }

    public static boolean isGlsm(String owner) {
        return OWNERS.contains(owner) && owner.startsWith("com/gtnewhorizons/angelica/glsm/");
    }
}
