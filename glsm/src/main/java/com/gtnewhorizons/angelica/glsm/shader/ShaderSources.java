package com.gtnewhorizons.angelica.glsm.shader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Loads the renderer's bundled shader resources independently of the host application. */
public final class ShaderSources {
    private ShaderSources() {}

    public static String getShaderSource(String identifier) {
        if (!identifier.matches("angelica:[a-zA-Z0-9_/.-]+") || identifier.contains("..")) {
            throw new IllegalArgumentException("Unsupported backend shader: " + identifier);
        }
        String path = "/assets/angelica/shaders/" + identifier.substring("angelica:".length());
        try (var stream = ShaderSources.class.getResourceAsStream(path)) {
            if (stream == null) throw new IllegalStateException("Missing bundled backend shader: " + identifier);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot read bundled backend shader: " + identifier, failure);
        }
    }
}
