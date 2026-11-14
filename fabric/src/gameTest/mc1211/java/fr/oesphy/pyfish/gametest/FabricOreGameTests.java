package fr.oesphy.pyfish.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public final class FabricOreGameTests {
    @GameTest(template = "pyfish_ore_tests:empty", timeoutTicks = 400)
    public void contentBlocks(GameTestHelper helper) {
        ContentRegressionChecks.blocks(helper);
    }

    @GameTest(template = "pyfish_ore_tests:empty", timeoutTicks = 400)
    public void contentTools(GameTestHelper helper) {
        ContentRegressionChecks.tools(helper);
    }

    @GameTest(template = "pyfish_ore_tests:empty", timeoutTicks = 400)
    public void contentRecipes(GameTestHelper helper) {
        ContentRegressionChecks.recipes(helper);
    }

    @GameTest(template = "pyfish_ore_tests:empty", timeoutTicks = 400)
    public void contentInteractions(GameTestHelper helper) {
        ContentRegressionChecks.interactions(helper);
    }

    @GameTest(template = "pyfish_ore_tests:empty", timeoutTicks = 400)
    public void contentInventoryAndParticles(GameTestHelper helper) {
        ContentRegressionChecks.inventoryAndParticles(helper);
    }

    @GameTest(template = "pyfish_ore_tests:empty", timeoutTicks = 400)
    public void naturalChunks(GameTestHelper helper) {
        OreGenerationChecks.naturalChunks(helper);
    }

    @GameTest(template = "pyfish_ore_tests:empty", timeoutTicks = 400)
    public void targetsAndHeight(GameTestHelper helper) {
        OreGenerationChecks.targetsAndHeight(helper);
    }

    @GameTest(template = "pyfish_ore_tests:empty", timeoutTicks = 400)
    public void excludedBiome(GameTestHelper helper) {
        OreGenerationChecks.excludedBiome(helper);
    }
}
