package fr.oesphy.pyfish.fabric.compat;

import fr.oesphy.pyfish.content.ContentProperties;
import net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;

public final class FabricBlockRenderCompat {
    private FabricBlockRenderCompat() {
    }

    public static void register() {
        ContentProperties.renderLayers().forEach((block, layer) -> {
            ChunkSectionLayer type = switch (layer) {
                case "cutout" -> ChunkSectionLayer.CUTOUT;
                case "cutout_mipped" -> java.util.Arrays.stream(ChunkSectionLayer.values())
                        .filter(value -> value.name().equals("CUTOUT_MIPPED")).findFirst().orElse(ChunkSectionLayer.CUTOUT);
                case "translucent" -> ChunkSectionLayer.TRANSLUCENT;
                default -> ChunkSectionLayer.SOLID;
            };
            BlockRenderLayerMap.putBlock(block, type);
        });
    }
}
