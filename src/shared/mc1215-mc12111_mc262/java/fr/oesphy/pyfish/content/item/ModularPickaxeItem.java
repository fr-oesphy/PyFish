package fr.oesphy.pyfish.content.item;

import net.minecraft.world.item.ToolMaterial;

import java.util.Map;

public class ModularPickaxeItem extends ModularItem {
    public ModularPickaxeItem(ToolMaterial toolMaterial, float attackDamage, float attackSpeed,
            Properties properties, Map<String, Object> events) {
        super(properties.pickaxe(toolMaterial, attackDamage, attackSpeed), events);
    }
}
