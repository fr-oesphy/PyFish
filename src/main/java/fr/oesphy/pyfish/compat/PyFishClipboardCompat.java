package fr.oesphy.pyfish.compat;

import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public final class PyFishClipboardCompat {
    private static volatile String fallbackClipboard = "";

    private PyFishClipboardCompat() {
    }

    public static void copy(String text) {
        fallbackClipboard = text != null ? text : "";

        if (copyViaAwt(fallbackClipboard)) {
            return;
        }

        if (copyViaGlfwClipboard(fallbackClipboard)) {
            return;
        }

        copyViaMinecraftClient(fallbackClipboard);
    }

    public static String paste() {
        String awtValue = pasteViaAwt();
        if (awtValue != null) {
            fallbackClipboard = awtValue;
            return awtValue;
        }

        String glfwValue = pasteViaGlfwClipboard();
        if (glfwValue != null) {
            fallbackClipboard = glfwValue;
            return glfwValue;
        }

        String clientValue = pasteViaMinecraftClient();
        if (clientValue != null) {
            fallbackClipboard = clientValue;
            return clientValue;
        }

        return fallbackClipboard;
    }

    private static boolean copyViaAwt(String text) {
        if (isHeadless()) {
            return false;
        }

        try {
            Toolkit.getDefaultToolkit()
                    .getSystemClipboard()
                    .setContents(new StringSelection(text), null);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String pasteViaAwt() {
        if (isHeadless()) {
            return null;
        }

        try {
            var clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            if (!clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                return null;
            }

            Object value = clipboard.getData(DataFlavor.stringFlavor);
            return value != null ? value.toString() : "";
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean copyViaGlfwClipboard(String text) {
        Long windowHandle = resolveWindowHandle();
        if (windowHandle == null || windowHandle == 0L) {
            return false;
        }

        try {
            Class<?> glfwClass = Class.forName("org.lwjgl.glfw.GLFW");
            glfwClass.getMethod("glfwSetClipboardString", long.class, CharSequence.class)
                    .invoke(null, windowHandle.longValue(), text);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static String pasteViaGlfwClipboard() {
        Long windowHandle = resolveWindowHandle();
        if (windowHandle == null || windowHandle == 0L) {
            return null;
        }

        try {
            Class<?> glfwClass = Class.forName("org.lwjgl.glfw.GLFW");
            Object value = glfwClass.getMethod("glfwGetClipboardString", long.class)
                    .invoke(null, windowHandle.longValue());
            return value != null ? value.toString() : "";
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static boolean copyViaMinecraftClient(String text) {
        Object keyboardHandler = resolveKeyboardHandler();
        if (keyboardHandler == null) {
            return false;
        }

        try {
            keyboardHandler.getClass().getMethod("setClipboard", String.class).invoke(keyboardHandler, text);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static String pasteViaMinecraftClient() {
        Object keyboardHandler = resolveKeyboardHandler();
        if (keyboardHandler == null) {
            return null;
        }

        try {
            Object value = keyboardHandler.getClass().getMethod("getClipboard").invoke(keyboardHandler);
            return value != null ? value.toString() : "";
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Object resolveKeyboardHandler() {
        Object client = resolveMinecraftClient();
        if (client == null) {
            return null;
        }

        for (String fieldName : new String[] { "keyboardHandler", "keyboard" }) {
            try {
                Field field = client.getClass().getField(fieldName);
                Object value = field.get(client);
                if (value != null) {
                    return value;
                }
            } catch (ReflectiveOperationException ignored) {
                try {
                    Field field = client.getClass().getDeclaredField(fieldName);
                    field.setAccessible(true);
                    Object value = field.get(client);
                    if (value != null) {
                        return value;
                    }
                } catch (ReflectiveOperationException ignoredAgain) {
                    // Try the next candidate.
                }
            }
        }

        return null;
    }

    private static Object resolveMinecraftClient() {
        Object fabricGameInstance = resolveFabricGameInstance();
        if (fabricGameInstance != null) {
            return fabricGameInstance;
        }

        for (String className : new String[] {
                "net.minecraft.client.Minecraft",
                "net.minecraft.client.MinecraftClient"
        }) {
            try {
                Class<?> clientClass = Class.forName(className);
                Method getInstance = clientClass.getMethod("getInstance");
                Object client = getInstance.invoke(null);
                if (client != null) {
                    return client;
                }
            } catch (ReflectiveOperationException ignored) {
                // Try the next candidate.
            }
        }

        return null;
    }

    private static Object resolveFabricGameInstance() {
        try {
            Class<?> loaderClass = Class.forName("net.fabricmc.loader.api.FabricLoader");
            Object loader = loaderClass.getMethod("getInstance").invoke(null);
            Method getGameInstance = loaderClass.getMethod("getGameInstance");
            Object gameInstance = getGameInstance.invoke(loader);
            return gameInstance != null ? gameInstance : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Long resolveWindowHandle() {
        Object client = resolveMinecraftClient();
        if (client == null) {
            return null;
        }

        Object window = resolveWindowObject(client);
        if (window == null) {
            return null;
        }

        return extractWindowHandle(window);
    }

    private static Object resolveWindowObject(Object client) {
        for (String methodName : new String[] { "getWindow" }) {
            try {
                Object value = client.getClass().getMethod(methodName).invoke(client);
                if (value != null) {
                    return value;
                }
            } catch (ReflectiveOperationException ignored) {
                // Try the next candidate.
            }
        }

        for (String fieldName : new String[] { "window" }) {
            Object value = readField(client, fieldName);
            if (value != null) {
                return value;
            }
        }

        for (Field field : client.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }

            try {
                field.setAccessible(true);
                Object candidate = field.get(client);
                if (candidate == null) {
                    continue;
                }

                String typeName = candidate.getClass().getName().toLowerCase();
                if (typeName.contains("window")) {
                    return candidate;
                }

                if (extractWindowHandle(candidate) != null) {
                    return candidate;
                }
            } catch (ReflectiveOperationException ignored) {
                // Keep scanning.
            }
        }

        return null;
    }

    private static Long extractWindowHandle(Object window) {
        for (String methodName : new String[] { "getWindow", "getHandle", "getWindowHandle" }) {
            Long handle = invokeLongMethod(window, methodName);
            if (handle != null && handle.longValue() != 0L) {
                return handle;
            }
        }

        for (Method method : window.getClass().getMethods()) {
            if (Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 0) {
                continue;
            }

            Class<?> returnType = method.getReturnType();
            if (returnType != long.class && returnType != Long.class) {
                continue;
            }

            try {
                Object value = method.invoke(window);
                if (value instanceof Long boxed && boxed.longValue() != 0L) {
                    return boxed;
                }
            } catch (ReflectiveOperationException ignored) {
                // Try the next candidate.
            }
        }

        return null;
    }

    private static Long invokeLongMethod(Object instance, String methodName) {
        try {
            Object value = instance.getClass().getMethod(methodName).invoke(instance);
            if (value instanceof Long boxed) {
                return boxed;
            }
        } catch (ReflectiveOperationException ignored) {
            return null;
        }

        return null;
    }

    private static Object readField(Object instance, String fieldName) {
        try {
            Field field = instance.getClass().getField(fieldName);
            return field.get(instance);
        } catch (ReflectiveOperationException ignored) {
            try {
                Field field = instance.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(instance);
            } catch (ReflectiveOperationException ignoredAgain) {
                return null;
            }
        }
    }

    private static boolean isHeadless() {
        try {
            return GraphicsEnvironment.isHeadless();
        } catch (Throwable ignored) {
            return false;
        }
    }
}
