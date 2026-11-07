package com.gtnewhorizons.angelica.sdlgpu.diagnostic;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FrameCountersTest {
    private static final String LABEL = "angelica.debug.counters.label";

    @AfterEach
    void clear() {
        System.clearProperty(LABEL);
        FrameCounters.close();
    }

    @Test
    void totalsPerFrameOnlyWhileALabelIsSet(@TempDir Path output) throws Exception {
        FrameCounters.open(output);
        FrameCounters.DRAWS.add(1000);
        FrameCounters.endFrame();

        System.setProperty(LABEL, "town");
        FrameCounters.endFrame();
        for (int frame = 0; frame < 4; frame++) {
            FrameCounters.DRAWS.add(10);
            FrameCounters.PIPELINE_BINDS.add(2);
            FrameCounters.endFrame();
        }
        System.clearProperty(LABEL);
        FrameCounters.endFrame();

        var line = Files.readString(output.resolve("counters.txt")).strip();
        assertTrue(line.startsWith("label=town frames=4 draws=10.0 pipelineBinds=2.0"), line);
    }
}
