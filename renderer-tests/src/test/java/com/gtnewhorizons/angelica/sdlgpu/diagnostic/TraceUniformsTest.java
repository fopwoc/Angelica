package com.gtnewhorizons.angelica.sdlgpu.diagnostic;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.util.spvc.Spvc.SPVC_BASETYPE_FP32;

import com.gtnewhorizons.angelica.sdlgpu.shader.ShaderManager.UniformMemberInfo;
import java.nio.FloatBuffer;
import org.junit.jupiter.api.Test;

class TraceUniformsTest {
    @Test
    void reportsAllMatrixColumnsWithoutStd140Padding() {
        var buffer = FloatBuffer.allocate(20);
        for (int i = 0; i < buffer.capacity(); i++) buffer.put(i, 99);
        for (int c = 0; c < 3; c++) for (int r = 0; r < 3; r++) buffer.put(4 + c * 4 + r, c * 3 + r + 1);
        var member = new UniformMemberInfo(16, 48, 0, false, 3, 3, SPVC_BASETYPE_FP32, 0);
        assertEquals(
                "[1.0,2.0,3.0,4.0,5.0,6.0,7.0,8.0,9.0]@16",
                TraceUniforms.packed(buffer, new UniformMemberInfo[] {member}, 0));
    }

    @Test
    void reportsArrayElementsAtTheirReflectedStrideAndBoundsTheRead() {
        var buffer = FloatBuffer.allocate(12);
        buffer.put(0, .25f).put(4, .5f).put(8, .75f);
        var member = new UniformMemberInfo(0, 48, 16, false, 1, 1, SPVC_BASETYPE_FP32, 3);
        assertEquals("[0.25,0.5,0.75]@0", TraceUniforms.packed(buffer, new UniformMemberInfo[] {member}, 0));
        assertEquals(
                "out-of-bounds@0", TraceUniforms.packed(FloatBuffer.allocate(1), new UniformMemberInfo[] {member}, 0));
    }
}
