package fr.oesphy.pyfish.script;

/**
 * Represents the type of script and when it should be executed.
 */
public enum ScriptType {
    /**
     * Startup scripts run before registries freeze.
     * They can register content (items, blocks, recipes, tags, etc.).
     * Cannot be hot-reloaded.
     */
    STARTUP,

    /**
     * Server scripts run on server start and on /pyfish reload.
     * They handle server-side events and logic.
     * Can be hot-reloaded.
     */
    SERVER,

    /**
     * Client scripts run on client init and resource reload.
     * They handle client-side events (tooltips, HUD, keybinds, etc.).
     * Can be hot-reloaded.
     */
    CLIENT
}
