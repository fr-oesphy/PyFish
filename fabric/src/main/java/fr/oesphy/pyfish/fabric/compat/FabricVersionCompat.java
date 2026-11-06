package fr.oesphy.pyfish.fabric.compat;

import fr.oesphy.pyfish.network.PyFishClientHelloPayload;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class FabricVersionCompat {
    private FabricVersionCompat() {
    }

    public static CreativeModeTab.Builder tabBuilder() {
        return FabricItemGroup.builder();
    }

    public static void addToTab(ResourceKey<CreativeModeTab> tab, Item item) {
        ItemGroupEvents.modifyEntriesEvent(tab).register(entries -> entries.accept(item));
    }

    public static void registerClientHello() {
        PayloadTypeRegistry.playC2S().register(PyFishClientHelloPayload.TYPE, PyFishClientHelloPayload.STREAM_CODEC);
    }

    public static void onLevelLoad(BiConsumer<MinecraftServer, ServerLevel> callback) {
        ServerWorldEvents.LOAD.register(callback::accept);
    }

    public static void onLevelUnload(BiConsumer<MinecraftServer, ServerLevel> callback) {
        ServerWorldEvents.UNLOAD.register(callback::accept);
    }

    public static void onLevelTick(Consumer<ServerLevel> callback) {
        ServerTickEvents.END_WORLD_TICK.register(callback::accept);
    }

    public static void onDimensionChange(DimensionChange callback) {
        ServerEntityWorldChangeEvents.AFTER_ENTITY_CHANGE_WORLD.register(
                (original, entity, origin, destination) -> callback.accept(entity, origin, destination));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register(callback::accept);
    }

    @FunctionalInterface
    public interface DimensionChange {
        void accept(Entity entity, ServerLevel origin, ServerLevel destination);
    }
}
