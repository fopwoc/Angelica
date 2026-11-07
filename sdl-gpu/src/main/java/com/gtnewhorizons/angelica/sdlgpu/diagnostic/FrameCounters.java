package com.gtnewhorizons.angelica.sdlgpu.diagnostic;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.concurrent.atomic.LongAdder;

/**
 * Opt-in counts of what each frame asks of the GPU, totalled per label for as long as a tool names one in {@code
 * angelica.debug.counters.label}. Call sites check {@link #ENABLED} first, so a normal run pays nothing per draw.
 */
public final class FrameCounters {
    public static final boolean ENABLED = Boolean.getBoolean("angelica.debug.counters");
    private static final String LABEL = "angelica.debug.counters.label";

    // Uploads can come from worker threads; the rest come from the render thread but share the same simple type.
    public static final LongAdder DRAWS = new LongAdder();
    public static final LongAdder PIPELINE_BINDS = new LongAdder();
    public static final LongAdder RENDER_PASSES = new LongAdder();
    public static final LongAdder COPY_PASSES = new LongAdder();
    public static final LongAdder UPLOADS = new LongAdder();

    private static Path file;
    private static String label;
    private static long frames;

    private FrameCounters() {}

    public static void open(Path output) {
        file = output.resolve("counters.txt");
    }

    /** Runs on the render thread at each frame boundary. */
    public static void endFrame() {
        String current = System.getProperty(LABEL);
        if (current == null ? label == null : current.equals(label)) {
            frames++;
            return;
        }
        if (label != null) write();
        label = current;
        reset();
    }

    public static void close() {
        if (label != null) write();
        label = null;
    }

    private static void reset() {
        frames = 0;
        for (var counter : new LongAdder[] {DRAWS, PIPELINE_BINDS, RENDER_PASSES, COPY_PASSES, UPLOADS}) counter.reset();
    }

    private static void write() {
        if (file == null || frames == 0) return;
        double perFrame = 1.0 / frames;
        var line = String.format(
                Locale.ROOT,
                "label=%s frames=%d draws=%.1f pipelineBinds=%.1f renderPasses=%.1f copyPasses=%.1f uploads=%.1f%n",
                label,
                frames,
                DRAWS.sum() * perFrame,
                PIPELINE_BINDS.sum() * perFrame,
                RENDER_PASSES.sum() * perFrame,
                COPY_PASSES.sum() * perFrame,
                UPLOADS.sum() * perFrame);
        try {
            Files.writeString(file, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException failure) {
            throw new UncheckedIOException("Cannot write frame counters", failure);
        }
    }
}
