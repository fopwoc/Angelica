# Default GL blocks can use 64 KiB. SDL's 32 KiB Metal pool cannot hold those uploads.
file(READ "${backend}" source)
julia_replace("UNIFORM_BUFFER_SIZE" "(65536 * 8)" 3)
julia_replace("    MetalUniformBuffer *metalUniformBuffer;\n    Uint32 alignedDataLength;" [=[    MetalUniformBuffer *metalUniformBuffer;
    Uint32 alignedDataLength;
    if (length > 65536) {
        SDL_LogCritical(SDL_LOG_CATEGORY_GPU,
            "Uniform upload exceeds Metal block limit: %u bytes, limit 65536", length);
        abort();
    }]=] 1)
file(WRITE "${backend}" "${source}")
