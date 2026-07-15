package fr.oesphy.pyfish.network;

import fr.oesphy.pyfish.PyFish;
import fr.oesphy.pyfish.compat.MinecraftCompat;
import fr.oesphy.pyfish.config.PyFishConfigManager;
import fr.oesphy.pyfish.config.PyFishServerConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PyFishClientPresenceService {
    private static final PyFishClientPresenceService INSTANCE = new PyFishClientPresenceService();

    private final Map<UUID, Integer> pendingPlayers = new ConcurrentHashMap<>();
    private volatile PyFishServerConfig serverConfig = PyFishServerConfig.defaults();

    private PyFishClientPresenceService() {
    }

    public static PyFishClientPresenceService getInstance() {
        return INSTANCE;
    }

    public void onServerStarting() {
        pendingPlayers.clear();
        serverConfig = PyFishConfigManager.loadServerConfig();
    }

    public void onServerStopped() {
        pendingPlayers.clear();
    }

    public void onPlayerJoin(ServerPlayer player, int currentServerTick) {
        if (!serverConfig.requireClientPyFish()) {
            pendingPlayers.remove(player.getUUID());
            return;
        }

        // Use server ticks rather than wall time so a paused or overloaded server does not
        // consume the client's handshake window while it cannot process packets.
        int deadlineTick = currentServerTick + serverConfig.clientHandshakeTimeoutTicks();
        pendingPlayers.put(player.getUUID(), deadlineTick);
        PyFish.LOGGER.debug(
                "[PyFish] Waiting for PyFish client hello from {} until tick {}",
                MinecraftCompat.getPlayerName(player),
                deadlineTick);
    }

    public void onPlayerLeave(ServerPlayer player) {
        pendingPlayers.remove(player.getUUID());
    }

    public void onClientHello(ServerPlayer player, PyFishClientHelloPayload payload) {
        pendingPlayers.remove(player.getUUID());
        PyFish.LOGGER.info(
                "[PyFish] Received client hello from {} via {} (protocol v{})",
                MinecraftCompat.getPlayerName(player),
                payload.loaderName(),
                payload.protocolVersion());
    }

    public void onServerTick(MinecraftServer server) {
        if (!serverConfig.requireClientPyFish() || pendingPlayers.isEmpty()) {
            return;
        }

        int currentTick = server.getTickCount();
        Iterator<Map.Entry<UUID, Integer>> iterator = pendingPlayers.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Integer> entry = iterator.next();
            if (currentTick < entry.getValue()) {
                continue;
            }

            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            iterator.remove();
            if (player == null) {
                continue;
            }

            PyFish.LOGGER.info(
                    "[PyFish] Disconnecting {} because this server requires PyFish on the client.",
                    MinecraftCompat.getPlayerName(player));
            player.connection.disconnect(Component.literal(serverConfig.missingClientMessage()));
        }
    }
}