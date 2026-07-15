package fr.oesphy.pyfish.content.registry;

import fr.oesphy.pyfish.content.PyFishContentManager;

import java.util.Map;

public final class DynamicItemRegistry {
    private final PyFishContentManager manager;

    public DynamicItemRegistry(PyFishContentManager manager) {
        this.manager = manager;
    }

    public void registerItem(String id, Map<String, Object> properties) {
        manager.registerItem(id, properties);
    }

    public void registerFood(String id, int nutrition, double saturation, Map<String, Object> properties) {
        manager.registerFood(id, nutrition, saturation, properties);
    }

    public void registerTool(String id, String type, Map<String, Object> properties) {
        manager.registerTool(id, type, properties);
    }
}
