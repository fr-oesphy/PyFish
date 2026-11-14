package fr.oesphy.pyfish.api;

import fr.oesphy.pyfish.api.wrapper.ItemStackWrapper;
import fr.oesphy.pyfish.api.wrapper.NbtWrapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests for {@link ItemStackWrapper}.
 *
 * <p>All tests in this class require Minecraft to be bootstrapped (registries
 * initialised).  The class is guarded via {@code assumeTrue} in each test so
 * that they skip gracefully in pure-unit-test environments.
 *
 * <p>This is in its own class (not merged into {@link ApiLayerTest}) to prevent
 * loading {@link ItemStackWrapper}'s static imports ({@code DataComponents},
 * {@code BuiltInRegistries}) from poisoning the rest of the test suite with an
 * {@link ExceptionInInitializerError} when Minecraft is not bootstrapped.
 */
class ItemStackWrapperTest {

    private static boolean isMcBootstrapped() {
        try {
            var ignored = net.minecraft.world.item.Items.AIR;
            return true;
        } catch (Exception | Error e) {
            return false;
        }
    }

    @Test
    void itemStackWrapperEmpty() {
        assumeTrue(isMcBootstrapped(), "Minecraft bootstrap not available");
        assertTrue(ItemStackWrapper.empty().isEmpty());
    }

    @Test
    void itemStackWrapperFromString() {
        assumeTrue(isMcBootstrapped(), "Minecraft bootstrap not available");
        ItemStackWrapper w = ItemStackWrapper.of("minecraft:stone", 3);
        assertEquals(3, w.count());
    }

    @Test
    void itemStackWrapperNbtEmpty() {
        assumeTrue(isMcBootstrapped(), "Minecraft bootstrap not available");
        NbtWrapper nbt = ItemStackWrapper.empty().nbt();
        assertNotNull(nbt);
        assertTrue(nbt.isEmpty());
    }

    @Test
    void itemStackWrapperToString() {
        assumeTrue(isMcBootstrapped(), "Minecraft bootstrap not available");
        // empty stack should not throw toString()
        String s = ItemStackWrapper.empty().toString();
        assertNotNull(s);
    }
}
