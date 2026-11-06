package fr.oesphy.pyfish.api.wrapper;

import net.minecraft.network.chat.Component;

import java.util.Objects;

/**
 * Wraps a Minecraft {@link Component} (chat/text component) for use in Python scripts.
 */
public final class TextWrapper {

    private final Component component;

    private TextWrapper(Component component) {
        this.component = Objects.requireNonNull(component, "Component must not be null");
    }

    // -------------------------------------------------------------------------
    // Factory methods
    // -------------------------------------------------------------------------

    /**
     * Creates a literal text component.
     *
     * @param text the raw text string; must not be {@code null}
     * @return a new {@link TextWrapper}
     */
    public static TextWrapper literal(String text) {
        Objects.requireNonNull(text, "text must not be null");
        return new TextWrapper(Component.literal(text));
    }

    /**
     * Creates a translatable text component.
     *
     * @param key  the translation key; must not be {@code null}
     * @param args optional format arguments
     * @return a new {@link TextWrapper}
     */
    public static TextWrapper translatable(String key, Object... args) {
        Objects.requireNonNull(key, "translation key must not be null");
        return new TextWrapper(Component.translatable(key, args));
    }

    /**
     * Wraps an existing {@link Component}.
     *
     * @param component the component; must not be {@code null}
     * @return a new {@link TextWrapper}
     */
    public static TextWrapper of(Component component) {
        return new TextWrapper(component);
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the plain-text string of the component via {@link Component#getString()}.
     *
     * @return plain string
     */
    public String string() {
        return component.getString();
    }

    /** @return the underlying {@link Component} */
    public Component unwrap() {
        return component;
    }

    // -------------------------------------------------------------------------
    // Object overrides
    // -------------------------------------------------------------------------

    @Override
    public String toString() {
        return string();
    }
}
