package fr.oesphy.pyfish.content;

import fr.oesphy.pyfish.PyFish;

public final class PyFishScriptContext {
    // Content callbacks can be nested or arrive on different game threads. A scoped
    // ThreadLocal preserves the declaring mod's namespace without a process-wide mutable id.
    private static final ThreadLocal<String> CURRENT_NAMESPACE = ThreadLocal.withInitial(() -> PyFish.MODID);

    private PyFishScriptContext() {
    }

    public static Scope push(String modId) {
        String previous = CURRENT_NAMESPACE.get();
        String next = modId != null && !modId.isBlank() ? modId : PyFish.MODID;
        CURRENT_NAMESPACE.set(next);
        return new Scope(previous);
    }

    public static String currentNamespace() {
        return CURRENT_NAMESPACE.get();
    }

    public static final class Scope implements AutoCloseable {
        private final String previousNamespace;
        private boolean closed;

        private Scope(String previousNamespace) {
            this.previousNamespace = previousNamespace;
        }

        @Override
        public void close() {
            if (!closed) {
                CURRENT_NAMESPACE.set(previousNamespace);
                closed = true;
            }
        }
    }
}