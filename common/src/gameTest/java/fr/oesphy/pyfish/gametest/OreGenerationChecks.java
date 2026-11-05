package fr.oesphy.pyfish.gametest;

import fr.oesphy.pyfish.PyFish;
import fr.oesphy.pyfish.compat.MinecraftCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.Heightmap;
import java.util.Set;

public final class OreGenerationChecks {
    private OreGenerationChecks() {
    }

    public static void naturalChunks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel().getServer().getLevel(
                MinecraftCompat.createResourceKey(Registries.DIMENSION, "pyfish_ore_tests:overworld"));
        check(level != null, "Noise-generated test dimension was not loaded");
        Block stoneOre = block("stone_ore");
        Block deepOre = block("deep_ore");
        int stoneCount = 0;
        int deepCount = 0;
        // Request fresh FULL chunks through the normal generator, with no manual placement.
        for (int cx = 32; cx < 34; cx++) {
            for (int cz = 32; cz < 34; cz++) {
                var chunk = level.getChunk(cx, cz);
                for (int x = cx * 16; x < cx * 16 + 16; x++) {
                    for (int z = cz * 16; z < cz * 16 + 16; z++) {
                        for (int y = -64; y <= 64; y++) {
                            BlockState state = chunk.getBlockState(new BlockPos(x, y, z));
                            if (state.is(stoneOre)) stoneCount++;
                            if (state.is(deepOre)) deepCount++;
                            if (state.is(stoneOre) || state.is(deepOre)) {
                                check(y >= -56 && y <= 56, "Ore generated outside the vein height envelope: " + y);
                            }
                        }
                    }
                }
            }
        }
        check(stoneCount > 0, "No stone ore generated in four normal chunks");
        check(deepCount > 0, "No deepslate ore generated in four normal chunks");
        PyFish.LOGGER.info("[PyFish/OreGameTest] Natural chunks: stone={}, deepslate={}", stoneCount, deepCount);
        helper.succeed();
    }

    public static void targetsAndHeight(GameTestHelper helper) {
        ServerLevel level = noiseLevel(helper);
        BlockPos origin = new BlockPos(1024, 0, 1024);
        fill(level, origin, false);
        PlacedFeature feature = OreTestRegistry.feature(level, "controlled");
        check(level.getBiome(new BlockPos(origin.getX(), 30, origin.getZ())).value()
                .getGenerationSettings().hasFeature(feature), "Feature was not added to the test biome");
        boolean placed = feature.placeWithBiomeCheck(level, level.getChunkSource().getGenerator(), RandomSource.create(42),
                new BlockPos(origin.getX(), 30, origin.getZ()));
        int stone = 0;
        int deep = 0;
        for (int x = -8; x < 24; x++) {
            for (int z = -8; z < 24; z++) {
                for (int y = 8; y < 56; y++) {
                    BlockState state = level.getBlockState(new BlockPos(origin.getX() + x, y, origin.getZ() + z));
                    if (state.is(block("stone_ore"))) {
                        stone++;
                        check(y >= 30, "Stone target replaced deepslate");
                    }
                    if (state.is(block("deep_ore"))) {
                        deep++;
                        check(y < 30, "Deepslate target replaced stone");
                    }
                    if (state.is(block("stone_ore")) || state.is(block("deep_ore"))) {
                        check(y >= 16 && y <= 44, "Placed vein escaped the height envelope");
                    }
                }
            }
        }
        check(stone > 0 && deep > 0, "Expected both target replacements: placed=" + placed
                + ", stone=" + stone + ", deepslate=" + deep);
        fill(level, origin, true);
        feature.placeWithBiomeCheck(level, level.getChunkSource().getGenerator(), RandomSource.create(42),
                new BlockPos(origin.getX(), 30, origin.getZ()));
        for (int x = -8; x < 24; x++) {
            for (int z = -8; z < 24; z++) {
                for (int y = 8; y < 56; y++) {
                    check(level.getBlockState(new BlockPos(origin.getX() + x, y, origin.getZ() + z)).is(Blocks.DIRT),
                            "Ore replaced a block outside its target rules");
                }
            }
        }
        PyFish.LOGGER.info("[PyFish/OreGameTest] Controlled placement: stone={}, deepslate={}; dirt unchanged", stone, deep);
        helper.succeed();
    }

    public static void excludedBiome(GameTestHelper helper) {
        ServerLevel level = noiseLevel(helper);
        BlockPos origin = new BlockPos(1152, 0, 1024);
        fill(level, origin, false);
        PlacedFeature feature = OreTestRegistry.feature(level, "excluded");
        check(!level.getBiome(origin).value().getGenerationSettings().hasFeature(feature),
                "Nether-only feature was inserted into an overworld biome");
        boolean placed = feature.placeWithBiomeCheck(level, level.getChunkSource().getGenerator(), RandomSource.create(42),
                new BlockPos(origin.getX(), 30, origin.getZ()));
        check(!placed, "Biome filter allowed an excluded biome");
        PyFish.LOGGER.info("[PyFish/OreGameTest] Excluded biome rejected placement");
        helper.succeed();
    }

    private static void fill(ServerLevel level, BlockPos origin, boolean dirt) {
        for (int x = -8; x < 24; x++) {
            for (int z = -8; z < 24; z++) {
                for (int y = 8; y < 56; y++) {
                    BlockState state = dirt ? Blocks.DIRT.defaultBlockState()
                            : (y < 30 ? Blocks.DEEPSLATE : Blocks.STONE).defaultBlockState();
                    level.setBlock(new BlockPos(origin.getX() + x, y, origin.getZ() + z), state, 2);
                }
            }
        }
        // FULL chunks do not normally maintain the worldgen-only heightmap.
        for (int cx = (origin.getX() - 8) >> 4; cx <= (origin.getX() + 23) >> 4; cx++) {
            for (int cz = (origin.getZ() - 8) >> 4; cz <= (origin.getZ() + 23) >> 4; cz++) {
                Heightmap.primeHeightmaps(level.getChunk(cx, cz), Set.of(Heightmap.Types.OCEAN_FLOOR_WG));
            }
        }
    }

    private static Block block(String path) {
        Block block = MinecraftCompat.registryGet(BuiltInRegistries.BLOCK, "pyfish_ore_tests:" + path);
        check(block != null && block != Blocks.AIR, "Python did not register ore block: " + path);
        return block;
    }

    private static ServerLevel noiseLevel(GameTestHelper helper) {
        ServerLevel level = helper.getLevel().getServer().getLevel(
                MinecraftCompat.createResourceKey(Registries.DIMENSION, "pyfish_ore_tests:overworld"));
        check(level != null, "Noise-generated test dimension was not loaded");
        return level;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
