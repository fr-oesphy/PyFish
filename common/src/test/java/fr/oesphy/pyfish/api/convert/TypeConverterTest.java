package fr.oesphy.pyfish.api.convert;

import fr.oesphy.pyfish.api.wrapper.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TypeConverterTest {

    @Test
    void testToIdentifier() {
        ResourceLocation id = TypeConverter.toIdentifier("minecraft:stone");
        assertEquals("minecraft", id.getNamespace());
        assertEquals("stone", id.getPath());
    }

    @Test
    void testToIdentifierWithTagPrefix() {
        ResourceLocation id = TypeConverter.toIdentifier("#minecraft:logs");
        assertEquals("minecraft", id.getNamespace());
        assertEquals("logs", id.getPath());
    }

    @Test
    void testToIdentifierEmpty() {
        ResourceLocation id = TypeConverter.toIdentifier("");
        assertEquals("minecraft", id.getNamespace());
        assertEquals("air", id.getPath());
    }

    @Test
    void testWrapBlockPos() {
        BlockPos blockPos = new BlockPos(1, 2, 3);
        Object wrapped = TypeConverter.wrap(blockPos);
        assertTrue(wrapped instanceof Pos);
        assertEquals(1, ((Pos) wrapped).getX());
    }

    @Test
    void testUnwrap() {
        BlockPos blockPos = new BlockPos(5, 10, 15);
        Pos pos = new Pos(blockPos);
        Object unwrapped = TypeConverter.unwrap(pos);
        assertSame(blockPos, unwrapped);
    }

    @Test
    void testWrapNull() {
        assertNull(TypeConverter.wrap(null));
    }

    @Test
    void testUnwrapNull() {
        assertNull(TypeConverter.unwrap(null));
    }

    @Test
    void testWrapNonWrapper() {
        String str = "test";
        Object wrapped = TypeConverter.wrap(str);
        assertSame(str, wrapped);
    }
}
