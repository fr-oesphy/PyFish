package fr.oesphy.pyfish.api.wrapper;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.Objects;

/**
 * Wraps a {@link ItemStack} for use in Python scripts.
 *
 * <p>All accessors use Python-friendly snake_case names.
 *
 * <p><b>Note on bootstrap:</b> {@link #of(ItemStack)}, {@link #of(String, int)},
 * and most accessors require Minecraft to be bootstrapped.  {@link #empty()} uses a
 * lazy holder so that the class can be loaded without triggering MC's static
 * initializers — allowing pure-Java tests to import the class safely.
 */
public final class ItemStackWrapper {

    private final ItemStack stack;

    private ItemStackWrapper(ItemStack stack) {
        this.stack = Objects.requireNonNull(stack, "ItemStack must not be null");
    }

    // -------------------------------------------------------------------------
    // Lazy empty holder (bootstrap-safe)
    // -------------------------------------------------------------------------

    /**
     * Lazily holds the singleton empty wrapper.
     * The class is not loaded until {@link #empty()} is first called, which defers
     * the access to {@link ItemStack#EMPTY} until MC is bootstrapped.
     */
    private static final class EmptyHolder {
        static final ItemStackWrapper INSTANCE = new ItemStackWrapper(ItemStack.EMPTY);
    }

    // -------------------------------------------------------------------------
    // Factory methods
    // -------------------------------------------------------------------------

    /**
     * Wraps an existing {@link ItemStack}.
     *
     * @param stack the item stack; must not be {@code null}
     * @return a new {@link ItemStackWrapper}
     */
    public static ItemStackWrapper of(ItemStack stack) {
        return new ItemStackWrapper(stack);
    }

    /**
     * Creates a wrapper from an item registry ID and a count.
     * Requires Minecraft to be bootstrapped.
     *
     * @param itemId the registry key, e.g. {@code "minecraft:stone"}
     * @param count  the stack count
     * @return a new {@link ItemStackWrapper}; air-stack if the item is not registered
     */
    public static ItemStackWrapper of(String itemId, int count) {
        Objects.requireNonNull(itemId, "itemId must not be null");
        ResourceLocation rl = fr.oesphy.pyfish.api.convert.TypeCoerce.toResourceLocation(itemId);
        // In MC 1.21.1, BuiltInRegistries.ITEM.get(ResourceLocation) returns
        // Optional<Holder.Reference<Item>>. Unwrap it; fall back to air on a miss.
        java.util.Optional<? extends net.minecraft.world.item.Item> resolved =
                BuiltInRegistries.ITEM.getOptional(rl);
        net.minecraft.world.item.Item item = resolved.isPresent()
                ? resolved.get()
                : net.minecraft.world.item.Items.AIR;
        return new ItemStackWrapper(new ItemStack(item, count));
    }

    /**
     * Returns a wrapper around an empty {@link ItemStack}.
     *
     * <p>Uses a lazy holder — safe to call before MC bootstrap <em>as long as the
     * JVM does not resolve {@link ItemStack#EMPTY}</em>.  In practice this means
     * calling {@code empty()} without bootstrap will throw
     * {@link ExceptionInInitializerError}; guard with {@code assumeTrue(isMcBootstrapped())}
     * in tests that call it.
     *
     * @return an empty wrapper
     */
    public static ItemStackWrapper empty() {
        return EmptyHolder.INSTANCE;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the registry key of the item, e.g. {@code "minecraft:stone"}.
     *
     * @return item id string
     */
    public String id() {
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key != null ? key.toString() : "minecraft:air";
    }

    /** @return the stack count */
    public int count() {
        return stack.getCount();
    }

    /** @return {@code true} if the stack is empty */
    public boolean isEmpty() {
        return stack.isEmpty();
    }

    /**
     * Returns the NBT custom data of this stack, or an empty wrapper if no data is present.
     *
     * @return the NBT wrapper
     */
    public NbtWrapper nbt() {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null || customData.isEmpty()) {
            return NbtWrapper.empty();
        }
        return NbtWrapper.of(customData.copyTag());
    }

    /** @return the underlying {@link ItemStack} */
    public ItemStack unwrap() {
        return stack;
    }

    // -------------------------------------------------------------------------
    // Object overrides
    // -------------------------------------------------------------------------

    @Override
    public String toString() {
        return count() + " x " + id();
    }
}
