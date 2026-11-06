package com.gtnewhorizons.angelica.sdlgpu.diagnostic;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** An opt-in diagnostic stop before a selected compiler input can reach GPU execution. */
final class ComputeCheckpoint {
    private final String fingerprint;
    private final Map<String, Boolean> matches = new HashMap<>();

    ComputeCheckpoint(String fingerprint) {
        this.fingerprint = fingerprint.toLowerCase(Locale.ROOT);
        if (!fingerprint.isEmpty() && !fingerprint.matches("[a-fA-F0-9]{64}"))
            throw new IllegalArgumentException("angelica.debug.stopBeforeCompute requires a shader SHA-256");
    }

    boolean enabled() { return !fingerprint.isEmpty(); }

    void beforeDispatch(int program, String source, Runnable snapshot) {
        if (!enabled() || source == null || !matches.computeIfAbsent(source, s -> TraceShaders.fingerprint(s).equals(fingerprint)))
            return;
        snapshot.run();
        throw new IllegalStateException("Diagnostic stop before compute dispatch: program=" + program
                + " shader=" + fingerprint + "; selected dispatch was not submitted");
    }
}
