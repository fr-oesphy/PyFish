package fr.oesphy.pyfish.compat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class VersionProfilesTest {
    private JsonObject profiles() throws Exception {
        return JsonParser.parseString(Files.readString(Path.of("../gradle/version-profiles.json")))
                .getAsJsonObject();
    }

    @Test
    void minecraft262UsesJava25AndUnobfuscatedArtifacts() throws Exception {
        JsonObject profile = profiles().getAsJsonObject("mc262");
        assertEquals("26.2", profile.get("minecraft_version").getAsString());
        assertEquals(25, profile.get("java_version").getAsInt());
        assertFalse(profile.get("minecraft_obfuscated").getAsBoolean());
        assertTrue(profile.get("neoforge_version_selector").getAsString().startsWith("26.2."));
        assertTrue(profile.get("neo_form_version_selector").getAsString().startsWith("26.2-"));
        assertTrue(profile.get("fabric_api_version_selector").getAsString().endsWith("+26.2"));
    }

    @Test
    void legacyProfilesUseJava25AndKeepRemapping() throws Exception {
        var properties = new java.util.Properties();
        try (var reader = Files.newBufferedReader(Path.of("../gradle.properties"))) {
            properties.load(reader);
        }
        assertEquals("25", properties.getProperty("java_version"));
        for (var entry : profiles().entrySet()) {
            if (!entry.getKey().startsWith("mc121")) {
                continue;
            }
            JsonObject profile = entry.getValue().getAsJsonObject();
            assertTrue(!profile.has("java_version") || profile.get("java_version").getAsInt() == 25,
                    entry.getKey());
            assertTrue(!profile.has("minecraft_obfuscated") || profile.get("minecraft_obfuscated").getAsBoolean(),
                    entry.getKey());
        }
    }

    @Test
    void artifactNamesAndFabricApiSuffixMatchEachTarget() throws Exception {
        for (var entry : profiles().entrySet()) {
            JsonObject profile = entry.getValue().getAsJsonObject();
            String version = profile.get("minecraft_version").getAsString();
            assertEquals(version, profile.get("minecraft_artifact_target").getAsString(), entry.getKey());
            assertEquals("+" + version, profile.get("fabric_api_minecraft_suffix").getAsString(), entry.getKey());
            assertTrue(profile.get("fabric_api_version_selector").getAsString().endsWith("+" + version),
                    entry.getKey());
        }
    }
}
