package fr.oesphy.pyfish.config;

public record PyFishServerConfig(
        boolean requireClientPyFish,
        int clientHandshakeTimeoutTicks,
        String missingClientMessage) {
    public static final boolean DEFAULT_REQUIRE_CLIENT_PYFISH = false;
    public static final int DEFAULT_CLIENT_HANDSHAKE_TIMEOUT_TICKS = 60;
    public static final String DEFAULT_MISSING_CLIENT_MESSAGE =
            "This server requires PyFish on the client.";

    public static PyFishServerConfig defaults() {
        return new PyFishServerConfig(
                DEFAULT_REQUIRE_CLIENT_PYFISH,
                DEFAULT_CLIENT_HANDSHAKE_TIMEOUT_TICKS,
                DEFAULT_MISSING_CLIENT_MESSAGE);
    }

    public PyFishServerConfig sanitized() {
        int timeoutTicks = Math.max(1, clientHandshakeTimeoutTicks);
        String message = missingClientMessage == null || missingClientMessage.isBlank()
                ? DEFAULT_MISSING_CLIENT_MESSAGE
                : missingClientMessage;
        return new PyFishServerConfig(requireClientPyFish, timeoutTicks, message);
    }
}
