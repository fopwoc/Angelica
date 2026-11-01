package com.gtnewhorizons.angelica.sdlgpu;

import com.gtnewhorizons.angelica.glsm.backend.BackendManager;
import com.gtnewhorizons.angelica.sdlgpu.device.Device;
import com.gtnewhorizons.angelica.sdlgpu.shader.ShaderManager;
import org.taumc.glsl.grammar.GLSLParser;

/** Shared device and shader prewarming for an application-owned window lifecycle. */
public final class SDLGPUGate {
    private static Device device;

    private SDLGPUGate() {}

    public static synchronized Device device() {
        if (device == null) device = new Device();
        return device;
    }

    public static boolean isEngaged() {
        return device().getDevice() != 0;
    }

    public static boolean isActive() {
        return BackendManager.RENDER_BACKEND instanceof SDLGPURenderBackend;
    }

    public static void ensureDrawableInstalled() {
        if (isActive()) SDLGPUDisplayBridge.ensureDrawableInstalled();
    }

    public static void prewarmSpirv(String source, int shaderType) {
        if (isActive()) ShaderManager.prewarmSpirv(source, shaderType);
    }

    public static void prewarmSpirv(String source, GLSLParser.Translation_unitContext bodyTree,
            int headerLen, int shaderType) {
        if (isActive()) ShaderManager.prewarmSpirv(source, bodyTree, headerLen, shaderType);
    }

    public static void clearShaderPrewarmCache() {
        if (isActive()) ShaderManager.clearPrewarmCache();
    }
}
