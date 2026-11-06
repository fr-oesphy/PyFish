package fr.oesphy.pyfish;

import fr.oesphy.pyfish.platform.PyFishPlatform;

/**
 * Shared initialisation hook called by both loader entrypoints.
 *
 * <p>This class exists so that the common initialisation path has the name called
 * for in the architecture specification. Both
 * {@code PyFishFabric.onInitialize()} and {@code PyFishNeoForge}'s constructor
 * delegate here via {@link PyFish#initialize(PyFishPlatform)}.
 *
 * <p>Future phases will add side-specific hooks ({@code onServerSetup},
 * {@code onClientSetup}) here rather than scattering them across the loader
 * modules.
 */
public final class PyFishCommon {

    private PyFishCommon() {
    }

    /**
     * Entry point called once by both loaders during their startup phase.
     *
     * <p>Must be called <em>after</em> the platform's content backend has been
     * bootstrapped into {@link fr.oesphy.pyfish.content.PyFishContentManager} and
     * <em>before</em> registries are frozen.
     *
     * @param platform the loader-specific platform implementation
     */
    public static void init(PyFishPlatform platform) {
        PyFish.initialize(platform);
    }
}
