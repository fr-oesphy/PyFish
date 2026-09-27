package fr.oesphy.pyfish.content.api;

import fr.oesphy.pyfish.content.PyFishContentManager;
import fr.oesphy.pyfish.content.PyFishResourceGenerator;

import java.awt.image.BufferedImage;

public final class TextureAPI {
    private final PyFishContentManager manager;

    public TextureAPI(PyFishContentManager manager) {
        this.manager = manager;
    }

    public BufferedImage loadTexture(String textureId) {
        return PyFishResourceGenerator.loadTexture(manager.resolveTextureReference(textureId));
    }

    public void registerImage(String textureId, BufferedImage image) {
        try {
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(image, "png", buffer);
            registerTextureBytes(textureId, buffer.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException("Unable to register image " + textureId, e);
        }
    }

    public byte[] getTextureBytes(String textureId) {
        return PyFishResourceGenerator.getTextureBytes(manager.resolveTextureReference(textureId));
    }

    public void registerTextureBytes(String textureId, byte[] data) {
        PyFishResourceGenerator.registerTextureBytes(manager.resolveTextureReference(textureId), data);
    }
}
