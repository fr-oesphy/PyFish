package fr.oesphy.pyfish.content.item;

import net.minecraft.world.item.ToolMaterial;

import java.util.Map;

public class ModularSwordItem extends ModularItem {
    public ModularSwordItem(ToolMaterial toolMaterial, float attackDamage, float attackSpeed,
            Properties properties, Map<String, Object> events) {
        super(properties.sword(toolMaterial, attackDamage, attackSpeed), events);
    }
}
