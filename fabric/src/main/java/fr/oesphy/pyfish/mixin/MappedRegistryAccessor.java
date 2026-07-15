package fr.oesphy.pyfish.mixin;

import net.minecraft.core.MappedRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MappedRegistry.class)
public interface MappedRegistryAccessor {
    @Accessor("frozen")
    boolean pyfish$isFrozen();

    @Accessor("frozen")
    void pyfish$setFrozen(boolean frozen);
}
