package com.gtnewhorizons.angelica.glsm.shader;

import org.jetbrains.annotations.Nullable;

/** Image loads become samplerless fetches without changing the texel's scalar type. */
final class ReadOnlyImages {
    private ReadOnlyImages() {}

    static @Nullable String textureType(String imageType) {
        return switch (imageType) {
            case "image1D", "image2D", "image3D" -> "texture" + imageType.substring("image".length());
            case "iimage1D", "iimage2D", "iimage3D" -> "itexture" + imageType.substring("iimage".length());
            case "uimage1D", "uimage2D", "uimage3D" -> "utexture" + imageType.substring("uimage".length());
            default -> null;
        };
    }
}
