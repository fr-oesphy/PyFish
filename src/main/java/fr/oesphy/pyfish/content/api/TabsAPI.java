package fr.oesphy.pyfish.content.api;

import fr.oesphy.pyfish.content.PyFishContentManager;

import java.util.Map;

public final class TabsAPI {
    private final PyFishContentManager manager;

    public TabsAPI(PyFishContentManager manager) {
        this.manager = manager;
    }

    public void create(String id, Map<String, Object> properties) {
        manager.registerTab(id, properties);
    }
}
