package com.gtnewhorizons.angelica.sdlgpu;

import static org.lwjgl.opengl.GL12.GL_TEXTURE_MAX_LEVEL;

import com.gtnewhorizons.angelica.glsm.GLSMTextures;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.backend.BackendManager;
import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import com.gtnewhorizons.angelica.sdlgpu.frame.FrameManager;
import com.gtnewhorizons.angelica.sdlgpu.pipeline.AttachmentClear;
import com.gtnewhorizons.angelica.sdlgpu.pipeline.DrawDispatch;
import com.gtnewhorizons.angelica.sdlgpu.pipeline.PipelineApplier;
import com.gtnewhorizons.angelica.sdlgpu.resource.FBOClearTracker;
import com.gtnewhorizons.angelica.sdlgpu.resource.ResourceManager;
import com.gtnewhorizons.angelica.sdlgpu.shader.ShaderManager;
import com.gtnewhorizons.angelica.sdlgpu.texture.TextureStorage;
import java.util.HashSet;

/**
 * Access to the SDL backend's internals for the hosted backend and its subsystems, which live in {@link Subsystems}.
 * Upstream members it needs are widened in place rather than reached by reflection.
 *
 * <p>Every accessor requires the hosted backend and the thread that owns the SDL context.
 */
public final class Upstream {
    private Upstream() {}

    public static PipelineApplier pipeline() {
        return upstream().pipelineApplier;
    }

    public static DrawDispatch drawDispatch() {
        return upstream().drawDispatch;
    }

    /** The pinned readback path selects its object from the execution mirror, not the logical target bank. */
    public static void withReadbackTexture(int texture, Runnable operation) {
        var state = context();
        int unit = state.activeTextureUnit;
        int previous = state.boundTextures[unit];
        try {
            state.boundTextures[unit] = texture;
            operation.run();
        } finally {
            state.boundTextures[unit] = previous;
        }
    }

    /** GLSM's draw lock as a try-with-resources scope; a nested scope leaves release to the one that acquired it. */
    public enum DrawLock implements AutoCloseable {
        ACQUIRED,
        NESTED;

        @Override
        public void close() {
            if (this == ACQUIRED) GLStateManager.releaseDrawLock();
        }
    }

    // Draw paths take this per call, so the scope is a constant rather than a captured action.
    public static DrawLock drawLock() {
        ownedBackend();
        return GLStateManager.acquireDrawLock() ? DrawLock.ACQUIRED : DrawLock.NESTED;
    }

    public static ContextState context() {
        backend();
        var context = SDLGPURenderBackend.s();
        if (context.owner != Thread.currentThread()) {
            throw new IllegalStateException("SDL context state accessed from another thread");
        }
        return context;
    }

    public static ShaderManager shaders() {
        return upstream().shaderManager;
    }

    public static void detachShader(int program, int shader) {
        var manager = shaders();
        var object = manager.getProgram(program);
        if (object.vertexShader == shader) object.vertexShader = 0;
        else if (object.fragmentShader == shader) object.fragmentShader = 0;
        else if (object.geometryShader == shader) object.geometryShader = 0;
        else if (object.computeShader == shader) object.computeShader = 0;
        else throw new IllegalStateException("Internal shader is not attached to its program");
        manager.releaseShaderAttachment(shader);
    }

    public static ResourceManager resources() {
        return upstream().resourceManager;
    }

    public static FrameManager frames() {
        return upstream().frameManager;
    }

    public static FBOClearTracker clears() {
        return upstream().fboClearTracker;
    }

    public static AttachmentClear attachmentClear() {
        return upstream().attachmentClear;
    }

    public static void aliasTexture(int name, int original) {
        var resources = resources();
        resources.wLock.lock();
        try {
            if (resources.textureHandles.containsKey(name))
                throw new IllegalArgumentException("Destination texture already allocated");
            resources.textureHandles.put(name, resources.textureHandles.get(original));
            resources.textureMetas.put(name, resources.textureMetas.get(original));
        } finally {
            resources.wLock.unlock();
            TextureStorage.changed();
        }
    }

    public static void registerTextureView(int name, long handle, ResourceManager.TextureMeta meta) {
        var resources = resources();
        resources.wLock.lock();
        try {
            if (resources.textureHandles.containsKey(name))
                throw new IllegalArgumentException("Destination texture already allocated");
            resources.textureHandles.put(name, handle);
            resources.textureMetas.put(name, meta);
            resources.trackTextureHandle(handle);
        } finally {
            resources.wLock.unlock();
            TextureStorage.changed();
        }
    }

    public static boolean textureReferenced(ResourceManager resources, long handle) {
        resources.wLock.lock();
        try {
            return resources.textureHandles.containsValue(handle);
        } finally {
            resources.wLock.unlock();
        }
    }

    public static void deduplicateTextures() {
        var resources = resources();
        resources.wLock.lock();
        try {
            var seen = new HashSet<Long>();
            var iterator = resources.textureHandles.int2LongEntrySet().iterator();
            while (iterator.hasNext()) if (!seen.add(iterator.next().getLongValue())) iterator.remove();
        } finally {
            resources.wLock.unlock();
            TextureStorage.changed();
        }
    }

    /**
     * Ends any open render or copy pass, after flushing queued compute and clears, and returns the command buffer the
     * next pass will record into. Timestamps and occlusion query boundaries must sit between passes.
     */
    public static long commandBufferOutsidePasses() {
        ownedBackend().endComputeDispatchBatch();
        var state = context();
        var manager = frames();
        clears().materializeAllPendingClears(state);
        if (state.pendingSwapchainClear || state.pendingSwapchainDepthClear || state.pendingSwapchainStencilClear)
            manager.ensureFbo0RenderPass(manager.frame(), state);
        manager.endRenderPassIfActive(manager.frame());
        manager.endCopyPassIfActive();
        return manager.getCommandBuffer();
    }

    public static void submitFenceCommands(long sync) {
        var backend = upstream();
        backend.midFrameFenceFlush();
        if (backend.fenceTracker.fenceMap.get(sync) == 0)
            throw new IllegalStateException("SDL submission did not acquire a GPU fence");
    }

    public static void submitPendingCommands() {
        upstream().flushAndSubmitMidFrame();
    }

    /** Named and floating-point setters must also update GLSM's integer cache and allocation hint. */
    public static void textureMipParameter(int name, int parameter, int value) {
        int target = Subsystems.current().textureObjects().target(name);
        GLSMTextures.updateTexParameteriCache(target, name, parameter, value);
        if (parameter == GL_TEXTURE_MAX_LEVEL) resources().getOrCreateTexSamplerState(name).maxLevel = value;
    }

    public static void flushTextureReferences() {
        resources().beforeTextureReplaced();
    }

    /** The hosted backend as Angelica's type, for upstream calls made on the hosted backend's behalf. */
    public static SDLGPURenderBackend renderBackend() {
        return ownedBackend();
    }

    // Package-private upstream members are reachable only through the upstream type, not the hosted subclass.
    private static SDLGPURenderBackend upstream() {
        return ownedBackend();
    }

    public static HostedBackend ownedBackend() {
        context();
        return backend();
    }

    private static HostedBackend backend() {
        if (!(BackendManager.RENDER_BACKEND instanceof HostedBackend backend)) {
            throw new IllegalStateException("Expected the hosted SDL backend");
        }
        return backend;
    }
}
