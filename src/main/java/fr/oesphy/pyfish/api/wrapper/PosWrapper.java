package fr.oesphy.pyfish.api.wrapper;

import net.minecraft.core.BlockPos;

import java.util.Objects;

/**
 * Wrapper around {@link BlockPos} for use in Python scripts.
 *
 * <p>Instances are immutable; all mutation methods return new instances.
 */
public final class PosWrapper {

    private final BlockPos pos;

    private PosWrapper(BlockPos pos) {
        this.pos = Objects.requireNonNull(pos, "BlockPos must not be null");
    }

    // -------------------------------------------------------------------------
    // Factory methods
    // -------------------------------------------------------------------------

    /**
     * Creates a {@link PosWrapper} from integer coordinates.
     *
     * @param x x-coordinate
     * @param y y-coordinate
     * @param z z-coordinate
     * @return a new {@link PosWrapper}
     */
    public static PosWrapper of(int x, int y, int z) {
        return new PosWrapper(new BlockPos(x, y, z));
    }

    /**
     * Wraps an existing {@link BlockPos}.
     *
     * @param pos the block position; must not be {@code null}
     * @return a new {@link PosWrapper}
     */
    public static PosWrapper of(BlockPos pos) {
        return new PosWrapper(pos);
    }

    // -------------------------------------------------------------------------
    // Coordinate accessors
    // -------------------------------------------------------------------------

    /** @return the x-coordinate */
    public int x() { return pos.getX(); }

    /** @return the y-coordinate */
    public int y() { return pos.getY(); }

    /** @return the z-coordinate */
    public int z() { return pos.getZ(); }

    // -------------------------------------------------------------------------
    // Movement helpers
    // -------------------------------------------------------------------------

    /**
     * Returns a new {@link PosWrapper} offset by the given deltas.
     *
     * @param dx x delta
     * @param dy y delta
     * @param dz z delta
     * @return new offset wrapper
     */
    public PosWrapper offset(int dx, int dy, int dz) {
        return new PosWrapper(pos.offset(dx, dy, dz));
    }

    /**
     * Returns a new {@link PosWrapper} one block above this position.
     *
     * @return wrapper above this one
     */
    public PosWrapper above() {
        return new PosWrapper(pos.above());
    }

    /**
     * Returns a new {@link PosWrapper} one block below this position.
     *
     * @return wrapper below this one
     */
    public PosWrapper below() {
        return new PosWrapper(pos.below());
    }

    // -------------------------------------------------------------------------
    // Unwrap
    // -------------------------------------------------------------------------

    /** @return the underlying {@link BlockPos} */
    public BlockPos unwrap() {
        return pos;
    }

    // -------------------------------------------------------------------------
    // Object overrides
    // -------------------------------------------------------------------------

    @Override
    public String toString() {
        return "(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PosWrapper other)) return false;
        return pos.equals(other.pos);
    }

    @Override
    public int hashCode() {
        return pos.hashCode();
    }
}
