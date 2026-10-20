package fr.oesphy.pyfish.api.wrapper;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PosTest {

    @Test
    void testPosCreation() {
        Pos pos = new Pos(1, 2, 3);
        assertEquals(1, pos.getX());
        assertEquals(2, pos.getY());
        assertEquals(3, pos.getZ());
    }

    @Test
    void testPosFromBlockPos() {
        BlockPos blockPos = new BlockPos(5, 10, 15);
        Pos pos = new Pos(blockPos);
        assertEquals(5, pos.getX());
        assertEquals(10, pos.getY());
        assertEquals(15, pos.getZ());
    }

    @Test
    void testPosOffset() {
        Pos pos = new Pos(1, 2, 3);
        Pos offset = pos.offset(5, 5, 5);
        assertEquals(6, offset.getX());
        assertEquals(7, offset.getY());
        assertEquals(8, offset.getZ());
    }

    @Test
    void testPosToString() {
        Pos pos = new Pos(1, 2, 3);
        assertEquals("Pos(1, 2, 3)", pos.toString());
    }

    @Test
    void testPosUnwrap() {
        BlockPos blockPos = new BlockPos(5, 10, 15);
        Pos pos = new Pos(blockPos);
        assertSame(blockPos, pos.unwrap());
    }
}
