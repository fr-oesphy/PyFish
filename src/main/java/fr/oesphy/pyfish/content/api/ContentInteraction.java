package fr.oesphy.pyfish.content.api;

import org.graalvm.polyglot.Value;
import java.util.Locale;

public enum ContentInteraction {
    PASS, SUCCESS, CONSUME, FAIL;

    public net.minecraft.world.InteractionResult vanilla() {
        return switch (this) {
            case PASS -> net.minecraft.world.InteractionResult.PASS;
            case SUCCESS -> net.minecraft.world.InteractionResult.SUCCESS;
            case CONSUME -> net.minecraft.world.InteractionResult.CONSUME;
            case FAIL -> net.minecraft.world.InteractionResult.FAIL;
        };
    }

    public static ContentInteraction from(Value result) {
        if (result == null || result.isNull()) return PASS;
        if (result.isBoolean()) return result.asBoolean() ? SUCCESS : FAIL;
        if (result.isString()) return parse(result.asString());
        throw new IllegalArgumentException("Content callbacks must return None, a boolean, or pass/success/consume/fail");
    }

    public static ContentInteraction parse(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
