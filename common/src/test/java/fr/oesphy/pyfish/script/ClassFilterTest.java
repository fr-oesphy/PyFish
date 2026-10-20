package fr.oesphy.pyfish.script;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClassFilterTest {

    @Test
    void testAllowAllMode() {
        ClassFilter filter = ClassFilter.allowAll();
        assertEquals(ClassFilter.FilterMode.ALLOW_ALL, filter.getMode());
        assertTrue(filter.check("java.lang.String"));
        assertTrue(filter.check("any.class.Here"));
        // Empty strings are allowed in ALLOW_ALL mode for compatibility
        assertTrue(filter.check(""));
    }

    @Test
    void testDenyAllMode() {
        ClassFilter filter = ClassFilter.denyAll();
        assertEquals(ClassFilter.FilterMode.DENY_ALL, filter.getMode());
        assertFalse(filter.check("java.lang.String"));
        assertFalse(filter.check("any.class.Here"));
    }

    @Test
    void testWhitelistMode() {
        List<String> allowed = List.of("java.lang", "net.minecraft");
        ClassFilter filter = ClassFilter.whitelist(allowed);

        assertTrue(filter.check("java.lang.String"));
        assertTrue(filter.check("java.lang.Integer"));
        assertTrue(filter.check("net.minecraft.block.Block"));
        assertFalse(filter.check("java.util.List"));
        assertFalse(filter.check("com.example.Test"));
    }

    @Test
    void testBlacklistMode() {
        List<String> denied = List.of("java.io", "java.nio.file");
        ClassFilter filter = ClassFilter.blacklist(denied);

        assertTrue(filter.check("java.lang.String"));
        assertTrue(filter.check("java.util.List"));
        assertFalse(filter.check("java.io.File"));
        assertFalse(filter.check("java.nio.file.Path"));
    }

    @Test
    void testAddAllowedPrefix() {
        ClassFilter filter = ClassFilter.denyAll();
        filter.addAllowedPrefix("java.lang");

        assertTrue(filter.check("java.lang.String"));
        assertFalse(filter.check("java.util.List"));
    }

    @Test
    void testAddDeniedPrefix() {
        ClassFilter filter = ClassFilter.blacklist(List.of());
        filter.addDeniedPrefix("java.io");

        assertTrue(filter.check("java.lang.String"));
        assertFalse(filter.check("java.io.File"));
    }

    @Test
    void testCreateDefaultSecure() {
        ClassFilter filter = ClassFilter.createDefaultSecure();
        assertEquals(ClassFilter.FilterMode.WHITELIST, filter.getMode());

        assertTrue(filter.check("net.minecraft.block.Block"));
        assertTrue(filter.check("fr.oesphy.pyfish.PyFish"));
        assertTrue(filter.check("java.lang.String"));
        assertTrue(filter.check("java.util.List"));
        assertFalse(filter.check("com.example.Test"));
        assertFalse(filter.check("org.apache.http"));
    }

    @Test
    void testNullClassName() {
        ClassFilter filter = ClassFilter.allowAll();
        assertFalse(filter.check(null));
    }

    @Test
    void testEmptyClassNameInSecureMode() {
        ClassFilter filter = ClassFilter.whitelist(List.of("java.lang"));
        assertFalse(filter.check(""));
    }

    @Test
    void testExactMatch() {
        List<String> allowed = List.of("java.lang.String");
        ClassFilter filter = ClassFilter.whitelist(allowed);

        assertTrue(filter.check("java.lang.String"));
        assertFalse(filter.check("java.lang.Integer"));
    }
}
