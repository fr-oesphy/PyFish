package fr.oesphy.pyfish.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import fr.oesphy.pyfish.PyFish;
import fr.oesphy.pyfish.script.ScriptError;
import fr.oesphy.pyfish.script.ScriptManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;

import java.util.List;

/**
 * Registers the {@code /pyfish} command tree.
 *
 * <p>This class is pure common code — it imports only Brigadier and
 * vanilla {@code net.minecraft.commands} types that are present on both
 * Fabric and NeoForge. Loader entrypoints call
 * {@link #register(CommandDispatcher, ScriptManager)} from their
 * command-registration event.
 *
 * <h3>Sub-commands</h3>
 * <ul>
 *   <li>{@code /pyfish reload} — clears and re-runs SERVER scripts.</li>
 *   <li>{@code /pyfish errors} — lists the last error per script.</li>
 *   <li>{@code /pyfish eval <code>} — ops-only inline Python evaluation.</li>
 *   <li>{@code /pyfish version} — prints mod and GraalPy versions.</li>
 * </ul>
 *
 * <h3>Permission levels</h3>
 * <ul>
 *   <li>{@code reload}, {@code errors}, {@code version} — requires op level 2.</li>
 *   <li>{@code eval} — requires op level 4 (server owner equivalent).</li>
 * </ul>
 */
public final class PyFishCommands {

    private static final Logger LOGGER = PyFish.LOGGER;

    private PyFishCommands() {}

    // -------------------------------------------------------------------------
    // Registration
    // -------------------------------------------------------------------------

    /**
     * Registers the {@code /pyfish} command tree with the given dispatcher.
     *
     * <p>Call this once from each loader's command registration callback.
     * Calling it more than once on the same dispatcher is harmless (Brigadier
     * silently replaces duplicate registrations).
     *
     * @param dispatcher    the command dispatcher to register into
     * @param serverManager the SERVER {@link ScriptManager} to operate on
     */
    public static void register(
            CommandDispatcher<CommandSourceStack> dispatcher,
            ScriptManager serverManager) {

        dispatcher.register(
            Commands.literal("pyfish")
                .requires(src -> src.hasPermission(2))

                // /pyfish reload
                .then(Commands.literal("reload")
                        .executes(ctx -> executeReload(ctx, serverManager)))

                // /pyfish errors
                .then(Commands.literal("errors")
                        .executes(ctx -> executeErrors(ctx, serverManager)))

                // /pyfish eval <code>  — op level 4
                .then(Commands.literal("eval")
                        .requires(src -> src.hasPermission(4))
                        .then(Commands.argument("code", StringArgumentType.greedyString())
                                .executes(ctx -> executeEval(ctx, serverManager))))

                // /pyfish version
                .then(Commands.literal("version")
                        .executes(PyFishCommands::executeVersion))
        );

        LOGGER.debug("[PyFish/Commands] /pyfish command tree registered.");
    }

    // -------------------------------------------------------------------------
    // Sub-command handlers
    // -------------------------------------------------------------------------

    private static int executeReload(
            CommandContext<CommandSourceStack> ctx,
            ScriptManager serverManager) {

        CommandSourceStack source = ctx.getSource();
        source.sendSuccess(() -> Component.literal("[PyFish] Reloading server scripts..."), true);
        LOGGER.info("[PyFish/Commands] /pyfish reload issued by {}", source.getTextName());

        try {
            serverManager.clearServerScripts();
            serverManager.runAll();

            List<ScriptError> errors = serverManager.collectErrors();
            int total  = serverManager.getScripts().size();
            int errCnt = errors.size();
            int ok     = total - errCnt;

            if (errCnt == 0) {
                source.sendSuccess(
                        () -> Component.literal("[PyFish] Reload complete: " + ok + " script(s) OK."),
                        true);
            } else {
                source.sendSuccess(
                        () -> Component.literal("[PyFish] Reload complete: "
                                + ok + " OK, " + errCnt + " error(s). Use /pyfish errors for details."),
                        true);
            }
        } catch (Exception e) {
            LOGGER.error("[PyFish/Commands] Unexpected error during /pyfish reload", e);
            source.sendFailure(Component.literal(
                    "[PyFish] Reload failed unexpectedly: " + e.getMessage()));
        }

        return 1;
    }

    private static int executeErrors(
            CommandContext<CommandSourceStack> ctx,
            ScriptManager serverManager) {

        CommandSourceStack source = ctx.getSource();
        List<ScriptError> errors = serverManager.collectErrors();

        if (errors.isEmpty()) {
            source.sendSuccess(() -> Component.literal("[PyFish] No script errors recorded."), false);
            return 1;
        }

        source.sendSuccess(
                () -> Component.literal("[PyFish] " + errors.size() + " script error(s):"),
                false);

        for (int i = 0; i < errors.size(); i++) {
            final ScriptError err = errors.get(i);
            final int idx = i + 1;
            source.sendSuccess(
                    () -> Component.literal("  " + idx + ". " + err.summary()),
                    false);
            if (!err.pythonTraceback().isBlank()) {
                // Split traceback into lines to avoid a single wall-of-text message
                for (String line : err.pythonTraceback().split("\n")) {
                    source.sendSuccess(() -> Component.literal("     " + line), false);
                }
            }
        }

        return errors.size();
    }

    private static int executeEval(
            CommandContext<CommandSourceStack> ctx,
            ScriptManager serverManager) {

        CommandSourceStack source = ctx.getSource();
        String code = StringArgumentType.getString(ctx, "code");

        LOGGER.info("[PyFish/Commands] /pyfish eval from {}: {}", source.getTextName(), code);

        try {
            String result = serverManager.getRuntime().runSafe(() -> {
                Value val = serverManager.getRuntime().getContext().eval("python", code);
                return val != null ? val.toString() : "null";
            });
            source.sendSuccess(() -> Component.literal("[PyFish/eval] " + result), false);
        } catch (Exception e) {
            String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            source.sendFailure(Component.literal("[PyFish/eval] Error: " + msg));
            LOGGER.warn("[PyFish/Commands] /pyfish eval error: {}", msg);
        }

        return 1;
    }

    private static int executeVersion(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();

        String graalPyVersion = detectGraalPyVersion();
        source.sendSuccess(
                () -> Component.literal("[PyFish] PyFish (mod id: " + PyFish.MODID + ") — GraalPy " + graalPyVersion),
                false);
        return 1;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String detectGraalPyVersion() {
        try {
            // GraalPy exposes its version via the engine API; reading it without an
            // active context requires the Engine class.
            Class<?> engineClass = Class.forName("org.graalvm.polyglot.Engine");
            Object engine = engineClass.getMethod("create").invoke(null);
            Object langs  = engineClass.getMethod("getLanguages").invoke(engine);
            if (langs instanceof java.util.Map<?,?> map) {
                Object python = map.get("python");
                if (python != null) {
                    Object ver = python.getClass().getMethod("getVersion").invoke(python);
                    engineClass.getMethod("close").invoke(engine);
                    return ver != null ? ver.toString() : "unknown";
                }
            }
            engineClass.getMethod("close").invoke(engine);
        } catch (Exception e) {
            LOGGER.debug("[PyFish/Commands] Could not detect GraalPy version: {}", e.getMessage());
        }
        return "unknown";
    }
}
