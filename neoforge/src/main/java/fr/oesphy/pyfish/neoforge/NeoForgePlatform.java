package fr.oesphy.pyfish.neoforge;

import fr.oesphy.pyfish.platform.PlatformEventBridge;
import fr.oesphy.pyfish.platform.PlatformPythonModuleLocator;
import fr.oesphy.pyfish.platform.PyFishPlatform;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;

import java.nio.file.Path;
import java.util.Optional;

public class NeoForgePlatform implements PyFishPlatform {
    private final PlatformPythonModuleLocator pythonModuleLocator = new NeoForgePythonModuleLocator();
    private final PlatformEventBridge eventBridge;

    public NeoForgePlatform(IEventBus modEventBus) {
        this.eventBridge = new NeoForgeEventBridge(modEventBus);
    }

    @Override
    public String getLoaderName() {
        return "NeoForge";
    }

    @Override
    public PlatformPythonModuleLocator getPythonModuleLocator() {
        return pythonModuleLocator;
    }

    @Override
    public PlatformEventBridge getEventBridge() {
        return eventBridge;
    }

    @Override
    public Optional<Path> getRuntimeResourceRoot() {
        return ModList.get().getMods().stream()
                .filter(mod -> "pyfish".equals(mod.getModId()))
                .findFirst()
                .map(mod -> mod.getOwningFile().getFile().getFilePath());
    }
}
