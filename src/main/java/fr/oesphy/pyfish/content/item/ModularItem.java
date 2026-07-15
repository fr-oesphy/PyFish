package fr.oesphy.pyfish.content.item;

import fr.oesphy.pyfish.content.api.PythonEventHandler;
import fr.oesphy.pyfish.content.api.PyFishContentCallbackSupport;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.Map;

public class ModularItem extends Item {
    private final PythonEventHandler eventHandler;

    public ModularItem(Properties properties, Map<String, Object> events) {
        super(properties);
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

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        PyFishContentCallbackSupport.callItemEat(eventHandler, stack, level, entity);
        return super.finishUsingItem(stack, level, entity);
    }
}
