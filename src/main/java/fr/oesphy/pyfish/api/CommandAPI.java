package fr.oesphy.pyfish.api;

import fr.oesphy.pyfish.PyFish;
import fr.oesphy.pyfish.script.ScriptManager;
import fr.oesphy.pyfish.script.ScriptType;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Administrative command API for PyFish.
 * Provides commands for script management, diagnostics, and runtime control.
 */
public class CommandAPI {
    private static final Logger LOGGER = PyFish.LOGGER;

    /**
     * Executes a PyFish administrative command.
     * @param command The command name (e.g., "reload", "scripts", "status")
     * @param args Command arguments
     * @param player The player executing the command (may be null for console)
     * @return Command result message
     */
    public static String executeCommand(String command, String[] args, ServerPlayer player) {
        try {
            return switch (command.toLowerCase()) {
                case "reload" -> executeReloadCommand(args, player);
                case "scripts" -> executeScriptsCommand(args, player);
                case "status" -> executeStatusCommand(args, player);
                case "validate" -> executeValidateCommand(args, player);
                case "help" -> executeHelpCommand(args, player);
                default -> "Unknown PyFish command: " + command + ". Use /pyfish help for available commands.";
            };
        } catch (Exception e) {
            LOGGER.error("[PyFish/Command] Error executing command '{}': {}", command, e.getMessage(), e);
            return "Error executing command: " + e.getMessage();
        }
    }

    private static String executeReloadCommand(String[] args, ServerPlayer player) {
        if (!PyFish.isInitialized()) {
            return "PyFish is not initialized.";
        }

        ScriptManager scriptManager = PyFish.getPythonRuntime().getScriptManager();
        if (scriptManager == null) {
            return "ScriptManager is not available.";
        }

        boolean selective = args.length > 0 && args[0].equalsIgnoreCase("server");

        if (selective) {
            scriptManager.reload(ScriptType.SERVER);
            return "Reloaded SERVER scripts. Use '/pyfish scripts' to check status.";
        } else {
            scriptManager.reload();
            scriptManager.executeScripts();
            return "Reloaded all scripts. Use '/pyfish scripts' to check status.";
        }
    }

    private static String executeScriptsCommand(String[] args, ServerPlayer player) {
        if (!PyFish.isInitialized()) {
            return "PyFish is not initialized.";
        }

        ScriptManager scriptManager = PyFish.getPythonRuntime().getScriptManager();
        if (scriptManager == null) {
            return "ScriptManager is not available.";
        }

        List<String> output = new ArrayList<>();
        output.add("=== PyFish Scripts ===");

        var packs = scriptManager.getPacks();
        if (packs.isEmpty()) {
            output.add("No script packs discovered.");
        } else {
            for (var entry : packs.entrySet()) {
                String namespace = entry.getKey();
                var pack = entry.getValue();
                output.add(String.format("Pack: %s (%d scripts)", namespace, pack.getScriptCount()));

                for (ScriptType type : ScriptType.values()) {
                    int count = pack.getScriptCount(type);
                    if (count > 0) {
                        output.add(String.format("  %s: %d scripts", type, count));
                    }
                }
            }
        }

        List<fr.oesphy.pyfish.script.ScriptFile> errorScripts = scriptManager.getScriptsWithErrors();
        if (!errorScripts.isEmpty()) {
            output.add("");
            output.add("Scripts with errors:");
            for (var script : errorScripts) {
                output.add(String.format("  - %s: %s", script.getRelativePath(),
                        script.getLastError() != null ? script.getLastError().getMessage() : "Unknown error"));
            }
        }

        return String.join("\n", output);
    }

