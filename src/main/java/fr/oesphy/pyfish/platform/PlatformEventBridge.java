package fr.oesphy.pyfish.platform;

import java.util.function.Consumer;
import java.util.function.Function;

public interface PlatformEventBridge {
    boolean registerEvent(String eventNameOrClass, Consumer<Object[]> dispatcher);

    boolean registerNativeEvent(String alias, String eventSource, Function<Object[], Object> dispatcher);
}
