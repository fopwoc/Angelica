# Julia's typed texel fetches use integer textures on its extended Metal/Vulkan backends.
# Keep SDL's stock restriction for other backends and retain the per-format device support checks below it.
set(gpu_api "${sdl_patched}/src/gpu/SDL_gpu.c")
file(READ "${gpu_api}" source)
julia_replace([=[        if (IsIntegerFormat(createinfo->format) && (createinfo->usage & SDL_GPU_TEXTUREUSAGE_SAMPLER)) {]=]
    [=[        if (IsIntegerFormat(createinfo->format) && (createinfo->usage & SDL_GPU_TEXTUREUSAGE_SAMPLER) &&
            SDL_strcmp(SDL_GetGPUDeviceDriver(device), "metal") != 0 &&
            SDL_strcmp(SDL_GetGPUDeviceDriver(device), "vulkan") != 0) {]=] 1)
file(WRITE "${gpu_api}" "${source}")
