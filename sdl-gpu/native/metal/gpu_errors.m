/* Julia extension: retain the original GPU failure instead of only a later failed timestamp. */
static id<MTLCommandBuffer> JULIA_METAL_CommandBuffer(id<MTLCommandQueue> queue, bool debug)
{
    if (!debug) return [queue commandBuffer];
    MTLCommandBufferDescriptor *descriptor = [MTLCommandBufferDescriptor new];
    descriptor.errorOptions = MTLCommandBufferErrorOptionEncoderExecutionStatus;
    id<MTLCommandBuffer> command = [queue commandBufferWithDescriptor:descriptor];
    command.label = [NSString stringWithFormat:@"Julia command %llu", (unsigned long long)SDL_GetTicksNS()];
    return command;
}

static void JULIA_METAL_CommandError(id<MTLCommandBuffer> command)
{
    NSError *error = command.error;
    if (!error) return;
    SDL_LogError(SDL_LOG_CATEGORY_GPU, "Metal command failed: label=%s domain=%s code=%ld description=%s details=%s",
        command.label.UTF8String ?: "unlabelled", error.domain.UTF8String, (long)error.code,
        error.localizedDescription.UTF8String, error.userInfo.description.UTF8String);
    NSArray<id<MTLCommandBufferEncoderInfo>> *encoders = error.userInfo[MTLCommandBufferEncoderInfoErrorKey];
    for (id<MTLCommandBufferEncoderInfo> encoder in encoders) {
        SDL_LogError(SDL_LOG_CATEGORY_GPU, "Metal encoder: label=%s state=%ld signposts=%s",
            encoder.label.UTF8String ?: "unlabelled", (long)encoder.errorState,
            encoder.debugSignposts.description.UTF8String);
    }
}
