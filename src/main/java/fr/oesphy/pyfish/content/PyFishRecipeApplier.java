package fr.oesphy.pyfish.content;

import fr.oesphy.pyfish.PyFish;
import fr.oesphy.pyfish.compat.MinecraftCompat;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class PyFishRecipeApplier {
    private PyFishRecipeApplier() {
    }

    @SuppressWarnings("unchecked")
    public static void applyQueuedRemovals(MinecraftServer server) {
        Set<String> removed = PyFishContentManager.getInstance().getApi().getRecipes().getRemovedRecipes();
        if (server == null || removed.isEmpty()) {
            return;
        }

        RecipeManager recipeManager = server.getRecipeManager();
        try {
            // RecipeManager keeps parallel by-id and by-type indexes. Updating only one leaves
            // recipes visible to some consumers until the next datapack reload.
            Field recipesField = findField(RecipeManager.class, "recipes", "byType");
            Field byNameField = findField(RecipeManager.class, "byName", "recipesByName");

            recipesField.setAccessible(true);
            byNameField.setAccessible(true);

            Object recipesObject = recipesField.get(recipeManager);
            Map<Object, RecipeHolder<?>> byName = new HashMap<>((Map<Object, RecipeHolder<?>>) byNameField.get(recipeManager));

            // Mojang changed the by-type container during 1.21. Copy-on-write supports both
            // layouts and preserves the immutable snapshots expected by vanilla readers.
            if (recipesObject instanceof com.google.common.collect.Multimap<?, ?> rawMultimap) {
                com.google.common.collect.Multimap<RecipeType<?>, RecipeHolder<?>> byType =
                        com.google.common.collect.ArrayListMultimap.create(
                                (com.google.common.collect.Multimap<RecipeType<?>, RecipeHolder<?>>) rawMultimap);

                for (String id : removed) {
                    Object parsedId = MinecraftCompat.parseId(id);
                    RecipeHolder<?> holder = byName.remove(parsedId);
                    if (holder != null) {
                        byType.remove(holder.value().getType(), holder);
                        PyFish.LOGGER.info("[PyFish/Content] Removed recipe {}", id);
                    }
                }

                recipesField.set(recipeManager, com.google.common.collect.ImmutableMultimap.copyOf(byType));
                byNameField.set(recipeManager, com.google.common.collect.ImmutableMap.copyOf(byName));
            } else {
                Map<RecipeType<?>, Map<Object, RecipeHolder<?>>> original =
                        (Map<RecipeType<?>, Map<Object, RecipeHolder<?>>>) recipesObject;
                Map<RecipeType<?>, Map<Object, RecipeHolder<?>>> mutable = new HashMap<>();

                for (Map.Entry<RecipeType<?>, Map<Object, RecipeHolder<?>>> entry : original.entrySet()) {
                    mutable.put(entry.getKey(), new HashMap<>(entry.getValue()));
                }

                for (String id : removed) {
                    Object parsedId = MinecraftCompat.parseId(id);
                    RecipeHolder<?> holder = byName.remove(parsedId);
                    if (holder != null) {
                        Map<Object, RecipeHolder<?>> typeMap = mutable.get(holder.value().getType());
                        if (typeMap != null) {
                            typeMap.remove(parsedId);
                        }
                        PyFish.LOGGER.info("[PyFish/Content] Removed recipe {}", id);
                    }
                }

                Map<RecipeType<?>, Map<Object, RecipeHolder<?>>> immutable = new HashMap<>();
                for (Map.Entry<RecipeType<?>, Map<Object, RecipeHolder<?>>> entry : mutable.entrySet()) {
                    immutable.put(entry.getKey(), com.google.common.collect.ImmutableMap.copyOf(entry.getValue()));
                }

                recipesField.set(recipeManager, com.google.common.collect.ImmutableMap.copyOf(immutable));
                byNameField.set(recipeManager, com.google.common.collect.ImmutableMap.copyOf(byName));
            }
        } catch (Exception e) {
            PyFish.LOGGER.error("[PyFish/Content] Failed to apply queued recipe removals: {}", e.getMessage(), e);
        }
    }

    private static Field findField(Class<?> owner, String... names) throws NoSuchFieldException {
        for (String name : names) {
            try {
                return owner.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException("Unable to find expected field on " + owner.getName());
    }
}