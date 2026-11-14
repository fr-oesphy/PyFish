package fr.oesphy.pyfish.content.item;

import fr.oesphy.pyfish.content.api.PythonEventHandler;
import fr.oesphy.pyfish.content.api.ContentInteraction;
import fr.oesphy.pyfish.content.api.PyFishContentCallbackSupport;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.Map;

public class ModularShovelItem extends ShovelItem {
    private final PythonEventHandler eventHandler;

    public ModularShovelItem(ToolMaterial toolMaterial, float attackDamage, float attackSpeed,
            Properties properties, Map<String, Object> events) {
        super(toolMaterial, attackDamage, attackSpeed, properties);
        this.eventHandler = new PythonEventHandler(events);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        var result = PyFishContentCallbackSupport.callItemUse(eventHandler, level, player, hand);
        if (result != ContentInteraction.PASS) return result.vanilla();
        return super.use(level, player, hand);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        var result = PyFishContentCallbackSupport.callItemUseOn(eventHandler, context);
        if (result != ContentInteraction.PASS) return result.vanilla();
        return super.useOn(context);
    }
}
