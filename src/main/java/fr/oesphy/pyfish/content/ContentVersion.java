package fr.oesphy.pyfish.content;

import java.io.IOException;
import java.util.Properties;

public final class ContentVersion {
    private static final String MINECRAFT = loadVersion();

    private ContentVersion() {
    }

    private static String loadVersion() {
        try (var stream = ContentVersion.class.getResourceAsStream("/pyfish-build.properties")) {
            if (stream == null) throw new IllegalStateException("Missing PyFish build capabilities");
            Properties properties = new Properties();
            properties.load(stream);
            return properties.getProperty("minecraft");
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read PyFish build capabilities", e);
        }
    }

    public static boolean legacyIngredients() {
        return MINECRAFT.equals("1.21.1");
    }

    public static boolean automaticRenderLayers() {
        return MINECRAFT.startsWith("26.");
    }

    public static String namedRenderLayer(String requested) {
        return MINECRAFT.equals("1.21.11") && requested.equals("cutout_mipped") ? "cutout" : requested;
    }
}
