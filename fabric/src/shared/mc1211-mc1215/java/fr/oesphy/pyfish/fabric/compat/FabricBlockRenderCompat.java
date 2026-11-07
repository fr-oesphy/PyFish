package fr.oesphy.pyfish.fabric.compat;

import fr.oesphy.pyfish.content.ContentProperties;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.minecraft.client.renderer.RenderType;

public final class FabricBlockRenderCompat {
    private FabricBlockRenderCompat() {
    }

    public static void register() {
        ContentProperties.renderLayers().forEach((block, layer) -> {
            RenderType type = switch (layer) {
                case "cutout" -> RenderType.cutout();
                case "cutout_mipped" -> RenderType.cutoutMipped();
                case "translucent" -> RenderType.translucent();
                default -> RenderType.solid();
            };
            BlockRenderLayerMap.INSTANCE.putBlock(block, type);
        });
    }
}
