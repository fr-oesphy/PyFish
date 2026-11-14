package fr.oesphy.pyfish.script;

import fr.oesphy.pyfish.PyFish;
import fr.oesphy.pyfish.runtime.GraalPyRuntimeResources;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.python.embedding.GraalPyResources;
import org.graalvm.python.embedding.VirtualFileSystem;
import org.slf4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * Builds a GraalPy {@link Context} configured for a particular {@link ScriptType}.
 *
 * <p>This is the <strong>single entry point</strong> for GraalPy context construction
 * in PyFish. No other class may call {@code Context.newBuilder()} or
 * {@code GraalPyResources.contextBuilder()} directly.
 *
 * <h3>Dual-path initialisation</h3>
 * <dl>
 *   <dt>Development (Gradle run / test)</dt>
 *   <dd>GraalPy JARs are on the classpath; {@link VirtualFileSystem} is built with
 *       {@code resourceLoadingClass(PyContextFactory.class)} so the VFS uses the
 *       correct classloader, then passed to {@link GraalPyResources#contextBuilder(VirtualFileSystem)}.
 *       This matches {@link fr.oesphy.pyfish.runtime.GraalPyRuntimeManager}'s pattern.</dd>
 *   <dt>Production (packaged JAR)</dt>
 *   <dd>GraalPy resources are extracted from the mod JAR to a versioned cache by
 *       {@link GraalPyRuntimeResources#prepare}; paths are passed explicitly via
 *       {@code python.CoreHome}, {@code python.StdLibHome}, etc.</dd>
 * </dl>
 *
 * <h3>Sandbox (Phase 6 target)</h3>
 * <p>Phase 1 runs with {@code allowAllAccess(true)}.  Phase 6 replaces this with a
 * restrictive {@link HostAccess} policy, an IO sandbox limited to {@code pyfish/}
 * and world data, and a network toggle backed by the config file.
 *
 * <h3>Thread safety</h3>
 * <p>A context is not thread-safe.  Wrap all usage in {@link PyRuntime#runSafe} to
 * ensure the runtime lock is held before entering the context.
 *
 * @see PyRuntime
 * @see ScriptType
 * @see GraalPyRuntimeResources
 */
public final class PyContextFactory {

    private static final Logger LOGGER = PyFish.LOGGER;

    private PyContextFactory() {
    }

    // -------------------------------------------------------------------------
    // Public factory methods
    // -------------------------------------------------------------------------

    /**
     * Creates a fully initialised {@link Context} for the given script type.
     *
     * <p>Uses the development (classpath VFS) path.  Suitable for unit tests and
     * Gradle run configurations where GraalPy JARs are on the classpath.
     *
     * @param type script lifecycle type (STARTUP, SERVER, or CLIENT)
     * @param out  output stream for Python's stdout
     * @param err  output stream for Python's stderr
     * @return a new, ready-to-use context
     * @throws RuntimeException if GraalPy initialisation fails
     */
    public static Context build(ScriptType type, OutputStream out, OutputStream err) {
        return build(type, Optional.empty(), out, err);
    }

    /**
     * Creates a fully initialised {@link Context} using an optional extracted
     * runtime home.
     *
     * <p>When {@code runtimeHome} is present (production packaged JAR), the context
     * builder is configured with explicit filesystem paths instead of the VFS.
     * When absent (development), the VFS-based classpath path is used.
     *
     * @param type        script lifecycle type
     * @param runtimeHome extracted GraalPy language home; empty for development
     * @param out         stdout stream
     * @param err         stderr stream
     * @return a new, ready-to-use context
     * @throws RuntimeException if GraalPy initialisation fails
     */
    public static Context build(
            ScriptType type,
            Optional<GraalPyRuntimeResources.RuntimeHome> runtimeHome,
            OutputStream out,
            OutputStream err) {

        LOGGER.info("[PyFish/ContextFactory] Creating GraalPy context (type={}, mode={})",
                type, runtimeHome.isPresent() ? "extracted" : "classpath-vfs");

        try {
            Context.Builder builder = createBuilder(type, runtimeHome);
            applyCommonOptions(builder, out, err);

            Context context = builder.build();
            // Force language initialisation so setup failures are attributed here,
            // not to the first user script.
            context.eval("python", "import sys");
            LOGGER.info("[PyFish/ContextFactory] GraalPy context ready (type={})", type);
            return context;
        } catch (Exception e) {
            LOGGER.error("[PyFish/ContextFactory] Failed to build context for type {}: {}",
                    type, e.getMessage(), e);
            throw new RuntimeException("Failed to initialise GraalPy context (type=" + type + ")", e);
        }
    }

    /**
     * Convenience overload: development path with auto-created logging streams.
     *
     * @param type script lifecycle type
     * @return a new, ready-to-use context
     */
    public static Context build(ScriptType type) {
        return build(type, Optional.empty(),
                makeLoggingStream(false), makeLoggingStream(true));
    }

    /**
     * Creates an {@link OutputStream} that forwards completed lines to SLF4J.
     *
     * @param isError {@code true} → stderr → ERROR level;
     *                {@code false} → stdout → INFO level
     */
    public static OutputStream makeLoggingStream(boolean isError) {
        return new LoggingOutputStream(isError);
    }

    // -------------------------------------------------------------------------
    // Internal builder helpers
    // -------------------------------------------------------------------------

    private static Context.Builder createBuilder(
            ScriptType type,
            Optional<GraalPyRuntimeResources.RuntimeHome> runtimeHome) {

        if (runtimeHome.isPresent()) {
            // Production: GraalPy resources extracted to a versioned cache directory.
            // NeoForge's transformer and Fabric Loom's remapper wrap the classloader
            // in ways that break GraalPy's internal resource lookup, so we bypass it.
            Context.Builder builder = Context.newBuilder()
                    .option("python.PosixModuleBackend", "java")
                    .option("python.DontWriteBytecodeFlag", "true")
                    .option("python.ForceImportSite", "true")
                    .option("python.CheckHashPycsMode", "never");
            runtimeHome.get().configure(builder);
            LOGGER.debug("[PyFish/ContextFactory] Using extracted GraalPy runtime: {}",
                    runtimeHome.get().root());
            return builder;
        }

        // Development: GraalPy JARs are on the Gradle / test classpath.
        // Pin resourceLoadingClass to PyContextFactory so the VFS uses the same
        // classloader as the mod classes — matching GraalPyRuntimeManager's pattern
        // exactly, and avoiding the "no entries after init" AssertionError that
        // occurs when the thread-context classloader is a Gradle test worker JVM.
        LOGGER.debug("[PyFish/ContextFactory] Using classpath GraalPy VFS (development mode).");
        VirtualFileSystem vfs = VirtualFileSystem.newBuilder()
                .resourceLoadingClass(PyContextFactory.class)
                .build();
        return GraalPyResources.contextBuilder(vfs);
    }

    private static void applyCommonOptions(Context.Builder builder, OutputStream out, OutputStream err) {
        // Phase 6: use ClassFilter for host class lookup.
        // allowJavaInterop=true (config) → pass everything through (ClassFilter still blocks dangerous);
        // allowJavaInterop=false          → only ClassFilter-approved names are reachable.
        boolean allowJavaInterop = fr.oesphy.pyfish.CommonProperties.get().allowJavaInterop();
        builder
                .allowAllAccess(true)
                .allowHostAccess(HostAccess.ALL)
                .allowHostClassLookup(className ->
                        allowJavaInterop || ClassFilter.DEFAULT.isAllowed(className))
                // PYZ mods are trusted local code; GraalPy also needs this for its own native libs.
                .allowNativeAccess(true)
                // Windows launcher setups sometimes provide no CWD; GraalPy rejects that.
                .currentWorkingDirectory(Paths.get(".").toAbsolutePath())
                .out(out)
                .err(err)
                .allowExperimentalOptions(true)
                .option("python.EmulateJython", "true")
                .option("python.IsolateNativeModules", "true")
                .option("engine.WarnInterpreterOnly", "false");
    }

    // -------------------------------------------------------------------------
    // SLF4J-redirecting output stream
    // -------------------------------------------------------------------------

    /** Buffers bytes until a newline, then forwards the completed line to SLF4J. */
    private static final class LoggingOutputStream extends OutputStream {
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private final boolean isError;

        LoggingOutputStream(boolean isError) {
            this.isError = isError;
        }

        @Override
        public void write(int b) throws IOException {
            if (b == '\n') {
                flushLine();
            } else {
                buffer.write(b);
            }
        }

        @Override
        public void flush() {
            flushLine();
        }

        private void flushLine() {
            String line = buffer.toString();
            buffer.reset();
            if (!line.isEmpty()) {
                if (isError) {
                    LOGGER.error("[PyFish/Python] {}", line);
                } else {
                    LOGGER.info("[PyFish/Python] {}", line);
                }
            }
        }
    }
}
