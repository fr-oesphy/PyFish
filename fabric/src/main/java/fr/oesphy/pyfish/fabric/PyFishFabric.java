package fr.oesphy.pyfish.fabric;

import fr.oesphy.pyfish.PyFish;
import fr.oesphy.pyfish.content.PyFishContentManager;
import fr.oesphy.pyfish.content.PyFishRecipeApplier;
import fr.oesphy.pyfish.api.event.PyFishEventObjects;
import fr.oesphy.pyfish.fabric.content.FabricPyFishContentBackend;
import fr.oesphy.pyfish.network.PyFishClientHelloPayload;
import fr.oesphy.pyfish.network.PyFishClientPresenceService;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public class PyFishFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        PyFishContentManager.bootstrap(new FabricPyFishContentBackend());
        if (!PyFish.isInitialized()) {
            PyFish.initialize(new FabricPlatform());
        }

        PayloadTypeRegistry.playC2S().register(PyFishClientHelloPayload.TYPE, PyFishClientHelloPayload.STREAM_CODEC);

        ServerPlayNetworking.registerGlobalReceiver(PyFishClientHelloPayload.TYPE, (payload, context) ->
                PyFishClientPresenceService.getInstance().onClientHello(context.player(), payload));

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            PyFishClientPresenceService.getInstance().onServerStarting();
            PyFish.getPythonRuntime().getMinecraftAPI().trigger(
                    "on_server_starting",
                    new PyFishEventObjects.ServerEvent(server));
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(server ->
                PyFishClientPresenceService.getInstance().onServerStopped());

        ServerLifecycleEvents.SERVER_STARTED.register(server ->
                PyFishRecipeApplier.applyQueuedRemovals(server));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                PyFishClientPresenceService.getInstance().onPlayerJoin(handler.getPlayer(), server.getTickCount()));

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                PyFishClientPresenceService.getInstance().onPlayerLeave(handler.getPlayer()));

        ServerTickEvents.END_SERVER_TICK.register(server ->
                PyFishClientPresenceService.getInstance().onServerTick(server));
    }
}