    private static String executeStatusCommand(String[] args, ServerPlayer player) {
        if (!PyFish.isInitialized()) {
            return "PyFish is not initialized.";
        }

        List<String> output = new ArrayList<>();
        output.add("=== PyFish Status ===");
        output.add("Runtime: " + (PyFish.getPythonRuntime() != null ? "Active" : "Inactive"));

        ScriptManager scriptManager = PyFish.getPythonRuntime().getScriptManager();
        if (scriptManager != null) {
            output.add("Script Manager: Active");
            output.add("Total Scripts: " + scriptManager.getTotalScriptCount());
            output.add("STARTUP Scripts: " + scriptManager.getScriptCount(ScriptType.STARTUP));
            output.add("SERVER Scripts: " + scriptManager.getScriptCount(ScriptType.SERVER));
            output.add("CLIENT Scripts: " + scriptManager.getScriptCount(ScriptType.CLIENT));
            output.add("Scripts with Errors: " + scriptManager.getScriptsWithErrors().size());
        } else {
            output.add("Script Manager: Not available");
        }

        if (PyFish.getPythonRuntime().getMinecraftAPI() != null) {
            output.add("Event Callbacks: " + PyFish.getPythonRuntime().getMinecraftAPI().getEventCallbackCount());
        }

        return String.join("\n", output);
    }

    private static String executeHelpCommand(String[] args, ServerPlayer player) {
        return """
                === PyFish Commands ===
                /pyfish reload [server] - Reload all scripts or only SERVER scripts
                /pyfish scripts - List all discovered scripts and their status
                /pyfish status - Show PyFish runtime status and statistics
                /pyfish validate - Validate resources in pyfish directories
                /pyfish help - Show this help message
                """;
    }

    private static String executeValidateCommand(String[] args, ServerPlayer player) {
        if (!PyFish.isInitialized()) {
            return "PyFish is not initialized.";
        }

        List<String> output = new ArrayList<>();
        output.add("=== PyFish Resource Validation ===");

        java.nio.file.Path pyfishRoot = java.nio.file.Paths.get("pyfish");
        if (!java.nio.file.Files.exists(pyfishRoot)) {
            output.add("pyfish directory does not exist.");
            return String.join("\n", output);
        }

        fr.oesphy.pyfish.content.ResourceValidator validator = new fr.oesphy.pyfish.content.ResourceValidator();

        // Validate namespace directories
        try {
            var namespaceDirs = java.nio.file.Files.list(pyfishRoot)
                    .filter(java.nio.file.Files::isDirectory)
                    .filter(dir -> !dir.getFileName().toString().startsWith("."))
                    .toList();

            for (var namespaceDir : namespaceDirs) {
                String namespace = namespaceDir.getFileName().toString();
                if (validator.validateIdentifier(namespace + ":dummy")) {
                    output.add("✓ Valid namespace: " + namespace);
                } else {
                    output.add("✗ Invalid namespace: " + namespace);
                    for (var error : validator.getErrors()) {
                        output.add("  " + error.toString());
                    }
                    validator.clear();
                }
            }
        } catch (Exception e) {
            output.add("Error listing namespaces: " + e.getMessage());
        }

        // Validate data resources
        java.nio.file.Path dataRoot = pyfishRoot.resolve("data");
        if (java.nio.file.Files.exists(dataRoot)) {
            output.add("");
            output.add("Validating data resources...");
            int dataFileCount = 0;
            try {
                var dataFiles = java.nio.file.Files.walk(dataRoot)
                        .filter(java.nio.file.Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".json"))
                        .toList();

                for (var dataFile : dataFiles) {
                    dataFileCount++;
                    try {
                        String content = java.nio.file.Files.readString(dataFile);
                        String context = dataFile.toString();
                        if (validator.validateJson(content, context)) {
                            dataFileCount--;
                        } else {
                            output.add("✗ Invalid JSON: " + dataFile);
                            for (var error : validator.getErrors()) {
                                output.add("  " + error.toString());
                            }
                            validator.clear();
                        }
                    } catch (Exception e) {
                        output.add("✗ Error reading file: " + dataFile);
                    }
                }
            } catch (Exception e) {
                output.add("Error validating data resources: " + e.getMessage());
            }

            if (dataFileCount == 0) {
                output.add("✓ All data resources valid");
            }
        }

        int errorCount = validator.getErrors().size();
        int warningCount = validator.getWarnings().size();

        output.add("");
        output.add(String.format("Validation complete: %d errors, %d warnings", errorCount, warningCount));

        return String.join("\n", output);
    }
}
