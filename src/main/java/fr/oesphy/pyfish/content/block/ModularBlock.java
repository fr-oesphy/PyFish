package fr.oesphy.pyfish.content.block;

import fr.oesphy.pyfish.content.api.PythonEventHandler;
import fr.oesphy.pyfish.content.api.PyFishContentCallbackSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Map;

public class ModularBlock extends Block {
    private final PythonEventHandler eventHandler;

    public ModularBlock(Properties properties, Map<String, Object> events) {
        super(properties);
        this.eventHandler = new PythonEventHandler(events);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hitResult) {
        PyFishContentCallbackSupport.callBlockUse(eventHandler, stack, state, level, pos, player, hand, hitResult);
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }
}
