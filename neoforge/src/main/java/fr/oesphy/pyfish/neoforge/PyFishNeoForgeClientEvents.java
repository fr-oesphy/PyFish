package fr.oesphy.pyfish.neoforge;

import fr.oesphy.pyfish.PyFish;
import fr.oesphy.pyfish.network.PyFishClientHelloPayload;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

@EventBusSubscriber(modid = PyFish.MODID, value = Dist.CLIENT)
public final class PyFishNeoForgeClientEvents {
    private PyFishNeoForgeClientEvents() {
    }

    @SubscribeEvent
    public static void onClientLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        if (NetworkRegistry.hasChannel(event.getConnection(), ConnectionProtocol.PLAY, PyFishClientHelloPayload.TYPE.id())) {
            sendToServer(PyFishClientHelloPayload.create("neoforge"));
        }
    }

    private static void sendToServer(CustomPacketPayload payload) {
        if (invokeDistributor("net.neoforged.neoforge.client.network.ClientPacketDistributor", payload)) {
            return;
        }

        if (invokeDistributor("net.neoforged.neoforge.network.PacketDistributor", payload)) {
            return;
        }

        throw new IllegalStateException("No compatible NeoForge client packet distributor was found");
    }

    private static boolean invokeDistributor(String className, CustomPacketPayload payload) {
        try {
            Class<?> distributorClass = Class.forName(className);
            distributorClass.getMethod("sendToServer", CustomPacketPayload.class, CustomPacketPayload[].class)
                    .invoke(null, payload, (Object) new CustomPacketPayload[0]);
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            return false;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to send PyFish client hello through " + className, e);
        }
    }
}
