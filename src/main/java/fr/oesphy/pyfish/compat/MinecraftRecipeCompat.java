package fr.oesphy.pyfish.compat;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.RecipeHolder;
import java.util.List;

public final class MinecraftRecipeCompat {
    private MinecraftRecipeCompat() {
    }

    public static void replace(MinecraftServer server, List<RecipeHolder<?>> recipes) {
        server.getRecipeManager().replaceRecipes(recipes);
    }
}
