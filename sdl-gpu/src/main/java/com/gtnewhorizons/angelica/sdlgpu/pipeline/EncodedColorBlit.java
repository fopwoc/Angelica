package com.gtnewhorizons.angelica.sdlgpu.pipeline;

import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import com.gtnewhorizons.angelica.sdlgpu.frame.FrameManager;
import com.gtnewhorizons.angelica.sdlgpu.resource.ResourceManager;
import java.nio.ByteBuffer;
import java.util.function.LongSupplier;
import org.lwjgl.opengl.GL11;
import org.lwjgl.sdl.SDL_GPUColorTargetDescription;
import org.lwjgl.sdl.SDL_GPUColorTargetInfo;
import org.lwjgl.sdl.SDL_GPUDepthStencilState;
import org.lwjgl.sdl.SDL_GPUTextureSamplerBinding;
import org.lwjgl.system.MemoryStack;

import static org.lwjgl.sdl.SDLGPU.*;

/**
 * Blits an sRGB color attachment into a non-sRGB one without losing its encoding.
 *
 * <p>GL leaves stored bytes alone here while {@code GL_FRAMEBUFFER_SRGB} is off, and mods rely on it to present their
 * encoded output. {@code SDL_BlitGPUTexture} samples the source, which decodes it, and the non-sRGB target does not
 * encode it back, so the picture would reach the screen as linear values and look far too dark. Re-encoding here gives
 * the same bytes on every GPU backend, whereas reinterpreting the texture would need a format-changing native view.
 */
public final class EncodedColorBlit {
    public static final String FRAGMENT = """
            #version 460 core
            uniform sampler2D source;
            uniform vec4 region;
            uniform vec4 target;
            uniform vec4 sourceLevel;
            layout(location = 0) out vec4 encoded;
            void main() {
                vec2 uv = region.xy + (gl_FragCoord.xy - target.xy) * target.zw * region.zw;
                vec4 sampled = textureLod(source, uv, sourceLevel.x);
                vec3 low = sampled.rgb * 12.92;
                vec3 high = 1.055 * pow(max(sampled.rgb, vec3(0.0)), vec3(1.0 / 2.4)) - 0.055;
                encoded = vec4(mix(high, low, lessThanEqual(sampled.rgb, vec3(0.0031308))), sampled.a);
            }
            """;
    private static final int UNIFORM_BYTES = 48;
    private static final long KEY_SEED = 0x3C6EF372FE94F82BL;

    private final FrameManager frameManager;
    private final ResourceManager resourceManager;
    private final PipelineStore pipelineStore;
    private final FullscreenPass fullscreen;
    private final FullscreenPass.Fragment fragment;
    private final LongSupplier builder = this::createPipeline;
    private int buildFormat;

    public EncodedColorBlit(
            FrameManager frameManager,
            ResourceManager resourceManager,
            PipelineStore pipelineStore,
            FullscreenPass fullscreen) {
        this.frameManager = frameManager;
        this.resourceManager = resourceManager;
        this.pipelineStore = pipelineStore;
        this.fullscreen = fullscreen;
        fragment = fullscreen.fragment("EncodedColorBlit", UNIFORM_BYTES, () -> FRAGMENT);
    }

    public static boolean required(int sourceFormat, int targetFormat) {
        return encoded(sourceFormat) && !encoded(targetFormat);
    }

    private static boolean encoded(int format) {
        return format == SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM_SRGB
                || format == SDL_GPU_TEXTUREFORMAT_B8G8R8A8_UNORM_SRGB;
    }

    /** {@code rect} holds the clipped source then destination rectangles, each as x, y, width, height. */
    public void blit(
            ContextState st,
            long source,
            int sourceLevel,
            int sourceWidth,
            int sourceHeight,
            int[] rect,
            boolean flipH,
            boolean flipV,
            long target,
            int targetLevel,
            int targetFormat,
            int targetWidth,
            int targetHeight,
            int glFilter) {
        final FrameManager.FrameState frame = frameManager.frame();
        if (!frame.frameActive) return;
        final long sampler = glFilter == GL11.GL_LINEAR ? fullscreen.getLinearSampler() : fullscreen.getNearestSampler();
        final long pipeline = pipelineFor(targetFormat);
        if (pipeline == 0L || pipeline == PipelineStore.BAD_PIPELINE_SENTINEL || sampler == 0L)
            throw new IllegalStateException("Cannot create the sRGB-preserving blit");

        frameManager.endCopyPassIfActive();
        frameManager.endRenderPassIfActive(FrameManager.PASS_END_COPY);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final SDL_GPUColorTargetInfo.Buffer color = SDL_GPUColorTargetInfo.calloc(1, stack);
            color.get(0).texture(target).mip_level(targetLevel).load_op(SDL_GPU_LOADOP_LOAD).store_op(SDL_GPU_STOREOP_STORE);
            final long pass = frameManager.beginRenderPass(color, null);
            SDL_BindGPUGraphicsPipeline(pass, pipeline);
            FullscreenPass.setViewport(pass, st, targetWidth, targetHeight, 0f);
            FullscreenPass.setScissor(pass, st, rect[4], rect[5], rect[6], rect[7]);
            final SDL_GPUTextureSamplerBinding.Buffer binding = SDL_GPUTextureSamplerBinding.calloc(1, stack);
            binding.get(0).texture(source).sampler(sampler);
            SDL_BindGPUFragmentSamplers(pass, 0, binding);
            final ByteBuffer uniforms = stack.calloc(UNIFORM_BYTES);
            uniforms.putFloat(0, (flipH ? rect[0] + rect[2] : rect[0]) / (float) sourceWidth);
            uniforms.putFloat(4, (flipV ? rect[1] + rect[3] : rect[1]) / (float) sourceHeight);
            uniforms.putFloat(8, (flipH ? -rect[2] : rect[2]) / (float) sourceWidth);
            uniforms.putFloat(12, (flipV ? -rect[3] : rect[3]) / (float) sourceHeight);
            uniforms.putFloat(16, rect[4]);
            uniforms.putFloat(20, rect[5]);
            uniforms.putFloat(24, 1f / rect[6]);
            uniforms.putFloat(28, 1f / rect[7]);
            uniforms.putFloat(32, sourceLevel);
            SDL_PushGPUFragmentUniformData(frameManager.getCommandBuffer(frame), 0, uniforms);
            SDL_DrawGPUPrimitives(pass, 3, 1, 0, 0);
            frameManager.endRenderPassIfActive(frame, FrameManager.PASS_END_COPY);
        }
        resourceManager.markTextureWritten(target);
        FullscreenPass.markStateClobbered(st);
        st.lastPushedProgramFs = -1;
    }

    private long pipelineFor(int targetFormat) {
        if (fragment.shader() == 0L) return 0L;
        buildFormat = targetFormat;
        return pipelineStore.getOrBuild(Hashing.fmix64(KEY_SEED, targetFormat), builder);
    }

    private long createPipeline() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final SDL_GPUColorTargetDescription.Buffer color = SDL_GPUColorTargetDescription.calloc(1, stack);
            color.get(0).format(buildFormat).blend_state().enable_blend(false);
            final SDL_GPUDepthStencilState depthStencil =
                    SDL_GPUDepthStencilState.calloc(stack).compare_op(SDL_GPU_COMPAREOP_ALWAYS);
            return fullscreen.createPipeline(
                    pipelineStore, stack, fragment.shader(), color, 1, depthStencil, 0, "EncodedColorBlit");
        }
    }
}
