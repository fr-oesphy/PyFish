package fr.oesphy.pyfish.gametest;

import java.util.function.Consumer;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod("pyfish_ore_tests")
public final class NeoForgeOreGameTests {
    public NeoForgeOreGameTests(IEventBus bus) {
        DeferredRegister<Consumer<GameTestHelper>> functions = DeferredRegister.create(Registries.TEST_FUNCTION, "pyfish_ore_tests");
        functions.register("natural_chunks", () -> OreGenerationChecks::naturalChunks);
        functions.register("targets_and_height", () -> OreGenerationChecks::targetsAndHeight);
        functions.register("excluded_biome", () -> OreGenerationChecks::excludedBiome);
        functions.register("content_blocks", () -> ContentRegressionChecks::blocks);
        functions.register("content_tools", () -> ContentRegressionChecks::tools);
        functions.register("content_recipes", () -> ContentRegressionChecks::recipes);
        functions.register("content_interactions", () -> ContentRegressionChecks::interactions);
        functions.register("content_inventory_and_particles", () -> ContentRegressionChecks::inventoryAndParticles);
        functions.register(bus);
        bus.addListener(NeoForgeOreGameTests::registerTests);
    }

    private static ResourceKey<Consumer<GameTestHelper>> key(String name) {
        return ResourceKey.create(Registries.TEST_FUNCTION, Identifier.fromNamespaceAndPath("pyfish_ore_tests", name));
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        var environment = event.registerEnvironment(Identifier.fromNamespaceAndPath("pyfish_ore_tests", "default"));
        var data = new TestData<>(environment, Identifier.fromNamespaceAndPath("pyfish_ore_tests", "empty"), 400, 0, true);
        for (String name : new String[] {"natural_chunks", "targets_and_height", "excluded_biome",
                "content_blocks", "content_tools", "content_recipes", "content_interactions", "content_inventory_and_particles"}) {
            event.registerTest(Identifier.fromNamespaceAndPath("pyfish_ore_tests", name),
                    new FunctionGameTestInstance(key(name), data));
        }
    }
}
