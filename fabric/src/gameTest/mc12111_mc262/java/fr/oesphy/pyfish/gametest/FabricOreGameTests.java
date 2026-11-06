package fr.oesphy.pyfish.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public final class FabricOreGameTests {
    @GameTest(structure = "pyfish_ore_tests:empty", maxTicks = 400)
    public void naturalChunks(GameTestHelper helper) {
        OreGenerationChecks.naturalChunks(helper);
    }

    @GameTest(structure = "pyfish_ore_tests:empty", maxTicks = 400)
    public void targetsAndHeight(GameTestHelper helper) {
        OreGenerationChecks.targetsAndHeight(helper);
    }

    @GameTest(structure = "pyfish_ore_tests:empty", maxTicks = 400)
    public void excludedBiome(GameTestHelper helper) {
        OreGenerationChecks.excludedBiome(helper);
    }
}
