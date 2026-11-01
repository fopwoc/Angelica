package com.gtnewhorizons.angelica.sdlgpu.memory;

import static org.junit.jupiter.api.Assertions.*;

import com.gtnewhorizon.gtnhlib.bytebuf.CheckIntrinsics;
import com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities;
import java.nio.ByteOrder;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

class MemoryUtilitiesTest {
    @Test
    void initializesBufferLayoutAndCreatesAliasingViewsOnTheCurrentJdk() {
        var allocation = MemoryUtil.memAlloc(32);
        try {
            long address = MemoryUtil.memAddress(allocation);
            var direct = CheckIntrinsics.NewDirectByteBuffer(address, 32);
            assertTrue(direct.isDirect());
            assertEquals(ByteOrder.nativeOrder(), direct.order());
            var bytes = MemoryUtilities.memByteBuffer(address, 32);
            bytes.putInt(4, 0x12345678);
            assertEquals(0x12345678, direct.getInt(4));
            bytes.position(4).limit(20).mark().position(12).reset();
            assertEquals(4, bytes.position());
            assertEquals(20, bytes.limit());
            assertEquals(address + 4, MemoryUtilities.memAddress(bytes));
            var floats = MemoryUtilities.memFloatBuffer(address + 8, 4);
            floats.put(1, 3.5f);
            assertEquals(3.5f, direct.getFloat(12));
            floats.position(1);
            assertEquals(address + 12, MemoryUtilities.memAddress(floats));
        } finally {
            MemoryUtil.memFree(allocation);
        }
    }
}
