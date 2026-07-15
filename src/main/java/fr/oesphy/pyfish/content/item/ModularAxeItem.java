package fr.oesphy.pyfish.content.item;

import fr.oesphy.pyfish.content.api.PythonEventHandler;
import fr.oesphy.pyfish.content.api.PyFishContentCallbackSupport;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.Map;

public class ModularAxeItem extends AxeItem {
    private final PythonEventHandler eventHandler;

    public ModularAxeItem(Tier tier, Properties properties, Map<String, Object> events) {
        super(tier, properties);
        this.eventHandler = new PythonEventHandler(events);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        PyFishContentCallbackSupport.callItemUse(eventHandler, level, player, hand);
        return super.use(level, player, hand);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        PyFishContentCallbackSupport.callItemUseOn(eventHandler, context);
        return super.useOn(context);
    }
}
