package fr.oesphy.pyfish.script;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Class filter for controlling Java class access from Python scripts.
 * Supports allow-all, deny-all, and whitelist modes.
 */
public class ClassFilter {
    public enum FilterMode {
        /**
         * Allow all classes (no filtering).
         * This is the default for compatibility but is not secure.
         */
        ALLOW_ALL,

        /**
         * Deny all classes (whitelist only).
         * Only classes in the whitelist are allowed.
         */
        DENY_ALL,

        /**
         * Whitelist mode (explicit allow list).
         * Only classes matching allowed prefixes are accessible.
         */
        WHITELIST,

        /**
         * Blacklist mode (explicit deny list).
         * All classes except those matching denied prefixes are accessible.
         */
        BLACKLIST
    }

    private final FilterMode mode;
    private final Set<String> allowedPrefixes;
    private final Set<String> deniedPrefixes;

    public ClassFilter(FilterMode mode) {
        this.mode = mode;
        this.allowedPrefixes = new HashSet<>();
        this.deniedPrefixes = new HashSet<>();
    }

    /**
     * Creates a class filter with ALLOW_ALL mode (default, insecure).
     */
    public static ClassFilter allowAll() {
        return new ClassFilter(FilterMode.ALLOW_ALL);
    }

    /**
     * Creates a class filter with DENY_ALL mode (secure, whitelist required).
     */
    public static ClassFilter denyAll() {
        return new ClassFilter(FilterMode.DENY_ALL);
    }

    /**
     * Creates a class filter with WHITELIST mode and the given prefixes.
     */
    public static ClassFilter whitelist(List<String> prefixes) {
        ClassFilter filter = new ClassFilter(FilterMode.WHITELIST);
        filter.allowedPrefixes.addAll(prefixes);
        return filter;
    }

    /**
     * Creates a class filter with BLACKLIST mode and the given prefixes.
     */
    public static ClassFilter blacklist(List<String> prefixes) {
        ClassFilter filter = new ClassFilter(FilterMode.BLACKLIST);
        filter.deniedPrefixes.addAll(prefixes);
        return filter;
    }

    /**
     * Checks if a class is allowed to be accessed.
     *
     * @param className The fully qualified class name (e.g., "java.lang.String")
     * @return true if the class is allowed, false otherwise
     */
    public boolean check(String className) {
        if (className == null) {
            return false;
        }

        // In ALLOW_ALL mode, even empty strings are allowed for compatibility
        if (mode == FilterMode.ALLOW_ALL) {
            return true;
        }

        if (className.isEmpty()) {
            return false;
        }

        switch (mode) {
            case DENY_ALL:
                // In deny-all mode, only allow if in whitelist
                return matchesAnyPrefix(className, allowedPrefixes);

            case WHITELIST:
                // In whitelist mode, only allow if matches allowed prefix
                return matchesAnyPrefix(className, allowedPrefixes);

            case BLACKLIST:
                // In blacklist mode, deny if matches denied prefix
                return !matchesAnyPrefix(className, deniedPrefixes);

            default:
                return false;
        }
    }

    /**
     * Adds an allowed prefix (for WHITELIST or DENY_ALL modes).
     */
    public void addAllowedPrefix(String prefix) {
        allowedPrefixes.add(prefix);
    }

    /**
     * Adds a denied prefix (for BLACKLIST mode).
     */
    public void addDeniedPrefix(String prefix) {
        deniedPrefixes.add(prefix);
    }

    /**
     * Gets the current filter mode.
     */
    public FilterMode getMode() {
        return mode;
    }

    /**
     * Gets the list of allowed prefixes.
     */
    public List<String> getAllowedPrefixes() {
        return new ArrayList<>(allowedPrefixes);
    }

    /**
     * Gets the list of denied prefixes.
     */
    public List<String> getDeniedPrefixes() {
        return new ArrayList<>(deniedPrefixes);
    }

    /**
     * Checks if a class name matches any of the given prefixes.
     */
    private boolean matchesAnyPrefix(String className, Set<String> prefixes) {
        if (prefixes.isEmpty()) {
            return false;
        }

        for (String prefix : prefixes) {
            if (className.equals(prefix) || className.startsWith(prefix + ".")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Creates a default secure class filter with common Minecraft and PyFish packages allowed.
     */
    public static ClassFilter createDefaultSecure() {
        List<String> allowed = new ArrayList<>();
        allowed.add("net.minecraft");
        allowed.add("fr.oesphy.pyfish");
        allowed.add("java.lang");
        allowed.add("java.util");
        allowed.add("java.io");
        allowed.add("java.nio");
        allowed.add("org.slf4j");
        return whitelist(allowed);
    }

    @Override
    public String toString() {
        return "ClassFilter{" +
                "mode=" + mode +
                ", allowedPrefixes=" + allowedPrefixes.size() +
                ", deniedPrefixes=" + deniedPrefixes.size() +
                '}';
    }
}
