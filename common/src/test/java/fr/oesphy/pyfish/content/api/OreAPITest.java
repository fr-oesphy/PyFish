package fr.oesphy.pyfish.content.api;

import com.google.gson.JsonObject;
import fr.oesphy.pyfish.content.OreBiomeSelection;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OreAPITest {
    @Test
    void generatesStoneAndDeepslateTargetsAndPlacement() {
        OreAPI.Definition ore = OreAPI.Definition.parse("example:tin_ore", Map.of(
                "block", "tin_ore",
                "deepslateBlock", "deepslate_tin_ore",
                "size", 8,
                "count", 12,
                "minY", -32,
                "maxY", 48,
                "distribution", "trapezoid"));

        JsonObject config = ore.configuredFeature().getAsJsonObject("config");
        assertEquals("example:tin_ore", config.getAsJsonArray("targets").get(0).getAsJsonObject()
                .getAsJsonObject("state").get("Name").getAsString());
        assertEquals("minecraft:deepslate_ore_replaceables", config.getAsJsonArray("targets").get(1)
                .getAsJsonObject().getAsJsonObject("target").get("tag").getAsString());
        assertEquals("example:deepslate_tin_ore", config.getAsJsonArray("targets").get(1)
                .getAsJsonObject().getAsJsonObject("state").get("Name").getAsString());
        assertEquals("minecraft:trapezoid", ore.placedFeature().getAsJsonArray("placement").get(2)
                .getAsJsonObject().getAsJsonObject("height").get("type").getAsString());
        assertEquals(12, ore.placedFeature().getAsJsonArray("placement").get(0)
                .getAsJsonObject().get("count").getAsInt());
    }

    @Test
    void resolvesExplicitBiomeIdsAndTargetTags() {
        OreAPI.Definition ore = OreAPI.Definition.parse("example:custom_ore", Map.of(
                "targets", List.of(Map.of("replaceable", "#minecraft:stone_ore_replaceables", "block", "custom_ore")),
                "biomes", List.of("minecraft:nether_wastes", "minecraft:crimson_forest")));

        assertEquals(OreBiomeSelection.Kind.BIOMES, ore.biomes().kind());
        assertEquals(2, ore.biomes().values().size());
        assertEquals("minecraft:stone_ore_replaceables", ore.configuredFeature().getAsJsonObject("config")
                .getAsJsonArray("targets").get(0).getAsJsonObject().getAsJsonObject("target")
                .get("tag").getAsString());
    }

    @Test
    void rejectsInvalidHeightsAndEmptyBiomeSelections() {
        assertThrows(IllegalArgumentException.class, () -> OreAPI.Definition.parse("example:ore",
                Map.of("block", "ore", "minY", 20, "maxY", 10)));
        assertThrows(IllegalArgumentException.class, () -> OreAPI.Definition.parse("example:ore",
                Map.of("block", "ore", "biomes", List.of())));
    }
}
