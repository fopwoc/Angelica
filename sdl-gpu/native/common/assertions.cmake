# Julia GPU failures are fatal, including in headless probes; never ask through SDL's assertion dialog.
set(gpu_api "${sdl_patched}/src/gpu/SDL_gpu.c")
file(READ "${gpu_api}" source)
julia_replace([=[SDL_GPUDevice *SDL_CreateGPUDeviceWithProperties(SDL_PropertiesID props)
{]=] [=[static SDL_AssertState SDLCALL JULIA_GPU_Assertion(const SDL_AssertData *data, void *userdata)
{
    (void)userdata;
    SDL_LogCritical(SDL_LOG_CATEGORY_GPU, "SDL GPU assertion: %s in %s (%s:%d)",
        data->condition, data->function, data->filename, data->linenum);
    return SDL_ASSERTION_ABORT;
}

SDL_GPUDevice *SDL_CreateGPUDeviceWithProperties(SDL_PropertiesID props)
{
    SDL_SetAssertionHandler(JULIA_GPU_Assertion, NULL);]=] 1)
file(WRITE "${gpu_api}" "${source}")
