package com.gtnewhorizons.angelica.sdlgpu.geometry;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.Test;

class GeometryIndirectTest {
    @Test
    void decodesPaddedCommandsFromTheCurrentPositionWithoutChangingTheInput() {
        var input = ByteBuffer.allocate(64).order(ByteOrder.nativeOrder());
        input.position(4);
        input.putInt(4, 6).putInt(8, 3).putInt(12, 12).putInt(16, 2);
        input.putInt(36, 3).putInt(40, 4).putInt(44, 18).putInt(48, -1);
        var draws = GeometryIndirect.decode(input, 2, 32);
        assertEquals(new GeometryDraw(12, 6, 0, 0, 0, 3, 2, 0), draws.get(0));
        assertEquals(6, draws.get(0).primitives());
        assertEquals(new GeometryDraw(18, 3, 0, 0, 0, 4, -1, 1), draws.get(1));
        assertEquals(4, input.position());
    }

    @Test
    void keepsEmptyCommandIdsAndBoundsAllCommandAndDispatchArithmetic() {
        var input = ByteBuffer.allocate(48).order(ByteOrder.nativeOrder());
        input.putInt(16, 3).putInt(20, 2).putInt(24, 6).putInt(28, 4);
        var draws = GeometryIndirect.decode(input, 3, 0);
        assertEquals(0, draws.get(0).primitives());
        assertEquals(1, draws.get(1).drawId());
        assertEquals(0, draws.get(2).primitives());
        assertEquals(0, GeometryIndirect.bytes(0, 0));
        assertThrows(IllegalArgumentException.class, () -> GeometryIndirect.decode(input, 4, 0));
        assertThrows(IllegalArgumentException.class, () -> GeometryIndirect.bytes(1, 3));
        assertThrows(UnsupportedOperationException.class, () -> GeometryIndirect.bytes(Integer.MAX_VALUE, 16));
        assertThrows(UnsupportedOperationException.class, () -> GeometryIndirect.bytes(2, Integer.MAX_VALUE - 3));
        assertThrows(
                ArithmeticException.class,
                () -> new GeometryDraw(0, Integer.MAX_VALUE, 0, 0, 0, Integer.MAX_VALUE, 0, 0).primitives());
        input.putInt(0, -1);
        assertThrows(IllegalArgumentException.class, () -> GeometryIndirect.decode(input, 1, 0));
    }
}
