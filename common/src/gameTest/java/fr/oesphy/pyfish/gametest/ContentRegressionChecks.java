package fr.oesphy.pyfish.gametest;

import com.mojang.authlib.GameProfile;
import fr.oesphy.pyfish.PyFish;
import fr.oesphy.pyfish.compat.MinecraftCompat;
import fr.oesphy.pyfish.content.ParticleSupport;
import fr.oesphy.pyfish.content.PyFishRecipeApplier;
import fr.oesphy.pyfish.content.PyFishResourceGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ContentRegressionChecks {
    private ContentRegressionChecks() {
    }

    private static Item item(String name) {
        return MinecraftCompat.registryGet(BuiltInRegistries.ITEM, "pyfish_ore_tests:" + name);
    }

    private static void check(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }

    private static ItemStack assemble(Recipe<CraftingInput> recipe, CraftingInput input,
            net.minecraft.server.level.ServerLevel level) {
        // The registry-lookup parameter was removed in 26.2; fixtures run in named development mappings.
        var method = java.util.Arrays.stream(Recipe.class.getMethods())
                .filter(candidate -> candidate.getName().equals("assemble")).findFirst().orElseThrow();
        try {
            return (ItemStack) method.invoke(recipe, method.getParameterCount() == 1
                    ? new Object[] {input} : new Object[] {input, level.registryAccess()});
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to craft the regression recipe", e);
        }
    }

    public static void blocks(GameTestHelper helper) {
        var level = helper.getLevel();
        Block block = MinecraftCompat.registryGet(BuiltInRegistries.BLOCK, "pyfish_ore_tests:glass_slab");
        var state = block.defaultBlockState();
        var pos = helper.absolutePos(BlockPos.ZERO);
        level.setBlock(pos, state, 3);
        check(!state.canOcclude(), "Transparent block still occludes its neighbours");
        check(state.getLightEmission() == 7, "Block emitted light was ignored");
        check(state.getSoundType() == SoundType.GLASS, "Block sound was ignored");
        check(state.getCollisionShape(level, pos).max(Direction.Axis.Y) == 0.5, "Custom collision shape was ignored");
        var composed = fr.oesphy.pyfish.content.ContentProperties.shape(List.of(
                List.of(0, 0, 0, 16, 4, 16), List.of(4, 4, 4, 12, 16, 12)));
        check(composed.max(Direction.Axis.Y) == 1.0 && composed.toAabbs().size() > 1, "Compound shape lost its boxes");
        check(new ItemStack(item("glass_slab")).getMaxStackSize() == 16, "Block-item properties were ignored");
        var drops = Block.getDrops(state, level, pos, null, null, new ItemStack(item("high_pickaxe")));
        check(drops.stream().anyMatch(stack -> stack.is(item("glass_slab"))), "Default block loot is missing: " + block.getLootTable());
        Block ghost = MinecraftCompat.registryGet(BuiltInRegistries.BLOCK, "pyfish_ore_tests:ghost");
        check(ghost.defaultBlockState().getCollisionShape(level, pos).isEmpty(), "noCollision was ignored");
        check(Block.getDrops(ghost.defaultBlockState(), level, pos, null).isEmpty(), "drops=none was ignored");
        helper.succeed();
    }

    public static void tools(GameTestHelper helper) {
        var obsidian = Blocks.OBSIDIAN.defaultBlockState();
        check(!new ItemStack(item("low_pickaxe")).isCorrectToolForDrops(obsidian), "Low tier pickaxe can harvest obsidian");
        check(new ItemStack(item("high_pickaxe")).isCorrectToolForDrops(obsidian), "High tier pickaxe cannot harvest obsidian");
        check(!new ItemStack(item("low_multitool")).isCorrectToolForDrops(obsidian), "Multitool bypasses material restrictions");
        Block customBlock = MinecraftCompat.registryGet(BuiltInRegistries.BLOCK, "pyfish_ore_tests:glass_slab");
        var custom = customBlock.defaultBlockState();
        check(!new ItemStack(item("low_pickaxe")).isCorrectToolForDrops(custom), "Custom block requiredTier was ignored");
        check(new ItemStack(item("high_pickaxe")).isCorrectToolForDrops(custom), "Custom block miningTool tag was ignored");
        helper.succeed();
    }

    @SuppressWarnings("unchecked")
    public static void recipes(GameTestHelper helper) {
        var level = helper.getLevel();
        PyFishRecipeApplier.applyQueuedRemovals(level.getServer());
        check(!PyFishRecipeApplier.applyQueuedRemovals(level.getServer()), "Recipe changes are not idempotent");
        check(Set.copyOf(PyFishResourceGenerator.getItemsByTag("pyfish_ore_tests:test_ingots"))
                .equals(Set.of("minecraft:iron_ingot", "pyfish_ore_tests:preferred_ingot")), "Authored/API tag members were lost");
        PyFishResourceGenerator.addItemTag("pyfish_ore_tests:unloaded_tag", "minecraft:iron_ingot");
        check(PyFishResourceGenerator.getItemsByTag("pyfish_ore_tests:unloaded_tag").isEmpty(),
                "Startup resources overrode the live registry's absent tag");
        boolean replaced = false;
        boolean ordered = false;
        boolean shaped = false;
        var input = CraftingInput.of(1, 1, List.of(new ItemStack(Items.COBBLESTONE)));
        for (var holder : level.getServer().getRecipeManager().getRecipes()) {
            String id = MinecraftCompat.resourceKeyIdString(holder.id());
            check(!id.equals("pyfish_ore_tests:removed_recipe"), "Queued recipe removal failed");
            check(!id.equals("pyfish_ore_tests:filtered_recipe"), "Filtered recipe removal failed");
            if (id.equals("pyfish_ore_tests:ordered_recipe")) {
                check(assemble((Recipe<CraftingInput>) holder.value(), input, level).is(Items.STONE), "Recipe edits were applied more than once");
                ordered = true;
            }
            if (id.equals("pyfish_ore_tests:output_recipe") || id.equals("pyfish_ore_tests:shaped_recipe")) {
                var recipe = (Recipe<CraftingInput>) holder.value();
                check(recipe.matches(input, level), "Generated recipe does not match its input");
                ItemStack output = assemble(recipe, input, level);
                if (id.endsWith("output_recipe")) {
                    check(output.is(item("preferred_ingot")) && output.getCount() == 2, "Explicit output replacement failed");
                    replaced = true;
                } else {
                    check(output.is(Items.STICK), "Shaped recipe has the wrong output");
                    shaped = true;
                }
            }
        }
        check(replaced && shaped && ordered, "Minecraft rejected one of the edited recipes");
        helper.succeed();
    }

    public static void interactions(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        Item wand = item("blocking_wand");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(wand));
        check(OreTestRegistry.refusedUse(wand, helper.getLevel(), player), "Python use result was discarded");
        var pos = helper.absolutePos(BlockPos.ZERO);
        var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        check(wand.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit)) == InteractionResult.CONSUME,
                "Python use_on result was discarded");
        Block block = MinecraftCompat.registryGet(BuiltInRegistries.BLOCK, "pyfish_ore_tests:glass_slab");
        check(OreTestRegistry.refusedBlockUse(block.defaultBlockState(), helper.getLevel(), player, hit),
                "Python block-use result was discarded");
        helper.succeed();
    }

    public static void inventoryAndParticles(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = new ServerPlayer(level.getServer(), level,
                new GameProfile(UUID.randomUUID(), "pyfish-regression"), ClientInformation.createDefault());
        var pos = helper.absolutePos(BlockPos.ZERO).above(2);
        MinecraftCompat.setEntityPosition(player, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            player.getInventory().setItem(slot, new ItemStack(Items.DIRT, 64));
        }
        PyFish.getPythonRuntime().getMinecraftAPI().giveItem(player, "pyfish_ore_tests:preferred_ingot", 131);
        int dropped = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(4)).stream()
                .map(ItemEntity::getItem).filter(stack -> stack.is(item("preferred_ingot"))).mapToInt(ItemStack::getCount).sum();
        check(dropped == 131, "give_item lost the full-inventory remainder");
        check(level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(4)).stream()
                .map(ItemEntity::getItem).allMatch(stack -> stack.getCount() <= stack.getMaxStackSize()),
                "give_item emitted an oversized dropped stack");
        var original = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState());
        var json = ParticleTypes.BLOCK.codec().codec().encodeStart(
                level.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE), original).getOrThrow();
        Map<String, Object> options = new com.google.gson.Gson().fromJson(json, Map.class);
        check(ParticleSupport.create(level, "minecraft:block", options).getType() == ParticleTypes.BLOCK,
                "Parameterized block particles failed to decode");
        check(ParticleSupport.create(level, "minecraft:happy_villager", Map.of()).getType() == ParticleTypes.HAPPY_VILLAGER,
                "Simple particle compatibility regressed");
        helper.succeed();
    }
}
