package com.gtnewhorizons.angelica.sdlgpu.vertex;

import com.gtnewhorizons.angelica.sdlgpu.resource.PersistentMapping;
import java.nio.ByteBuffer;
import org.lwjgl.opengl.GL44;

/**
 * GL makes writes through a coherent mapping visible to the next command with no flush, but those writes only reach this
 * backend's CPU staging copy, so the GPU buffer still holds stale commands. Reading records here keeps that ordering
 * without ending the render pass to upload before every indirect draw.
 */
public final class CoherentIndirect {
    private CoherentIndirect() {}

    /** Commands at buffer offset zero of the returned view, or null when the GPU copy is already authoritative. */
    public static ByteBuffer view(PersistentMapping mapping, long offset, long bytes) {
        if (mapping == null || (mapping.accessFlags & GL44.GL_MAP_COHERENT_BIT) == 0) return null;
        if (!mapping.covers(offset, bytes))
            throw new IllegalArgumentException("Indirect commands extend outside the coherent mapping");
        return mapping.staging
                .duplicate()
                .order(mapping.staging.order())
                .position((int) mapping.stagingIndex(offset))
                .slice()
                .order(mapping.staging.order());
    }
}
