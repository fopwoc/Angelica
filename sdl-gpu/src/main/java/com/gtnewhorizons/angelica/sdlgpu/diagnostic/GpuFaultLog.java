package com.gtnewhorizons.angelica.sdlgpu.diagnostic;

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;
import static org.lwjgl.sdl.SDLLog.SDL_LOG_PRIORITY_ERROR;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Persists native GPU errors before a fatal assertion or collateral query error can terminate the process. */
public final class GpuFaultLog {
    private static final System.Logger LOG = System.getLogger(GpuFaultLog.class.getName());
    private static Path output;

    private GpuFaultLog() {}

    public static synchronized void configure(Path directory) {
        output = directory.resolve("gpu-errors.txt");
    }

    public static synchronized void record(int priority, String message) {
        if (output == null || priority < SDL_LOG_PRIORITY_ERROR) return;
        try {
            Files.writeString(output, message + "\n", CREATE, APPEND);
        } catch (IOException | SecurityException failure) {
            Path failed = output;
            output = null;
            LOG.log(System.Logger.Level.ERROR, "Cannot preserve GPU errors in " + failed, failure);
        }
    }
}
