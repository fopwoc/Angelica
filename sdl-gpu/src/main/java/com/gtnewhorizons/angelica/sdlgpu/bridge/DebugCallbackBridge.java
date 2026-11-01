package com.gtnewhorizons.angelica.sdlgpu.bridge;

import com.gtnewhorizons.angelica.glsm.GLSMDraws;
import com.gtnewhorizons.angelica.sdlgpu.Upstream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;

/** Registers a host-owned Java callback in the SDL validation relay, without allocating a native closure. */
public final class DebugCallbackBridge {
    /** GL's debug callback with the user parameter last, as the host binds the application's callback. */
    public static final MethodType TYPE = MethodType.methodType(
            void.class, int.class, int.class, int.class, int.class, int.class, long.class, long.class);

    private DebugCallbackBridge() {}

    public static void glDebugMessageCallback(MethodHandle callback, long userParam) {
        Upstream.context();
        if (callback != null && !callback.type().equals(TYPE)) {
            throw new IllegalArgumentException("Invalid debug callback signature: " + callback.type());
        }
        GLSMDraws.registerDebugMessageListener(
                callback == null
                        ? null
                        : (source, type, id, severity, length, message, parameter) -> {
                            try {
                                callback.invokeExact(source, type, id, severity, length, message, userParam);
                            } catch (RuntimeException | Error failure) {
                                throw failure;
                            } catch (Throwable failure) {
                                throw new IllegalStateException("Debug callback failed", failure);
                            }
                        },
                userParam);
    }
}
