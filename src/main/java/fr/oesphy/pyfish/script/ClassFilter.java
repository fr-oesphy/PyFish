package fr.oesphy.pyfish.script;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Allow/deny list that controls which Java class names Python scripts may access.
 *
 * <p>The filter works as follows:
 * <ol>
 *   <li>If the class name starts with any prefix in the <em>deny</em> list → <strong>deny</strong></li>
 *   <li>If the <em>allow</em> list is non-empty and the class name is <strong>not</strong>
 *       in the allow list → <strong>deny</strong></li>
 *   <li>Otherwise → <strong>allow</strong></li>
 * </ol>
 *
 * <p>The public singleton {@link #DEFAULT} is pre-configured with a sensible set of
 * denied prefixes.  It can be mutated at runtime via {@link #allow}, {@link #deny},
 * and {@link #reset}.
 */
public final class ClassFilter {

    // -------------------------------------------------------------------------
    // Default deny-list  (must be declared BEFORE DEFAULT to avoid null at init)
    // -------------------------------------------------------------------------

    private static final Set<String> DEFAULT_DENIED_PREFIXES = Set.of(
            "java.lang.Runtime",
            "java.lang.ProcessBuilder",
            "java.lang.Process",
            "java.lang.reflect.",
            "sun.",
            "jdk.internal.",
            "com.sun."
    );

    // -------------------------------------------------------------------------
    // Public singleton
    // -------------------------------------------------------------------------

    /** The default, shared {@link ClassFilter} instance. */
    public static final ClassFilter DEFAULT = new ClassFilter();

    // -------------------------------------------------------------------------
    // Instance state
    // -------------------------------------------------------------------------

    private final CopyOnWriteArraySet<String> denied = new CopyOnWriteArraySet<>(DEFAULT_DENIED_PREFIXES);
    private final CopyOnWriteArraySet<String> allowed = new CopyOnWriteArraySet<>();

    // -------------------------------------------------------------------------
    // Core logic
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if the given class name is permitted to be accessed from Python.
     *
     * @param className fully-qualified class name; must not be {@code null}
     * @return {@code true} if allowed
     */
    public boolean isAllowed(String className) {
        if (className == null) return false;

        // 1. Deny-list check
        for (String prefix : denied) {
            if (className.startsWith(prefix)) {
                return false;
            }
        }

        // 2. Allow-list check (non-empty allow-list acts as a whitelist)
        if (!allowed.isEmpty() && !allowed.contains(className)) {
            return false;
        }

        return true;
    }

    // -------------------------------------------------------------------------
    // Mutation
    // -------------------------------------------------------------------------

    /**
     * Adds the given class name to the allow-list.  Once the allow-list is
     * non-empty only names present in it are permitted (subject to deny-list).
     *
     * @param className the exact class name to allow
     */
    public void allow(String className) {
        if (className != null) {
            allowed.add(className);
        }
    }

    /**
     * Adds the given prefix to the deny-list.  Any class whose name starts with
     * this prefix will be denied.
     *
     * @param classNamePrefix the prefix to deny (e.g. {@code "my.dangerous."})
     */
    public void deny(String classNamePrefix) {
        if (classNamePrefix != null) {
            denied.add(classNamePrefix);
        }
    }

    /**
     * Resets this filter to the default state (default deny-list, empty allow-list).
     */
    public void reset() {
        denied.clear();
        denied.addAll(DEFAULT_DENIED_PREFIXES);
        allowed.clear();
    }
}
