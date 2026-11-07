package fr.oesphy.pyfish.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@Mod("pyfish_ore_tests")
@GameTestHolder("pyfish_ore_tests")
@PrefixGameTestTemplate(false)
public final class NeoForgeOreGameTests {
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void contentBlocks(GameTestHelper helper) {
        ContentRegressionChecks.blocks(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void contentTools(GameTestHelper helper) {
        ContentRegressionChecks.tools(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void contentRecipes(GameTestHelper helper) {
        ContentRegressionChecks.recipes(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void contentInteractions(GameTestHelper helper) {
        ContentRegressionChecks.interactions(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void contentInventoryAndParticles(GameTestHelper helper) {
        ContentRegressionChecks.inventoryAndParticles(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void naturalChunks(GameTestHelper helper) {
        OreGenerationChecks.naturalChunks(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void targetsAndHeight(GameTestHelper helper) {
        OreGenerationChecks.targetsAndHeight(helper);
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void excludedBiome(GameTestHelper helper) {
        OreGenerationChecks.excludedBiome(helper);
    }
}
