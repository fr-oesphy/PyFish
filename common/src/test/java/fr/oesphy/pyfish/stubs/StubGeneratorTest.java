package fr.oesphy.pyfish.stubs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link StubGenerator}.
 *
 * <p>All tests are pure-Java — no GraalPy or Minecraft bootstrap required.
 */
class StubGeneratorTest {

    @TempDir
    Path tempDir;

    // -------------------------------------------------------------------------
    // buildStub – unit tests
    // -------------------------------------------------------------------------

    @Test
    void buildStub_header_contains_moduleName() {
        String stub = StubGenerator.buildStub("test_module", List.of(SampleClass.class));
        assertTrue(stub.contains("test_module"),
                "Stub header should contain the module name");
    }

    @Test
    void buildStub_contains_typing_imports() {
        String stub = StubGenerator.buildStub("x", List.of(SampleClass.class));
        assertTrue(stub.contains("from typing import"), "Should have typing imports");
    }

    @Test
    void buildStub_class_declaration() {
        String stub = StubGenerator.buildStub("x", List.of(SampleClass.class));
        assertTrue(stub.contains("class SampleClass:"), "Should declare the class");
    }

    @Test
    void buildStub_public_method_present() {
        String stub = StubGenerator.buildStub("x", List.of(SampleClass.class));
        assertTrue(stub.contains("def greet("), "Should include public method 'greet'");
    }

    @Test
    void buildStub_private_method_absent() {
        String stub = StubGenerator.buildStub("x", List.of(SampleClass.class));
        assertFalse(stub.contains("def privateHelper"), "Should NOT include private methods");
    }

    @Test
    void buildStub_static_method_has_decorator() {
        String stub = StubGenerator.buildStub("x", List.of(SampleClass.class));
        assertTrue(stub.contains("@staticmethod"), "Static methods should have @staticmethod decorator");
        assertTrue(stub.contains("def create("), "Should include static 'create' method");
    }

    @Test
    void buildStub_void_return_maps_to_None() {
        String stub = StubGenerator.buildStub("x", List.of(SampleClass.class));
        // greet() returns void
        assertTrue(stub.contains("-> None:"), "void methods should map to -> None");
    }

    @Test
    void buildStub_bool_return_type() {
        String stub = StubGenerator.buildStub("x", List.of(SampleClass.class));
        // isActive() returns boolean
        assertTrue(stub.contains("-> bool:"), "boolean should map to bool");
    }

    @Test
    void buildStub_int_return_type() {
        String stub = StubGenerator.buildStub("x", List.of(SampleClass.class));
        // count() returns int
        assertTrue(stub.contains("-> int:"), "int should map to int");
    }

    @Test
    void buildStub_string_return_type() {
        String stub = StubGenerator.buildStub("x", List.of(SampleClass.class));
        // name() returns String
        assertTrue(stub.contains("-> str:"), "String should map to str");
    }

    @Test
    void buildStub_subclass_has_parent_in_declaration() {
        String stub = StubGenerator.buildStub("x", List.of(SubSampleClass.class));
        assertTrue(stub.contains("class SubSampleClass(SampleClass):"),
                "Subclass stub should include the parent class name");
    }

    @Test
    void buildStub_empty_class_has_ellipsis() {
        String stub = StubGenerator.buildStub("x", List.of(EmptyClass.class));
        assertTrue(stub.contains("..."), "Empty class should have '...' body");
    }

    @Test
    void buildStub_multiple_classes() {
        String stub = StubGenerator.buildStub("x", List.of(SampleClass.class, EmptyClass.class));
        assertTrue(stub.contains("class SampleClass:"), "Should contain SampleClass");
        assertTrue(stub.contains("class EmptyClass:"), "Should contain EmptyClass");
    }

    // -------------------------------------------------------------------------
    // generate – filesystem tests
    // -------------------------------------------------------------------------

    @Test
    void generate_creates_stub_files(@TempDir Path dir) throws IOException {
        StubGenerator.generate(dir);

        // Should have created at least __init__.pyi
        assertTrue(Files.exists(dir.resolve("__init__.pyi")),
                "__init__.pyi should be created");

        // Should have created the standard stub files
        assertTrue(Files.exists(dir.resolve("wrappers.pyi")), "wrappers.pyi should exist");
        assertTrue(Files.exists(dir.resolve("events.pyi")), "events.pyi should exist");
        assertTrue(Files.exists(dir.resolve("script.pyi")), "script.pyi should exist");
        assertTrue(Files.exists(dir.resolve("config.pyi")), "config.pyi should exist");
        assertTrue(Files.exists(dir.resolve("packs.pyi")), "packs.pyi should exist");
    }

    @Test
    void generate_init_pyi_has_comment(@TempDir Path dir) throws IOException {
        StubGenerator.generate(dir);
        String content = Files.readString(dir.resolve("__init__.pyi"));
        assertTrue(content.contains("auto-generated"), "__init__.pyi should have auto-generated comment");
    }

    @Test
    void generate_wrappers_pyi_mentions_identifierwrapper(@TempDir Path dir) throws IOException {
        StubGenerator.generate(dir);
        String content = Files.readString(dir.resolve("wrappers.pyi"));
        assertTrue(content.contains("IdentifierWrapper"), "wrappers.pyi should contain IdentifierWrapper class");
        assertTrue(content.contains("PosWrapper"), "wrappers.pyi should contain PosWrapper");
    }

    @Test
    void generate_events_pyi_mentions_eventfacade(@TempDir Path dir) throws IOException {
        StubGenerator.generate(dir);
        String content = Files.readString(dir.resolve("events.pyi"));
        assertTrue(content.contains("EventFacade"), "events.pyi should mention EventFacade");
    }

    @Test
    void generate_packs_pyi_mentions_virtual_packs(@TempDir Path dir) throws IOException {
        StubGenerator.generate(dir);
        String content = Files.readString(dir.resolve("packs.pyi"));
        assertTrue(content.contains("VirtualAssetPack"), "packs.pyi should contain VirtualAssetPack");
        assertTrue(content.contains("VirtualDataPack"), "packs.pyi should contain VirtualDataPack");
    }

    @Test
    void generate_idempotent(@TempDir Path dir) throws IOException {
        // Run twice — should not throw
        StubGenerator.generate(dir);
        StubGenerator.generate(dir);
        assertTrue(Files.exists(dir.resolve("wrappers.pyi")), "wrappers.pyi should still exist after second run");
    }

    @Test
    void generate_script_pyi_mentions_consoleapi(@TempDir Path dir) throws IOException {
        StubGenerator.generate(dir);
        String content = Files.readString(dir.resolve("script.pyi"));
        assertTrue(content.contains("ConsoleApi"), "script.pyi should mention ConsoleApi");
        assertTrue(content.contains("TickScheduler"), "script.pyi should mention TickScheduler");
    }

    // -------------------------------------------------------------------------
    // Helper sample classes for reflection tests
    // -------------------------------------------------------------------------

    @SuppressWarnings("unused")
    public static class SampleClass {
        public void greet(String name) {}
        public boolean isActive() { return false; }
        public int count() { return 0; }
        public String name() { return ""; }
        public static SampleClass create() { return new SampleClass(); }
        @SuppressWarnings("unused")
        private void privateHelper() {}
    }

    @SuppressWarnings("unused")
    public static class SubSampleClass extends SampleClass {
        public double score() { return 0.0; }
    }

    @SuppressWarnings("unused")
    public static class EmptyClass {
        // no public methods
    }
}
