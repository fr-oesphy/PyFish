package fr.oesphy.pyfish.network;

import fr.oesphy.pyfish.PyFish;
import fr.oesphy.pyfish.compat.MinecraftCompat;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record PyFishClientHelloPayload(int protocolVersion, String loaderName) implements CustomPacketPayload {
    public static final int PROTOCOL_VERSION = 1;
    public static final Type<PyFishClientHelloPayload> TYPE = new Type<>(
            MinecraftCompat.id(PyFish.MODID, "client_hello"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PyFishClientHelloPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    PyFishClientHelloPayload::protocolVersion,
                    ByteBufCodecs.STRING_UTF8,
                    PyFishClientHelloPayload::loaderName,
                    PyFishClientHelloPayload::new);

    public static PyFishClientHelloPayload create(String loaderName) {
        return new PyFishClientHelloPayload(PROTOCOL_VERSION, loaderName);
    }

    @Override
    public Type<PyFishClientHelloPayload> type() {
        return TYPE;
    }
}
