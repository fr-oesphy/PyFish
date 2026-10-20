package fr.oesphy.pyfish.api.wrapper;

import net.minecraft.core.BlockPos;

/**
 * Wrapper for block positions with Python-friendly access.
 */
public class Pos extends BaseWrapper<BlockPos> {
    public Pos(BlockPos pos) {
        super(pos);
    }

    public Pos(int x, int y, int z) {
        super(new BlockPos(x, y, z));
    }

    public int getX() {
        return wrapped.getX();
    }

    public int getY() {
        return wrapped.getY();
    }

    public int getZ() {
        return wrapped.getZ();
    }

    public Pos offset(int x, int y, int z) {
        return new Pos(wrapped.offset(x, y, z));
    }

    @Override
    public String toString() {
        return "Pos(" + getX() + ", " + getY() + ", " + getZ() + ")";
    }
}
