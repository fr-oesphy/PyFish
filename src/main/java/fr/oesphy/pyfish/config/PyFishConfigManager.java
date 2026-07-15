package fr.oesphy.pyfish.config;

import fr.oesphy.pyfish.PyFish;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public final class PyFishConfigManager {
    private static final Path CONFIG_DIRECTORY = Paths.get("config", "pyfish");
    private static final Path SERVER_CONFIG_PATH = CONFIG_DIRECTORY.resolve("server.properties");

    private PyFishConfigManager() {
    }

    public static synchronized PyFishServerConfig loadServerConfig() {
        ensureServerConfigExists();

        Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(SERVER_CONFIG_PATH)) {
            properties.load(inputStream);
        } catch (IOException e) {
            PyFish.LOGGER.error("[PyFish] Failed to read server config at {}", SERVER_CONFIG_PATH, e);
            return PyFishServerConfig.defaults();
        }

        PyFishServerConfig defaults = PyFishServerConfig.defaults();
        boolean requireClient = parseBoolean(
                properties.getProperty("requireClientPyFish"),
                defaults.requireClientPyFish());
        int handshakeTimeoutTicks = parseInteger(
                properties.getProperty("clientHandshakeTimeoutTicks"),
                defaults.clientHandshakeTimeoutTicks());
        String missingClientMessage = properties.getProperty(
                "missingClientMessage",
                defaults.missingClientMessage());

        PyFishServerConfig config = new PyFishServerConfig(
                requireClient,
                handshakeTimeoutTicks,
                missingClientMessage).sanitized();

        PyFish.LOGGER.info(
                "[PyFish] Loaded server config: requireClientPyFish={}, clientHandshakeTimeoutTicks={}",
                config.requireClientPyFish(),
                config.clientHandshakeTimeoutTicks());

        return config;
    }

    private static void ensureServerConfigExists() {
        try {
            Files.createDirectories(CONFIG_DIRECTORY);
            if (Files.exists(SERVER_CONFIG_PATH)) {
                return;
            }

            Properties defaults = new Properties();
            defaults.setProperty(
                    "requireClientPyFish",
                    Boolean.toString(PyFishServerConfig.DEFAULT_REQUIRE_CLIENT_PYFISH));
            defaults.setProperty(
                    "clientHandshakeTimeoutTicks",
                    Integer.toString(PyFishServerConfig.DEFAULT_CLIENT_HANDSHAKE_TIMEOUT_TICKS));
            defaults.setProperty(
                    "missingClientMessage",
                    PyFishServerConfig.DEFAULT_MISSING_CLIENT_MESSAGE);

            try (OutputStream outputStream = Files.newOutputStream(SERVER_CONFIG_PATH)) {
                defaults.store(outputStream, """
                        PyFish server connection policy
                        requireClientPyFish=false keeps the client-side mod optional.
                        Set requireClientPyFish=true if this server should only allow players with PyFish installed.
                        clientHandshakeTimeoutTicks controls how long the server waits for the client handshake.
                        """);
            }

            PyFish.LOGGER.info("[PyFish] Created default server config at {}", SERVER_CONFIG_PATH.toAbsolutePath());
        } catch (IOException e) {
            PyFish.LOGGER.error("[PyFish] Failed to create default server config at {}", SERVER_CONFIG_PATH, e);
        }
    }

    private static boolean parseBoolean(String value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        return Boolean.parseBoolean(value.trim());
    }

    private static int parseInteger(String value, int fallback) {
        if (value == null) {
            return fallback;
        }

        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            PyFish.LOGGER.warn("[PyFish] Invalid integer value '{}' in {}", value, SERVER_CONFIG_PATH);
            return fallback;
        }
    }
}
