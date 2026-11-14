package fr.oesphy.pyfish.content.item;

import fr.oesphy.pyfish.content.api.PythonEventHandler;
import fr.oesphy.pyfish.content.api.ContentInteraction;
import fr.oesphy.pyfish.content.api.PyFishContentCallbackSupport;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.Map;

public class ModularDiggerItem extends DiggerItem {
    private final PythonEventHandler eventHandler;

    public ModularDiggerItem(Tier tier, TagKey<Block> blocks, Properties properties, Map<String, Object> events) {
        super(tier, blocks, properties);
        this.eventHandler = new PythonEventHandler(events);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var result = PyFishContentCallbackSupport.callItemUse(eventHandler, level, player, hand);
        if (result != ContentInteraction.PASS) return new InteractionResultHolder<>(result.vanilla(), player.getItemInHand(hand));
        return super.use(level, player, hand);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        var result = PyFishContentCallbackSupport.callItemUseOn(eventHandler, context);
        if (result != ContentInteraction.PASS) return result.vanilla();
        return super.useOn(context);
    }
}
