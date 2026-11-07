package fr.oesphy.pyfish.content.api;

import fr.oesphy.pyfish.api.event.PyFishEventObjects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class PyFishContentCallbackSupport {
    private PyFishContentCallbackSupport() {
    }

    public static ContentInteraction callItemUse(PythonEventHandler eventHandler, Level level, Player player, InteractionHand hand) {
        if (!eventHandler.hasHandler("use") || !(level instanceof ServerLevel serverLevel)) {
            return ContentInteraction.PASS;
        }

        return eventHandler.callInteraction("use", serverLevel, PyFishEventObjects.wrapContentPlayer(player, serverLevel), hand);
    }

    public static ContentInteraction callItemUseOn(PythonEventHandler eventHandler, UseOnContext context) {
        if (!eventHandler.hasHandler("use_on") || !(context.getLevel() instanceof ServerLevel serverLevel)) {
            return ContentInteraction.PASS;
        }

        return eventHandler.callInteraction("use_on", new PyFishEventObjects.ItemUseOnContextHandle(context, serverLevel));
    }

    public static void callItemEat(PythonEventHandler eventHandler, ItemStack stack, Level level, LivingEntity entity) {
        if (!eventHandler.hasHandler("eat") || !(level instanceof ServerLevel serverLevel)) {
            return;
        }

        eventHandler.call("eat", stack, serverLevel, PyFishEventObjects.wrapContentEntity(entity, serverLevel));
    }

    public static ContentInteraction callBlockUse(
            PythonEventHandler eventHandler,
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hitResult) {
        if (!eventHandler.hasHandler("use") || !(level instanceof ServerLevel serverLevel)) {
            return ContentInteraction.PASS;
        }

        return eventHandler.callInteraction(
                "use",
                stack,
                state,
                serverLevel,
                new PyFishEventObjects.BlockPosHandle(pos),
                PyFishEventObjects.wrapContentPlayer(player, serverLevel),
                hand,
                hitResult);
    }
}
