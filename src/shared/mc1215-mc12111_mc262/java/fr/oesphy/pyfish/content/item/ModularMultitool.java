package fr.oesphy.pyfish.content.item;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;

public class ModularMultitool extends ModularItem {
    private final ToolMaterial toolMaterial;

    public ModularMultitool(ToolMaterial toolMaterial, Properties properties, float attackDamage, float attackSpeed,
            Map<String, Object> events) {
        super(properties.tool(toolMaterial, BlockTags.MINEABLE_WITH_PICKAXE, attackDamage, attackSpeed, 1.0f), events);
        this.toolMaterial = toolMaterial;
    }

    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        if (state.is(toolMaterial.incorrectBlocksForDrops())) return false;
        return state.is(BlockTags.MINEABLE_WITH_PICKAXE)
                || state.is(BlockTags.MINEABLE_WITH_AXE)
                || state.is(BlockTags.MINEABLE_WITH_SHOVEL)
                || state.is(BlockTags.MINEABLE_WITH_HOE)
                || super.isCorrectToolForDrops(stack, state);
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)
                || state.is(BlockTags.MINEABLE_WITH_AXE)
                || state.is(BlockTags.MINEABLE_WITH_SHOVEL)
                || state.is(BlockTags.MINEABLE_WITH_HOE)) {
            return toolMaterial.speed();
        }
        return super.getDestroySpeed(stack, state);
    }
}
