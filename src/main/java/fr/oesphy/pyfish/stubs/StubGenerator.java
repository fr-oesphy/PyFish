package fr.oesphy.pyfish.stubs;

import fr.oesphy.pyfish.PyFish;
import org.slf4j.Logger;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates Python stub files ({@code .pyi}) for all PyFish Java API classes.
 *
 * <p>Stubs are written to {@code pyfish/stubs/} in the game directory and give
 * IDEs (VSCode + Pylance, PyCharm) auto-complete for the bindings injected by
 * {@link fr.oesphy.pyfish.script.BindingRegistry}.
 *
 * <p>Generation is triggered automatically on the first startup and whenever
 * {@code /pyfish reload} is run.
 */
public final class StubGenerator {

    private static final Logger LOGGER = PyFish.LOGGER;

    /** Classes for which we generate stubs, keyed by the stub filename (without .pyi). */
    private static final Map<String, List<Class<?>>> STUB_CLASSES = new LinkedHashMap<>();

    static {
        // Wrappers
        STUB_CLASSES.put("wrappers", List.of(
                fr.oesphy.pyfish.api.wrapper.IdentifierWrapper.class,
                fr.oesphy.pyfish.api.wrapper.PosWrapper.class,
                fr.oesphy.pyfish.api.wrapper.NbtWrapper.class,
                fr.oesphy.pyfish.api.wrapper.TextWrapper.class,
                fr.oesphy.pyfish.api.wrapper.ItemStackWrapper.class,
                fr.oesphy.pyfish.api.wrapper.EntityWrapper.class,
                fr.oesphy.pyfish.api.wrapper.PlayerWrapper.class,
                fr.oesphy.pyfish.api.wrapper.LevelWrapper.class
        ));

        // Event system
        STUB_CLASSES.put("events", List.of(
                fr.oesphy.pyfish.event.EventFacade.class,
                fr.oesphy.pyfish.event.EventGroup.class,
                fr.oesphy.pyfish.event.EventHandler.class,
                fr.oesphy.pyfish.event.PyEvent.class,
                fr.oesphy.pyfish.event.EventResult.class
        ));

        // Script utilities
        STUB_CLASSES.put("script", List.of(
                fr.oesphy.pyfish.script.ConsoleApi.class,
                fr.oesphy.pyfish.api.util.TickScheduler.class
        ));

        // Configuration
        STUB_CLASSES.put("config", List.of(
                fr.oesphy.pyfish.CommonProperties.class
        ));

        // Resource generation
        STUB_CLASSES.put("packs", List.of(
                fr.oesphy.pyfish.generator.VirtualAssetPack.class,
                fr.oesphy.pyfish.generator.VirtualDataPack.class
        ));
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Generates all stub files into the {@code pyfish/stubs/} directory.
     *
     * @param stubsDir the directory to write stubs into; created if absent
     */
    public static void generate(Path stubsDir) {
        try {
            Files.createDirectories(stubsDir);
        } catch (IOException e) {
            LOGGER.warn("[PyFish/StubGenerator] Could not create stubs directory {}: {}", stubsDir, e.getMessage());
            return;
        }

        // Write a __init__.pyi so the stubs directory is a package
        writeFile(stubsDir.resolve("__init__.pyi"), "# PyFish API stubs – auto-generated, do not edit\n");

        int total = 0;
        for (Map.Entry<String, List<Class<?>>> entry : STUB_CLASSES.entrySet()) {
            String fileName = entry.getKey() + ".pyi";
            String content = buildStub(entry.getKey(), entry.getValue());
            if (writeFile(stubsDir.resolve(fileName), content)) {
                total++;
                LOGGER.debug("[PyFish/StubGenerator] Wrote {}", fileName);
            }
        }

        LOGGER.info("[PyFish/StubGenerator] Generated {} stub file(s) in {}", total, stubsDir);
    }

    /**
     * Generates stubs to a {@code stubs/} subdirectory under the given pyfish root.
     *
     * @param pyfishRoot the pyfish script root directory (e.g. {@code gameDir/pyfish})
     */
    public static void generateUnder(Path pyfishRoot) {
        generate(pyfishRoot.resolve("stubs"));
    }

    // -------------------------------------------------------------------------
    // Stub building
    // -------------------------------------------------------------------------

    /**
     * Builds the content of a single {@code .pyi} stub file covering the
     * given list of classes.
     *
     * @param moduleName logical module name (used in the header comment)
     * @param classes    classes to include in this stub file
     * @return the full text of the {@code .pyi} file
     */
    public static String buildStub(String moduleName, List<Class<?>> classes) {
        StringBuilder sb = new StringBuilder();
        sb.append("# PyFish API stubs – ").append(moduleName).append(" – auto-generated, do not edit\n");
        sb.append("from typing import Any, Optional, List, Set, Callable\n\n");

        for (Class<?> cls : classes) {
            appendClassStub(sb, cls);
        }

        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private static void appendClassStub(StringBuilder sb, Class<?> cls) {
        String simpleName = cls.getSimpleName();
        Class<?> superClass = cls.getSuperclass();
        boolean hasNonObjectSuper = superClass != null
                && superClass != Object.class
                && !superClass.getName().startsWith("java.");

        if (hasNonObjectSuper) {
            sb.append("class ").append(simpleName).append("(").append(superClass.getSimpleName()).append("):\n");
        } else {
            sb.append("class ").append(simpleName).append(":\n");
        }

        List<Method> publicMethods = getPublicMethods(cls);
        if (publicMethods.isEmpty()) {
            sb.append("    ...\n\n");
            return;
        }

        for (Method method : publicMethods) {
            appendMethodStub(sb, method);
        }
        sb.append("\n");
    }

    private static List<Method> getPublicMethods(Class<?> cls) {
        List<Method> result = new ArrayList<>();
        // getDeclaredMethods gives us only the methods declared directly in this class
        // which avoids duplicating inherited Object methods
        for (Method m : cls.getDeclaredMethods()) {
            if (!Modifier.isPublic(m.getModifiers())) {
                continue;
            }
            // Skip synthetic/bridge methods
            if (m.isBridge() || m.isSynthetic()) {
                continue;
            }
            result.add(m);
        }
        // Sort for deterministic output
        result.sort((a, b) -> {
            // Static methods first, then alphabetical
            boolean aStatic = Modifier.isStatic(a.getModifiers());
            boolean bStatic = Modifier.isStatic(b.getModifiers());
            if (aStatic != bStatic) {
                return aStatic ? -1 : 1;
            }
            return a.getName().compareTo(b.getName());
        });
        return result;
    }

    private static void appendMethodStub(StringBuilder sb, Method method) {
        boolean isStatic = Modifier.isStatic(method.getModifiers());
        String returnType = javaTypeToPython(method.getReturnType());
        String name = method.getName();

        if (isStatic) {
            sb.append("    @staticmethod\n");
        }

        sb.append("    def ").append(name).append("(");
        List<String> params = new ArrayList<>();
        if (!isStatic) {
            params.add("self");
        }
        for (Parameter p : method.getParameters()) {
            params.add(p.getName() + ": " + javaTypeToPython(p.getType()));
        }
        sb.append(String.join(", ", params));
        sb.append(") -> ").append(returnType).append(": ...\n");
    }

    private static String javaTypeToPython(Class<?> type) {
        if (type == void.class || type == Void.class) return "None";
        if (type == boolean.class || type == Boolean.class) return "bool";
        if (type == int.class || type == Integer.class
                || type == long.class || type == Long.class
                || type == short.class || type == Short.class
                || type == byte.class || type == Byte.class) return "int";
        if (type == float.class || type == Float.class
                || type == double.class || type == Double.class) return "float";
        if (type == String.class || type == CharSequence.class) return "str";
        if (type == char.class || type == Character.class) return "str";
        if (type.isArray()) return "List[" + javaTypeToPython(type.getComponentType()) + "]";
        if (type == List.class || type == java.util.ArrayList.class
                || type == java.util.Collection.class) return "List[Any]";
        if (type == java.util.Set.class || type == java.util.HashSet.class
                || type == java.util.LinkedHashSet.class) return "Set[Any]";
        if (type == java.util.Map.class || type == java.util.HashMap.class
                || type == java.util.LinkedHashMap.class) return "dict";
        // For Minecraft types, use the simple name
        String simpleName = type.getSimpleName();
        // Known wrapper types map to themselves (they're in the stubs)
        if (type.getPackageName().startsWith("fr.oesphy.pyfish")) {
            return simpleName;
        }
        return "Any";
    }

    private static boolean writeFile(Path path, String content) {
        try {
            Files.writeString(path, content, StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            LOGGER.warn("[PyFish/StubGenerator] Failed to write {}: {}", path, e.getMessage());
            return false;
        }
    }

    // Non-instantiable
    private StubGenerator() {}
}
