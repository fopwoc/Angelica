package com.gtnewhorizons.angelica.glsm;

final class GLErrorReporter {

    private GLErrorReporter() {}

    static void reportError(int error) {
        throw new IllegalStateException("OpenGL error: 0x" + Integer.toHexString(error));
    }
}
