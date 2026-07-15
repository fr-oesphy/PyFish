package fr.oesphy.pyfish.content.item;

import fr.oesphy.pyfish.content.api.PythonEventHandler;
import fr.oesphy.pyfish.content.api.PyFishContentCallbackSupport;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;

public class ModularMultitool extends DiggerItem {
    private final PythonEventHandler eventHandler;
    private final Tier tier;

    public ModularMultitool(Tier tier, Properties properties, float attackDamage, float attackSpeed,
            Map<String, Object> events) {
        super(tier, BlockTags.MINEABLE_WITH_PICKAXE,
                properties.attributes(createAttributes(tier, attackDamage, attackSpeed)));
        this.tier = tier;
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

    public static ItemAttributeModifiers createAttributes(Tier tier, float attackDamage, float attackSpeed) {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(BASE_ATTACK_DAMAGE_ID,
                                attackDamage + tier.getAttackDamageBonus(),
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED,
                        new AttributeModifier(BASE_ATTACK_SPEED_ID, attackSpeed,
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build();
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
            return tier.getSpeed();
        }
        return super.getDestroySpeed(stack, state);
    }
}
