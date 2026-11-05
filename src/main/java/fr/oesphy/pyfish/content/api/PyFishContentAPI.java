package fr.oesphy.pyfish.content.api;

import fr.oesphy.pyfish.content.PyFishContentManager;
import fr.oesphy.pyfish.content.registry.DynamicBlockRegistry;
import fr.oesphy.pyfish.content.registry.DynamicItemRegistry;

public final class PyFishContentAPI {
    private final RecipeAPI recipes;
    private final DynamicItemRegistry items;
    private final DynamicBlockRegistry blocks;
    private final TagAPI tags;
    private final TextureAPI textures;
    private final TabsAPI tabs;
    private final OreAPI ores;

    public PyFishContentAPI(PyFishContentManager manager) {
        this.recipes = new RecipeAPI(manager);
        this.items = new DynamicItemRegistry(manager);
        this.blocks = new DynamicBlockRegistry(manager);
        this.tags = new TagAPI(manager);
        this.textures = new TextureAPI(manager);
        this.tabs = new TabsAPI(manager);
        this.ores = new OreAPI(manager);
    }

    public RecipeAPI getRecipes() {
        return recipes;
    }

    public DynamicItemRegistry getItems() {
        return items;
    }

    public DynamicBlockRegistry getBlocks() {
        return blocks;
    }

    public TagAPI getTags() {
        return tags;
    }

    public TextureAPI getTextures() {
        return textures;
    }

    public TabsAPI getTabs() {
        return tabs;
    }

    public OreAPI getOres() {
        return ores;
    }
}
