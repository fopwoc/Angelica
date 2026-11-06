package com.gtnewhorizons.angelica.sdlgpu.shader;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.sdl.SDLGPU.*;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_glsl_fragment_shader;

import com.gtnewhorizons.angelica.glsm.shader.SpirvCompiler;
import com.gtnewhorizons.angelica.sdlgpu.pipeline.EncodedColorBlit;
import org.junit.jupiter.api.Test;

class EncodedColorBlitShaderTest {
    @Test
    void shaderCompilesThroughTheRendererCompiler() {
        var compiled = SpirvCompiler.compile(
                EncodedColorBlit.FRAGMENT,
                shaderc_glsl_fragment_shader,
                "encoded-blit",
                SpirvCompiler.Options.vulkanForced460Core(),
                false);
        assertNotNull(compiled.spirv(), compiled.error());
    }

    @Test
    void onlyBlitsThatWouldLoseTheEncodingNeedIt() {
        assertTrue(EncodedColorBlit.required(
                SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM_SRGB, SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM));
        assertTrue(EncodedColorBlit.required(
                SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM_SRGB, SDL_GPU_TEXTUREFORMAT_B8G8R8A8_UNORM));
        assertFalse(EncodedColorBlit.required(
                SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM_SRGB, SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM_SRGB));
        assertFalse(EncodedColorBlit.required(
                SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM, SDL_GPU_TEXTUREFORMAT_R8G8B8A8_UNORM_SRGB));
    }
}
