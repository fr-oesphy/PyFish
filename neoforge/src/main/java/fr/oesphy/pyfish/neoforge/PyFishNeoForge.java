package fr.oesphy.pyfish.neoforge;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import fr.oesphy.pyfish.PyFish;
import fr.oesphy.pyfish.api.CommandAPI;
import fr.oesphy.pyfish.api.event.PyFishEventObjects;
import fr.oesphy.pyfish.compat.MinecraftCompat;
import fr.oesphy.pyfish.content.PyFishContentManager;
import fr.oesphy.pyfish.content.PyFishRecipeApplier;
import fr.oesphy.pyfish.neoforge.content.NeoForgePyFishContentBackend;
import fr.oesphy.pyfish.network.PyFishClientHelloPayload;
import fr.oesphy.pyfish.network.PyFishClientPresenceService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@Mod(PyFish.MODID)
public class PyFishNeoForge {
    private final NeoForgePyFishContentBackend contentBackend;

    public PyFishNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        this.contentBackend = new NeoForgePyFishContentBackend(modEventBus);
        PyFishContentManager.bootstrap(contentBackend);
        if (!PyFish.isInitialized()) {
            PyFish.initialize(new NeoForgePlatform(modEventBus));
        }

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerPayloadHandlers);
        modEventBus.addListener(this::onBuildCreativeModeTabContents);
        modEventBus.addListener(this::onAddPackFinders);
        NeoForge.EVENT_BUS.register(this);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        PyFish.LOGGER.info("[PyFish] NeoForge common setup complete");
        // Register commands during common setup
        event.enqueueWork(() -> {
            // Commands are registered via the Commands class in NeoForge
            // We'll use a different approach - register through the mod event bus later
        });
    }

    private void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(Integer.toString(PyFishClientHelloPayload.PROTOCOL_VERSION))
                .optional();
        registrar.playToServer(PyFishClientHelloPayload.TYPE, PyFishClientHelloPayload.STREAM_CODEC,
                (payload, context) -> PyFishClientPresenceService.getInstance().onClientHello(
                        (net.minecraft.server.level.ServerPlayer) context.player(),
                        payload));
    }

    private void onBuildCreativeModeTabContents(BuildCreativeModeTabContentsEvent event) {
        contentBackend.onBuildCreativeModeTabContents(event);
    }

    private void onAddPackFinders(AddPackFindersEvent event) {
        contentBackend.onAddPackFinders(event);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        PyFishClientPresenceService.getInstance().onServerStarting();
        PyFish.getPythonRuntime().getMinecraftAPI().trigger(
                "on_server_starting",
                new PyFishEventObjects.ServerEvent(event.getServer()));

        // Register commands
        CommandDispatcher<CommandSourceStack> dispatcher = event.getServer().getCommands().getDispatcher();
        registerPyFishCommands(dispatcher);
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        PyFishRecipeApplier.applyQueuedRemovals(event.getServer());
    }

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            PyFishClientPresenceService.getInstance().onPlayerJoin(player, MinecraftCompat.getServer(player).getTickCount());
        }
    }

    @SubscribeEvent
    public void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            PyFishClientPresenceService.getInstance().onPlayerLeave(player);
        }
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        PyFishClientPresenceService.getInstance().onServerTick(event.getServer());
        // Update task scheduler
        if (PyFish.isInitialized()) {
            PyFish.getPythonRuntime().getTaskScheduler().onServerTick(event.getServer());
        }
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        PyFishClientPresenceService.getInstance().onServerStopped();
    }

    private void registerPyFishCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("pyfish")
                .requires(source -> source.hasPermission(2)) // Require OP level 2
                .then(Commands.literal("reload")
                        .executes(this::executeReloadAll)
                        .then(Commands.literal("server")
                                .executes(this::executeReloadServer)))
                .then(Commands.literal("scripts")
                        .executes(this::executeScripts))
                .then(Commands.literal("status")
                        .executes(this::executeStatus))
                .then(Commands.literal("validate")
                        .executes(this::executeValidate))
                .then(Commands.literal("help")
                        .executes(this::executeHelp)));
    }

    private int executeReloadAll(CommandContext<CommandSourceStack> context) {
        String result = CommandAPI.executeCommand("reload", new String[0], null);
        context.getSource().sendSuccess(() -> Component.literal(result), true);
        return 1;
    }

    private int executeReloadServer(CommandContext<CommandSourceStack> context) {
        String result = CommandAPI.executeCommand("reload", new String[]{"server"}, null);
        context.getSource().sendSuccess(() -> Component.literal(result), true);
        return 1;
    }

    private int executeScripts(CommandContext<CommandSourceStack> context) {
        String result = CommandAPI.executeCommand("scripts", new String[0], null);
        context.getSource().sendSuccess(() -> Component.literal(result), false);
        return 1;
    }

    private int executeStatus(CommandContext<CommandSourceStack> context) {
        String result = CommandAPI.executeCommand("status", new String[0], null);
        context.getSource().sendSuccess(() -> Component.literal(result), false);
        return 1;
    }

    private int executeHelp(CommandContext<CommandSourceStack> context) {
        String result = CommandAPI.executeCommand("help", new String[0], null);
        context.getSource().sendSuccess(() -> Component.literal(result), false);
        return 1;
    }

    private int executeValidate(CommandContext<CommandSourceStack> context) {
        String result = CommandAPI.executeCommand("validate", new String[0], null);
        context.getSource().sendSuccess(() -> Component.literal(result), false);
        return 1;
    }
}
