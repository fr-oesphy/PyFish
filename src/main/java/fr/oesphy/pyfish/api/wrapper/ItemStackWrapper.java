package fr.oesphy.pyfish.api.wrapper;

import net.minecraft.world.item.ItemStack;

/**
 * Wrapper for ItemStack with Python-friendly methods.
 */
public class ItemStackWrapper extends BaseWrapper<ItemStack> {
    public ItemStackWrapper(ItemStack stack) {
        super(stack);
    }

    public int getCount() {
        return wrapped.getCount();
    }

    public void setCount(int count) {
        wrapped.setCount(count);
    }

    public boolean isEmpty() {
        return wrapped.isEmpty();
    }

    public String getItem() {
        return wrapped.getItem().toString();
    }

    @Override
    public String toString() {
        return "ItemStack[item=" + getItem() + ", count=" + getCount() + "]";
    }
}
