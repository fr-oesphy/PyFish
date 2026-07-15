package fr.oesphy.pyfish.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MinecraftCompatTest {
    @Test
    void unwrapRegistryValueReturnsDirectValues() {
        String value = MinecraftCompat.unwrapRegistryValue("direct:minecraft:stone");
        assertEquals("direct:minecraft:stone", value);
    }

    @Test
    void unwrapRegistryValueUnwrapsOptionalValues() {
        String value = MinecraftCompat.unwrapRegistryValue(java.util.Optional.of("optional:minecraft:stone"));
        assertEquals("optional:minecraft:stone", value);
    }

    @Test
    void unwrapRegistryValueReturnsNullForEmptyOptional() {
        String value = MinecraftCompat.unwrapRegistryValue(java.util.Optional.empty());
        assertNull(value);
    }

    @Test
    void unwrapRegistryValueUnwrapsHolderLikeValues() {
        String value = MinecraftCompat.unwrapRegistryValue(new HolderLike<>("holder:minecraft:stone"));
        assertEquals("holder:minecraft:stone", value);
    }

    @Test
    void unwrapRegistryValueUnwrapsOptionalHolderLikeValues() {
        String value = MinecraftCompat.unwrapRegistryValue(java.util.Optional.of(new HolderLike<>("holder:optional")));
        assertEquals("holder:optional", value);
    }

    private static final class HolderLike<T> {
        private final T value;

        private HolderLike(T value) {
            this.value = value;
        }

        public T value() {
            return value;
        }
    }
}