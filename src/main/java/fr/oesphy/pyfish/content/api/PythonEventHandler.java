package fr.oesphy.pyfish.content.api;

import fr.oesphy.pyfish.PyFish;
import fr.oesphy.pyfish.content.PyFishScriptContext;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public final class PythonEventHandler {
    private final Map<String, Value> handlers = new HashMap<>();
    private final String namespace;

    public PythonEventHandler(Map<String, Object> events) {
        // NeoForge constructs registered content later, outside the original script scope.
        this.namespace = events instanceof fr.oesphy.pyfish.content.CapturedEvents captured
                ? captured.namespace() : PyFishScriptContext.currentNamespace();
        if (events == null) {
            return;
        }

        // Convert callables while their GraalPy context is current. Re-wrapping a raw Python
        // object later from a Minecraft callback can bind it to the wrong polyglot context.
        events.forEach((name, candidate) -> {
            if (candidate instanceof Value value && value.canExecute()) {
                handlers.put(name, value);
                return;
            }

            try {
                Value captured = Context.getCurrent().asValue(candidate);
                if (captured.canExecute()) {
                    handlers.put(name, captured);
                }
            } catch (Exception e) {
                PyFish.LOGGER.warn("[PyFish/Content] Failed to capture Python handler '{}' : {}", name, e.getMessage());
            }
        });
    }

    public boolean hasHandler(String event) {
        return handlers.containsKey(event);
    }

    public Value call(String event, Object... args) {
        Value handler = handlers.get(event);
        if (handler == null) {
            return null;
        }

        // Item and block hooks often fire on both logical sides. Python content callbacks are
        // server-authoritative to avoid duplicate state changes and client-only host access.
        if (isClientSideInvocation(args)) {
            return null;
        }

        try {
            try (PyFishScriptContext.Scope ignored = PyFishScriptContext.push(namespace)) {
                return handler.execute(args);
            }
        } catch (Exception e) {
            PyFish.LOGGER.error("[PyFish/Content] Error in Python content handler '{}': {}", event, e.getMessage());
            return null;
        }
    }

    public ContentInteraction callInteraction(String event, Object... args) {
        try {
            return ContentInteraction.from(call(event, args));
        } catch (IllegalArgumentException e) {
            PyFish.LOGGER.warn("[PyFish/Content] Invalid result for '{}': {}", event, e.getMessage());
            return ContentInteraction.PASS;
        }
    }

    private boolean isClientSideInvocation(Object[] args) {
        if (args == null) {
            return false;
        }

        for (Object arg : args) {
            Boolean clientSide = extractClientSideFlag(arg);
            if (clientSide != null) {
                return clientSide;
            }
        }

        return false;
    }

    private Boolean extractClientSideFlag(Object candidate) {
        if (candidate == null) {
            return null;
        }

        // Across supported mappings, Level exposes this as either a field or an accessor;
        // context wrappers may expose the Level one method deeper.
        Boolean directField = readBooleanField(candidate, "isClientSide");
        if (directField != null) {
            return directField;
        }

        Boolean directFlag = invokeBoolean(candidate, "isClientSide");
        if (directFlag != null) {
            return directFlag;
        }

        for (String accessor : new String[] { "getLevel", "level" }) {
            Object nested = invokeNoArg(candidate, accessor);
            if (nested == null) {
                continue;
            }

            Boolean nestedField = readBooleanField(nested, "isClientSide");
            if (nestedField != null) {
                return nestedField;
            }

            Boolean nestedFlag = invokeBoolean(nested, "isClientSide");
            if (nestedFlag != null) {
                return nestedFlag;
            }
        }

        return null;
    }

    private Boolean invokeBoolean(Object target, String methodName) {
        Object result = invokeNoArg(target, methodName);
        return result instanceof Boolean bool ? bool : null;
    }

    private Boolean readBooleanField(Object target, String fieldName) {
        try {
            return target.getClass().getField(fieldName).getBoolean(target);
        } catch (ReflectiveOperationException ignored) {
            try {
                var field = target.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.getBoolean(target);
            } catch (ReflectiveOperationException ignoredAgain) {
                return null;
            }
        }
    }

    private Object invokeNoArg(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
