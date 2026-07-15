package fr.oesphy.pyfish.content;

import fr.oesphy.pyfish.content.api.PyFishContentAPI;
import fr.oesphy.pyfish.content.api.RecipeAPI;
import fr.oesphy.pyfish.content.api.TagAPI;
import fr.oesphy.pyfish.content.api.TabsAPI;
import fr.oesphy.pyfish.content.api.TextureAPI;
import fr.oesphy.pyfish.content.registry.DynamicBlockRegistry;
import fr.oesphy.pyfish.content.registry.DynamicItemRegistry;

public final class PyFishContentBridge {
    private PyFishContentBridge() {
    }

    private static PyFishContentAPI api() {
        return PyFishContentManager.getInstance().getApi();
    }

    public static RecipeAPI getRecipes() {
        return api().getRecipes();
    }

    public static DynamicItemRegistry getItems() {
        return api().getItems();
    }

    public static DynamicBlockRegistry getBlocks() {
        return api().getBlocks();
    }

    public static TagAPI getTags() {
        return api().getTags();
    }

    public static TextureAPI getTextures() {
        return api().getTextures();
    }

    public static TabsAPI getTabs() {
        return api().getTabs();
    }
}
