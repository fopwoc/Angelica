package com.gtnewhorizons.angelica.sdlgpu.diagnostic;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.sdl.SDLLog.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GpuFaultLogTest {
    @TempDir
    Path directory;

    @Test
    void preservesErrorDetailsAndRoutesNewRunsWithoutWritingForWarnings() throws Exception {
        GpuFaultLog.configure(directory);
        var report = directory.resolve("gpu-errors.txt");
        GpuFaultLog.record(SDL_LOG_PRIORITY_WARN, "warning");
        assertFalse(Files.exists(report));
        GpuFaultLog.record(SDL_LOG_PRIORITY_ERROR, "first fault\nencoder=geometry");
        GpuFaultLog.record(SDL_LOG_PRIORITY_CRITICAL, "SDL assertion: bounds");
        assertEquals("first fault\nencoder=geometry\nSDL assertion: bounds\n", Files.readString(report));
        var next = Files.createDirectory(directory.resolve("next"));
        GpuFaultLog.configure(next);
        GpuFaultLog.record(SDL_LOG_PRIORITY_ERROR, "second run");
        assertEquals("second run\n", Files.readString(next.resolve("gpu-errors.txt")));
        assertFalse(Files.readString(report).contains("second run"));
    }
}
