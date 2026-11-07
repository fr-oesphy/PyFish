package fr.oesphy.pyfish.content.block;

import fr.oesphy.pyfish.content.ContentProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;

public class ConfiguredBlock extends Block {
    private final VoxelShape outline;
    private final VoxelShape collision;

    public ConfiguredBlock(Properties properties, Map<String, Object> settings) {
        super(properties);
        outline = ContentProperties.shape(settings.get("shape"));
        collision = Boolean.TRUE.equals(settings.get("noCollision")) ? Shapes.empty()
                : settings.containsKey("collisionShape") ? ContentProperties.shape(settings.get("collisionShape")) : outline;
        ContentProperties.trackRenderLayer(this, settings);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return outline != null ? outline : super.getShape(state, level, pos, context);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return collision != null ? collision : super.getCollisionShape(state, level, pos, context);
    }
}
