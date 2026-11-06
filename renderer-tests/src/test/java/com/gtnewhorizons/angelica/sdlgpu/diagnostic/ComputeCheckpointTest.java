package com.gtnewhorizons.angelica.sdlgpu.diagnostic;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ComputeCheckpointTest {
    private static final String SOURCE = "#version 460\nvoid main() {}\n";

    @Test
    void writesTheCheckpointAndStopsBeforeTheMatchingSubmission() {
        var checkpoint = new ComputeCheckpoint(TraceShaders.fingerprint(SOURCE).toUpperCase());
        var snapshots = new AtomicInteger();
        var submitted = new AtomicInteger();
        var failure = assertThrows(IllegalStateException.class, () -> {
            checkpoint.beforeDispatch(17, SOURCE, snapshots::incrementAndGet);
            submitted.incrementAndGet();
        });
        assertEquals(1, snapshots.get());
        assertEquals(0, submitted.get());
        assertTrue(failure.getMessage().contains("program=17"));
        assertTrue(failure.getMessage().contains(TraceShaders.fingerprint(SOURCE)));
    }

    @Test
    void leavesUnrelatedAndDisabledDispatchesAlone() {
        var checkpoint = new ComputeCheckpoint(TraceShaders.fingerprint(SOURCE));
        Runnable unexpected = () -> fail("Unexpected checkpoint");
        checkpoint.beforeDispatch(1, SOURCE + " ", unexpected);
        checkpoint.beforeDispatch(1, null, unexpected);
        new ComputeCheckpoint("").beforeDispatch(1, SOURCE, unexpected);
        assertFalse(new ComputeCheckpoint("").enabled());
        assertThrows(IllegalArgumentException.class, () -> new ComputeCheckpoint("true"));
        assertThrows(IllegalArgumentException.class, () -> new ComputeCheckpoint("x".repeat(64)));
    }

    @Test
    void aSnapshotFailureCannotAllowSubmission() {
        var checkpoint = new ComputeCheckpoint(TraceShaders.fingerprint(SOURCE));
        var submitted = new AtomicInteger();
        assertThrows(UnsupportedOperationException.class, () -> {
            checkpoint.beforeDispatch(2, SOURCE, () -> {
                throw new UnsupportedOperationException("disk failure");
            });
            submitted.incrementAndGet();
        });
        assertEquals(0, submitted.get());
    }
}
