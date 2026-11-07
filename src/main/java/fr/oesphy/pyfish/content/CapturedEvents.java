package fr.oesphy.pyfish.content;

import java.util.HashMap;

public final class CapturedEvents extends HashMap<String, Object> {
    private final String namespace = PyFishScriptContext.currentNamespace();

    public String namespace() {
        return namespace;
    }
}
