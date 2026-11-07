package fr.oesphy.pyfish.content.api;

import fr.oesphy.pyfish.PyFish;
import fr.oesphy.pyfish.content.PyFishContentManager;
import fr.oesphy.pyfish.content.PyFishResourceGenerator;

import java.util.ArrayList;
import java.util.List;

public final class TagAPI {
    private final PyFishContentManager manager;

    public TagAPI(PyFishContentManager manager) {
        this.manager = manager;
    }

    public void addItemTag(String tag, String... items) {
        String tagId = manager.resolveId(tag);
        for (String item : items) {
            PyFishResourceGenerator.addItemTag(tagId, manager.resolveNamespacedString(item));
        }
        PyFish.LOGGER.info("[PyFish/Content] Added {} item entries to tag {}", items.length, tagId);
    }

    public void addBlockTag(String tag, String... blocks) {
        String tagId = manager.resolveId(tag);
        for (String block : blocks) {
            PyFishResourceGenerator.addBlockTag(tagId, manager.resolveNamespacedString(block));
        }
        PyFish.LOGGER.info("[PyFish/Content] Added {} block entries to tag {}", blocks.length, tagId);
    }

    public List<String> getItemsByTag(String tagId) {
        return new ArrayList<>(PyFishResourceGenerator.getItemsByTag(manager.resolveId(tagId)));
    }

}
