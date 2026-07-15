package fr.oesphy.pyfish.content;

import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;

import java.util.Objects;
import java.util.function.Supplier;

public final class PyFishLegacyTierFactory {
    private PyFishLegacyTierFactory() {
    }

    public static Tier create(int uses, float speed, float damageBonus, int enchantmentValue,
            TagKey<Block> incorrectBlocks, Supplier<Ingredient> repairSupplier) {
        return new PyFishLegacyTier(uses, speed, damageBonus, enchantmentValue, incorrectBlocks, repairSupplier);
    }

    private record PyFishLegacyTier(
            int uses,
            float speed,
            float damageBonus,
            int enchantmentValue,
            TagKey<Block> incorrectBlocks,
            Supplier<Ingredient> repairSupplier) implements Tier {
        private PyFishLegacyTier {
            Objects.requireNonNull(incorrectBlocks, "incorrectBlocks");
            Objects.requireNonNull(repairSupplier, "repairSupplier");
        }

        @Override
        public int getUses() {
            return uses;
        }

        @Override
        public float getSpeed() {
            return speed;
        }

        @Override
        public float getAttackDamageBonus() {
            return damageBonus;
        }

        @Override
        public TagKey<Block> getIncorrectBlocksForDrops() {
            return incorrectBlocks;
        }

        @Override
        public int getEnchantmentValue() {
            return enchantmentValue;
        }

        @Override
        public Ingredient getRepairIngredient() {
            return repairSupplier.get();
        }
    }
}
