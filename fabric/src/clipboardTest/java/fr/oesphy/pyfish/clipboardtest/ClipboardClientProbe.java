package fr.oesphy.pyfish.clipboardtest;

import com.google.gson.GsonBuilder;
import fr.oesphy.pyfish.PyFish;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;

import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Read-only diagnostic; neither clipboard text nor its hash is persisted. */
public final class ClipboardClientProbe implements ClientModInitializer {
    private boolean ready;
    private int ticks;

    @Override
    public void onInitializeClient() {
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> ready = true);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (ready && ++ticks == 40) compare();
        });
    }

    private static Object minecraftClipboard() throws ReflectiveOperationException {
        var client = net.minecraft.client.Minecraft.getInstance();
        Object keyboard = client.getClass().getField("keyboardHandler").get(client);
        return keyboard.getClass().getMethod("getClipboard").invoke(keyboard);
    }

    private static void compare() {
        Map<String, Object> report = new LinkedHashMap<>();
        try {
            long window = GLFW.glfwGetCurrentContext();
            report.put("thread", Thread.currentThread().getName());
            report.put("headless", GraphicsEnvironment.isHeadless());
            report.put("glfw_context_present", window != 0);
            report.put("window_visible", window != 0 && GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_VISIBLE) != 0);
            String expected = null;
            try {
                var clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                boolean text = clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor);
                if (text) expected = (String) clipboard.getData(DataFlavor.stringFlavor);
                report.put("awt", text ? "read_ok" : "accessible_without_text");
            } catch (Throwable error) {
                report.put("awt", "error:" + error.getClass().getSimpleName());
            }
            GLFW.glfwGetError((PointerBuffer) null);
            String glfw = window == 0 ? null : GLFW.glfwGetClipboardString(window);
            int glfwError = GLFW.glfwGetError((PointerBuffer) null);
            report.put("glfw_error", glfwError);
            report.put("glfw_read_ok", glfw != null && glfwError == 0);
            if (expected == null && glfw != null && glfwError == 0) expected = glfw;
            GLFW.glfwGetError((PointerBuffer) null);
            report.put("minecraft_nonnull", minecraftClipboard() != null);
            report.put("minecraft_error", GLFW.glfwGetError((PointerBuffer) null));

            var runtime = PyFish.getPythonRuntime();
            var contextField = runtime.getClass().getDeclaredField("sharedContext");
            contextField.setAccessible(true);
            Context context = (Context) contextField.get(runtime);
            if (context == null) throw new IllegalStateException("Startup did not create the real PyFish context");
            String python = """
                    def compare(expected):
                        import json, ctypes, pyperclip
                        library = ctypes.WinDLL('user32', use_last_error=True)
                        opening, closing = library.OpenClipboard, library.CloseClipboard
                        opening.argtypes, opening.restype = [ctypes.c_void_p], ctypes.c_int
                        closing.argtypes, closing.restype = [], ctypes.c_int
                        ctypes.set_last_error(0)
                        opened = opening(None)
                        error = ctypes.get_last_error()
                        if opened:
                            closing()
                        result = {'win32_open': bool(opened), 'win32_error': error}
                        try:
                            value = pyperclip.paste()
                            result['pyperclip'] = 'read_ok'
                            result['matches_java_read'] = 'no_successful_OS_read' if expected is None else value == expected
                        except Exception as failure:
                            result['pyperclip'] = 'error:' + type(failure).__name__
                        return json.dumps(result)
                    compare
                    """;
            String nativeResult = context.eval(Source.newBuilder("python", python, "clipboard-client-comparison.py")
                    .option("python.NewGlobals", "true").buildLiteral()).execute(expected).asString();
            report.put("python", com.google.gson.JsonParser.parseString(nativeResult));
        } catch (Throwable error) {
            report.put("diagnostic_error", error.getClass().getName());
        }
        try {
            String json = new GsonBuilder().setPrettyPrinting().create().toJson(report);
            Files.writeString(Path.of("clipboard-client-report.json"), json);
            PyFish.LOGGER.info("[PyFish/ClipboardClientTest] {}", json);
        } catch (Exception error) {
            PyFish.LOGGER.error("[PyFish/ClipboardClientTest] Could not write the status report", error);
        }
    }
}
