package fr.oesphy.pyfish.content.block;

import fr.oesphy.pyfish.content.api.PythonEventHandler;
import fr.oesphy.pyfish.content.api.ContentInteraction;
import fr.oesphy.pyfish.content.api.PyFishContentCallbackSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Map;

public class ModularBlock extends ConfiguredBlock {
    private final PythonEventHandler eventHandler;

    public void bindLootTable(String id) {
        // Late Fabric registration must not leave a loot key cached under the fallback block ID.
        this.drops = fr.oesphy.pyfish.compat.MinecraftCompat.createResourceKey(
                net.minecraft.core.registries.Registries.LOOT_TABLE,
                fr.oesphy.pyfish.content.PyFishResourceGenerator.namespaceOf(id) + ":blocks/"
                        + fr.oesphy.pyfish.content.PyFishResourceGenerator.pathOf(id));
    }

    public ModularBlock(Properties properties, Map<String, Object> events) {
        this(properties, events, Map.of());
    }

    public ModularBlock(Properties properties, Map<String, Object> events, Map<String, Object> settings) {
        super(properties, settings);
        this.eventHandler = new PythonEventHandler(events);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hitResult) {
        var result = PyFishContentCallbackSupport.callBlockUse(eventHandler, stack, state, level, pos, player, hand, hitResult);
        if (result != ContentInteraction.PASS) return ItemInteractionResult.valueOf(result.name());
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }
}
