package fr.oesphy.pyfish.gametest;

import fr.oesphy.pyfish.compat.MinecraftCompat;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

final class OreTestRegistry {
    static boolean refusedUse(net.minecraft.world.item.Item item, ServerLevel level, net.minecraft.world.entity.player.Player player) {
        return item.use(level, player, net.minecraft.world.InteractionHand.MAIN_HAND)
                == net.minecraft.world.InteractionResult.FAIL;
    }

    static boolean refusedBlockUse(net.minecraft.world.level.block.state.BlockState state, ServerLevel level,
            net.minecraft.world.entity.player.Player player, net.minecraft.world.phys.BlockHitResult hit) {
        return state.useItemOn(player.getMainHandItem(), level, player, net.minecraft.world.InteractionHand.MAIN_HAND, hit)
                == net.minecraft.world.InteractionResult.FAIL;
    }

    static PlacedFeature feature(ServerLevel level, String path) {
        return MinecraftCompat.registryGet(level.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE),
                "pyfish_ore_tests:" + path);
    }
}
