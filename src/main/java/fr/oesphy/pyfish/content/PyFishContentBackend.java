package fr.oesphy.pyfish.content;

import java.util.Map;

public interface PyFishContentBackend {
    void registerItem(String namespace, String path, Map<String, Object> properties);

    void registerFood(String namespace, String path, int nutrition, double saturation, Map<String, Object> properties);

    void registerTool(String namespace, String path, String type, Map<String, Object> properties);

    void registerBlock(String namespace, String path, Map<String, Object> blockProperties);

    void registerTab(String namespace, String path, Map<String, Object> properties);

    void registerOrePlacement(String featureId, OreBiomeSelection biomes);
}
