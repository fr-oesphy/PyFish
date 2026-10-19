package fr.oesphy.pyfish.script;

import fr.oesphy.pyfish.PyFish;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.python.embedding.GraalPyResources;
import org.slf4j.Logger;

import java.io.OutputStream;
import java.nio.file.Paths;

/**
 * Factory for creating locked-down GraalPy Context instances per ScriptType.
 * Applies security policies via ClassFilter and configures I/O streams.
 */
public class PyContextFactory {
    private static final Logger LOGGER = PyFish.LOGGER;

    private final ClassFilter classFilter;
    private final boolean allowNetwork;
    private final boolean allowNativeAccess;
    private final OutputStream stdout;
    private final OutputStream stderr;

    public PyContextFactory(ClassFilter classFilter, boolean allowNetwork, boolean allowNativeAccess,
                          OutputStream stdout, OutputStream stderr) {
        this.classFilter = classFilter;
        this.allowNetwork = allowNetwork;
        this.allowNativeAccess = allowNativeAccess;
        this.stdout = stdout;
        this.stderr = stderr;
    }

    /**
     * Creates a GraalPy Context for the given ScriptType.
     *
     * @param type The script type (STARTUP, SERVER, or CLIENT)
     * @return A configured GraalPy Context
     */
    public Context createContext(ScriptType type) {
        LOGGER.info("[PyFish] Creating Context for ScriptType: {}", type);

        Context.Builder builder = GraalPyResources.contextBuilder();

        // Apply security settings based on ClassFilter
        applySecuritySettings(builder);

        // Configure I/O streams
        builder.out(stdout);
        builder.err(stderr);

        // Set working directory
        builder.currentWorkingDirectory(Paths.get(".").toAbsolutePath());

        // Enable experimental options for Python compatibility
        builder.allowExperimentalOptions(true);

        // Python-specific options
        builder.option("python.EmulateJython", "true");
        builder.option("python.IsolateNativeModules", "true");
        builder.option("engine.WarnInterpreterOnly", "false");

        Context context = builder.build();
        LOGGER.info("[PyFish] Context created successfully for ScriptType: {}", type);

        return context;
    }

    /**
     * Applies security settings to the Context builder based on ClassFilter mode.
     */
    private void applySecuritySettings(Context.Builder builder) {
        if (classFilter == null || classFilter.getMode() == ClassFilter.FilterMode.ALLOW_ALL) {
            // Trusted mode: allow all access (insecure, but compatible)
            LOGGER.warn("[PyFish] Using ALLOW_ALL mode - no class filtering applied");
            builder.allowAllAccess(true);
            builder.allowHostAccess(HostAccess.ALL);
            builder.allowHostClassLookup(className -> true);
            builder.allowNativeAccess(true);
            return;
        }

        // Secure mode: apply restrictions
        builder.allowAllAccess(false);
        builder.allowHostAccess(HostAccess.EXPLICIT);
        builder.allowHostClassLookup(this::checkClassAccess);
        builder.allowNativeAccess(allowNativeAccess);

        LOGGER.info("[PyFish] Applied security settings - mode: {}, native access: {}",
                classFilter.getMode(), allowNativeAccess);
    }

    /**
     * Checks if a class should be accessible based on the ClassFilter.
     */
    private boolean checkClassAccess(String className) {
        if (classFilter == null) {
            return true;
        }

        boolean allowed = classFilter.check(className);
        if (!allowed) {
            LOGGER.debug("[PyFish] Class access denied: {}", className);
        }
        return allowed;
    }

    /**
     * Gets the ClassFilter being used.
     */
    public ClassFilter getClassFilter() {
        return classFilter;
    }

    /**
     * Checks if network access is allowed.
     */
    public boolean isAllowNetwork() {
        return allowNetwork;
    }

    /**
     * Checks if native access is allowed.
     */
    public boolean isAllowNativeAccess() {
        return allowNativeAccess;
    }

    /**
     * Creates a default factory with permissive settings (for compatibility).
     */
    public static PyContextFactory createDefault(OutputStream stdout, OutputStream stderr) {
        return new PyContextFactory(
                ClassFilter.allowAll(),
                true,  // allow network
                true,  // allow native access
                stdout,
                stderr
        );
    }

    /**
     * Creates a secure factory with restrictive settings.
     */
    public static PyContextFactory createSecure(OutputStream stdout, OutputStream stderr) {
        return new PyContextFactory(
                ClassFilter.createDefaultSecure(),
                false, // deny network
                false, // deny native access
                stdout,
                stderr
        );
    }
}
