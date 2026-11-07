package com.gtnewhorizons.angelica.glsm.diagnostic;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

/**
 * Opt-in record of GL calls as they enter GLSM, with the code that made them. It sits above every path that can drop a
 * call, so it answers what the draw trace cannot: whether a call was ever made, by whom, and in what state.
 *
 * <p>Callers check {@link #ENABLED} first, which keeps a normal run free of the stack walks this does per event.
 */
public final class GlEventTrace {
    public static final boolean ENABLED = Boolean.getBoolean("angelica.debug.glEvents");

    private static final int LINE_LIMIT = 65536;
    private static final int CALLER_DEPTH = 3;
    private static final String RENDERER_PACKAGE = "com.gtnewhorizons.angelica.";
    private static final StackWalker STACK = StackWalker.getInstance();

    private static BufferedWriter writer;
    private static long frame;
    private static int lines;

    private GlEventTrace() {}

    /** Starts a fresh capture, replacing the previous one in {@code file}. */
    public static synchronized void open(Path file, long startFrame) {
        close();
        try {
            writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException("Cannot open GL event trace", failure);
        }
        frame = startFrame;
        lines = 0;
    }

    public static synchronized void close() {
        if (writer == null) return;
        try {
            writer.close();
        } catch (IOException failure) {
            throw new UncheckedIOException("Cannot close GL event trace", failure);
        } finally {
            writer = null;
        }
    }

    public static synchronized void frame(long index) {
        frame = index;
        if (writer == null) return;
        try {
            // Lets a live session be read while it is still running.
            writer.flush();
        } catch (IOException failure) {
            throw new UncheckedIOException("Cannot flush GL event trace", failure);
        }
    }

    /** {@code discard} or {@code feedback} set, or {@code recording=COMPILE}, means GLSM drops this clear. */
    public static void clear(int mask, int framebuffer, Object recording, boolean discard, boolean feedback) {
        write("clear mask=" + Integer.toHexString(mask) + " fbo=" + framebuffer + " recording=" + recording + " discard="
                + discard + " feedback=" + feedback);
    }

    /** {@code changed=false} means the binding already matched, so the backend never hears about this call. */
    public static void bindFramebuffer(int target, int framebuffer, boolean changed) {
        write("bind target=" + Integer.toHexString(target) + " fbo=" + framebuffer + " changed=" + changed);
    }

    public static void clearColor(float red, float green, float blue, float alpha) {
        write("clearColor " + red + "," + green + "," + blue + "," + alpha);
    }

    public static void rasterizerDiscard(boolean enabled) {
        write("discard enabled=" + enabled);
    }

    private static synchronized void write(String event) {
        if (writer == null || lines >= LINE_LIMIT) return;
        lines++;
        try {
            writer.write("frame=" + frame + " thread=" + Thread.currentThread().getName() + " " + event + " caller="
                    + caller() + "\n");
        } catch (IOException failure) {
            throw new UncheckedIOException("Cannot write GL event trace", failure);
        }
    }

    private static String caller() {
        return STACK.walk(frames -> frames.filter(frame -> !frame.getClassName().startsWith(RENDERER_PACKAGE))
                .limit(CALLER_DEPTH)
                .map(frame -> frame.getClassName() + "." + frame.getMethodName() + ":" + frame.getLineNumber())
                .collect(Collectors.joining("<")));
    }
}
