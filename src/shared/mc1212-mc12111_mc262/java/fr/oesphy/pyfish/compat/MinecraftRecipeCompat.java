package fr.oesphy.pyfish.compat;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import java.util.List;

public final class MinecraftRecipeCompat {
    private MinecraftRecipeCompat() {
    }

    public static void replace(MinecraftServer server, List<RecipeHolder<?>> recipes) {
        var manager = server.getRecipeManager();
        // Match the mapped type, not a field name that changes in production Fabric.
        for (var field : RecipeManager.class.getDeclaredFields()) {
            if (field.getType() != RecipeMap.class) continue;
            try {
                field.setAccessible(true);
                field.set(manager, RecipeMap.create(recipes));
                manager.finalizeRecipeLoading(server.getWorldData().enabledFeatures());
                return;
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Unable to replace the recipe map", e);
            }
        }
        throw new IllegalStateException("Minecraft did not expose a recipe map");
    }
}
