package com.gtnewhorizons.angelica.sdlgpu.frame;

import static org.lwjgl.sdl.SDLGPU.*;

import com.gtnewhorizons.angelica.sdlgpu.SDLGPUGate;
import com.gtnewhorizons.angelica.sdlgpu.Upstream;
import com.gtnewhorizons.angelica.sdlgpu.util.Sdl;

/** Discards unfinished recordings before the renderer or its borrowed window is released. */
public final class FrameCommands {
    private FrameCommands() {}

    public static void abortFrame() {
        var frames = Upstream.frames();
        var frame = frames.frame();
        Upstream.renderBackend().endComputeDispatchBatch();
        frames.endRenderPassIfActive(frame);
        frames.endCopyPassIfActive();
        frame.commandBuffer = cancel(frame.commandBuffer, "frame");
        frame.frameActive = false;
    }

    public static void abortAndDrain() {
        abortFrame();
        Sdl.require(SDL_WaitForGPUIdle(SDLGPUGate.device().getDevice()), "Drain renderer");
    }

    /** Upload and arena recordings exist independently of the active frame and must also be cancelled. */
    public static void cancelPending() {
        abortFrame();
        var frame = Upstream.frames().frame();
        if (frame.arenaCopyPass != 0) {
            SDL_EndGPUCopyPass(frame.arenaCopyPass);
            frame.arenaCopyPass = 0;
        }
        frame.pendingUploadCommandBuffer = cancel(frame.pendingUploadCommandBuffer, "pending upload");
        frame.arenaCommandBuffer = cancel(frame.arenaCommandBuffer, "upload arena");
    }

    private static long cancel(long commands, String role) {
        if (commands != 0) Sdl.require(SDL_CancelGPUCommandBuffer(commands), "Cancel " + role + " commands");
        return 0;
    }
}
