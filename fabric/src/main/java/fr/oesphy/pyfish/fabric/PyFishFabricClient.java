package fr.oesphy.pyfish.fabric;

import fr.oesphy.pyfish.network.PyFishClientHelloPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class PyFishFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        fr.oesphy.pyfish.fabric.compat.FabricBlockRenderCompat.register();
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (ClientPlayNetworking.canSend(PyFishClientHelloPayload.TYPE)) {
                ClientPlayNetworking.send(PyFishClientHelloPayload.create("fabric"));
            }
        });
    }
}
