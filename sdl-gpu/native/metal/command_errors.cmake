# Julia extension: Metal's later timestamp failure can be an innocent victim of an earlier GPU fault.
file(READ "${backend}" source)
julia_replace("#include \"../SDL_sysgpu.h\"" "#include \"../SDL_sysgpu.h\"\n#include \"gpu_errors.m\"" 1)
julia_replace("commandBuffer->handle = [renderer->queue commandBuffer];"
    "commandBuffer->handle = JULIA_METAL_CommandBuffer(renderer->queue, renderer->debugMode);" 1)
julia_replace("[metalCommandBuffer->handle addCompletedHandler:^(id<MTLCommandBuffer> buffer) {\n          SDL_AtomicIncRef"
    "[metalCommandBuffer->handle addCompletedHandler:^(id<MTLCommandBuffer> buffer) {\n          JULIA_METAL_CommandError(buffer);\n          SDL_AtomicIncRef" 1)
julia_replace("SDL_setenv_unsafe(\"MTL_DEBUG_LAYER\", \"1\", 0);"
    "SDL_setenv_unsafe(\"MTL_DEBUG_LAYER\", \"1\", 0);\n            SDL_setenv_unsafe(\"MTL_SHADER_VALIDATION\", \"1\", 0);\n            SDL_setenv_unsafe(\"MTL_SHADER_VALIDATION_REPORT_TO_STDERR\", \"1\", 0);" 1)
file(WRITE "${backend}" "${source}")
