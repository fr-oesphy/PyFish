package fr.oesphy.pyfish.content;

import com.mojang.serialization.JsonOps;
import fr.oesphy.pyfish.PyFish;
import fr.oesphy.pyfish.compat.MinecraftCompat;
import fr.oesphy.pyfish.compat.MinecraftRecipeCompat;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public final class PyFishRecipeApplier {
    private record Applied(Map<String, RecipeHolder<?>> recipes, Map<String, RecipeHolder<?>> base, long revision, Set<String> removals) {}
    private static final Map<MinecraftServer, Applied> APPLIED = new WeakHashMap<>();
    private PyFishRecipeApplier() {}
    public static synchronized void invalidate(MinecraftServer server) { APPLIED.remove(server); }

    private static Map<String, RecipeHolder<?>> index(Iterable<RecipeHolder<?>> recipes) {
        Map<String, RecipeHolder<?>> result = new LinkedHashMap<>();
        for (var holder : recipes) result.put(MinecraftCompat.resourceKeyIdString(holder.id()), holder);
        return result;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static synchronized boolean applyQueuedRemovals(MinecraftServer server) {
        PyFishResourceGenerator.useLoadedTags(server != null);
        if (server == null) return false;
        var api = PyFishContentManager.getInstance().getApi().getRecipes();
        var editor = api.getEditor();
        var current = index(server.getRecipeManager().getRecipes());
        var previous = APPLIED.get(server);
        if (previous != null && previous.recipes().equals(current) && previous.revision() == editor.revision()
                && previous.removals().equals(api.getRemovedRecipes())) return false;
        // Re-evaluate changed rules against the original snapshot, never against our own edited outputs.
        var base = previous != null && previous.recipes().equals(current) ? previous.base() : current;
        var output = new ArrayList<RecipeHolder<?>>();
        var modified = new ArrayList<String>();
        var removed = new ArrayList<String>();
        var unsupported = new ArrayList<String>();
        Map<String, String> failed = new LinkedHashMap<>();
        var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        Map<String, Set<String>> tagCache = new LinkedHashMap<>();
        java.util.function.Function<String, Set<String>> tags = tag -> tagCache.computeIfAbsent(tag,
                key -> Set.copyOf(PyFishResourceGenerator.getItemsByTag(key)));
        for (var entry : base.entrySet()) {
            String id = entry.getKey();
            var holder = entry.getValue();
            if (api.getRemovedRecipes().contains(id)) { removed.add(id); continue; }
            if (editor.hasRules()) {
                try {
                    var json = Recipe.CODEC.encodeStart(ops, holder.value()).getOrThrow().getAsJsonObject();
                    if (!editor.supports(json.get("type").getAsString())) unsupported.add(id);
                    var result = editor.apply(id, json, tags);
                    if (result.removed()) { removed.add(id); continue; }
                    if (result.changed()) {
                        Recipe<?> recipe = Recipe.CODEC.parse(ops, json).getOrThrow();
                        output.add(new RecipeHolder(holder.id(), recipe));
                        modified.add(id);
                        continue;
                    }
                } catch (RuntimeException e) {
                    // Each recipe is a transaction: failed edits retain the original recipe.
                    failed.put(id, String.valueOf(e.getMessage()));
                    PyFish.LOGGER.warn("[PyFish/Recipes] Kept {} unchanged: {}", id, e.getMessage());
                }
            }
            output.add(holder);
        }
        boolean changed = !index(output).equals(current);
        if (changed) MinecraftRecipeCompat.replace(server, output);
        api.setLastReport(Map.of("modified", List.copyOf(modified), "removed", List.copyOf(removed),
                "unsupported", List.copyOf(unsupported), "failed", Map.copyOf(failed)));
        APPLIED.put(server, new Applied(index(output), base, editor.revision(), Set.copyOf(api.getRemovedRecipes())));
        PyFish.LOGGER.info("[PyFish/Recipes] {} modified, {} removed, {} without schemas, {} failed",
                modified.size(), removed.size(), unsupported.size(), failed.size());
        return changed;
    }
}
