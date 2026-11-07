package com.gtnewhorizons.angelica.glsm.diagnostic;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GlEventTraceTest {
    @TempDir
    Path directory;

    @AfterEach
    void close() {
        GlEventTrace.close();
    }

    @Test
    void recordsWhatWouldDropACallAndWhoMadeIt() throws Exception {
        var file = directory.resolve("events.txt");
        GlEventTrace.open(file, 7);
        GlEventTrace.clear(0x4100, 37, "NONE", true, false);
        GlEventTrace.frame(8);
        GlEventTrace.bindFramebuffer(0x8D40, 37, false);
        GlEventTrace.close();

        var lines = Files.readAllLines(file);
        assertEquals(2, lines.size());
        assertTrue(lines.get(0).startsWith("frame=7 "), lines.get(0));
        assertTrue(lines.get(0).contains("clear mask=4100 fbo=37 recording=NONE discard=true"), lines.get(0));
        // This test is itself in the renderer's package, so its frames must be skipped like the renderer's own.
        assertTrue(lines.get(0).matches(".* caller=\\S+$"), lines.get(0));
        assertFalse(lines.get(0).contains("com.gtnewhorizons"), lines.get(0));
        assertTrue(lines.get(1).startsWith("frame=8 "), lines.get(1));
        assertTrue(lines.get(1).contains("bind target=8d40 fbo=37 changed=false"), lines.get(1));
    }

    @Test
    void reopeningReplacesTheCaptureAndClosingStopsIt() throws Exception {
        var file = directory.resolve("events.txt");
        GlEventTrace.open(file, 1);
        GlEventTrace.rasterizerDiscard(true);
        GlEventTrace.open(file, 2);
        GlEventTrace.rasterizerDiscard(false);
        GlEventTrace.close();
        GlEventTrace.rasterizerDiscard(true);

        var lines = Files.readAllLines(file);
        assertEquals(1, lines.size());
        assertTrue(lines.getFirst().contains("frame=2 "), lines.getFirst());
        assertTrue(lines.getFirst().contains("discard enabled=false"), lines.getFirst());
    }
}
