package fr.oesphy.pyfish.script;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a namespace folder containing Python scripts.
 * One namespace folder = one ScriptPack.
 * Scripts in pyfish/scripts/ belong to the global pack (no namespace).
 * Scripts in pyfish/<namespace>/scripts/ belong to that namespace's pack.
 */
public class ScriptPack {
    private final String namespace;
    private final Path rootPath;
    private final Map<ScriptType, List<ScriptFile>> scriptsByType;
    private final Map<String, ScriptFile> scriptsByName;
    private boolean loaded;

    public ScriptPack(String namespace, Path rootPath) {
        this.namespace = namespace;
        this.rootPath = rootPath;
        this.scriptsByType = new HashMap<>();
        this.scriptsByName = new HashMap<>();
        this.loaded = false;

        // Initialize script lists for each type
        for (ScriptType type : ScriptType.values()) {
            scriptsByType.put(type, new ArrayList<>());
        }
    }

    /**
     * Gets the namespace for this pack, or null for the global pack.
     */
    public String getNamespace() {
        return namespace;
    }

    /**
     * Gets the root path of this pack (the pyfish/ or pyfish/<namespace>/ directory).
     */
    public Path getRootPath() {
        return rootPath;
    }

    /**
     * Gets all scripts of a specific type.
     */
    public List<ScriptFile> getScripts(ScriptType type) {
        return scriptsByType.getOrDefault(type, new ArrayList<>());
    }

    /**
     * Gets all scripts in this pack.
     */
    public List<ScriptFile> getAllScripts() {
        List<ScriptFile> all = new ArrayList<>();
        for (List<ScriptFile> scripts : scriptsByType.values()) {
            all.addAll(scripts);
        }
        return all;
    }

    /**
     * Gets a script by its full name (namespace:filename or filename).
     */
    public ScriptFile getScript(String fullName) {
        return scriptsByName.get(fullName);
    }

    /**
     * Adds a script to this pack.
     */
    public void addScript(ScriptFile script) {
        scriptsByType.get(script.getType()).add(script);
        scriptsByName.put(script.getFullName(), script);
    }

    /**
     * Removes a script from this pack.
     */
    public void removeScript(ScriptFile script) {
        scriptsByType.get(script.getType()).remove(script);
        scriptsByName.remove(script.getFullName());
    }

    /**
     * Clears all scripts from this pack.
     */
    public void clearScripts() {
        for (ScriptType type : ScriptType.values()) {
            scriptsByType.get(type).clear();
        }
        scriptsByName.clear();
    }

    /**
     * Checks if this pack has been loaded.
     */
    public boolean isLoaded() {
        return loaded;
    }

    /**
     * Sets the loaded state of this pack.
     */
    public void setLoaded(boolean loaded) {
        this.loaded = loaded;
    }

    /**
     * Gets the number of scripts in this pack.
     */
    public int getScriptCount() {
        return scriptsByName.size();
    }

    /**
     * Gets the number of scripts of a specific type.
     */
    public int getScriptCount(ScriptType type) {
        return scriptsByType.getOrDefault(type, new ArrayList<>()).size();
    }

    /**
     * Checks if this is the global pack (no namespace).
     */
    public boolean isGlobal() {
        return namespace == null || namespace.isEmpty();
    }

    @Override
    public String toString() {
        if (isGlobal()) {
            return "ScriptPack[global, " + getScriptCount() + " scripts]";
        }
        return "ScriptPack[" + namespace + ", " + getScriptCount() + " scripts]";
    }
}
