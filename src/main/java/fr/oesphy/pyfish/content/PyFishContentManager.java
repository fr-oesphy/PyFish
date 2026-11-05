package fr.oesphy.pyfish.content;

import fr.oesphy.pyfish.PyFish;
import fr.oesphy.pyfish.content.api.PyFishContentAPI;

import java.util.Map;

public final class PyFishContentManager {
    private static final PyFishContentManager INSTANCE = new PyFishContentManager();

    private final PyFishContentAPI api = new PyFishContentAPI(this);
    private PyFishContentBackend backend;

    private PyFishContentManager() {
    }

    public static PyFishContentManager getInstance() {
        return INSTANCE;
    }

    public static void bootstrap(PyFishContentBackend backend) {
        PyFishContentManager manager = getInstance();
        if (manager.backend == null) {
            manager.backend = backend;
            PyFish.LOGGER.info("[PyFish/Content] Bootstrapped content backend: {}", backend.getClass().getSimpleName());
        }
    }

    public PyFishContentAPI getApi() {
        return api;
    }

    public String resolveId(String rawId) {
        return PyFishResourceGenerator.resolveIdentifier(rawId, PyFishScriptContext.currentNamespace());
    }

    public String resolveNamespacedString(String rawId) {
        return PyFishResourceGenerator.resolveNamespacedString(rawId, PyFishScriptContext.currentNamespace());
    }

    public String resolveTextureReference(String rawId) {
        return PyFishResourceGenerator.resolveTextureReference(rawId, PyFishScriptContext.currentNamespace());
    }

    public void registerItem(String id, Map<String, Object> properties) {
        String resolvedId = resolveId(id);
        requireBackend().registerItem(PyFishResourceGenerator.namespaceOf(resolvedId),
                PyFishResourceGenerator.pathOf(resolvedId), properties);
    }

    public void registerFood(String id, int nutrition, double saturation, Map<String, Object> properties) {
        String resolvedId = resolveId(id);
        requireBackend().registerFood(PyFishResourceGenerator.namespaceOf(resolvedId),
                PyFishResourceGenerator.pathOf(resolvedId), nutrition, saturation, properties);
    }

    public void registerTool(String id, String type, Map<String, Object> properties) {
        String resolvedId = resolveId(id);
        requireBackend().registerTool(PyFishResourceGenerator.namespaceOf(resolvedId),
                PyFishResourceGenerator.pathOf(resolvedId), type, properties);
    }

    public void registerBlock(String id, Map<String, Object> properties) {
        String resolvedId = resolveId(id);
        requireBackend().registerBlock(PyFishResourceGenerator.namespaceOf(resolvedId),
                PyFishResourceGenerator.pathOf(resolvedId), properties);
    }

    public void registerTab(String id, Map<String, Object> properties) {
        String resolvedId = resolveId(id);
        requireBackend().registerTab(PyFishResourceGenerator.namespaceOf(resolvedId),
                PyFishResourceGenerator.pathOf(resolvedId), properties);
    }

    public void registerOrePlacement(String featureId, OreBiomeSelection biomes) {
        requireBackend().registerOrePlacement(featureId, biomes);
    }

    private PyFishContentBackend requireBackend() {
        if (backend == null) {
            throw new IllegalStateException("PyFish content backend has not been bootstrapped yet");
        }
        return backend;
    }
}
