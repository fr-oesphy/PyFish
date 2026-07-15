package fr.oesphy.pyfish.content.registry;

import fr.oesphy.pyfish.content.PyFishContentManager;

import java.util.Map;

public final class DynamicBlockRegistry {
    private final PyFishContentManager manager;

    public DynamicBlockRegistry(PyFishContentManager manager) {
        this.manager = manager;
    }

    public void registerBlock(String id, Map<String, Object> properties) {
        manager.registerBlock(id, properties);
    }
}
