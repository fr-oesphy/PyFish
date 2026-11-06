package fr.oesphy.pyfish.content.item;

import fr.oesphy.pyfish.content.api.PythonEventHandler;
import fr.oesphy.pyfish.content.api.PyFishContentCallbackSupport;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;

public class ModularMultitool extends DiggerItem {
    private final PythonEventHandler eventHandler;
    private final ToolMaterial toolMaterial;

    public ModularMultitool(ToolMaterial toolMaterial, Properties properties, float attackDamage, float attackSpeed,
            Map<String, Object> events) {
        super(toolMaterial, BlockTags.MINEABLE_WITH_PICKAXE, attackDamage, attackSpeed, properties);
        this.toolMaterial = toolMaterial;
        this.eventHandler = new PythonEventHandler(events);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        PyFishContentCallbackSupport.callItemUse(eventHandler, level, player, hand);
        return super.use(level, player, hand);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        PyFishContentCallbackSupport.callItemUseOn(eventHandler, context);
        return super.useOn(context);
    }

    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
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
