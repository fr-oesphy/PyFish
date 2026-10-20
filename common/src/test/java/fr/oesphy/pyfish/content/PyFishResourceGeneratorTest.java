package fr.oesphy.pyfish.content;

import net.minecraft.server.packs.PackType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PyFishResourceGeneratorTest {
    @TempDir
    Path temporaryDirectory;

    @AfterEach
    void clearGeneratedResources() {
        PyFishResourceGenerator.clear();
    }

    @Test
    void loadsAssetsAndDataFromExternalModDirectory() throws Exception {
        Path modDirectory = temporaryDirectory.resolve("example_mod");
        Path texture = modDirectory.resolve("assets/example_mod/textures/item/beryllium.png");
        Path shorthandTexture = modDirectory.resolve("assets/textures/item/beryllium_pickaxe.png");
        Path legacyTexture = modDirectory.resolve("assets/texture/item/beryllium_ingot.png");
        Path recipe = modDirectory.resolve("data/example_mod/recipe/beryllium_block.json");
        Path shorthandRecipe = modDirectory.resolve("data/recipe/beryllium_ingot.json");
        Path misplaced = modDirectory.resolve("assets/readme.txt");

        byte[] textureBytes = new byte[] { (byte) 0x89, 0x50, 0x4e, 0x47 };
        byte[] shorthandTextureBytes = new byte[] { 1, 2, 3 };
        byte[] legacyTextureBytes = new byte[] { 4, 5, 6 };
        byte[] recipeBytes = "{\"type\":\"minecraft:crafting_shaped\"}".getBytes(StandardCharsets.UTF_8);
        Files.createDirectories(texture.getParent());
        Files.createDirectories(shorthandTexture.getParent());
        Files.createDirectories(legacyTexture.getParent());
        Files.createDirectories(recipe.getParent());
        Files.createDirectories(shorthandRecipe.getParent());
        Files.write(texture, textureBytes);
        Files.write(shorthandTexture, shorthandTextureBytes);
        Files.write(legacyTexture, legacyTextureBytes);
        Files.write(recipe, recipeBytes);
        Files.write(shorthandRecipe, recipeBytes);
        Files.writeString(misplaced, "not namespaced");

        PyFishResourceGenerator.addDirectoryResources(modDirectory, "example_mod");

        Map<String, byte[]> assets = PyFishResourceGenerator.getGeneratedResources(PackType.CLIENT_RESOURCES);
        Map<String, byte[]> data = PyFishResourceGenerator.getGeneratedResources(PackType.SERVER_DATA);
        assertArrayEquals(textureBytes, assets.get("example_mod:textures/item/beryllium.png"));
        assertArrayEquals(recipeBytes, data.get("example_mod:recipe/beryllium_block.json"));
        assertFalse(assets.containsKey("example_mod:textures/item/beryllium_pickaxe.png"));
        assertFalse(assets.containsKey("example_mod:textures/item/beryllium_ingot.png"));
        assertFalse(data.containsKey("example_mod:recipe/beryllium_ingot.json"));
        assertFalse(assets.containsKey("texture:item/beryllium_ingot.png"));
        assertFalse(assets.containsKey("pyfish:readme.txt"));
    }

    @Test
    void normalizesCommonTextureFileSyntaxAndFindsConventionalFolders() {
        PyFishResourceGenerator.registerTextureBytes("example_mod:item/beryllium", new byte[] { 1 });
        PyFishResourceGenerator.registerTextureBytes("example_mod:block/beryllium_block", new byte[] { 2 });

        assertEquals("example_mod:item/beryllium",
                PyFishResourceGenerator.resolveTextureReference(
                        "example_mod:textures/item/beryllium.png", "example_mod"));
        assertEquals("example_mod:item/beryllium",
                PyFishResourceGenerator.preferTextureFolder("example_mod:beryllium", "item"));
        assertEquals("example_mod:block/beryllium_block",
                PyFishResourceGenerator.preferTextureFolder("example_mod:beryllium_block", "block"));
    }
}
