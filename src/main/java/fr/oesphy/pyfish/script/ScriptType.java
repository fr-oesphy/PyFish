package fr.oesphy.pyfish.script;

/**
 * Classifies a Python script by when it runs and which thread owns its
 * {@link PyRuntime}.
 *
 * <ul>
 *   <li>{@link #STARTUP} – executed before registries freeze; cannot be
 *       hot-reloaded.</li>
 *   <li>{@link #SERVER} – executed on server start and on {@code /pyfish reload};
 *       lives on the server thread.</li>
 *   <li>{@link #CLIENT} – executed on client init and resource reload; lives on
 *       the render/client thread.</li>
 * </ul>
 */
public enum ScriptType {

    /**
     * Scripts that register content (items, blocks, creative tabs, tags, recipes).
     * These run before registries freeze and may not be reloaded at runtime.
     */
    STARTUP,

    /**
     * Scripts that listen to server-side events, modify recipes, and react to
     * datapack reloads. Cleared and re-run on {@code /pyfish reload}.
     */
    SERVER,

    /**
     * Scripts that run on the logical client: tooltips, keybinds, lang overrides,
     * HUD hooks. Cleared and re-run on resource-pack reload.
     */
    CLIENT
}
