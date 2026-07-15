package fr.oesphy.pyfish.platform;

import java.util.function.Consumer;

public interface PlatformEventBridge {
    boolean registerEvent(String eventNameOrClass, Consumer<Object[]> dispatcher);
}
